package com.drivecinema.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Task M.1 regression: a source edit read the UTF-8 `TvHomeScreen.kt` as Windows-1252 and wrote it back, so the TV
 * showed "See all Aâ€“Z â†’" instead of "See all A–Z →" (user's TV photo, 2026-10-27). UTF-8 text decoded as
 * Windows-1252 always leaves one of these sequences behind; no real text in the app contains them.
 */
class SourceEncodingTest {

    private val mojibake = listOf("â€", "â†", "Ã", "Â")

    @Test
    fun appSourcesHaveNoDoubleEncodedText() {
        val root = File("src/main")
        assertTrue("run from the app module: ${root.absolutePath}", root.isDirectory)

        val hits = root.walkTopDown()
            .filter { it.isFile && it.extension in setOf("kt", "xml") }
            .flatMap { file ->
                file.readLines(Charsets.UTF_8).mapIndexedNotNull { index, line ->
                    if (mojibake.any { it in line }) "${file.path}:${index + 1}: ${line.trim()}" else null
                }
            }
            .toList()

        assertEquals("double-encoded text:\n" + hits.joinToString("\n"), emptyList<String>(), hits)
    }

    /**
     * TV Home's own non-ASCII text, read as UTF-8. The "See all A–Z →" link became the Films and Series tabs (UI task
     * ui/tv-browse-naming); the spotlight's " · " separator is the TV Home text with a non-ASCII character now.
     */
    @Test
    fun theTvSpotlightSeparatorIsAMiddleDot() {
        val spotlight = File("src/main/java/com/drivecinema/app/ui/tv/home/TvSpotlight.kt").readText(Charsets.UTF_8)
        assertTrue(spotlight.contains("joinToString(\" · \")"))
    }
}
