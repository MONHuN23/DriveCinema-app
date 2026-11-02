package com.drivecinema.app.core.naming

import com.drivecinema.app.core.metadata.FolderCheck
import com.drivecinema.app.core.metadata.FolderCheck.FileResult
import com.drivecinema.app.core.metadata.LibraryNames
import com.drivecinema.app.core.metadata.NamingGuide
import com.drivecinema.app.core.metadata.SeriesFolderListing
import com.drivecinema.app.core.metadata.TmdbEpisode
import com.drivecinema.app.ui.setup.SettingsLabels

/**
 * The naming helper's wording (Tasks R.2/R.3), the same on the phone, the TV and the desktop. Only words: every
 * name shown comes from R.1 ([NamingGuide], [FolderCheck]), never from a rule here.
 */
object NamingTexts {
    const val HELPER_TITLE = SettingsLabels.NAMING_HELPER
    const val CHECK_TITLE = SettingsLabels.NAMING_CHECK
    const val HELPER_SUPPORTING = "Folder and file names the app recognises"
    const val HELPER_INTRO = "Search TMDB for a series or a film to see the folder and file names DriveCinema " +
        "recognises. Name your files like this before you upload them."
    const val CHECK_INTRO = "What DriveCinema makes of each file in this series' Drive folder, and the name to use " +
        "for each problem. Nothing is changed on Drive."

    const val SERIES = "Series"
    const val FILMS = "Films"
    const val SEARCH = "Search"
    fun searchLabel(kind: NamingSearchKind) = if (kind == NamingSearchKind.SERIES) "Series name" else "Film title"
    const val SEARCHING = "Searching TMDB…"
    const val NEEDS_TMDB = "The naming helper needs TMDB, and this copy of DriveCinema has no TMDB key."
    const val TMDB_FAILED = "TMDB couldn't be reached. Check the internet connection and try again."
    const val RETRY = "Try again"
    fun noResults(query: String) = "TMDB has nothing called \"$query\"."
    const val ANIMATION = "Animation"
    const val LOADING = "Loading…"

    const val COPY = "Copy"
    const val COPY_ALL = "Copy all"
    const val COPIED = "Copied"

    // ---- The naming guide ----

    /** "Series/One Piece (Anime) (1999)/". */
    fun seriesFolderPath(folderName: String) = "${LibraryNames.SERIES_FOLDER}/$folderName/"

    /** Under the folder path: whether the sync's own search, given that name, finds the show. */
    fun folderCheck(findsShow: Boolean?): String? = when (findsShow) {
        true -> null
        // F-48: "Keep the year in the name" was wrong advice: the suggested name always has the year.
        false -> "Careful: with this folder name the app finds a different TMDB show, so these episode names may not be used."
        null -> "Couldn't check this folder name with TMDB."
    }

    const val LAYOUT_SHOW_SEASON = "Show's season"
    const val LAYOUT_ARC_FOLDER = "Arc as its own folder"
    const val LAYOUT_RUNNING_NUMBER = "Running numbers"

    fun layoutLabel(layout: NamingGuide.Layout) = when (layout) {
        NamingGuide.Layout.SHOW_SEASON -> LAYOUT_SHOW_SEASON
        NamingGuide.Layout.ARC_FOLDER -> LAYOUT_ARC_FOLDER
        NamingGuide.Layout.RUNNING_NUMBER -> LAYOUT_RUNNING_NUMBER
    }

    /** The layouts a season offers, the default first; one only: no chips. */
    fun layouts(section: NamingSeasonSection): List<NamingGuide.Layout> = buildList {
        add(NamingGuide.Layout.SHOW_SEASON)
        if (section.arcOffered) add(NamingGuide.Layout.ARC_FOLDER)
        if (section.runningNumbersOffered) add(NamingGuide.Layout.RUNNING_NUMBER)
    }

    /** "Season 2 · Thousand-Year Blood War", "Season 1", "Specials (Season 00)". */
    fun seasonTitle(season: Int, tmdbName: String?): String {
        if (season == 0) return "Specials (Season 00)"
        val name = tmdbName?.trim().orEmpty()
        return if (name.isEmpty() || name.equals("Season $season", ignoreCase = true)) "Season $season" else "Season $season · $name"
    }

    fun episodeCount(count: Int) = if (count == 1) "1 episode" else "$count episodes"

    fun noneOnDrive(count: Int) = "${episodeCount(count)}, none on Drive"

    /** "S02E14". */
    fun episodeCode(season: Int, episode: Int) =
        "S${season.toString().padStart(2, '0')}E${episode.toString().padStart(2, '0')}"

    /** A row's first line: "E14 · The Blood Warfare · 2023-01-10". */
    fun episodeLine(episode: TmdbEpisode): String =
        listOfNotNull("E${episode.episodeNumber.toString().padStart(2, '0')}", episode.name.takeIf { it.isNotBlank() }, episode.airDate?.takeIf { it.isNotBlank() })
            .joinToString(" · ")

    const val CANT_READ = "The app can't read a name for this episode."

    /** A season of the guide as text: its folder and every name, one per line (desktop "Copy all"). */
    fun guideCopy(table: NamingGuide.SeasonTable): String =
        table.rows.filter { it.readsBack }.joinToString("\n") { it.path }

    /** "Films/Ted (2012).mkv". */
    fun filmPath(plan: NamingGuide.FilmPlan) = filmPath(plan.fileName)

    fun filmPath(fileName: String) = "${LibraryNames.FILMS_FOLDER}/$fileName"

    // ---- The TV's results and names panel (UI task ui/tv-browse-naming) ----

    /** "12 results for “one”". */
    fun resultCount(count: Int, query: String) = (if (count == 1) "1 result" else "$count results") + " for “$query”"

    /** "2003 · Series", "Film". */
    fun kindLine(year: Int?, kind: NamingSearchKind) =
        listOfNotNull(year?.toString(), if (kind == NamingSearchKind.SERIES) "Series" else "Film").joinToString(" · ")

    const val PREVIEW_LABEL = "Names DriveCinema recognises"
    const val PREVIEW_HINT_SERIES = "Press OK to see every season and episode name and the checks."
    const val PREVIEW_HINT_FILM = "Press OK to check this name with TMDB."
    const val SERIES_FOLDER = "Series folder"
    const val BACK_TO_RESULTS = "Back: return to the results"

    /** A series preview as a small tree: the folder, its first season folder, the first episode's file. */
    fun previewTree(preview: NamingGuide.SeriesPreview) =
        "${seriesFolderPath(preview.folderName)}\n  ${preview.seasonFolder}/\n    ${preview.fileName}"

    fun filmCheck(plan: NamingGuide.FilmPlan): String? = when (plan.appFindsIt) {
        true -> null
        false -> "Careful: with this name the app finds another film" +
            (plan.appPicks?.let { " (${it.title}${it.year?.let { y -> ", $y" }.orEmpty()})" }.orEmpty()) + "."
        null -> "Couldn't check this name with TMDB."
    }

    // ---- The folder check ----

    const val ALL = "All"
    fun problems(count: Int) = "Problems ($count)"
    fun missing(count: Int) = "Missing ($count)"
    const val UNPLACED = "Files the app can't place"
    const val NO_PROBLEMS = "No problems: the app recognises every file."
    const val NOTHING_MISSING = "No aired episode is missing."
    const val LOCAL_SERIES = "The naming check reads Drive folders, and this series is in a folder on a device. " +
        "Settings → ${SettingsLabels.NAMING_HELPER} shows the names to use."
    const val DRIVE_FAILED = "Couldn't read this series' folder on Drive. Check the internet connection and try again."
    fun noShow(folderName: String) = "TMDB finds no show for the folder name \"$folderName\". Name the folder after " +
        "the show, with its year, as the naming helper shows."
    const val PICK_EPISODE = "Which episode is this?"
    const val PICK_INTRO = "Pick the episode this file is; its name to use is shown on the file's row."
    const val CLEAR_PICK = "Not this one"
    const val CHANGE_PICK = "Change episode"

    /** A file's path in the series folder: "Season 01/Breaking Bad S01E01.mkv". */
    fun filePath(file: SeriesFolderListing.ListedFile) = (file.seasonFolderName?.let { "$it/" } ?: "") + file.name

    fun suggestionPath(suggestion: FolderCheck.Suggestion) = (suggestion.seasonFolder?.let { "$it/" } ?: "") + suggestion.fileName

    /** A row's status word. */
    fun status(result: FileResult): String = when (result) {
        is FileResult.Matched -> "Recognised"
        is FileResult.NotRecognised -> "Not recognised"
        is FileResult.Duplicate -> "Two files for one episode"
        is FileResult.SeasonMismatch -> "Folder and file disagree"
    }

    const val MISSING_STATUS = "Not on Drive"

    /** Why a file is a problem, in one sentence. */
    fun reason(result: FileResult): String? = when (result) {
        is FileResult.Matched -> null
        is FileResult.NotRecognised -> when (result.reason) {
            FolderCheck.Reason.NO_EPISODE_NUMBER -> "No episode number in the name, so the app reads it as episode 1."
            FolderCheck.Reason.SEASON_FOLDER_WITHOUT_NUMBER ->
                "The folder \"${result.file.seasonFolderName}\" has no season number, so the app reads it as season 1."
            FolderCheck.Reason.NOT_ON_TMDB ->
                "The app reads it as ${episodeCode(result.key.season, result.key.episode)}, and TMDB has no such episode."
        }
        is FileResult.Duplicate -> "Another file is also ${episodeCode(result.episode.seasonNumber, result.episode.episodeNumber)}: " +
            result.otherFiles.joinToString(", ") { filePath(it) } + "."
        is FileResult.SeasonMismatch -> "The folder says season ${result.key.season}, the file says season " +
            "${result.fileSays}. The folder wins."
    }

    /** "Rename to: Season 02/Breaking Bad S02E03.mkv", saying when the file has to move to another folder too. */
    fun suggestionLine(suggestion: FolderCheck.Suggestion, file: SeriesFolderListing.ListedFile): String =
        if (suggestion.inPlace(file)) "Rename to: ${suggestion.fileName}"
        else "Move and rename to: ${suggestionPath(suggestion)}"

    /** A missing episode's name to use. */
    fun missingLine(missing: FolderCheck.Missing) = "Name: ${suggestionPath(missing.suggestion)}"

    /** Every rename the check suggests (the user's picks too), "old → new", one per line (desktop "Copy all"). */
    fun renameList(ready: NamingCheckState.Ready): String =
        ready.report.files.mapNotNull { result ->
            ready.suggestionFor(result)?.let { "${filePath(result.file)} → ${suggestionPath(it)}" }
        }.joinToString("\n")
}
