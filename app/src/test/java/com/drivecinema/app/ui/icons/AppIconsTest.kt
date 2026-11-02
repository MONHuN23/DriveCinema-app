package com.drivecinema.app.ui.icons

import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.addPathNodes
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Task L.5: Material icons instead of emoji on the phone and TV. */
class AppIconsTest {

    @Test
    fun everySharedIconPathParses() {
        MaterialIconPaths.all.forEach { (name, path) ->
            assertTrue("$name has no path commands", addPathNodes(path).size > 2)
        }
        assertTrue((AppIcons.PlayArrow.root[0] as VectorPath).pathData.isNotEmpty())
    }

    /** Emoji are drawn by each phone's or TV's emoji font (and some TVs have none): none may come back. */
    @Test
    fun appUiSourcesContainNoEmoji() {
        val uiRoot = File("src/main/java/com/drivecinema/app/ui")
        assertTrue("UI sources not found from ${File(".").absolutePath}", uiRoot.isDirectory)
        val offenders = uiRoot.walk().filter { it.extension == "kt" }.flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                val bad = line.codePoints().toArray().filter(::isEmojiLike)
                if (bad.isEmpty()) null
                else "${file.name}:${index + 1}: " + bad.joinToString(" ") { String(Character.toChars(it)) }
            }
        }.toList()
        assertTrue("Use AppIcons instead of emoji:\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }

    /** Same rule as the desktop's `DesktopIconsTest`: plain arrows (← → ↑ ↓) stay allowed in text. */
    private fun isEmojiLike(cp: Int): Boolean =
        cp >= 0x1F000 ||
            cp in 0x2194..0x21FF ||
            cp in 0x2300..0x23FF ||
            cp in 0x25A0..0x27BF ||
            cp in 0x2900..0x297F ||
            cp in 0x2B00..0x2BFF ||
            cp == 0xFE0F
}
