package com.drivecinema.desktop.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files

class WindowsVlcLocatorTest {

    private val env = mapOf(
        "ProgramFiles" to "C:\\Program Files",
        "PATH" to "C:\\WINDOWS\\system32;\"C:\\Tools\\VLC\";;C:\\Program Files\\VideoLAN\\VLC"
    )

    @Test
    fun registryFirstThenDefaultFolderThenPath() {
        assertEquals(
            listOf("D:\\Apps\\VLC", "C:\\Program Files\\VideoLAN\\VLC", "C:\\WINDOWS\\system32", "C:\\Tools\\VLC"),
            WindowsVlcLocator.candidates("D:\\Apps\\VLC", env::get)
        )
        // Without a registry entry (portable VLC on PATH) and without the environment variables.
        assertEquals("C:\\Program Files\\VideoLAN\\VLC", WindowsVlcLocator.candidates(null, env::get).first())
        assertEquals(emptyList<String>(), WindowsVlcLocator.candidates(null) { null })
    }

    @Test
    fun findsOnlyAFolderWithBothDllsAndNeverLooksInsideSubfolders() {
        val root = Files.createTempDirectory("vlc-locator").toFile()
        try {
            val onlyCore = File(root, "half").apply { mkdirs(); File(this, "libvlccore.dll").writeText("") }
            val nested = File(root, "parent").apply { File(this, "VLC").mkdirs() }
            File(nested, "VLC/libvlc.dll").writeText(""); File(nested, "VLC/libvlccore.dll").writeText("")
            val vlc = File(root, "vlc").apply { mkdirs(); File(this, "libvlc.dll").writeText(""); File(this, "libvlccore.dll").writeText("") }

            assertNull(WindowsVlcLocator.find(listOf(onlyCore.path, nested.path, File(root, "missing").path)))
            assertEquals(vlc.path, WindowsVlcLocator.find(listOf(onlyCore.path, nested.path, vlc.path)))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun anUnreadableFolderIsSkipped() {
        val found = WindowsVlcLocator.find(listOf("bad", "good")) { dir ->
            if (dir.name == "bad") throw SecurityException("denied") else true
        }
        assertEquals("good", found)
    }
}
