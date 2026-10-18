package com.drivecinema.app.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class PlaybackSourceTest {

    private val localUri = "content://com.android.externalstorage.documents/tree/primary%3AMovies/document/primary%3AMovies%2FDune.mkv"

    @Test
    fun driveFileResolvesToStreamUriKeyedByFileId() {
        val source = PlayerUriBuilder.resolveSource(driveFileId = "1a2b3c", localContentUri = null)

        assertEquals(PlaybackSource.Drive("1a2b3c"), source)
        assertEquals(
            "https://www.googleapis.com/drive/v3/files/1a2b3c?alt=media&supportsAllDrives=true",
            source!!.uri.toString()
        )
        assertEquals("1a2b3c", source.progressKey)
    }

    @Test
    fun localContentUriResolvesToLocalSourceKeyedByUri() {
        val source = PlayerUriBuilder.resolveSource(driveFileId = null, localContentUri = localUri)

        assertEquals(PlaybackSource.Local(localUri), source)
        assertEquals("content", source!!.uri.scheme)
        assertEquals(localUri, source.progressKey)
    }

    @Test
    fun localSourceTakesPrecedenceOverDriveFile() {
        val source = PlayerUriBuilder.resolveSource(driveFileId = "1a2b3c", localContentUri = localUri)

        assertEquals(PlaybackSource.Local(localUri), source)
    }

    @Test
    fun nonContentLocalUrisAreRejected() {
        assertNull(PlayerUriBuilder.resolveSource(null, "file:///sdcard/Movies/Dune.mkv"))
        assertNull(PlayerUriBuilder.resolveSource(null, "https://example.com/video.mp4"))
        // A rejected local URI falls back to the Drive file when one is given.
        assertEquals(
            PlaybackSource.Drive("1a2b3c"),
            PlayerUriBuilder.resolveSource("1a2b3c", "file:///sdcard/Movies/Dune.mkv")
        )
    }

    @Test
    fun missingExtrasResolveToNothing() {
        assertNull(PlayerUriBuilder.resolveSource(null, null))
        assertNull(PlayerUriBuilder.resolveSource("", ""))
    }
}
