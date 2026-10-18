package com.drivecinema.app.ui.setup

/**
 * In-app "Setup guide" text. Every [NamingExample] is checked against MediaFilenameParser in
 * SetupGuideContentTest, so the guide can never promise a file name the app doesn't understand.
 */
sealed interface NamingExample {
    val fileName: String

    data class Movie(override val fileName: String, val title: String, val year: Int?) : NamingExample
    data class Episode(override val fileName: String, val season: Int, val episode: Int) : NamingExample
}

data class GuideSection(
    val title: String,
    val intro: String? = null,
    val bullets: List<String> = emptyList(),
    val folderTree: String? = null,
    val examples: List<NamingExample> = emptyList()
)

object SetupGuideContent {

    val FOLDER_TREE = """
        Streaming/
        ├── Films/
        │   ├── Dune Part Two (2024).mkv
        │   └── Inception (2010)/
        │       └── Inception.2010.1080p.mkv
        └── Series/
            └── Breaking Bad/
                ├── Season 01/
                │   ├── Breaking Bad - S01E01.mkv
                │   └── Breaking Bad - S01E02.mkv
                └── Season 02/
                    └── Breaking Bad - S02E01.mkv
    """.trimIndent()

    val sections: List<GuideSection> = listOf(
        GuideSection(
            title = "1. Set up your folders",
            intro = "DriveCinema reads one library folder in Google Drive. Inside it, put a Films folder and a Series folder.",
            folderTree = FOLDER_TREE,
            bullets = listOf(
                "Name the library folder \"Streaming\" and DriveCinema finds it by itself when you sign in. Any other name works too — just pick it in the folder list.",
                "The two folders inside must be called \"Films\" and \"Series\" (capital letters don't matter).",
                "Films: put movie files straight in Films, or in their own subfolders — both are found.",
                "Series: make one folder per show (the folder name is the show name, e.g. \"Doctor Who (2005)\"), and inside it one folder per season: \"Season 1\", \"Season 01\" or \"S1\".",
                "Season folders are recommended. Episodes may also sit directly in the show folder, but then name them \"Show - S02E05.mkv\" (with the show name) so the season is read from the file — otherwise they count as season 1.",
                "Video files placed directly in the library folder are treated as films."
            )
        ),
        GuideSection(
            title = "2. Name your films",
            intro = "Best format:  Title (Year).mkv  — the year is what makes posters and details match.",
            bullets = listOf(
                "Dots, underscores and dashes are fine; they become spaces.",
                "Anything after the year (1080p, BluRay, x264…) is ignored.",
                "Without a year, extra tags stay in the title and the poster may not be found."
            ),
            examples = listOf(
                NamingExample.Movie("Dune Part Two (2024).mkv", "Dune Part Two", 2024),
                NamingExample.Movie("Inception.2010.1080p.BluRay.x264.mkv", "Inception", 2010),
                NamingExample.Movie("The.Matrix.[1999].mkv", "The Matrix", 1999)
            )
        ),
        GuideSection(
            title = "3. Name your episodes",
            intro = "Best format:  Show - S01E01 - Episode title.mkv",
            bullets = listOf(
                "The episode number comes from SxxEyy in the file name — always include it, with two digits (S01E05).",
                "For files inside a season folder, the season number comes from the folder name.",
                "The show name comes from the show's folder, so the name in the file is optional.",
                "Files without an episode number are all treated as episode 1, so they would overlap."
            ),
            examples = listOf(
                NamingExample.Episode("Breaking Bad - S01E01 - Pilot.mkv", season = 1, episode = 1),
                NamingExample.Episode("Stranger.Things.S04E07.mkv", season = 4, episode = 7),
                NamingExample.Episode("S02E05 - Episode Name.mp4", season = 2, episode = 5)
            )
        ),
        GuideSection(
            title = "4. Video and audio formats",
            intro = "For trouble-free playback on phones and TVs, use MP4 or MKV with H.264 video and AAC stereo audio.",
            bullets = listOf(
                "File types found: .mp4 and .mkv (recommended), also .avi, .mov, .wmv, .m4v, .webm, .ts.",
                "Video: H.264 plays everywhere. H.265 (HEVC) gives smaller files and plays on modern phones and TVs.",
                "✓ Audio that plays everywhere: AAC (best choice) and MP3. Opus and FLAC also work on most newer devices.",
                "⚠ Dolby Digital (AC-3 / E-AC-3): most TVs play it, many phones (e.g. Google Pixel) don't — you get video without sound and a warning.",
                "✗ DTS / TrueHD: usually no sound on phones and TVs.",
                "Tip: if a film has surround sound, keep an extra AAC stereo track in the file. You can switch tracks in the player.",
                "Subtitles are not shown in DriveCinema."
            )
        ),
        GuideSection(
            title = "5. Share your library with someone",
            bullets = listOf(
                "In Google Drive, right-click your library folder → Share → add the other person's Google account. \"Viewer\" is enough.",
                "They sign in to DriveCinema with that account; your folder appears under \"Shared with you\", next to the folders in their own Drive.",
                "Anyone can switch between libraries later: Account → Switch Library Folder.",
                "People only ever see folders that were shared with their account — nothing else from your Drive."
            )
        ),
        GuideSection(
            title = "6. Videos stored on this device",
            bullets = listOf(
                "Account → Add Local Folder, then pick a folder on the phone (or a USB drive on a TV that supports it).",
                "Use the same layout and names as on Drive: Films and Series folders inside the folder you pick.",
                "If the same film or episode is on Drive and on the device, it appears once and plays the local copy — no internet needed. Watch progress carries over between both copies.",
                "When DriveCinema isn't sure two titles are the same (for example a file without a year), it adds it separately and asks you: Account → Review possible duplicates.",
                "Removing a folder hides its videos; your watch history is kept."
            )
        ),
        GuideSection(
            title = "7. If something doesn't work",
            bullets = listOf(
                "\"Folder must contain Films, Series…\": rename the folders inside your library to Films and Series, then tap Refresh.",
                "Video plays without sound on the phone: the audio is Dolby or DTS — pick another audio track or add an AAC track.",
                "Wrong or missing poster: rename the file to Title (Year).mkv; details are updated on the next sync.",
                "A shared folder is missing: check it was shared with exactly the Google account you signed in with, then tap Refresh."
            )
        ),
        GuideSection(
            title = "8. About & credits",
            intro = TMDB_NOTICE,
            bullets = listOf(
                "Posters, backdrops and descriptions come from TMDB (The Movie Database, themoviedb.org).",
                "Your videos stay in your own Google Drive. DriveCinema only reads them; it never changes or uploads anything.",
                "The desktop app plays videos with VLC (libVLC) through vlcj.",
                "DriveCinema is free software under the GNU GPL v3."
            )
        )
    )

    /** Required wording from TMDB's API terms (Milestone J.3). */
    const val TMDB_NOTICE = "This product uses the TMDB API but is not endorsed or certified by TMDB."
}
