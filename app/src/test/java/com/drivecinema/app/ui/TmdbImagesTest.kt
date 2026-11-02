package com.drivecinema.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Task M.1: one TMDB size per role, so the next screen reuses the image Coil already cached. */
class TmdbImagesTest {

    @Test
    fun eachRoleHasItsOneSize() {
        assertEquals("https://image.tmdb.org/t/p/w500/p.jpg", TmdbImages.poster("/p.jpg"))
        assertEquals("https://image.tmdb.org/t/p/w500/s.jpg", TmdbImages.wideCard("/s.jpg"))
        assertEquals("https://image.tmdb.org/t/p/w1280/b.jpg", TmdbImages.backdrop("/b.jpg"))
    }

    @Test
    fun missingOrBlankPathsGiveNoUrl() {
        for (role in TmdbImages.Role.entries) {
            assertNull(TmdbImages.url(null, role))
            assertNull(TmdbImages.url("", role))
            assertNull(TmdbImages.url("  ", role))
        }
        assertNull(TmdbImages.detailsHeader(null, ""))
    }

    @Test
    fun theDetailsPageShowsTheCardsPosterAndTheTvBackdrop() {
        // The details thumbnail is the card's poster (was w342), the header the TV's ambient backdrop (was w780).
        assertEquals(TmdbImages.poster("/p.jpg"), TmdbImages.url("/p.jpg", TmdbImages.Role.POSTER))
        assertEquals(TmdbImages.backdrop("/b.jpg"), TmdbImages.detailsHeader("/b.jpg", "/p.jpg"))
    }

    @Test
    fun aTitleWithoutBackdropUsesTheCardsPosterInTheHeader() {
        assertEquals(TmdbImages.poster("/p.jpg"), TmdbImages.detailsHeader(null, "/p.jpg"))
        assertEquals(TmdbImages.poster("/p.jpg"), TmdbImages.detailsHeader("", "/p.jpg"))
    }

    @Test
    fun theOldDetailsSizesAreGone() {
        val urls = listOf(
            TmdbImages.poster("/x.jpg"),
            TmdbImages.wideCard("/x.jpg"),
            TmdbImages.backdrop("/x.jpg"),
            TmdbImages.detailsHeader(null, "/x.jpg"),
        )
        for (old in listOf("/w300/", "/w342/", "/w780/")) {
            assertEquals("no $old image", 0, urls.count { it!!.contains(old) })
        }
    }
}
