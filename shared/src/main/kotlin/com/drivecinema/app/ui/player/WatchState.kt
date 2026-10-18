package com.drivecinema.app.ui.player

/** What an episode or film row shows about its progress, the same on phone, TV and desktop (Milestone J.1). */
enum class WatchState {
    NOT_STARTED, IN_PROGRESS, WATCHED;

    companion object {
        fun of(positionMs: Long, isCompleted: Boolean): WatchState = when {
            isCompleted -> WATCHED
            positionMs > 0 -> IN_PROGRESS
            else -> NOT_STARTED
        }

        /** Row label: "✓ Watched", "12 min left" (or "Started" when the length is unknown), null when not started. */
        fun label(positionMs: Long, durationMs: Long, isCompleted: Boolean): String? = when (of(positionMs, isCompleted)) {
            WATCHED -> "✓ Watched"
            NOT_STARTED -> null
            IN_PROGRESS -> {
                if (durationMs <= 0) {
                    "Started"
                } else {
                    val minutesLeft = ((durationMs - positionMs).coerceAtLeast(0) + 59_999) / 60_000
                    if (minutesLeft <= 1) "1 min left" else "$minutesLeft min left"
                }
            }
        }
    }
}
