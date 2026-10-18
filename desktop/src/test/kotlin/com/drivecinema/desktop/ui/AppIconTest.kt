package com.drivecinema.desktop.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AppIconTest {

    @Test
    fun windowIconLoadsFromBundledLogo() {
        val size = appIconPainter().intrinsicSize
        assertEquals(512f, size.width)
        assertEquals(512f, size.height)
    }
}
