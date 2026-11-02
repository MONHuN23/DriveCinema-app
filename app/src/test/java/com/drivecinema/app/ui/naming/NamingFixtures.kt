package com.drivecinema.app.ui.naming

import com.drivecinema.app.core.metadata.FolderCheck
import com.drivecinema.app.core.metadata.FolderCheck.FileResult
import com.drivecinema.app.core.metadata.NamingGuide
import com.drivecinema.app.core.metadata.SeriesFolderListing
import com.drivecinema.app.core.metadata.SeriesLayout
import com.drivecinema.app.core.metadata.TmdbEpisode
import com.drivecinema.app.core.metadata.TmdbMetadata
import com.drivecinema.app.core.metadata.TmdbSeason
import com.drivecinema.app.core.metadata.TmdbSeriesResult
import com.drivecinema.app.core.naming.NamingCheckModel
import com.drivecinema.app.core.naming.NamingCheckState
import com.drivecinema.app.core.naming.NamingGuideState
import com.drivecinema.app.core.naming.NamingPick
import com.drivecinema.app.core.naming.NamingSeasonSection

/** Tasks R.2: screen states as R.1 and the shared models make them, without TMDB or Drive. */
object NamingFixtures {

    val onePiece = TmdbSeriesResult(TmdbMetadata(37854, "One Piece", "Pirates.", null, null, 1999), "ワンピース", animated = true)
    val onePieceLive = TmdbSeriesResult(TmdbMetadata(111110, "ONE PIECE", "Live action.", null, null, 2023), "ONE PIECE", animated = false)

    private val seasons = listOf(
        TmdbSeason(0, "Specials", episodeCount = 3),
        TmdbSeason(1, "East Blue", episodeCount = 61),
        TmdbSeason(22, "Egghead", episodeCount = 3)
    )

    val plan = NamingGuide.SeriesPlan(onePiece.metadata, "One Piece (Anime) (1999)", "One Piece", folderFindsShow = true, seasons = seasons)

    private fun episode(season: Int, number: Int) = TmdbEpisode(season, number, "OP $number", airDate = "2024-01-0${number % 9 + 1}")

    val season22 = NamingGuide.SeasonTable(
        22, NamingGuide.Layout.SHOW_SEASON, plan.folderName, true,
        listOf(
            NamingGuide.Row(episode(22, 1086), plan.folderName, "Season 22", "One Piece S22E1086.mkv", readsBack = true),
            NamingGuide.Row(episode(22, 1087), plan.folderName, "Season 22", "One Piece S22E1087.mkv", readsBack = true),
            NamingGuide.Row(episode(22, 1088), plan.folderName, "Season 22", "One Piece S22E1088.mkv", readsBack = false)
        )
    )

    val searched = NamingGuideState(query = "One Piece", seriesResults = listOf(onePiece, onePieceLive))

    /** One Piece picked; season 22 open with its three rows when [open]. */
    fun picked(open: Boolean = true) = searched.copy(
        picked = NamingPick.Series(
            onePiece,
            plan,
            isLoading = false,
            sections = mapOf(
                0 to NamingSeasonSection(0),
                1 to NamingSeasonSection(1, arcOffered = true),
                22 to NamingSeasonSection(22, isOpen = open, table = season22.takeIf { open })
            )
        )
    )

    private fun file(id: String, season: String?, name: String) =
        SeriesFolderListing.ListedFile(id, name, folderId = season ?: "show", seasonFolderName = season)

    private val bbSeasons = listOf(TmdbSeason(1, "Season 1", episodeCount = 4), TmdbSeason(2, "Season 2", episodeCount = 13))
    private val bb = TmdbMetadata(1396, "Breaking Bad", null, null, null, 2008)
    private fun bbEpisode(n: Int) = TmdbEpisode(1, n, if (n == 1) "Pilot" else "BB1 $n", airDate = "2008-0${n % 9 + 1}-01")

    /** A Breaking Bad folder: E01 recognised, E02 in the wrong folder, an extra without a number, E03 and E04 missing. */
    val checkReady: NamingCheckState.Ready = run {
        val pilot = file("f1", "Season 01", "Breaking Bad S01E01.mkv")
        val misplaced = file("f2", "Season 01", "Breaking Bad S02E02.mkv")
        val extra = file("f3", "Season 01", "Behind the scenes.mkv")
        val listing = SeriesFolderListing("show", "Breaking Bad (2008)", listOf(pilot, misplaced, extra))
        fun key(f: SeriesFolderListing.ListedFile) = SeriesLayout.episodeKey(f.seasonFolderName, f.name)
        fun missing(n: Int) = FolderCheck.Missing(bbEpisode(n), FolderCheck.Suggestion(bbEpisode(n), "Season 01", "Breaking Bad S01E0$n.mkv"))
        val report = FolderCheck.Report(
            listing = listing,
            show = bb,
            arc = null,
            files = listOf(
                FileResult.Matched(pilot, key(pilot), bbEpisode(1)),
                FileResult.SeasonMismatch(
                    misplaced, key(misplaced), bbEpisode(2), fileSays = 2,
                    suggestion = FolderCheck.Suggestion(TmdbEpisode(2, 2, "BB2 2"), "Season 02", "Breaking Bad S02E02.mkv")
                ),
                FileResult.NotRecognised(extra, key(extra), FolderCheck.Reason.NO_EPISODE_NUMBER, suggestion = null)
            ),
            missing = listOf(missing(3), missing(4)),
            seasons = bbSeasons
        )
        NamingCheckModel.ready(report)
    }
}
