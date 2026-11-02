package com.drivecinema.desktop.ui

import androidx.compose.ui.graphics.vector.VectorPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Task L.2: Material icons instead of emoji on the desktop. */
class DesktopIconsTest {

    @Test
    fun everyIconParsesIntoA24dpPath() {
        DesktopIcons.all.forEach { icon ->
            assertEquals(icon.name, 24f, icon.viewportWidth)
            val path = icon.root[0] as VectorPath
            assertTrue("${icon.name} has no path commands", path.pathData.size > 2)
        }
    }

    @Test
    fun tooltipNamesTheShortcutWhenThereIsOne() {
        assertEquals("Next episode (N)", tooltipText("Next episode", "N"))
        assertEquals("Subtitles", tooltipText("Subtitles"))
        assertEquals("Subtitles", tooltipText("Subtitles", ""))
    }

    /** Emoji are drawn by the system's emoji font and differ per OS (or are missing): none may come back. */
    @Test
    fun desktopUiSourcesContainNoEmoji() {
        val uiSources = File("src/main/kotlin/com/drivecinema/desktop/ui").listFiles { f -> f.extension == "kt" }.orEmpty()
        assertTrue("UI sources not found from ${File(".").absolutePath}", uiSources.isNotEmpty())
        val offenders = uiSources.flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                val bad = line.codePoints().toArray().filter(::isEmojiLike)
                if (bad.isEmpty()) null
                else "${file.name}:${index + 1}: " + bad.joinToString(" ") { String(Character.toChars(it)) }
            }
        }
        assertTrue("Use DesktopIcons instead of emoji:\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }

    /**
     * Pictographs, dingbats, media-control and miscellaneous symbols (▶ ★ ✓ ⚠ ⏸ 📁 …). Plain arrows (← → ↑ ↓, which
     * every UI font has) and maths signs stay allowed in text and tooltips.
     */
    private fun isEmojiLike(cp: Int): Boolean =
        cp >= 0x1F000 ||
            cp in 0x2194..0x21FF ||
            cp in 0x2300..0x23FF ||
            cp in 0x25A0..0x27BF ||
            cp in 0x2900..0x297F ||
            cp in 0x2B00..0x2BFF ||
            cp == 0xFE0F
}
