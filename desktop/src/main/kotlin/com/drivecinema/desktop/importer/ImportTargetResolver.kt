package com.drivecinema.desktop.importer

import com.drivecinema.app.core.metadata.MediaFilenameParser
import com.drivecinema.app.core.model.DriveFolder
import java.nio.file.Path
import kotlin.io.path.name

/** What kind of media the user keeps in a local import folder (Task I.1). */
enum class ImportFolderKind { MOVIES, SERIES }

sealed interface ImportTarget {
    /**
     * Upload [fileName] into this folder path under the Drive library root, e.g. `["Films"]` or
     * `["Series", "Ted (2024)", "Season 01"]`. The file keeps its local name.
     */
    data class Upload(val folderPath: List<String>, val fileName: String) : ImportTarget

    /** Not uploaded: shown on the "needs review" list with the reason. */
    data class NeedsReview(val reason: String) : ImportTarget
}

/**
 * Task I.1, identification: where a finished local file goes on Drive, using the same layout rules as the
 * crawlers (`LocalLibraryCrawler`, Drive sync): films into `Films/`, episodes into `Series/<Show>/Season NN/`.
 * Anything it can't place with confidence goes to "needs review" instead of a guessed folder.
 */
object ImportTargetResolver {
    const val FILMS_FOLDER = "Films"
    const val SERIES_FOLDER = "Series"

    fun resolve(kind: ImportFolderKind, root: Path, file: Path): ImportTarget {
        val parts = root.relativize(file).map { it.name }
        val fileName = parts.lastOrNull() ?: return ImportTarget.NeedsReview("Not inside the import folder.")
        if (parts.any { it == ".." }) return ImportTarget.NeedsReview("Not inside the import folder.")
        if (!ImportFileRules.isCandidate(fileName)) return ImportTarget.NeedsReview("Not a video file.")
        val marked = MediaFilenameParser.parseMarkedEpisode(fileName)
        return when (kind) {
            ImportFolderKind.MOVIES -> when {
                // Same rule as the crawler: "Show.S01E04" is an episode wherever it is.
                marked != null -> episode(marked.cleanShowName, marked.season, fileName)
                MediaFilenameParser.parseMovie(fileName).cleanTitle.isEmpty() ->
                    ImportTarget.NeedsReview("No film title in the file name.")
                else -> ImportTarget.Upload(listOf(FILMS_FOLDER), fileName)
            }
            ImportFolderKind.SERIES -> when (parts.size) {
                1 -> marked?.let { episode(it.cleanShowName, it.season, fileName) }
                    ?: ImportTarget.NeedsReview("Put it in a folder named after the show, or name it like \"Show.S01E02\".")
                2 -> {
                    if (!hasEpisodeNumber(fileName)) return ImportTarget.NeedsReview("No episode number (S01E02) in the file name.")
                    episode(parts[0], marked?.season ?: 1, fileName)
                }
                3 -> {
                    val season = MediaFilenameParser.parseSeasonFolderOrNull(parts[1])
                        ?: return ImportTarget.NeedsReview("\"${parts[1]}\" is not a season folder (Season 1, S01).")
                    if (!hasEpisodeNumber(fileName)) return ImportTarget.NeedsReview("No episode number (S01E02) in the file name.")
                    episode(parts[0], season, fileName)
                }
                else -> ImportTarget.NeedsReview("Too many folders: use Show/Season NN/episode.")
            }
        }
    }

    /** The Drive folder name for a season: "Season 01". */
    fun seasonFolderName(season: Int): String = "Season %02d".format(season)

    private fun episode(show: String, season: Int, fileName: String): ImportTarget {
        val showFolder = show.trim()
        if (showFolder.isEmpty()) return ImportTarget.NeedsReview("No show name.")
        return ImportTarget.Upload(listOf(SERIES_FOLDER, showFolder, seasonFolderName(season)), fileName)
    }

    /** True for "Show.S01E02…" and bare "Episode 3" / "03 - Title" names; false for names the parser only guessed. */
    private fun hasEpisodeNumber(fileName: String): Boolean =
        MediaFilenameParser.parseMarkedEpisode(fileName) != null ||
            MediaFilenameParser.parseEpisode(fileName).cleanShowName.isEmpty()
}

/** Result of looking for an existing Drive folder before creating one (existing folders are never renamed or moved). */
sealed interface FolderMatch {
    data class Existing(val folder: DriveFolder) : FolderMatch
    /** No folder fits; it would be created (only with the approved write scope). */
    data object Missing : FolderMatch
    /** More than one folder fits and none has the exact name: needs review, nothing is created. */
    data class Ambiguous(val candidates: List<DriveFolder>) : FolderMatch
}

object DriveFolderMatcher {

    /**
     * A show folder: the same name (case and spacing ignored); else the same title and year ("Ted 2024" = "Ted (2024)");
     * else the same title where only one side has a year ("Westworld" = "Westworld (2016)": episodes named
     * "Westworld.S02E01" carry no year). Two different years never match ("Ted (1990)" ≠ "Ted (2024)").
     */
    fun matchShow(wanted: String, existing: List<DriveFolder>): FolderMatch {
        val folders = existing.filter { it.isFolder }
        folders.filter { normalize(it.name) == normalize(wanted) }.let { exact -> pick(exact)?.let { return it } }
        val wantedShow = parseFolderTitle(wanted)
        val sameTitle = folders.map { it to parseFolderTitle(it.name) }
            .filter { (_, parsed) -> parsed.cleanTitle.equals(wantedShow.cleanTitle, ignoreCase = true) }
        pick(sameTitle.filter { (_, parsed) -> parsed.year == wantedShow.year }.map { it.first })?.let { return it }
        val oneYearMissing = sameTitle.filter { (_, parsed) -> parsed.year == null || wantedShow.year == null }.map { it.first }
        return pick(oneYearMissing) ?: FolderMatch.Missing
    }

    /** A season folder: the exact "Season NN" name, else any folder with that season number ("Season 1", "S01"). */
    fun matchSeason(season: Int, existing: List<DriveFolder>): FolderMatch {
        val folders = existing.filter { it.isFolder }
        folders.filter { normalize(it.name) == normalize(ImportTargetResolver.seasonFolderName(season)) }
            .let { exact -> pick(exact)?.let { return it } }
        return pick(folders.filter { MediaFilenameParser.parseSeasonFolderOrNull(it.name) == season }) ?: FolderMatch.Missing
    }

    private fun pick(matches: List<DriveFolder>): FolderMatch? = when (matches.size) {
        0 -> null
        1 -> FolderMatch.Existing(matches.single())
        else -> FolderMatch.Ambiguous(matches)
    }

    /** parseMovie drops a file extension; folder names have none ("Ted.2024" must keep its year). */
    private fun parseFolderTitle(name: String) = MediaFilenameParser.parseMovie("$name.folder")

    private fun normalize(name: String): String = name.trim().replace(Regex("""\s+"""), " ").lowercase()
}
