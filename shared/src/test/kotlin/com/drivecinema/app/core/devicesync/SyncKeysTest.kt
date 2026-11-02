package com.drivecinema.app.core.devicesync

import com.drivecinema.app.core.database.dao.SyncSourceRow
import com.drivecinema.app.core.database.dao.SyncTitleRow
import com.drivecinema.app.core.database.entity.MediaType
import com.drivecinema.app.core.database.entity.PlaybackProgressEntity
import com.drivecinema.app.core.database.entity.SourceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Milestone Q: progress keys ↔ keys every device understands (design §4). */
class SyncKeysTest {

    private val sources = listOf(
        // A film on Drive with a local copy on the phone.
        SyncSourceRow("1DriveFilm", "1DriveFilm", SourceKind.DRIVE, isAvailable = true),
        SyncSourceRow("content://tree/film.mkv", "1DriveFilm", SourceKind.LOCAL, isAvailable = true),
        // A film only on a paired PC, matched on TMDB.
        SyncSourceRow("dchost://pc1/f9", "local:abc", SourceKind.HOST, isAvailable = true),
        // An episode only on the phone.
        SyncSourceRow("content://tree/got-s1e3.mkv", "local:ep", SourceKind.LOCAL, isAvailable = false),
        // A film only on the phone, unknown to TMDB.
        SyncSourceRow("content://tree/home-video.mp4", "local:home", SourceKind.LOCAL, isAvailable = true)
    )
    private val titles = listOf(
        SyncTitleRow("1DriveFilm", MediaType.MOVIE, 603, null, null, null),
        SyncTitleRow("local:abc", MediaType.MOVIE, 78, null, null, null),
        SyncTitleRow("local:ep", MediaType.EPISODE, 63056, 1, 3, 1399),
        SyncTitleRow("local:home", MediaType.MOVIE, null, null, null, null)
    )
    private val keys = SyncKeys(sources, titles)

    @Test
    fun driveCopiesAndLegacyDriveRowsUseTheirFileId() {
        assertEquals("drive:1DriveFilm", keys.syncKeyOf("1DriveFilm"))
        assertEquals("a row from before media_sources", "drive:1Legacy_id-2", keys.syncKeyOf("1Legacy_id-2"))
    }

    @Test
    fun aLocalCopyOfADriveTitleUsesTheDriveCopy() {
        assertEquals("drive:1DriveFilm", keys.syncKeyOf("content://tree/film.mkv"))
    }

    @Test
    fun copiesWithoutADriveCopyFallBackToTmdb() {
        assertEquals("tmdb:movie:78", keys.syncKeyOf("dchost://pc1/f9"))
        assertEquals("tmdb:tv:1399:s1e3", keys.syncKeyOf("content://tree/got-s1e3.mkv"))
    }

    @Test
    fun titlesWithNeitherStayOnThisDevice() {
        assertNull(keys.syncKeyOf("content://tree/home-video.mp4"))
        assertNull("an unknown local key", keys.syncKeyOf("file:/videos/x.mkv"))
    }

    @Test
    fun driveEntriesAreWrittenToKnownDriveFilesOnly() {
        assertEquals("1DriveFilm", keys.localKeyOf("drive:1DriveFilm", emptyMap()))
        assertNull("not crawled here yet", keys.localKeyOf("drive:1Unknown", emptyMap()))
        assertNull("not a Drive key", keys.localKeyOf("drive:content://tree/film.mkv", emptyMap()))
    }

    @Test
    fun tmdbEntriesGoToTheTitlesNewestRowOrItsBestCopy() {
        assertEquals("only copy", "dchost://pc1/f9", keys.localKeyOf("tmdb:movie:78", emptyMap()))
        assertEquals("unavailable copies still count", "content://tree/got-s1e3.mkv", keys.localKeyOf("tmdb:tv:1399:s1e3", emptyMap()))
        val progress = mapOf(
            "content://tree/film.mkv" to PlaybackProgressEntity("content://tree/film.mkv", 10, 100, false, 500),
            "1DriveFilm" to PlaybackProgressEntity("1DriveFilm", 10, 100, false, 400)
        )
        assertEquals("newest row", "content://tree/film.mkv", keys.localKeyOf("tmdb:movie:603", progress))
        assertEquals("no row: the Drive copy", "1DriveFilm", keys.localKeyOf("tmdb:movie:603", emptyMap()))
        assertNull(keys.localKeyOf("tmdb:movie:1", emptyMap()))
        assertNull(keys.localKeyOf("tmdb:tv:1399:s1e4", emptyMap()))
    }
}
