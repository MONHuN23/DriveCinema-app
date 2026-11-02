package com.drivecinema.app.core.remote

import com.drivecinema.app.core.host.ConnectionVerdict
import com.drivecinema.app.ui.player.PlaybackSource
import com.drivecinema.app.ui.player.ResolvedPlayback
import com.drivecinema.app.ui.player.withStartPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import javax.crypto.KeyGenerator

/** Milestone D on the app side: the TV's hand-over to MainActivity, the position override and the link store. */
@RunWith(RobolectricTestRunner::class)
class PlayOnTvAppTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun aPendingPlayIsTakenOnceAndOnlyWithItsToken() {
        val play = PendingPlay.Play("driveFile1", "Alien", 600_000)
        val token = PendingPlay.post(play)
        assertNull("no token", PendingPlay.take(null))
        assertNull("forged token", PendingPlay.take("0".repeat(32)))
        assertEquals(play, PendingPlay.take(token))
        assertNull("only once", PendingPlay.take(token))
    }

    @Test
    fun aNewerPlayReplacesTheWaitingOne() {
        val first = PendingPlay.post(PendingPlay.Play("a", "A", 0))
        val second = PendingPlay.post(PendingPlay.Play("b", "B", 0))
        assertNull(PendingPlay.take(first))
        assertEquals("b", PendingPlay.take(second)!!.mediaItemId)
    }

    @Test
    fun thePhonesPositionWinsOverEveryCopy() {
        val drive = ResolvedPlayback.Playable(PlaybackSource.Drive("f"), resumePositionMs = 5_000)
        assertEquals(42_000L, (drive.withStartPosition(42_000) as ResolvedPlayback.Playable).resumePositionMs)

        val pc = ResolvedPlayback.Playable(PlaybackSource.Host("dchost://h/file/1", "https://x"), resumePositionMs = 1)
        val slow = ResolvedPlayback.SlowConnection(pc, drive, ConnectionVerdict(neededMbps = 10.0, measuredMbps = 2.0))
        val moved = slow.withStartPosition(42_000) as ResolvedPlayback.SlowConnection
        assertEquals(42_000L, moved.pc.resumePositionMs)
        assertEquals(42_000L, moved.fallback!!.resumePositionMs)

        assertEquals(ResolvedPlayback.Unavailable, ResolvedPlayback.Unavailable.withStartPosition(42_000))
    }

    @Test
    fun linksAreStoredEncryptedAndSurviveARestart() {
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val file = File(folder.root, "links.enc")
        val store = DeviceLinkStore(file) { key }
        val self = store.selfId()
        val tv = DeviceLink("tv000001", "TCL", DeviceLink.newKey(), 8769, "192.168.1.30")
        store.save(tv)
        store.save(tv.copy(name = "Living room"))
        assertEquals("same ID replaces", 1, store.links().size)

        assertFalse("key not in plain text", String(file.readBytes(), Charsets.ISO_8859_1).contains(tv.key))
        val reopened = DeviceLinkStore(file) { key }
        assertEquals(self, reopened.selfId())
        assertEquals("Living room", reopened.find("tv000001")!!.name)

        reopened.remove("tv000001")
        assertTrue(DeviceLinkStore(file) { key }.links().isEmpty())
    }

    @Test
    fun withoutAKeyStoreLinksStayInMemoryOnly() {
        val file = File(folder.root, "none.enc")
        val store = DeviceLinkStore(file) { null }
        store.save(DeviceLink("tv000001", "TCL", DeviceLink.newKey()))
        assertEquals(1, store.links().size)
        assertFalse("nothing written without a key", file.exists())
    }
}
