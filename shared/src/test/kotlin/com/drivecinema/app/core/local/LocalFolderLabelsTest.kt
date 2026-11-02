package com.drivecinema.app.core.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** UI polish: the Local folders rows' path and count lines. */
class LocalFolderLabelsTest {

    @Test
    fun internalStoragePathsReadAsFolders() {
        assertEquals("Internal storage › Movies › Anime", LocalFolderLabels.readablePath("primary:Movies/Anime"))
        assertEquals("Internal storage", LocalFolderLabels.readablePath("primary:"))
        assertEquals("Internal storage › Movies", LocalFolderLabels.readablePath("primary:Movies/"))
    }

    @Test
    fun anSdCardIsNamedNotNumbered() {
        assertEquals("SD card › Media", LocalFolderLabels.readablePath("1A2B-3C4D:Media"))
    }

    @Test
    fun idsThatAreNotStoragePathsShowNoPath() {
        assertNull(LocalFolderLabels.readablePath("msf:1234"))
        assertNull(LocalFolderLabels.readablePath("raw"))
        assertNull(LocalFolderLabels.readablePath(":Movies"))
    }

    @Test
    fun countsSayWhatTheLastScanFound() {
        assertEquals("24 videos", LocalFolderLabels.videoCount(24, isScanning = false))
        assertEquals("1 video", LocalFolderLabels.videoCount(1, isScanning = true))
        assertEquals("No videos found", LocalFolderLabels.videoCount(0, isScanning = false))
        assertEquals("Scanning…", LocalFolderLabels.videoCount(null, isScanning = true))
        assertEquals("Couldn't read this folder", LocalFolderLabels.videoCount(null, isScanning = false))
    }
}
