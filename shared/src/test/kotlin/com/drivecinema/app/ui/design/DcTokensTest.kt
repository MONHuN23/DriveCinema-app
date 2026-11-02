package com.drivecinema.app.ui.design

import com.drivecinema.app.ui.design.DcTokens.Colors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/** Milestone P: the design tokens stay readable (WCAG contrast) and the type scales stay in order. */
class DcTokensTest {

    private val surfaces = mapOf(
        "CANVAS" to Colors.CANVAS,
        "SURFACE_1" to Colors.SURFACE_1,
        "SURFACE_2" to Colors.SURFACE_2,
        "SURFACE_3" to Colors.SURFACE_3
    )

    @Test
    fun everyTextColourMeetsWcagAaOnEverySurface() {
        val texts = mapOf(
            "TEXT_1" to Colors.TEXT_1,
            "TEXT_2" to Colors.TEXT_2,
            "TEXT_3" to Colors.TEXT_3,
            "SUCCESS" to Colors.SUCCESS,
            "WARNING" to Colors.WARNING,
            "DANGER" to Colors.DANGER
        )
        val failures = texts.flatMap { (textName, text) ->
            surfaces.mapNotNull { (surfaceName, surface) ->
                val ratio = contrast(text, surface)
                if (ratio < 4.5) "$textName on $surfaceName: ${"%.2f".format(ratio)}" else null
            }
        }
        assertTrue("Below 4.5:1:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun contentOnTheBrandAndOnTheFocusFillIsReadable() {
        assertTrue(contrast(Colors.ON_BRAND, Colors.BRAND) >= 4.5)
        assertTrue(contrast(Colors.INK, Colors.FOCUS) >= 4.5)
        // Why a focused primary button keeps the brand red: the brighter red used before gives white text only 3.8:1.
        assertTrue(contrast(Colors.ON_BRAND, 0xFFFF1E28) < 4.5)
    }

    @Test
    fun surfacesAreOpaqueAndContainersAreTranslucent() {
        (surfaces.values + Colors.DIVIDER + Colors.OUTLINE).forEach { assertEquals(0xFF, alpha(it)) }
        listOf(Colors.BRAND_CONTAINER, Colors.WARNING_CONTAINER, Colors.DANGER_CONTAINER).forEach {
            assertTrue(alpha(it) in 1..0x40)
        }
    }

    @Test
    fun typeScalesGoFromLargeToSmall() {
        listOf(DcTokens.PHONE, DcTokens.TV, DcTokens.DESKTOP).forEach { form ->
            val t = form.type
            val sizes = listOf(t.display, t.headline, t.title, t.rowTitle, t.body, t.caption).map { it.sizeSp }
            assertEquals(sizes.sortedDescending(), sizes)
            listOf(t.display, t.headline, t.title, t.rowTitle, t.body, t.label, t.caption).forEach {
                assertTrue("line height below size: $it", it.lineHeightSp >= it.sizeSp)
            }
        }
    }

    @Test
    fun theTvIsReadFromFurtherAway() {
        val phone = DcTokens.PHONE.type
        val tv = DcTokens.TV.type
        assertTrue(tv.body.sizeSp >= 16)
        assertTrue(tv.display.sizeSp > phone.display.sizeSp)
        assertTrue(tv.caption.sizeSp > phone.caption.sizeSp)
        assertTrue(DcTokens.TV.rowMinHeight > DcTokens.PHONE.rowMinHeight)
        assertTrue(DcTokens.PHONE.buttonHeight >= 48) // touch target
    }

    private fun alpha(argb: Long): Int = ((argb shr 24) and 0xFF).toInt()

    private fun contrast(a: Long, b: Long): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun luminance(argb: Long): Double {
        fun channel(shift: Int): Double {
            val c = ((argb shr shift) and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }
}
