package com.drivecinema.desktop.importer

import com.drivecinema.app.core.logging.AppLog
import com.drivecinema.app.core.metadata.VideoFiles
import java.io.Closeable
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.ClosedWatchServiceException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.StandardWatchEventKinds.ENTRY_CREATE
import java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY
import java.nio.file.StandardWatchEventKinds.OVERFLOW
import java.nio.file.WatchKey
import java.nio.file.WatchService
import kotlin.io.path.isDirectory
import kotlin.io.path.name

/** A video in an import folder that has stopped changing (Task I.1). The key for "seen before" is path + size + time. */
data class FinishedFile(val path: Path, val sizeBytes: Long, val lastModifiedMs: Long)

/** Which names are worth looking at: videos only, never a download or copy that is still in progress. */
object ImportFileRules {
    /** Browsers, torrent clients and copy tools write to these names and rename the file when it is complete. */
    private val PARTIAL_SUFFIXES = listOf(".part", ".crdownload", ".!qb", ".tmp", ".partial", ".download", ".!ut")

    fun isPartialDownload(name: String): Boolean {
        val lower = name.lowercase()
        return PARTIAL_SUFFIXES.any { lower.endsWith(it) } || lower.startsWith("~$")
    }

    fun isCandidate(name: String): Boolean =
        !name.startsWith(".") && !isPartialDownload(name) && VideoFiles.isVideo(null, name)
}

/**
 * Decides when a file is finished: its size and modification time stayed the same for [quietPeriodMs] and it
 * can be opened for reading. A file is reported once per size + time; if it changes later it is reported again.
 */
class StabilityTracker(
    private val quietPeriodMs: Long,
    private val clock: () -> Long,
    private val canRead: (Path) -> Boolean = ::canOpenForReading
) {
    private data class Observation(val sizeBytes: Long, val lastModifiedMs: Long, val unchangedSinceMs: Long)

    private val pending = HashMap<Path, Observation>()
    private val reported = HashMap<Path, FinishedFile>()

    /** Returns the file when it has just become finished, null while it is still changing or was already reported. */
    fun observe(path: Path, sizeBytes: Long, lastModifiedMs: Long): FinishedFile? {
        val file = FinishedFile(path, sizeBytes, lastModifiedMs)
        if (reported[path] == file) return null
        val now = clock()
        val previous = pending[path]
        if (previous == null || previous.sizeBytes != sizeBytes || previous.lastModifiedMs != lastModifiedMs) {
            pending[path] = Observation(sizeBytes, lastModifiedMs, now)
            return null
        }
        if (now - previous.unchangedSinceMs < quietPeriodMs || sizeBytes == 0L || !canRead(path)) return null
        pending.remove(path)
        reported[path] = file
        return file
    }

    /** A file that disappeared (deleted or renamed): forget it, so a new file with the same name starts over. */
    fun forget(path: Path) {
        pending.remove(path)
        reported.remove(path)
    }

    /** Paths seen but not finished yet; they are checked again on every tick even without a watch event. */
    fun pendingPaths(): Set<Path> = pending.keys.toSet()

    companion object {
        fun canOpenForReading(path: Path): Boolean = try {
            FileChannel.open(path, StandardOpenOption.READ).use { true }
        } catch (e: IOException) {
            false
        }
    }
}

/**
 * Watches import folders for finished videos (Task I.1, detection step). `WatchService` events say where to look;
 * a full rescan every [rescanEveryTicks] ticks catches lost events (overflow, network drives), and duplicate events
 * are harmless because [StabilityTracker] reports a file once. Never writes to, moves or deletes local files.
 *
 * Not thread-safe: call [tick] from one coroutine, e.g. every 2 s.
 */
class FinishedFileDetector(
    private val roots: List<Path>,
    private val tracker: StabilityTracker,
    private val rescanEveryTicks: Int = 30,
    private val watchService: WatchService? = runCatching { FileSystems.getDefault().newWatchService() }.getOrNull()
) : Closeable {
    private val watchedDirs = HashMap<WatchKey, Path>()
    private var ticks = 0

    init {
        roots.filter { it.isDirectory() }.forEach { registerTree(it) }
    }

    /** Returns the files that finished since the last tick. */
    fun tick(): List<FinishedFile> {
        val candidates = LinkedHashSet<Path>()
        if (ticks++ % rescanEveryTicks == 0 || !drainWatchEvents(candidates)) candidates += fullScan()
        candidates += tracker.pendingPaths()
        return candidates.mapNotNull { check(it) }
    }

    /** Every candidate video under the roots, also used for the first tick (files that arrived while the app was off). */
    fun fullScan(): List<Path> = roots.filter { it.isDirectory() }.flatMap { root ->
        try {
            Files.walk(root).use { stream ->
                stream.filter { Files.isRegularFile(it) && ImportFileRules.isCandidate(it.name) && !isHidden(root, it) }.toList()
            }
        } catch (e: IOException) {
            AppLog.w(TAG, "Scanning $root for new files failed: ${e.message}")
            emptyList()
        }
    }

    private fun check(path: Path): FinishedFile? {
        if (!Files.isRegularFile(path)) {
            tracker.forget(path)
            return null
        }
        return try {
            tracker.observe(path, Files.size(path), Files.getLastModifiedTime(path).toMillis())
        } catch (e: IOException) {
            null
        }
    }

    /** Collects changed files; false when events were lost and a full rescan is needed. */
    private fun drainWatchEvents(into: MutableSet<Path>): Boolean {
        val service = watchService ?: return false
        var complete = true
        while (true) {
            val key = try {
                service.poll() ?: break
            } catch (e: ClosedWatchServiceException) {
                return false
            }
            val dir = watchedDirs[key]
            for (event in key.pollEvents()) {
                if (event.kind() == OVERFLOW || dir == null) {
                    complete = false
                    continue
                }
                val child = dir.resolve(event.context() as Path)
                when {
                    child.isDirectory() -> if (event.kind() == ENTRY_CREATE) {
                        registerTree(child)
                        // Files copied together with a new folder may be in it before the folder is watched.
                        complete = false
                    }
                    // add(), not +=: a Path is also an Iterable<Path>.
                    ImportFileRules.isCandidate(child.name) -> into.add(child)
                }
            }
            if (!key.reset()) watchedDirs.remove(key)
        }
        return complete
    }

    private fun registerTree(dir: Path) {
        val service = watchService ?: return
        try {
            Files.walk(dir).use { stream ->
                stream.filter { it.isDirectory() }.forEach { folder ->
                    runCatching { watchedDirs[folder.register(service, ENTRY_CREATE, ENTRY_MODIFY)] = folder }
                }
            }
        } catch (e: IOException) {
            AppLog.w(TAG, "Watching $dir failed, relying on rescans: ${e.message}")
        }
    }

    private fun isHidden(root: Path, file: Path): Boolean = root.relativize(file).any { it.name.startsWith(".") }

    override fun close() {
        runCatching { watchService?.close() }
    }

    private companion object {
        const val TAG = "FinishedFileDetector"
    }
}
