package com.drivecinema.desktop.importer

import com.drivecinema.app.core.model.DriveFolder
import com.drivecinema.app.core.network.GoogleDriveGateway
import com.drivecinema.app.core.player.TokenProvider
import com.drivecinema.app.core.player.TokenRefreshInterceptor
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** A file or folder in a Drive folder listing. */
data class DriveChild(val id: String, val name: String, val mimeType: String, val sizeBytes: Long?) {
    val isFolder: Boolean get() = mimeType == GoogleDriveGateway.FOLDER_MIME_TYPE
    fun asFolder() = DriveFolder(id, name, isFolder = isFolder, mimeType = mimeType)
}

/** Where a resumable upload stands, from Drive's answer. */
sealed interface UploadStatus {
    data class Done(val fileId: String) : UploadStatus
    data class Incomplete(val nextOffset: Long) : UploadStatus
    /** The session URI is unknown or older than a week: start a new upload (after the duplicate check). */
    data object Expired : UploadStatus
}

/** The upload session pointed somewhere other than Google over HTTPS: never retried, nothing is sent there. */
class UntrustedUploadAddressException : IOException("Unexpected upload address from Google Drive; upload stopped.")

/** Drive answered with an error. [retryable]: try again later with backoff; [quotaExhausted]: pause the queue. */
class DriveApiException(val httpCode: Int, val reason: String?, message: String) : IOException(message) {
    val retryable: Boolean get() = httpCode >= 500 || httpCode == 429 || (httpCode == 403 && reason in RATE_LIMIT_REASONS)
    val quotaExhausted: Boolean get() = httpCode == 403 && reason in QUOTA_REASONS

    private companion object {
        val RATE_LIMIT_REASONS = setOf("rateLimitExceeded", "userRateLimitExceeded")
        /** Daily upload limit (750 GB/day per user) and a full Drive: nothing helps until tomorrow / until space is freed. */
        val QUOTA_REASONS = setOf("dailyLimitExceeded", "uploadLimitExceeded", "storageQuotaExceeded")
    }
}

/**
 * The only Drive calls that can change anything (Task I.1, scope decision A): list, create a folder, and the
 * resumable upload of a **new** file. There are deliberately no update, delete, trash, move or rename calls;
 * `DriveWriteClientTest` checks the methods and paths sent. The token goes only to Google over HTTPS
 * ([TokenRefreshInterceptor], report F-02).
 */
class DriveWriteClient(
    tokenProvider: TokenProvider,
    baseClient: OkHttpClient = OkHttpClient(),
    private val apiBase: String = "https://www.googleapis.com/drive/v3",
    internal val uploadBase: String = "https://www.googleapis.com/upload/drive/v3"
) {
    internal val client: OkHttpClient = baseClient.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .addInterceptor(TokenRefreshInterceptor(tokenProvider))
        .build()

    /** Folders and files directly in [parentId] (not trashed). */
    fun listChildren(parentId: String): List<DriveChild> = query("'${escape(parentId)}' in parents and trashed = false")

    /** Files named exactly [name] in [parentId], used to avoid uploading the same file twice. */
    fun findByName(parentId: String, name: String): List<DriveChild> =
        query("'${escape(parentId)}' in parents and name = '${escape(name)}' and trashed = false")

    fun createFolder(name: String, parentId: String): DriveFolder {
        val metadata = JSONObject()
            .put("name", name)
            .put("mimeType", GoogleDriveGateway.FOLDER_MIME_TYPE)
            .put("parents", JSONArray().put(parentId))
        val url = "$apiBase/files".toHttpUrl().newBuilder()
            .addQueryParameter("fields", "id, name, mimeType")
            .addQueryParameter("supportsAllDrives", "true")
            .build()
        val request = Request.Builder().url(url).post(metadata.toString().toRequestBody(JSON)).build()
        val json = execute(request) { JSONObject(it.body!!.string()) }
        return DriveFolder(json.getString("id"), json.getString("name"))
    }

    /**
     * Starts a resumable upload of a new file into [parentId]; returns the session URI (valid for a week, stored by the
     * queue so the upload survives a restart). The URI must point back to Google over HTTPS: the token is never sent
     * anywhere else (report F-02).
     */
    fun startUpload(name: String, parentId: String, sizeBytes: Long, mimeType: String): String {
        val metadata = JSONObject().put("name", name).put("parents", JSONArray().put(parentId))
        val url = "$uploadBase/files".toHttpUrl().newBuilder()
            .addQueryParameter("uploadType", "resumable")
            .addQueryParameter("supportsAllDrives", "true")
            .addQueryParameter("fields", "id, name, size")
            .build()
        val request = Request.Builder().url(url)
            .header("X-Upload-Content-Length", sizeBytes.toString())
            .header("X-Upload-Content-Type", mimeType)
            .post(metadata.toString().toRequestBody(JSON))
            .build()
        val location = execute(request) { it.header("Location") }
            ?: throw IOException("Google Drive did not return an upload address.")
        val sessionUrl = location.toHttpUrlOrNull()
        if (sessionUrl == null || !mayReceiveToken(sessionUrl)) throw UntrustedUploadAddressException()
        return location
    }

    /** Sends [bytes] as the part of the file starting at [offset]. */
    fun uploadChunk(sessionUri: String, bytes: ByteArray, offset: Long, totalBytes: Long): UploadStatus {
        val end = offset + bytes.size - 1
        val request = Request.Builder().url(sessionUri)
            .header("Content-Range", "bytes $offset-$end/$totalBytes")
            .put(bytes.toRequestBody(OCTET_STREAM))
            .build()
        return uploadStatus(request)
    }

    /** Asks how much of the file Drive already has (after an error or an app restart). */
    fun queryUpload(sessionUri: String, totalBytes: Long): UploadStatus {
        val request = Request.Builder().url(sessionUri)
            .header("Content-Range", "bytes */$totalBytes")
            .put(ByteArray(0).toRequestBody(null))
            .build()
        return uploadStatus(request)
    }

    private fun uploadStatus(request: Request): UploadStatus = client.newCall(request).execute().use { response ->
        when (response.code) {
            200, 201 -> UploadStatus.Done(JSONObject(response.body!!.string()).getString("id"))
            // "Range: bytes=0-N" = Drive has N + 1 bytes; no header = nothing yet. Never assume a whole chunk arrived.
            308 -> UploadStatus.Incomplete(response.header("Range")?.substringAfterLast('-')?.toLongOrNull()?.plus(1) ?: 0L)
            404 -> UploadStatus.Expired
            else -> throw apiError(response)
        }
    }

    private fun query(q: String): List<DriveChild> {
        val children = mutableListOf<DriveChild>()
        var pageToken: String? = null
        do {
            val url = "$apiBase/files".toHttpUrl().newBuilder()
                .addQueryParameter("q", q)
                .addQueryParameter("fields", "nextPageToken, files(id, name, mimeType, size)")
                .addQueryParameter("supportsAllDrives", "true")
                .addQueryParameter("includeItemsFromAllDrives", "true")
                .addQueryParameter("pageSize", "1000")
                .apply { pageToken?.let { addQueryParameter("pageToken", it) } }
                .build()
            val json = execute(Request.Builder().url(url).get().build()) { JSONObject(it.body!!.string()) }
            val files = json.optJSONArray("files") ?: JSONArray()
            for (i in 0 until files.length()) {
                val file = files.getJSONObject(i)
                children += DriveChild(
                    id = file.getString("id"),
                    name = file.getString("name"),
                    mimeType = file.optString("mimeType"),
                    sizeBytes = file.optString("size").toLongOrNull()
                )
            }
            pageToken = json.optString("nextPageToken").ifEmpty { null }
        } while (pageToken != null)
        return children
    }

    internal fun <T> execute(request: Request, onSuccess: (Response) -> T): T =
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw apiError(response)
            onSuccess(response)
        }

    internal fun apiError(response: Response): DriveApiException {
        val error = runCatching { JSONObject(response.body?.string().orEmpty()).getJSONObject("error") }.getOrNull()
        val reason = error?.optJSONArray("errors")?.optJSONObject(0)?.optString("reason")?.ifEmpty { null }
            ?: error?.optString("status")?.ifEmpty { null }
        val message = error?.optString("message")?.ifEmpty { null } ?: "HTTP ${response.code}"
        return DriveApiException(response.code, reason, "Google Drive refused the request: $message")
    }

    internal companion object {
        val JSON = "application/json; charset=UTF-8".toMediaType()
        val OCTET_STREAM = "application/octet-stream".toMediaType()
        private val LOOPBACK_HOSTS = setOf("localhost", "127.0.0.1")

        /** The same rule as [TokenRefreshInterceptor]: Google over HTTPS (plain HTTP only for loopback test servers). */
        fun mayReceiveToken(url: okhttp3.HttpUrl): Boolean =
            (url.isHttps || url.host in LOOPBACK_HOSTS) && TokenRefreshInterceptor.isTrustedGoogleHost(url.host)

        /** Quotes inside a Drive query string (`name = '...'`). */
        fun escape(value: String): String = value.replace("\\", "\\\\").replace("'", "\\'")
    }
}
