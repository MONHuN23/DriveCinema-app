package com.drivecinema.app.ui

/**
 * Task M.1: the one place the phone/TV app builds TMDB image URLs, with one size per role. Coil caches by URL, so a
 * poster or backdrop already shown on one screen is a cache hit on the next: the details page shows the card's
 * poster (not a w342 copy) and the TV's ambient backdrop (not a w780 copy). The desktop has its own sizes
 * (`com.drivecinema.desktop.ui.TmdbImages`). Blank or missing paths give `null`, so callers show their placeholder.
 */
object TmdbImages {
    const val BASE_URL = "https://image.tmdb.org/t/p/"

    enum class Role(val size: String) {
        /** Portrait cards (TV and phone rows, grids, search) and the poster thumbnail on the details pages. */
        POSTER("w500"),

        /** 16:9 cards: Continue Watching and the episode rows on the series page. */
        WIDE_CARD("w500"),

        /** Full-width images: the TV's ambient backdrop and the details pages' header. */
        BACKDROP("w1280"),
    }

    fun url(path: String?, role: Role): String? =
        path?.takeIf { it.isNotBlank() }?.let { BASE_URL + role.size + it }

    fun poster(path: String?): String? = url(path, Role.POSTER)

    fun wideCard(path: String?): String? = url(path, Role.WIDE_CARD)

    fun backdrop(path: String?): String? = url(path, Role.BACKDROP)

    /**
     * The details pages' header: the backdrop, or the poster at its card size when a title has none (a cache hit
     * instead of a third copy of the poster).
     */
    fun detailsHeader(backdropPath: String?, posterPath: String?): String? =
        backdrop(backdropPath) ?: poster(posterPath)
}
