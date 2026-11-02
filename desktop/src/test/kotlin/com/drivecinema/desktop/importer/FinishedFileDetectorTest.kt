package com.drivecinema.desktop.importer

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime

/** Task I.1, detection: a file is reported once, only after it stopped changing, and partial downloads never. */
class FinishedFileDetectorTest {

    private lateinit var root: Path
    private var now = 0L
    private val detectors = mutableListOf<FinishedFileDetector>()

    @Before
    fun setUp() {
        root = Files.createTempDirectory("drivecinema-import")
    }

    @After
    fun tearDown() {
        detectors.forEach { it.close() }
        root.toFile().deleteRecursively()
    }

    @Test
    fun partialDownloadNamesAndNonVideosAreIgnored() {
        listOf("Film.mkv.part", "Film.mkv.crdownload", "Film.mkv.!qB", "Film.tmp", "~\$Film.mkv", ".Film.mkv", "notes.txt")
            .forEach { assertFalse(it, ImportFileRules.isCandidate(it)) }
        listOf("Film (2024).mkv", "Show.S01E02.mp4", "clip.WEBM").forEach { assertTrue(it, ImportFileRules.isCandidate(it)) }
    }

    @Test
    fun fileIsReportedOnlyAfterTheQuietPeriod() {
        val tracker = StabilityTracker(quietPeriodMs = 10_000, clock = { now }, canRead = { true })
        val path = root.resolve("Film.mkv")

        assertNull(tracker.observe(path, 100, 1))
        now = 5_000
        assertNull("size grew: still copying", tracker.observe(path, 200, 2))
        now = 14_000
        assertNull("only 9 s unchanged", tracker.observe(path, 200, 2))
        now = 15_000
        assertEquals(FinishedFile(path, 200, 2), tracker.observe(path, 200, 2))
    }

    @Test
    fun duplicateEventsReportAFileOnce() {
        val tracker = StabilityTracker(quietPeriodMs = 1_000, clock = { now }, canRead = { true })
        val path = root.resolve("Film.mkv")
        tracker.observe(path, 100, 1)
        now = 2_000
        assertTrue(tracker.observe(path, 100, 1) != null)
        repeat(3) {
            now += 5_000
            assertNull(tracker.observe(path, 100, 1))
        }
    }

    @Test
    fun aFileThatChangesAfterBeingReportedIsReportedAgain() {
        val tracker = StabilityTracker(quietPeriodMs = 1_000, clock = { now }, canRead = { true })
        val path = root.resolve("Film.mkv")
        tracker.observe(path, 100, 1)
        now = 2_000
        tracker.observe(path, 100, 1)
        tracker.observe(path, 300, 3)
        now = 4_000
        assertEquals(FinishedFile(path, 300, 3), tracker.observe(path, 300, 3))
    }

    @Test
    fun unreadableOrEmptyFilesAreNotFinished() {
        var readable = false
        val tracker = StabilityTracker(quietPeriodMs = 1_000, clock = { now }, canRead = { readable })
        val path = root.resolve("Film.mkv")
        tracker.observe(path, 100, 1)
        tracker.observe(root.resolve("Empty.mkv"), 0, 1)
        now = 2_000
        assertNull("still locked by the copying program", tracker.observe(path, 100, 1))
        assertNull(tracker.observe(root.resolve("Empty.mkv"), 0, 1))
        readable = true
        assertTrue(tracker.observe(path, 100, 1) != null)
    }

    @Test
    fun detectorFindsFilesThatArrivedWhileTheAppWasOffAndInNewSubfolders() {
        val film = write("Films/Film (2024).mkv", 1_000)
        write("Films/Film (2024).mkv.part", 500)
        val detector = detector()

        assertTrue("first tick only starts the quiet period", detector.tick().isEmpty())
        now = 20_000
        assertEquals(listOf(film), detector.tick().map { it.path })

        // A new season folder: found by the rescan fallback even if its watch event is lost.
        val episode = write("Series/Show/Season 01/Show.S01E01.mkv", 2_000)
        now = 21_000
        assertTrue(detector.tick().isEmpty())
        now = 40_000
        assertEquals("the film is not reported twice", listOf(episode), detector.tick().map { it.path })
    }

    @Test
    fun aFileStillBeingWrittenIsNotReportedUntilItStops() {
        val film = write("Film.mkv", 1_000)
        val detector = detector()
        detector.tick()
        now = 20_000
        append(film, 1_000)
        assertTrue("it grew since the last tick", detector.tick().isEmpty())
        now = 25_000
        assertTrue(detector.tick().isEmpty())
        now = 31_000
        assertEquals(listOf(2_000L), detector.tick().map { it.sizeBytes })
        now = 90_000
        assertTrue("reported once", detector.tick().isEmpty())
    }

    @Test
    fun localFilesAreNeverChanged() {
        val film = write("Film.mkv", 1_000)
        val before = Files.getLastModifiedTime(film)
        val detector = detector()
        detector.tick()
        now = 20_000
        detector.tick()
        assertEquals(before, Files.getLastModifiedTime(film))
        assertEquals(1_000L, Files.size(film))
    }

    private fun detector(): FinishedFileDetector =
        FinishedFileDetector(listOf(root), StabilityTracker(quietPeriodMs = 10_000, clock = { now }), rescanEveryTicks = 1)
            .also { detectors += it }

    private fun write(relative: String, size: Int): Path {
        val file = root.resolve(relative.replace('/', File.separatorChar))
        Files.createDirectories(file.parent)
        Files.write(file, ByteArray(size))
        Files.setLastModifiedTime(file, FileTime.fromMillis(1_000))
        return file
    }

    private fun append(file: Path, size: Int) {
        Files.write(file, ByteArray(size), java.nio.file.StandardOpenOption.APPEND)
        Files.setLastModifiedTime(file, FileTime.fromMillis(2_000))
    }
}
