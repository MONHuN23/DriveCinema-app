package com.drivecinema.desktop.host

import com.drivecinema.app.core.metadata.VideoFiles
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.io.path.isDirectory
import kotlin.io.path.name

/** One entry of a shared folder listing. */
data class HostEntry(val id: String, val name: String, val isFolder: Boolean, val sizeBytes: Long?)

/**
 * What the PC shares (Task I.2): its local library folders, read-only. Clients never see paths: every folder and
 * file has an ID (an HMAC of its path with a secret only this PC knows), stable across restarts, so source keys and
 * watch progress stay valid. Only videos and folders are listed or served; hidden entries and anything outside the
 * shared folders (including through symbolic links) are not.
 */
class HostFileIndex(
    private val sharedFolders: () -> List<Path>,
    secret: ByteArray,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private var lastRebuild = Long.MIN_VALUE / 2
    private val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(secret, "HmacSHA256")) }
    private val known = ConcurrentHashMap<String, Path>()

    fun id(path: Path): String = synchronized(mac) {
        Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(path.toString().toByteArray(Charsets.UTF_8))).take(22)
    }

    /** The shared folders that exist right now. */
    fun roots(): List<HostEntry> = sharedRoots().map { HostEntry(remember(it), it.name, isFolder = true, sizeBytes = null) }

    /** Children of a shared folder; null for an unknown or non-shared ID. */
    fun children(folderId: String): List<HostEntry>? {
        val folder = resolve(folderId)?.takeIf { it.isDirectory(LinkOption.NOFOLLOW_LINKS) } ?: return null
        return try {
            Files.list(folder).use { stream ->
                stream.filter { !it.name.startsWith(".") && isInsideRoots(it) }
                    .map { path ->
                        val isFolder = path.isDirectory(LinkOption.NOFOLLOW_LINKS)
                        when {
                            isFolder -> HostEntry(remember(path), path.name, true, null)
                            Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && VideoFiles.isVideo(null, path.name) ->
                                HostEntry(remember(path), path.name, false, runCatching { Files.size(path) }.getOrNull())
                            else -> null
                        }
                    }
                    .toList()
                    .filterNotNull()
                    .sortedBy { it.name.lowercase() }
            }
        } catch (e: IOException) {
            null
        }
    }

    /** The video file for an ID, or null (unknown, not a video, or outside the shared folders). */
    fun file(fileId: String): Path? =
        resolve(fileId)?.takeIf { Files.isRegularFile(it, LinkOption.NOFOLLOW_LINKS) && VideoFiles.isVideo(null, it.name) }

    private fun resolve(id: String): Path? {
        known[id]?.let { return it.takeIf { path -> isInsideRoots(path) } }
        // Unknown (e.g. after a restart): walk the shared folders once to learn the IDs again.
        rebuild()
        return known[id]?.takeIf { isInsideRoots(it) }
    }

    /** At most every [REBUILD_INTERVAL_MS], so unknown IDs can't keep the disk busy. */
    @Synchronized
    private fun rebuild() {
        if (clock() - lastRebuild < REBUILD_INTERVAL_MS) return
        lastRebuild = clock()
        for (root in sharedRoots()) {
            remember(root)
            runCatching {
                Files.walk(root).use { stream ->
                    stream.filter { path -> root.relativize(path).none { it.name.startsWith(".") } }
                        .forEach { remember(it) }
                }
            }
        }
    }

    private fun remember(path: Path): String = id(path).also { known[it] = path }

    private fun sharedRoots(): List<Path> = sharedFolders().map { it.toAbsolutePath().normalize() }.filter { it.isDirectory() }

    /** True only for paths inside a shared folder, after resolving symbolic links. */
    private fun isInsideRoots(path: Path): Boolean {
        val real = runCatching { path.toRealPath() }.getOrNull() ?: return false
        return sharedRoots().any { root -> runCatching { real.startsWith(root.toRealPath()) }.getOrDefault(false) }
    }

    private companion object {
        const val REBUILD_INTERVAL_MS = 30_000L
    }
}
