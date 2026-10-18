package com.drivecinema.app.core.metadata

/** Which Drive/local files count as videos (same rule on every platform). */
object VideoFiles {

    val VIDEO_EXTENSIONS = setOf(
        "mp4", "mkv", "avi", "mov", "wmv", "m4v", "webm", "ts"
    )

    fun isVideo(mimeType: String?, name: String?): Boolean {
        if (mimeType?.startsWith("video/") == true) return true
        val ext = name?.substringAfterLast('.', "")?.lowercase()
        return ext in VIDEO_EXTENSIONS
    }
}
