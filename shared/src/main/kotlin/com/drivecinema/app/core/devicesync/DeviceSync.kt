package com.drivecinema.app.core.devicesync

import com.drivecinema.app.core.database.dao.DeviceSyncDao
import com.drivecinema.app.core.database.entity.PlaybackProgressEntity
import com.drivecinema.app.core.logging.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.security.SecureRandom

/** The calls [DeviceSync] needs from the app folder ([AppDataClient] in the app, a fake in tests). */
interface AppFolder {
    suspend fun listSyncFiles(accessToken: String): List<AppDataFile>
    suspend fun download(fileId: String, accessToken: String): String
    suspend fun create(name: String, content: String, accessToken: String): String
    suspend fun update(fileId: String, content: String, accessToken: String)
}

/** Who this device is in the app folder. [platform]: `android-phone`, `android-tv` or `desktop`. */
data class DeviceSyncIdentity(val deviceId: String, val deviceName: String, val platform: String) {
    companion object {
        /** 16 random hex characters, made once per install. */
        fun newDeviceId(random: SecureRandom = SecureRandom()): String =
            ByteArray(8).also(random::nextBytes).joinToString("") { "%02x".format(it) }
    }
}

sealed interface DeviceSyncResult {
    /** [applied]: rows this device took from others; [otherDevices]: sync files read; [uploaded]: this device's file was written. */
    data class Synced(val applied: Int, val otherDevices: Int, val uploaded: Boolean, val unknownTitles: Int) : DeviceSyncResult
    /** The sign-in has no `drive.appdata` scope: turn sync on again (phone, desktop) or pair the TV again. */
    data object NeedsPermission : DeviceSyncResult
    data class Failed(val message: String) : DeviceSyncResult
}

/**
 * Milestone Q (design `docs/design/q0-device-sync.md`): one run reads every device's file in the app folder, writes the
 * entries that win ([ProgressMerge]) into this device's `playback_progress`, and uploads this device's own file when
 * its entries changed. It never deletes a row and never touches another device's file.
 */
class DeviceSync(
    private val dao: DeviceSyncDao,
    private val folder: AppFolder,
    private val identity: DeviceSyncIdentity,
    private val clock: () -> Long = System::currentTimeMillis
) {
    suspend fun run(accessToken: String): DeviceSyncResult = try {
        runOrThrow(accessToken)
    } catch (e: CancellationException) {
        throw e
    } catch (e: AppDataException) {
        if (e.needsPermission) DeviceSyncResult.NeedsPermission else DeviceSyncResult.Failed(e.message ?: "Drive error")
    } catch (e: IOException) {
        DeviceSyncResult.Failed(e.message ?: e.javaClass.simpleName)
    }

    private suspend fun runOrThrow(accessToken: String): DeviceSyncResult {
        val now = clock()
        val files = folder.listSyncFiles(accessToken)
        val ownFile = files.filter { SyncFile.deviceIdOf(it.name) == identity.deviceId }.minByOrNull { it.id }
        val others = files.filter { SyncFile.deviceIdOf(it.name).let { id -> id != null && id != identity.deviceId } }
        val ownEntries = ownFile?.let { SyncFileCodec.decode(folder.download(it.id, accessToken))?.entries }
        val remote = others.mapNotNull { file ->
            SyncFileCodec.decode(folder.download(file.id, accessToken)).also {
                if (it == null) AppLog.w(TAG, "Skipped an unreadable sync file (${file.name})")
            }?.entries
        }

        // Pull: the winning entry per sync key, then per local key; written only where it beats the stored row.
        // This device's own earlier file counts too, so a cleared database gets its history back.
        val keys = SyncKeys(dao.allSources(), dao.allTitles())
        val before = dao.allProgress().associateBy { it.mediaItemId }
        val incoming = HashMap<String, SyncEntry>()
        var unknown = 0
        for ((syncKey, entry) in ProgressMerge.mergeAll(remote + listOfNotNull(ownEntries), now)) {
            val localKey = keys.localKeyOf(syncKey, before)
            if (localKey == null) { unknown++; continue }
            incoming[localKey] = incoming[localKey]?.let { ProgressMerge.newer(it, entry) } ?: entry
        }
        var applied = 0
        for ((localKey, entry) in incoming) {
            if (!ProgressMerge.wins(entry, before[localKey]?.toEntry())) continue
            dao.saveIfNewer(localKey, entry.positionMs, entry.durationMs, entry.isCompleted, entry.lastWatchedAt)
            applied++
        }

        // Push: this device's state (with what it just took), only when it differs from its file.
        val exported = HashMap<String, SyncEntry>()
        for (row in dao.allProgress()) {
            val entry = row.toEntry()
            if (!ProgressMerge.isUsable(entry, now)) continue
            val syncKey = keys.syncKeyOf(row.mediaItemId) ?: continue
            exported[syncKey] = exported[syncKey]?.let { ProgressMerge.newer(it, entry) } ?: entry
        }
        val uploaded = if (ownFile != null && ownEntries == exported) false else {
            val content = SyncFileCodec.encode(SyncFile(identity.deviceId, identity.deviceName, identity.platform, now, exported))
            if (ownFile == null) folder.create(SyncFile.fileName(identity.deviceId), content, accessToken)
            else folder.update(ownFile.id, content, accessToken)
            true
        }
        AppLog.i(TAG, "Synced: $applied taken from ${remote.size} other device(s), ${exported.size} shared, uploaded=$uploaded, unknown=$unknown")
        return DeviceSyncResult.Synced(applied, remote.size, uploaded, unknown)
    }

    private fun PlaybackProgressEntity.toEntry() = SyncEntry(positionMs, durationMs, isCompleted, lastWatchedAt)

    companion object {
        private const val TAG = "DeviceSync"
    }
}

/**
 * One sync at a time. A [Trigger.FOREGROUND] run (app back in front, window focus) is skipped within
 * [minIntervalMs] of the last run or while one is running; other triggers (start, player closed, Refresh) always run,
 * after a running one.
 */
class DeviceSyncRunner(
    private val minIntervalMs: Long = 60_000L,
    private val clock: () -> Long = System::currentTimeMillis
) {
    enum class Trigger { STARTUP, PLAYER_CLOSED, REFRESH, FOREGROUND }

    private val mutex = Mutex()
    @Volatile private var lastStartedAt: Long? = null

    /** Null when the run was skipped. */
    suspend fun run(trigger: Trigger, block: suspend () -> DeviceSyncResult): DeviceSyncResult? {
        if (trigger == Trigger.FOREGROUND) {
            val last = lastStartedAt
            if (mutex.isLocked || (last != null && clock() - last < minIntervalMs)) return null
        }
        return mutex.withLock {
            lastStartedAt = clock()
            block()
        }
    }
}
