package com.drivecinema.desktop.importer

import com.drivecinema.app.core.model.DriveFolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Path
import java.nio.file.Paths

/** Task I.1, identification: the Drive folder for a local file, or "needs review" when it can't be placed safely. */
class ImportTargetResolverTest {

    private val movies = Paths.get("media", "Movies").toAbsolutePath()
    private val series = Paths.get("media", "TV").toAbsolutePath()

    @Test
    fun filmsGoToFilmsWhateverTheirSubfolder() {
        assertEquals(upload("Films", file = "Dune (2021).mkv"), resolve(ImportFolderKind.MOVIES, movies, "Dune (2021).mkv"))
        assertEquals(upload("Films", file = "Heat.1995.1080p.mkv"), resolve(ImportFolderKind.MOVIES, movies, "Crime/Heat.1995.1080p.mkv"))
    }

    @Test
    fun anEpisodeInTheMoviesFolderGoesToItsShow() {
        assertEquals(
            upload("Series", "Rick and Morty", "Season 02", file = "Rick.and.Morty.S02E04.mkv"),
            resolve(ImportFolderKind.MOVIES, movies, "Rick.and.Morty.S02E04.mkv")
        )
    }

    @Test
    fun episodesKeepTheLocalShowFolderNameAndGetASeasonFolder() {
        assertEquals(
            upload("Series", "Ted (2024)", "Season 01", file = "Ted.S01E03.mkv"),
            resolve(ImportFolderKind.SERIES, series, "Ted (2024)/Season 1/Ted.S01E03.mkv")
        )
        assertEquals(
            upload("Series", "Ted (2024)", "Season 02", file = "03 - Pilot.mkv"),
            resolve(ImportFolderKind.SERIES, series, "Ted (2024)/S2/03 - Pilot.mkv")
        )
        assertEquals(
            upload("Series", "Ted (2024)", "Season 02", file = "Ted.S02E01.mkv"),
            resolve(ImportFolderKind.SERIES, series, "Ted (2024)/Ted.S02E01.mkv")
        )
        assertEquals(
            upload("Series", "Show", "Season 01", file = "Show.S01E05.mkv"),
            resolve(ImportFolderKind.SERIES, series, "Show.S01E05.mkv")
        )
    }

    @Test
    fun unclearFilesNeedReviewInsteadOfAGuessedFolder() {
        listOf(
            "Extras/Show/Making of.mkv",
            "Show/Extras/Show.S01E01.mkv",
            "Show/Season 1/Behind the scenes.mkv",
            "Show/Season 1/Disc 1/Show.S01E01.mkv",
            "Random video.mkv",
            "Show/Season 1/notes.txt"
        ).forEach { relative ->
            assertTrue(relative, resolve(ImportFolderKind.SERIES, series, relative) is ImportTarget.NeedsReview)
        }
        assertTrue(ImportTargetResolver.resolve(ImportFolderKind.MOVIES, movies, Paths.get("elsewhere", "x.mkv").toAbsolutePath()) is ImportTarget.NeedsReview)
    }

    @Test
    fun existingShowFolderIsReusedEvenWithDifferentSpelling() {
        val existing = listOf(folder("1", "ted (2024)"), folder("2", "Ted Lasso"))
        assertEquals(FolderMatch.Existing(existing[0]), DriveFolderMatcher.matchShow("Ted (2024)", existing))
        assertEquals(FolderMatch.Existing(existing[0]), DriveFolderMatcher.matchShow("Ted.2024", existing))
        assertEquals(FolderMatch.Missing, DriveFolderMatcher.matchShow("Ted (1990)", existing))
    }

    @Test
    fun aShowNameWithoutAYearFindsTheFolderWithOne() {
        // Found on the device: "Westworld.S02E01…" (no year) created a second "Westworld" next to "Westworld (2016)".
        val westworld = folder("w", "Westworld (2016)")
        assertEquals(FolderMatch.Existing(westworld), DriveFolderMatcher.matchShow("Westworld", listOf(westworld)))
        assertEquals(FolderMatch.Existing(folder("x", "Westworld")), DriveFolderMatcher.matchShow("Westworld (2016)", listOf(folder("x", "Westworld"))))
        val two = listOf(folder("a", "Ted (2012)"), folder("b", "Ted (2024)"))
        assertTrue("two years and no year given: ask", DriveFolderMatcher.matchShow("Ted", two) is FolderMatch.Ambiguous)
        assertEquals("the same year wins over a yearless folder", FolderMatch.Existing(two[1]),
            DriveFolderMatcher.matchShow("Ted (2024)", two + folder("c", "Ted")))
    }

    @Test
    fun existingSeasonFolderIsReusedWhateverItsNamingStyle() {
        val existing = listOf(folder("s1", "S01"), folder("s2", "Season 2"), folder("x", "Extras"))
        assertEquals(FolderMatch.Existing(existing[0]), DriveFolderMatcher.matchSeason(1, existing))
        assertEquals(FolderMatch.Existing(existing[1]), DriveFolderMatcher.matchSeason(2, existing))
        assertEquals(FolderMatch.Missing, DriveFolderMatcher.matchSeason(3, existing))
    }

    @Test
    fun twoPossibleFoldersAreAmbiguousUnlessOneHasTheExactName() {
        val two = listOf(folder("a", "Season 1"), folder("b", "S1"))
        assertTrue(DriveFolderMatcher.matchSeason(1, two) is FolderMatch.Ambiguous)
        val withExact = two + folder("c", "Season 01")
        assertEquals(FolderMatch.Existing(withExact[2]), DriveFolderMatcher.matchSeason(1, withExact))
        val files = listOf(folder("f", "Season 01", isFolder = false))
        assertEquals("files are never taken for folders", FolderMatch.Missing, DriveFolderMatcher.matchSeason(1, files))
    }

    private fun resolve(kind: ImportFolderKind, root: Path, relative: String): ImportTarget =
        ImportTargetResolver.resolve(kind, root, root.resolve(relative.split('/').joinToString(java.io.File.separator)))

    private fun upload(vararg folders: String, file: String) = ImportTarget.Upload(folders.toList(), file)

    private fun folder(id: String, name: String, isFolder: Boolean = true) = DriveFolder(id, name, isFolder = isFolder)
}
