package com.drivecinema.app.core.metadata

/**
 * Picks the right show from TMDB's `/search/tv` results, which come sorted by popularity. Searching "Ted"
 * returns "Ted Lasso" first; the folder "Ted (2024)" means the 2024 show "ted". Preference order:
 * exact name and year, exact name, year, then TMDB's first result. A folder marked "(Anime)" ("One Piece (Anime)")
 * looks among animated shows first: "One Piece" alone finds the more popular live-action show.
 *
 * A bare entry (no poster, no genres) is a last resort (report F-48): in November 2026 someone added an empty
 * "Bleach: Thousand-Year Blood War" (2022, one episode) to TMDB, and its exact name and year beat Bleach, whose
 * season 2 is the arc. A filled-in show that fits the name, or whose name starts the folder's ("Bleach"), wins.
 */
object TmdbSeriesMatcher {

    data class Candidate(
        val name: String,
        val originalName: String,
        val year: Int?,
        val animated: Boolean = false,
        /** No poster and no genres: an entry someone has only started on TMDB. */
        val bare: Boolean = false
    )

    /** TMDB's genre ID for Animation. */
    const val ANIMATION_GENRE = 16

    /** A trailing "Anime" word, as the folder "One Piece (Anime)" is read ("One Piece Anime"). */
    private val ANIME_TAG = Regex("""^(.+?)\s+anime$""", RegexOption.IGNORE_CASE)

    /** [title] without its "Anime" tag ("One Piece Anime" -> "One Piece"), or null when it has none. */
    fun withoutAnimeTag(title: String): String? = ANIME_TAG.matchEntire(title.trim())?.groupValues?.get(1)

    /**
     * Index into [candidates] of the best match, or null when there are none. With [animated], the rules are tried
     * on the animated candidates first, then on all of them.
     */
    fun pick(candidates: List<Candidate>, title: String, year: Int?, animated: Boolean = false): Int? {
        if (candidates.isEmpty()) return null
        if (animated) {
            val cartoons = candidates.withIndex().filter { it.value.animated }
            pick(cartoons.map { it.value }, title, year)?.let { return cartoons[it].index }
        }
        val filled = candidates.withIndex().filter { !it.value.bare }
        if (filled.size < candidates.size) {
            firstByRules(filled.map { it.value }, title, year, byNameOnly = true)?.let { return filled[it].index }
        }
        return firstByRules(candidates, title, year) ?: 0
    }

    /**
     * Index of the candidate the first fitting rule picks, or null. [byNameOnly] (the filled-in shows next to a bare
     * one) drops "same year" and adds "its name starts the title": a bare new show still beats another show that
     * merely first aired the same year.
     */
    private fun firstByRules(candidates: List<Candidate>, title: String, year: Int?, byNameOnly: Boolean = false): Int? {
        val wanted = normalize(title)
        fun exactName(c: Candidate) = normalize(c.name) == wanted || normalize(c.originalName) == wanted
        fun sameYear(c: Candidate) = year != null && c.year == year
        // "Bleach" for "Bleach: Thousand-Year Blood War"; 4+ characters so a short name like "It" doesn't count.
        fun parentOf(c: Candidate) = listOf(c.name, c.originalName).map(::normalize)
            .any { it.length >= 4 && it.length < wanted.length && wanted.startsWith(it) }

        val rules: List<(Candidate) -> Boolean> = listOf(
            { c: Candidate -> exactName(c) && sameYear(c) },
            { c: Candidate -> exactName(c) },
            if (byNameOnly) { c: Candidate -> parentOf(c) } else { c: Candidate -> sameYear(c) }
        )
        for (rule in rules) {
            val index = candidates.indexOfFirst(rule)
            if (index >= 0) return index
        }
        return null
    }

    /** Case and punctuation don't matter, and "&" equals "and": "Grey's Anatomy" == "greys anatomy". */
    fun normalize(name: String): String =
        name.lowercase().replace("&", "and").filter { it.isLetterOrDigit() }
}
