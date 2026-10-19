package com.drivecinema.app.core.metadata

/**
 * Picks the right show from TMDB's `/search/tv` results, which come sorted by popularity. Searching "Ted"
 * returns "Ted Lasso" first; the folder "Ted (2024)" means the 2024 show "ted". Preference order:
 * exact name and year, exact name, year, then TMDB's first result.
 */
object TmdbSeriesMatcher {

    data class Candidate(val name: String, val originalName: String, val year: Int?)

    /** Index into [candidates] of the best match, or null when there are none. */
    fun pick(candidates: List<Candidate>, title: String, year: Int?): Int? {
        if (candidates.isEmpty()) return null
        val wanted = normalize(title)
        fun exactName(c: Candidate) = normalize(c.name) == wanted || normalize(c.originalName) == wanted
        fun sameYear(c: Candidate) = year != null && c.year == year

        val rules: List<(Candidate) -> Boolean> = listOf(
            { exactName(it) && sameYear(it) },
            { exactName(it) },
            { sameYear(it) }
        )
        for (rule in rules) {
            val index = candidates.indexOfFirst(rule)
            if (index >= 0) return index
        }
        return 0
    }

    /** Case and punctuation don't matter, and "&" equals "and": "Grey's Anatomy" == "greys anatomy". */
    fun normalize(name: String): String =
        name.lowercase().replace("&", "and").filter { it.isLetterOrDigit() }
}
