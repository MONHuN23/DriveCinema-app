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

/** What DriveCinema shows for the example's file: "Inception (2010)", "Season 4, episode 7". */
val NamingExample.shownAs: String
    get() = when (this) {
        is NamingExample.Movie -> if (year != null) "$title ($year)" else title
        is NamingExample.Episode -> "Season $season, episode $episode"
    }

/** Where a guide section is shown (Task J.7): some features exist only on some platforms. */
enum class GuidePlatform(val label: String) {
    PHONE("Phone"),
    TV("TV"),
    DESKTOP("Desktop")
}

/** The Help Center's groups (Task P.4), in the order they are shown. */
enum class GuideCategory(val label: String) {
    GET_STARTED("Get started"),
    WATCHING("Watching"),
    DEVICES("Devices"),
    SHARING("Sharing and privacy"),
    PROBLEMS("Problems")
}

/** An icon in a [GuideIllustration.Flow]; each platform draws it with its own icon set. */
enum class GuideIcon { PHONE, TV, PC, WIFI, VPN, CLOUD, FOLDER, LOCK }

/** One stop of a [GuideIllustration.Flow]: an icon and a short label under it. */
data class GuideFlowNode(val icon: GuideIcon, val label: String)

/** A topic's picture (Task P.4), drawn by each platform from this data; there are no image files. */
sealed interface GuideIllustration {
    /** [SetupGuideContent.FOLDER_TREE] with folder and file icons. */
    data object Folders : GuideIllustration

    /** The topic's [GuideSection.examples]: file name → what DriveCinema shows. */
    data object FileNames : GuideIllustration

    /** Devices or places, left to right, with an arrow between each two. */
    data class Flow(val nodes: List<GuideFlowNode>, val caption: String? = null) : GuideIllustration
}

/**
 * One Help Center topic (a "section" of the J.7 guide). [steps] are done in order and numbered; [tips] are the rest
 * ("Good to know"). Together they hold the J.7 guide's sentences word for word.
 */
data class GuideSection(
    val id: String,
    val category: GuideCategory,
    val title: String,
    /** One line under the title in the topic list. */
    val summary: String,
    val intro: String? = null,
    val steps: List<String> = emptyList(),
    val tips: List<String> = emptyList(),
    val illustration: GuideIllustration? = null,
    val examples: List<NamingExample> = emptyList(),
    val platforms: Set<GuidePlatform> = GuidePlatform.entries.toSet()
) {
    /** "Phone and TV only", "Desktop only"; null when every platform shows the section. */
    val platformNote: String?
        get() = if (platforms.size == GuidePlatform.entries.size) null
        else GuidePlatform.entries.filter { it in platforms }.joinToString(" and ") { it.label } + " only"

    /** Every sentence of the topic, in reading order. */
    val text: List<String> get() = listOfNotNull(intro) + steps + tips
}

/** One line of [SetupGuideContent.FOLDER_TREE]: its depth (0 for the library folder), its name and whether it's a folder. */
data class FolderTreeLine(val depth: Int, val name: String, val isFolder: Boolean)

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

    /** [FOLDER_TREE] as lines to draw with icons: every 4 characters of "│   " or "├── " are one level. */
    val folderTreeLines: List<FolderTreeLine> = FOLDER_TREE.lines().map { line ->
        val name = line.trimStart('│', '├', '└', '─', ' ')
        FolderTreeLine(depth = (line.length - name.length) / 4, name = name.removeSuffix("/"), isFolder = name.endsWith("/"))
    }

    // Topic ids: other screens link to some of them (the folder picker to FOLDERS, Settings to ABOUT).
    const val FOLDERS = "folders"
    const val FILM_NAMES = "film-names"
    const val EPISODE_NAMES = "episode-names"
    const val FORMATS = "formats"
    const val SKIP_INTRO = "skip-intro"
    const val TV_SIGN_IN = "tv-sign-in"
    const val DEVICE_FOLDERS = "device-folders"
    const val COMPUTER_FOLDERS = "computer-folders"
    const val PC_STREAMING = "pc-streaming"
    const val IMPORT = "import"
    const val SHARE = "share"
    const val PAIRED_ACCESS = "paired-access"
    const val ABOUT = "about"
    const val PROBLEMS = "problems"

    private val PHONE_AND_TV = setOf(GuidePlatform.PHONE, GuidePlatform.TV)
    private val DESKTOP_ONLY = setOf(GuidePlatform.DESKTOP)

    /** Every topic, in Help Center order (by [GuideCategory]); [sectionsFor] picks one platform's. */
    val sections: List<GuideSection> = listOf(
        GuideSection(
            id = FOLDERS,
            category = GuideCategory.GET_STARTED,
            title = "Set up your folders",
            summary = "A Films and a Series folder in one library folder",
            intro = "DriveCinema reads one library folder in Google Drive. Inside it, put a Films folder and a Series folder.",
            illustration = GuideIllustration.Folders,
            steps = listOf(
                "Name the library folder \"Streaming\" and DriveCinema finds it by itself when you sign in. Any other name works too — just pick it in the folder list.",
                "The two folders inside must be called \"Films\" and \"Series\" (capital letters don't matter).",
                "Films: put movie files straight in Films, or in their own subfolders — both are found.",
                "Series: make one folder per show (the folder name is the show name, e.g. \"Doctor Who (2005)\"), and inside it one folder per season: \"Season 1\", \"Season 01\" or \"S1\"."
            ),
            tips = listOf(
                "Season folders are recommended. Episodes may also sit directly in the show folder, but then name them \"Show - S02E05.mkv\" (with the show name) so the season is read from the file — otherwise they count as season 1.",
                "Video files placed directly in the library folder are treated as films."
            )
        ),
        GuideSection(
            id = FILM_NAMES,
            category = GuideCategory.GET_STARTED,
            title = "Name your films",
            summary = "Title (Year) is what makes posters match",
            intro = "Best format:  Title (Year).mkv  — the year is what makes posters and details match.",
            illustration = GuideIllustration.FileNames,
            tips = listOf(
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
            id = EPISODE_NAMES,
            category = GuideCategory.GET_STARTED,
            title = "Name your episodes",
            summary = "SxxEyy in the name, seasons from the folders",
            intro = "Best format:  Show - S01E01 - Episode title.mkv",
            illustration = GuideIllustration.FileNames,
            tips = listOf(
                "The episode number comes from SxxEyy in the file name — always include it, with two digits (S01E05).",
                "For files inside a season folder, the season number comes from the folder name.",
                "The show name comes from the show's folder, so the name in the file is optional.",
                "Files without an episode number are all treated as episode 1, so they would overlap.",
                "For the exact folder and file names of one show, use the naming helper: " +
                    "${SettingsLabels.path(SettingsLabels.NAMING_HELPER)} on the phone and TV, account menu → " +
                    "${SettingsLabels.NAMING_HELPER} on the desktop. A series page's ${SettingsLabels.NAMING_CHECK} " +
                    "shows what the app makes of each of its files."
            ),
            examples = listOf(
                NamingExample.Episode("Breaking Bad - S01E01 - Pilot.mkv", season = 1, episode = 1),
                NamingExample.Episode("Stranger.Things.S04E07.mkv", season = 4, episode = 7),
                NamingExample.Episode("S02E05 - Episode Name.mp4", season = 2, episode = 5)
            )
        ),
        GuideSection(
            id = FORMATS,
            category = GuideCategory.WATCHING,
            title = "Video, audio and subtitles",
            summary = "Which files play; audio tracks and subtitles",
            intro = "MP4 or MKV with H.264 or H.265 (HEVC) video plays on phones, TVs and computers.",
            tips = listOf(
                "File types found: .mp4 and .mkv (recommended), also .avi, .mov, .wmv, .m4v, .webm, .ts.",
                "Video: H.264 plays everywhere. H.265 (HEVC) gives smaller files and plays on modern phones and TVs.",
                "Audio: AAC, MP3, Opus, FLAC, Dolby Digital (AC-3, E-AC-3), DTS and TrueHD all play with sound. When a phone or TV can't decode Dolby or DTS itself, DriveCinema decodes it; the desktop plays them with VLC.",
                "A film with several audio tracks (languages, commentary): pick one with the Audio button in the player.",
                "Subtitles inside the video (common in MKV files): pick them with the Subtitles button in the player, or turn them off. Subtitles in separate .srt files next to a video on Drive aren't shown yet."
            )
        ),
        GuideSection(
            id = SKIP_INTRO,
            category = GuideCategory.WATCHING,
            title = "Skip intro",
            summary = "The Skip intro button, and what it sends",
            tips = listOf(
                "When an episode's intro times are known, a \"Skip intro\" button appears while the intro plays. On the TV, press OK to skip (Back hides the button); on the desktop, press S.",
                "The times come from SkipDB (skipdb.tv), or from IntroDB (introdb.app) when SkipDB has none. DriveCinema sends them the show, season and episode number, plus the video's length to SkipDB, and nothing else: no file names, no account, no watch history.",
                "The button only appears when the times were made for a video of the same length as yours, so it never skips to the wrong place. Many episodes have no times yet.",
                "Turn it off in ${SettingsLabels.path(SettingsLabels.SKIP_INTRO)} (phone and TV) or in the account menu → Skip intro (desktop); then nothing is sent."
            )
        ),
        GuideSection(
            id = TV_SIGN_IN,
            category = GuideCategory.DEVICES,
            title = "Sign in on a TV",
            summary = "A TV signs in through your phone",
            platforms = PHONE_AND_TV,
            intro = "TVs can't use Google sign-in, so a TV signs in through your phone. The phone must be signed in and on the same Wi-Fi.",
            illustration = GuideIllustration.Flow(
                listOf(
                    GuideFlowNode(GuideIcon.TV, "TV shows a code"),
                    GuideFlowNode(GuideIcon.PHONE, "Phone: enter the code"),
                    GuideFlowNode(GuideIcon.LOCK, "Sign-in sent, encrypted")
                ),
                caption = "Both on the same Wi-Fi"
            ),
            steps = listOf(
                "On the TV: open DriveCinema → Pair with Phone. The TV shows a 6-character code and its address.",
                "On the phone: Home → Pair TV → enter the code. The phone finds the TV on the Wi-Fi; if it doesn't, type the TV's address shown on the TV."
            ),
            tips = listOf(
                "After 3 wrong codes the TV stops pairing: start again on the TV for a new code.",
                "The phone hands its sign-in to the TV, encrypted. The TV then shows the same library as the phone and keeps it after a restart (see \"What paired devices can access\")."
            )
        ),
        GuideSection(
            id = DEVICE_FOLDERS,
            category = GuideCategory.DEVICES,
            title = "Videos stored on this device",
            summary = "Play videos from this device's storage, even offline",
            platforms = PHONE_AND_TV,
            steps = listOf(
                "${SettingsLabels.path(SettingsLabels.LOCAL_FOLDERS, SettingsLabels.ADD_FOLDER)}, then pick a folder on the phone (or a USB drive on a TV that supports it).",
                "Use the same layout and names as on Drive: Films and Series folders inside the folder you pick."
            ),
            tips = listOf(
                "If the same film or episode is on Drive and on the device, it appears once and plays the local copy — no internet needed. Watch progress carries over between both copies.",
                "When DriveCinema isn't sure two titles are the same (for example a file without a year), it adds it separately and asks you: ${SettingsLabels.path(SettingsLabels.REVIEW_DUPLICATES)}.",
                "Removing a folder hides its videos; your watch history is kept."
            )
        ),
        GuideSection(
            id = COMPUTER_FOLDERS,
            category = GuideCategory.DEVICES,
            title = "Videos stored on this computer",
            summary = "Play videos from this computer's folders, even offline",
            platforms = DESKTOP_ONLY,
            steps = listOf(
                "Account menu → Local folders → Add folder, then pick a folder on this computer.",
                "Use the same layout and names as on Drive: Films and Series folders inside the folder you pick."
            ),
            tips = listOf(
                "If the same film or episode is on Drive and on this computer, it appears once and plays the local copy — no internet needed. Watch progress carries over between both copies.",
                "Removing a folder hides its videos; your watch history is kept."
            )
        ),
        GuideSection(
            id = PC_STREAMING,
            category = GuideCategory.DEVICES,
            title = "Stream from your PC",
            summary = "Watch a computer's films without uploading them",
            intro = "Watch the films in a computer's local folders on your phone, TV or another computer, without uploading them to Drive.",
            illustration = GuideIllustration.Flow(
                listOf(
                    GuideFlowNode(GuideIcon.PC, "PC shares its folders"),
                    GuideFlowNode(GuideIcon.WIFI, "Home network"),
                    GuideFlowNode(GuideIcon.TV, "Phone, TV or computer")
                ),
                caption = "Away from home: through Tailscale"
            ),
            steps = listOf(
                "The PC must be switched on, with DriveCinema open, and on the same home network as the phone or TV.",
                "On the PC: add your folders (account menu → Local folders), then account menu → Share with my devices → turn on Share my local folders.",
                "Windows asks once \"Allow DriveCinema to communicate on these networks?\": allow Private networks only. On public networks (cafés, hotels) sharing stays blocked.",
                "Pair each device once. On the PC: Share with my devices → Pair a device shows a 6-character code. On the phone or TV: ${SettingsLabels.path(SettingsLabels.MY_PCS, SettingsLabels.ADD_A_PC)} → ${SettingsLabels.SEARCH_THIS_NETWORK} → enter the code → ${SettingsLabels.PAIR}. On another computer: account menu → Play from another PC."
            ),
            tips = listOf(
                "The PC's films and series appear in your library. A title that is also on Drive stays one title: at home it plays from the PC, and from Drive when the PC is off.",
                "If the connection to the PC is too slow for a film, DriveCinema asks: Play from Drive (when the film is also on Drive) or Play from the PC anyway.",
                "Away from home: install Tailscale (a free VPN) on the PC and on the phone, signed in to the same Tailscale account. On the PC turn on Allow streaming over a VPN and note the address it shows; on the phone enter it in ${SettingsLabels.path(SettingsLabels.MY_PCS)}: under ${SettingsLabels.AWAY_FROM_HOME} when adding the PC, or later with ${SettingsLabels.SET_TAILSCALE_ADDRESS} on the PC's card.",
                "The PC isn't found: check that both are on the same Wi-Fi, not a guest network. Some routers keep devices from seeing each other (\"AP isolation\" or \"client isolation\"): turn it off in the router settings, or type the PC's address by hand (the PC shows it under Pair a device).",
                "A PC on another part of the network (another subnet, e.g. behind a second router) isn't found by Search this network: type its address by hand.",
                "The PC's titles disappeared: DriveCinema is closed on the PC, or the PC is asleep. They come back after a Refresh once the PC is on again."
            )
        ),
        GuideSection(
            id = IMPORT,
            category = GuideCategory.DEVICES,
            title = "Import to Drive",
            summary = "Upload new videos from this computer to Drive",
            platforms = DESKTOP_ONLY,
            intro = "The desktop app can upload new videos from folders on this computer into your Drive library, so your phone and TV get them too.",
            illustration = GuideIllustration.Flow(
                listOf(
                    GuideFlowNode(GuideIcon.FOLDER, "Folder on this computer"),
                    GuideFlowNode(GuideIcon.CLOUD, "Drive: Films and Series"),
                    GuideFlowNode(GuideIcon.TV, "Phone and TV")
                ),
                caption = "Only new videos; nothing is moved or deleted"
            ),
            steps = listOf(
                "Account menu → Import to Drive → Allow uploads to Drive. Google then asks for full access to your Drive: DriveCinema needs it to put files into your existing Films and Series folders. Phones and TVs keep read-only access.",
                "Choose a local Films folder and a Series folder. A new video is uploaded once it has finished copying or downloading: films to Films/, episodes to Series/<Show>/Season NN/ (the show and season folders are made when missing)."
            ),
            tips = listOf(
                "Videos already in the folders when you choose them are listed but not uploaded; use Upload or Upload existing.",
                "Files DriveCinema can't place (no SxxEyy, an Extras folder) show \"Needs review\" and are not uploaded.",
                "Your local files are never moved, renamed or deleted, and nothing already on Drive is changed or deleted.",
                "Remove write access (on the same screen) signs you out; sign in again for the normal read-only access."
            )
        ),
        GuideSection(
            id = SHARE,
            category = GuideCategory.SHARING,
            title = "Share your library with someone",
            summary = "Let someone else watch your library",
            steps = listOf(
                "In Google Drive, right-click your library folder → Share → add the other person's Google account. \"Viewer\" is enough.",
                "They sign in to DriveCinema with that account; your folder appears under \"Shared with you\", next to the folders in their own Drive."
            ),
            tips = listOf(
                "Anyone can switch between libraries later: ${SettingsLabels.path(SettingsLabels.SWITCH_LIBRARY_FOLDER)} on the phone and TV, account menu → Change library folder on the desktop.",
                "People only ever see folders that were shared with their account — nothing else from your Drive."
            )
        ),
        GuideSection(
            id = PAIRED_ACCESS,
            category = GuideCategory.SHARING,
            title = "What paired devices can access",
            summary = "What a paired TV or device can and can't see",
            tips = listOf(
                "A TV paired with your phone gets your phone's Google sign-in: it can read the same Drive library, read-only, like the phone. Signing out on the phone doesn't sign out the TV: sign out on the TV too, or remove DriveCinema's access at myaccount.google.com/permissions (that signs out every device).",
                "A device paired with your PC can only list and play the videos in the folders you share — nothing else on the PC — and it gets no Google sign-in. Each device has its own key; remove one on the PC: Share with my devices → Paired devices → Remove.",
                "Other devices on your network can't connect: they would need a pairing code, and the PC's stream is encrypted. After 3 wrong codes, pairing stops until you start it again.",
                "With Allow streaming over a VPN off, only devices on your home network can reach the PC. With Share my local folders off, the PC doesn't listen at all."
            )
        ),
        GuideSection(
            id = ABOUT,
            category = GuideCategory.SHARING,
            title = "About & credits",
            summary = "Where posters and intro times come from; the licence",
            intro = TMDB_NOTICE,
            tips = listOf(
                "Posters, backdrops and descriptions come from TMDB (The Movie Database, themoviedb.org).",
                SkipIntroTexts.ATTRIBUTION,
                "Your videos stay in your own Google Drive. DriveCinema only reads them; only the desktop's optional Import to Drive uploads new videos, and it never changes or deletes what is already there.",
                "The desktop app plays videos with VLC (libVLC) through vlcj.",
                "DriveCinema is free software under the GNU GPL v3."
            )
        ),
        GuideSection(
            id = PROBLEMS,
            category = GuideCategory.PROBLEMS,
            title = "If something doesn't work",
            summary = "Common problems and how to fix them",
            tips = listOf(
                "\"Folder must contain Films, Series…\": rename the folders inside your library to Films and Series, then tap Refresh.",
                "A film plays without sound: try another track with the Audio button in the player. If no track has sound, please report your device model and the audio format shown on the film's details page.",
                "Wrong or missing poster: rename the file to Title (Year).mkv; details are updated on the next sync.",
                "A shared folder is missing: check it was shared with exactly the Google account you signed in with, then tap Refresh.",
                "The PC's films don't appear: see \"Stream from your PC\"."
            )
        )
    )

    /** The topics [platform] shows, in Help Center order (Task J.7's per-platform choice). */
    fun sectionsFor(platform: GuidePlatform): List<GuideSection> = sections.filter { platform in it.platforms }

    /** [platform]'s topics in their groups, in [GuideCategory] order; empty groups are left out. */
    fun categoriesFor(platform: GuidePlatform): List<Pair<GuideCategory, List<GuideSection>>> =
        sectionsFor(platform).groupBy { it.category }.toList().sortedBy { it.first.ordinal }

    /** The topic with [id] if [platform] shows it. */
    fun topic(platform: GuidePlatform, id: String): GuideSection? = sectionsFor(platform).find { it.id == id }

    /** The topic [offset] places after (or, negative, before) [id] in [platform]'s order; null past either end. */
    fun neighbour(platform: GuidePlatform, id: String, offset: Int): GuideSection? {
        val topics = sectionsFor(platform)
        val index = topics.indexOfFirst { it.id == id }
        return if (index < 0) null else topics.getOrNull(index + offset)
    }

    /**
     * A "Problems" tip as a heading and its fix, split at the first ": " ("Wrong or missing poster" / "rename the
     * file…"); the desktop shows them as fold-outs. Null when the tip has no such colon.
     */
    fun problemAndFix(tip: String): Pair<String, String>? {
        val at = tip.indexOf(": ")
        if (at <= 0) return null
        return tip.substring(0, at) to tip.substring(at + 2).replaceFirstChar { it.uppercase() }
    }

    /** Required wording from TMDB's API terms (Milestone J.3). */
    const val TMDB_NOTICE = "This product uses the TMDB API but is not endorsed or certified by TMDB."
}
