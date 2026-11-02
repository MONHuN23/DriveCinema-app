package com.drivecinema.app.core.devicesync

import org.json.JSONObject

/** One title's watch state in a sync file (Milestone Q): the fields of a `playback_progress` row, without its key. */
data class SyncEntry(
    val positionMs: Long,
    val durationMs: Long,
    val isCompleted: Boolean,
    val lastWatchedAt: Long
)

/**
 * One device's file in the Drive app folder, `progress-<deviceId>.json` (design `docs/design/q0-device-sync.md` §3).
 * Each device writes only its own file and reads everyone's, so two devices never write the same file.
 * [entries] are keyed by sync key (`drive:<fileId>`, `tmdb:…`, see [SyncKeys]); the file holds no titles.
 */
data class SyncFile(
    val deviceId: String,
    val deviceName: String,
    val platform: String,
    val updatedAt: Long,
    val entries: Map<String, SyncEntry>
) {
    companion object {
        const val FILE_PREFIX = "progress-"
        const val FILE_SUFFIX = ".json"

        fun fileName(deviceId: String): String = "$FILE_PREFIX$deviceId$FILE_SUFFIX"

        /** The device ID in a sync file's name, or null when the name isn't one of ours. */
        fun deviceIdOf(fileName: String): String? =
            fileName.takeIf { it.startsWith(FILE_PREFIX) && it.endsWith(FILE_SUFFIX) }
                ?.removePrefix(FILE_PREFIX)?.removeSuffix(FILE_SUFFIX)?.takeIf { it.isNotEmpty() }
    }
}

/** JSON for [SyncFile]. A file from a newer app (`format` > 1) or a broken file reads as null; a broken entry is skipped. */
object SyncFileCodec {
    const val FORMAT = 1

    fun encode(file: SyncFile): String {
        val entries = JSONObject()
        // Sorted keys: the same state always gives the same text.
        for ((key, entry) in file.entries.toSortedMap()) {
            entries.put(key, JSONObject().apply {
                put("p", entry.positionMs)
                put("d", entry.durationMs)
                put("c", entry.isCompleted)
                put("t", entry.lastWatchedAt)
            })
        }
        return JSONObject().apply {
            put("format", FORMAT)
            put("deviceId", file.deviceId)
            put("deviceName", file.deviceName)
            put("platform", file.platform)
            put("updatedAt", file.updatedAt)
            put("entries", entries)
        }.toString()
    }

    fun decode(text: String): SyncFile? = runCatching {
        val root = JSONObject(text)
        if (root.optInt("format", -1) != FORMAT) return null
        val deviceId = root.optString("deviceId").takeIf { it.isNotEmpty() } ?: return null
        val entries = LinkedHashMap<String, SyncEntry>()
        val json = root.optJSONObject("entries")
        if (json != null) {
            for (key in json.keys()) {
                val o = json.optJSONObject(key) ?: continue
                val time = o.optLong("t", 0L)
                if (key.isEmpty() || time <= 0L || !o.has("p")) continue
                entries[key] = SyncEntry(
                    positionMs = o.optLong("p", 0L).coerceAtLeast(0L),
                    durationMs = o.optLong("d", 0L).coerceAtLeast(0L),
                    isCompleted = o.optBoolean("c", false),
                    lastWatchedAt = time
                )
            }
        }
        SyncFile(
            deviceId = deviceId,
            deviceName = root.optString("deviceName"),
            platform = root.optString("platform"),
            updatedAt = root.optLong("updatedAt", 0L),
            entries = entries
        )
    }.getOrNull()
}
