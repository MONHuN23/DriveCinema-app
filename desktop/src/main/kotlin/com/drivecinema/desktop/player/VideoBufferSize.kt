package com.drivecinema.desktop.player

/** Size of the frames VLC hands to the app (it scales into this size before converting to RGB). */
data class VideoBufferSize(val width: Int, val height: Int) {
    companion object {
        /**
         * Keeps the source size, but never more pixels than [maxWidth] x [maxHeight] (the screen): a 4K film on a
         * 1080p screen is converted and copied as 1080p, which is about a quarter of the work per frame.
         * Width and height stay even (required by the video chroma formats).
         */
        fun fit(sourceWidth: Int, sourceHeight: Int, maxWidth: Int, maxHeight: Int): VideoBufferSize {
            if (sourceWidth <= 0 || sourceHeight <= 0) return VideoBufferSize(sourceWidth, sourceHeight)
            val scale = minOf(1.0, maxWidth.toDouble() / sourceWidth, maxHeight.toDouble() / sourceHeight)
            fun even(value: Double) = (value.toInt() / 2 * 2).coerceAtLeast(2)
            return if (scale >= 1.0) VideoBufferSize(sourceWidth, sourceHeight)
            else VideoBufferSize(even(sourceWidth * scale), even(sourceHeight * scale))
        }

        /** Logical screen size × display scale = real pixels (e.g. 1536 × 1.25 = 1920). */
        fun physical(logicalWidth: Int, logicalHeight: Int, displayScale: Double): Pair<Int, Int> =
            Math.round(logicalWidth * displayScale).toInt() to Math.round(logicalHeight * displayScale).toInt()
    }
}
