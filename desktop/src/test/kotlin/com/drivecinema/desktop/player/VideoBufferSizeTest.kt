package com.drivecinema.desktop.player

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoBufferSizeTest {

    @Test
    fun fourKOnAFullHdScreenIsDrawnAtFullHd() {
        assertEquals(VideoBufferSize(1920, 1080), VideoBufferSize.fit(3840, 2160, 1920, 1080))
    }

    @Test
    fun smallerVideosKeepTheirSize() {
        assertEquals(VideoBufferSize(1280, 720), VideoBufferSize.fit(1280, 720, 1920, 1080))
        assertEquals(VideoBufferSize(1920, 1080), VideoBufferSize.fit(1920, 1080, 1920, 1080))
    }

    @Test
    fun aspectRatioIsKeptAndSizesStayEven() {
        // Cinemascope 4K on a 1920x1200 screen: width is the limit.
        val size = VideoBufferSize.fit(3840, 1608, 1920, 1200)
        assertEquals(1920, size.width)
        assertEquals(804, size.height)
        val odd = VideoBufferSize.fit(3841, 2161, 1001, 1001)
        assertEquals(0, odd.width % 2)
        assertEquals(0, odd.height % 2)
    }

    @Test
    fun hiDpiScreensUseRealPixels() {
        // Regression: at 125 % scaling AWT reports 1536x960, which shrank 1080p films to 1536 px wide.
        val (width, height) = VideoBufferSize.physical(1536, 960, 1.25)
        assertEquals(1920 to 1200, width to height)
        assertEquals(VideoBufferSize(1920, 816), VideoBufferSize.fit(1920, 816, width, height))
    }

    @Test
    fun streamStatsReadableInTheLog() {
        assertEquals("streamed 12.5 MB in 10.0 s (10.0 Mbit/s)", StreamStats.describe(12_500_000, 10_000))
        assertEquals("streamed 0.0 MB in 0.0 s (0.0 Mbit/s)", StreamStats.describe(0, 0))
    }
}
