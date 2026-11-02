package com.drivecinema.desktop.ui

import com.drivecinema.app.core.database.entity.ImportQueueEntity
import com.drivecinema.app.core.database.entity.ImportState
import com.drivecinema.app.ui.design.DcStatusKind
import com.drivecinema.desktop.local.LocalFolderStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

/** Task P.6: the words on the desktop's settings pages (pure; the desktop has no Compose UI-test library). */
class PageTextTest {

    private val utc = ZoneOffset.UTC
    private fun at(text: String) = LocalDateTime.parse(text).toInstant(utc).toEpochMilli()
    private val now = at("2026-10-28T15:30:00")

    @Test
    fun longPathsAndFileNamesAreShortenedInTheMiddle() {
        val path = "C:\\Users\\Janix\\Videos\\Downloads\\Series\\Breaking Bad\\Season 01"
        val short = middleEllipsis(path, 40)
        assertEquals(40, short.length)
        assertTrue(short, short.startsWith("C:\\Users\\Janix"))
        assertTrue(short, short.endsWith("Breaking Bad\\Season 01"))
        assertEquals(1, short.count { it == '…' })

        val file = "Stranger.Things.S04E07.Chapter.Seven.The.Massacre.at.Hawkins.Lab.2160p.mkv"
        assertTrue(middleEllipsis(file, 30).endsWith("2160p.mkv"))
        assertEquals("Short.mkv", middleEllipsis("Short.mkv", 30))
    }

    @Test
    fun datesReadAsTodayYesterdayOrADay() {
        assertEquals("today", friendlyDay(at("2026-10-28T09:00:00"), now, utc))
        assertEquals("yesterday", friendlyDay(at("2026-10-27T23:59:00"), now, utc))
        assertEquals("21 Oct", friendlyDay(at("2026-10-21T10:00:00"), now, utc))
        assertEquals("3 Dec 2025", friendlyDay(at("2025-12-03T10:00:00"), now, utc))

        assertEquals("today 12:49", friendlyDateTime(at("2026-10-28T12:49:00"), now, utc))
        assertEquals("yesterday 09:03", friendlyDateTime(at("2026-10-27T09:03:00"), now, utc))
        assertEquals("27 Sep, 12:49", friendlyDateTime(at("2026-09-27T12:49:00"), now, utc))
    }

    @Test
    fun aPairedDeviceSaysWhenItWasPairedAndLastUsed() {
        assertEquals(
            "Paired 21 Oct · last used today 12:49",
            pairedDeviceLine(at("2026-10-21T10:00:00"), at("2026-10-28T12:49:00"), now, utc)
        )
        assertEquals("Paired yesterday", pairedDeviceLine(at("2026-10-27T10:00:00"), null, now, utc))
    }

    @Test
    fun theDeviceIconIsGuessedFromItsName() {
        assertEquals(DeviceKind.PHONE, deviceKind("Google Pixel 9a"))
        assertEquals(DeviceKind.PHONE, deviceKind("Xiaomi 2201117TG"))
        assertEquals(DeviceKind.PHONE, deviceKind("Sony XQ-DQ54"))
        assertEquals(DeviceKind.TV, deviceKind("TCL 55C6K"))
        assertEquals(DeviceKind.TV, deviceKind("NVIDIA SHIELD Android TV"))
        assertEquals(DeviceKind.TV, deviceKind("Xiaomi MiTV-AXSO0"))
        assertEquals(DeviceKind.PC, deviceKind("DESKTOP-ELT3101"))
        assertEquals(DeviceKind.PC, deviceKind("janix-ubuntu"))
        assertEquals(DeviceKind.PC, deviceKind("DriveCinema desktop"))
    }

    @Test
    fun everyQueueStateHasAChipAndAtMostOneAction() {
        assertEquals("Waiting" to DcStatusKind.Neutral, importChip(ImportState.QUEUED))
        assertEquals("Uploading" to DcStatusKind.Brand, importChip(ImportState.UPLOADING))
        assertEquals("Done" to DcStatusKind.Success, importChip(ImportState.DONE))
        assertEquals("Needs review" to DcStatusKind.Warning, importChip(ImportState.NEEDS_REVIEW))
        assertEquals("Failed" to DcStatusKind.Warning, importChip(ImportState.FAILED))
        assertEquals("Cancelled" to DcStatusKind.Neutral, importChip(ImportState.CANCELLED))
        assertEquals("Not uploaded" to DcStatusKind.Neutral, importChip(ImportState.SKIPPED))

        assertEquals(ImportRowAction.CANCEL, importRowAction(ImportState.UPLOADING))
        assertEquals(ImportRowAction.CANCEL, importRowAction(ImportState.QUEUED))
        assertEquals(ImportRowAction.RETRY, importRowAction(ImportState.FAILED))
        assertEquals(ImportRowAction.RETRY, importRowAction(ImportState.NEEDS_REVIEW))
        assertEquals(ImportRowAction.UPLOAD, importRowAction(ImportState.SKIPPED))
        assertNull(importRowAction(ImportState.DONE))
    }

    @Test
    fun aQueueRowSaysWhereTheFileGoesAndWhy() {
        val entry = ImportQueueEntity(
            importKey = "k", localPath = "D:\\in\\Ted.S01E02.mkv", fileName = "Ted.S01E02.mkv", sizeBytes = 1, lastModifiedMs = 1,
            targetFolderPath = "Series/Ted (2024)/Season 01", libraryRootId = "root", state = ImportState.QUEUED
        )
        assertEquals("→ Series/Ted (2024)/Season 01", queueDetails(entry))
        assertEquals(
            "Already in the folder when you chose it · → Series/Ted (2024)/Season 01",
            queueDetails(entry.copy(state = ImportState.SKIPPED))
        )
        assertEquals("No SxxEyy in the name", queueDetails(entry.copy(targetFolderPath = null, state = ImportState.NEEDS_REVIEW, message = "No SxxEyy in the name")))
    }

    @Test
    fun theWatcherChipSaysWhatTheImportIsDoing() {
        assertEquals("Watching" to DcStatusKind.Success, watcherChip(isRunning = true, pausedByUser = false, stoppedReason = null))
        assertEquals("Paused" to DcStatusKind.Neutral, watcherChip(isRunning = false, pausedByUser = true, stoppedReason = null))
        assertEquals("Stopped" to DcStatusKind.Warning, watcherChip(isRunning = false, pausedByUser = false, stoppedReason = "Drive is full"))
        assertEquals("Not running" to DcStatusKind.Neutral, watcherChip(isRunning = false, pausedByUser = false, stoppedReason = null))
    }

    @Test
    fun aLocalFolderShowsItsNameCountAndNotes() {
        assertEquals("Season 01", folderName("D:\\Series\\Show\\Season 01\\"))
        assertEquals("Films", folderName("/home/janix/Videos/Films"))
        val folder = LocalFolderStatus(path = "D:\\Films", isAvailable = true, videoCount = 12, linkedToDrive = 3, possibleDuplicates = 1)
        assertEquals("12 videos" to DcStatusKind.Neutral, folderChip(folder))
        assertEquals("3 also on Drive · 1 possible duplicate kept separate", folderNotes(folder))
        assertEquals("Not found" to DcStatusKind.Warning, folderChip(folder.copy(isAvailable = false)))
        assertEquals("Not scanned yet" to DcStatusKind.Neutral, folderChip(folder.copy(videoCount = null)))
        assertNull(folderNotes(folder.copy(linkedToDrive = 0, possibleDuplicates = null)))
    }

    @Test
    fun pairShowsWhatIsMissing() {
        assertEquals("AB3D4E", pairingCodeInput("ab-3d 4e5"))
        assertEquals("Find the PC, or type its address", pairBlockedReason("", "AB3D4E"))
        assertEquals("Enter all 6 characters of the code", pairBlockedReason("192.168.1.20", "AB3"))
        assertNull(pairBlockedReason("192.168.1.20", "AB3D4E"))
    }
}
