package com.drivecinema.app.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class PlayerUriBuilderTest {
    @Test
    fun testBuildDriveStreamUri() {
        val fileId = "1a2b3c4d5e"
        val uri = PlayerUriBuilder.buildStreamUri(fileId)
        assertEquals("https://www.googleapis.com/drive/v3/files/1a2b3c4d5e?alt=media&supportsAllDrives=true", uri.toString())
    }
}
