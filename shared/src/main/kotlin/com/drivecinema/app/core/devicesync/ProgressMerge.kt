package com.drivecinema.app.core.devicesync

/**
 * Milestone Q merge rules (design §4). The newer play wins: position, duration and the watched mark all come from it.
 * On equal times a watched entry wins, then the larger position. So sync never replaces a watched state with an older
 * or equal unwatched one; only a newer real play (re-watching from the start) clears a watched mark, as on one device.
 */
object ProgressMerge {
    /** Times further ahead than this come from a wrong device clock and are ignored. */
    const val MAX_FUTURE_MS = 24 * 60 * 60 * 1000L

    /** True when [candidate] should replace [current]. */
    fun wins(candidate: SyncEntry, current: SyncEntry?): Boolean {
        if (current == null) return true
        if (candidate.lastWatchedAt != current.lastWatchedAt) return candidate.lastWatchedAt > current.lastWatchedAt
        if (candidate.isCompleted != current.isCompleted) return candidate.isCompleted
        return candidate.positionMs > current.positionMs
    }

    fun newer(a: SyncEntry, b: SyncEntry): SyncEntry = if (wins(b, a)) b else a

    /** Entries a device can trust: a time that is set and not in the far future. */
    fun isUsable(entry: SyncEntry, now: Long): Boolean = entry.lastWatchedAt > 0L && entry.lastWatchedAt <= now + MAX_FUTURE_MS

    /** Per key, the winning entry across [sources]; unusable entries are dropped. Order of [sources] doesn't matter. */
    fun mergeAll(sources: List<Map<String, SyncEntry>>, now: Long): Map<String, SyncEntry> {
        val merged = HashMap<String, SyncEntry>()
        for (source in sources) {
            for ((key, entry) in source) {
                if (!isUsable(entry, now)) continue
                val current = merged[key]
                if (wins(entry, current)) merged[key] = entry
            }
        }
        return merged
    }
}
