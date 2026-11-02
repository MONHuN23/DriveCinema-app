package com.drivecinema.desktop.importer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.random.Random

/** One local file to upload into the Drive folder [parentId]. [sizeBytes] and [lastModifiedMs] are what the detector saw. */
data class UploadRequest(
    val file: Path,
    val fileName: String,
    val parentId: String,
    val sizeBytes: Long,
    val lastModifiedMs: Long
)

sealed interface UploadOutcome {
    data class Uploaded(val fileId: String) : UploadOutcome
    /** A file with the same name and size is already in the folder: nothing was uploaded. */
    data class AlreadyOnDrive(val fileId: String) : UploadOutcome
}

/** The local file changed while it was being uploaded; the upload stops and the detector reports it again later. */
class FileChangedException(message: String) : IOException(message)

/**
 * Task I.1: uploads one file with Drive's resumable protocol. The session URI is handed to [onSession] as soon as it
 * exists (null once the upload is done), so after a crash or restart the queue passes it back and the upload goes on
 * from what Drive already has instead of starting a second copy. Retries 5xx, 429, rate limits and dropped connections
 * with exponential backoff; a full quota or Drive is reported at once (the queue pauses). The local file is only read.
 */
class ResumableUploader(
    private val drive: DriveWriteClient,
    private val chunkSize: Int = 8 * 1024 * 1024,
    private val maxAttempts: Int = 6,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
    private val random: Random = Random.Default
) {
    init {
        require(chunkSize > 0 && chunkSize % CHUNK_GRANULARITY == 0) { "Drive needs chunks in multiples of 256 KiB" }
    }

    suspend fun upload(
        request: UploadRequest,
        storedSessionUri: String? = null,
        onSession: (String?) -> Unit = {},
        onProgress: (bytesOnDrive: Long) -> Unit = {}
    ): UploadOutcome = withContext(Dispatchers.IO) { uploadLoop(request, storedSessionUri, onSession, onProgress) }

    private suspend fun uploadLoop(
        request: UploadRequest,
        storedSessionUri: String?,
        onSession: (String?) -> Unit,
        onProgress: (Long) -> Unit
    ): UploadOutcome {
        var sessionUri = storedSessionUri
        // Only a stored session may already hold bytes; a new one starts at 0 without asking.
        var resume = storedSessionUri != null
        var restarts = 0
        while (true) {
            if (sessionUri == null) {
                findDuplicate(request)?.let { return UploadOutcome.AlreadyOnDrive(it) }
                sessionUri = retrying { drive.startUpload(request.fileName, request.parentId, request.sizeBytes, mimeTypeOf(request.fileName)) }
                onSession(sessionUri)
            }
            val status = try {
                send(request, sessionUri, resume, onProgress)
            } catch (e: DriveApiException) {
                // Any other 4xx during a resumable upload: Google says start again with a new session.
                if (e.retryable || e.quotaExhausted || e.httpCode !in 400..499) throw e
                UploadStatus.Expired
            }
            if (status is UploadStatus.Done) {
                onSession(null)
                return UploadOutcome.Uploaded(status.fileId)
            }
            // Expired or refused session: forget it and start over; the duplicate check runs again first.
            if (++restarts > MAX_RESTARTS) throw IOException("Google Drive keeps refusing the upload of ${request.fileName}; try again later.")
            sessionUri = null
            resume = false
            onSession(null)
        }
    }

    /** Sends the rest of the file; returns Done, or Expired when the session is gone. */
    private suspend fun send(request: UploadRequest, sessionUri: String, resume: Boolean, onProgress: (Long) -> Unit): UploadStatus {
        var status: UploadStatus = if (resume) retrying { drive.queryUpload(sessionUri, request.sizeBytes) } else UploadStatus.Incomplete(0)
        FileChannel.open(request.file, StandardOpenOption.READ).use { channel ->
            while (status is UploadStatus.Incomplete) {
                // Cancel/Pause take effect between chunks: the HTTP calls block, so this is where the uploader stops
                // (at most one chunk later). Found on the device: a cancelled upload used to run to the end.
                currentCoroutineContext().ensureActive()
                val offset = (status as UploadStatus.Incomplete).nextOffset
                onProgress(offset)
                requireUnchanged(request)
                val chunk = read(channel, offset, minOf(chunkSize.toLong(), request.sizeBytes - offset).toInt())
                status = retrying(
                    onRetry = { drive.queryUpload(sessionUri, request.sizeBytes) }
                ) { drive.uploadChunk(sessionUri, chunk, offset, request.sizeBytes) }
            }
        }
        if (status is UploadStatus.Done) onProgress(request.sizeBytes)
        return status
    }

    /**
     * Runs [block], retrying temporary failures with backoff. With [onRetry] (a status query after a failed chunk)
     * the retry asks Drive where it stands instead of resending blindly.
     */
    private suspend fun <T> retrying(onRetry: (() -> T)? = null, block: () -> T): T {
        var attempt = 0
        var next: () -> T = block
        while (true) {
            try {
                return next()
            } catch (e: IOException) {
                if (e is FileChangedException || e is NeedsReviewException || e is UntrustedUploadAddressException || (e is DriveApiException && !e.retryable)) throw e
                if (++attempt >= maxAttempts) throw e
                sleep(backoffMs(attempt))
                next = onRetry ?: block
            }
        }
    }

    private fun backoffMs(attempt: Int): Long =
        minOf(MAX_BACKOFF_MS, 1_000L shl (attempt - 1)) + random.nextLong(0, 1_000)

    /** Same name and size: already uploaded (e.g. the app stopped before it could note the upload). Same name, other size: ask. */
    private fun findDuplicate(request: UploadRequest): String? {
        val sameName = drive.findByName(request.parentId, request.fileName).filter { !it.isFolder }
        sameName.firstOrNull { it.sizeBytes == request.sizeBytes }?.let { return it.id }
        if (sameName.isNotEmpty()) throw NeedsReviewException("A different file named \"${request.fileName}\" is already in that Drive folder.")
        return null
    }

    private fun requireUnchanged(request: UploadRequest) {
        val size = runCatching { Files.size(request.file) }.getOrNull()
        val modified = runCatching { Files.getLastModifiedTime(request.file).toMillis() }.getOrNull()
        if (size != request.sizeBytes || modified != request.lastModifiedMs) {
            throw FileChangedException("${request.fileName} changed while it was being uploaded.")
        }
    }

    private fun read(channel: FileChannel, offset: Long, length: Int): ByteArray {
        val buffer = ByteBuffer.allocate(length)
        var position = offset
        while (buffer.hasRemaining()) {
            val read = channel.read(buffer, position)
            if (read < 0) throw FileChangedException("The file got shorter while it was being uploaded.")
            position += read
        }
        return buffer.array()
    }

    companion object {
        const val CHUNK_GRANULARITY = 256 * 1024
        private const val MAX_BACKOFF_MS = 64_000L
        private const val MAX_RESTARTS = 2

        private val MIME_TYPES = mapOf(
            "mkv" to "video/x-matroska", "mp4" to "video/mp4", "m4v" to "video/x-m4v", "avi" to "video/x-msvideo",
            "mov" to "video/quicktime", "wmv" to "video/x-ms-wmv", "webm" to "video/webm", "ts" to "video/mp2t"
        )

        fun mimeTypeOf(fileName: String): String =
            MIME_TYPES[fileName.substringAfterLast('.', "").lowercase()] ?: "application/octet-stream"
    }
}
