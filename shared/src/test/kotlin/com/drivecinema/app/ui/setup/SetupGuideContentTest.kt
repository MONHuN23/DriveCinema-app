package com.drivecinema.app.ui.setup

import com.drivecinema.app.core.metadata.MediaFilenameParser
import com.drivecinema.app.core.sync.FolderQueueCrawler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Keeps the in-app setup guide honest: every naming promise in it is checked against the real parser. */
class SetupGuideContentTest {

    private val examples = SetupGuideContent.sections.flatMap { it.examples }

    @Test
    fun guideHasNamingExamplesForFilmsAndEpisodes() {
        assertTrue(examples.any { it is NamingExample.Movie })
        assertTrue(examples.any { it is NamingExample.Episode })
    }

    @Test
    fun everyFilmExampleIsParsedExactlyAsTheGuideSays() {
        examples.filterIsInstance<NamingExample.Movie>().forEach { example ->
            val parsed = MediaFilenameParser.parseMovie(example.fileName)
            assertEquals("Title for ${example.fileName}", example.title, parsed.cleanTitle)
            assertEquals("Year for ${example.fileName}", example.year, parsed.year)
        }
    }

    @Test
    fun everyEpisodeExampleIsParsedExactlyAsTheGuideSays() {
        examples.filterIsInstance<NamingExample.Episode>().forEach { example ->
            val parsed = MediaFilenameParser.parseEpisode(example.fileName)
            assertEquals("Season for ${example.fileName}", example.season, parsed.season)
            assertEquals("Episode for ${example.fileName}", example.episode, parsed.episode)
        }
    }

    @Test
    fun everyExampleAndTreeFileIsRecognisedAsAVideo() {
        val treeFiles = SetupGuideContent.folderTreeLines.filterNot { it.isFolder }.map { it.name }
        assertTrue(treeFiles.isNotEmpty())
        (examples.map { it.fileName } + treeFiles).forEach { name ->
            assertTrue("$name must be a recognised video file", FolderQueueCrawler.isVideo(null, name))
        }
    }

    @Test
    fun folderTreeEpisodesParseToTheirSeasonAndEpisode() {
        val s01e02 = MediaFilenameParser.parseEpisode("Breaking Bad - S01E02.mkv")
        assertEquals(1, s01e02.season)
        assertEquals(2, s01e02.episode)
        val movie = MediaFilenameParser.parseMovie("Inception.2010.1080p.mkv")
        assertEquals("Inception", movie.cleanTitle)
        assertEquals(2010, movie.year)
    }

    @Test
    fun guideNamesTheFoldersTheAppActuallyLooksFor() {
        val text = SetupGuideContent.sections.joinToString(" ") { it.text.joinToString(" ") }
        assertTrue(text.contains("\"Films\""))
        assertTrue(text.contains("\"Series\""))
        assertTrue(text.contains("\"Streaming\""))
        assertTrue(SetupGuideContent.FOLDER_TREE.contains("Films/"))
        assertTrue(SetupGuideContent.FOLDER_TREE.contains("Series/"))
        assertFalse("Subtitles are out of scope; the guide must not promise them", text.contains("subtitle file", ignoreCase = true))
    }

    @Test
    fun guideCarriesTheExactTmdbNotice() {
        // TMDB's API terms require this wording in an About/Credits section (Milestone J.3).
        val credits = SetupGuideContent.sections.single { it.title.contains("credits", ignoreCase = true) }
        assertEquals("This product uses the TMDB API but is not endorsed or certified by TMDB.", credits.intro)
    }

    private fun textOf(sections: List<GuideSection>) =
        sections.joinToString(" ") { s -> (listOf(s.title) + listOfNotNull(s.platformNote) + s.text).joinToString(" ") }

    private fun titles(platform: GuidePlatform) = SetupGuideContent.sectionsFor(platform).map { it.title }

    @Test
    fun eachPlatformShowsItsOwnSections() {
        // Task J.7 (F-34): TV sign-in is for phone and TV; Import to Drive and this computer's folders for the desktop.
        for (platform in listOf(GuidePlatform.PHONE, GuidePlatform.TV)) {
            val t = titles(platform)
            assertTrue(t.toString(), "Sign in on a TV" in t && "Videos stored on this device" in t)
            assertFalse(t.toString(), "Import to Drive" in t || "Videos stored on this computer" in t)
        }
        val desktop = titles(GuidePlatform.DESKTOP)
        assertTrue(desktop.toString(), "Import to Drive" in desktop && "Videos stored on this computer" in desktop)
        assertFalse(desktop.toString(), "Sign in on a TV" in desktop || "Videos stored on this device" in desktop)
        for (platform in GuidePlatform.entries) {
            val t = titles(platform)
            for (common in listOf("Stream from your PC", "What paired devices can access", "Skip intro", "About & credits")) {
                assertTrue("$platform lacks $common", common in t)
            }
        }
    }

    @Test
    fun limitedSectionsSayWhereTheyAreShown() {
        val byTitle = SetupGuideContent.sections.associateBy { it.title }
        assertEquals("Phone and TV only", byTitle.getValue("Sign in on a TV").platformNote)
        assertEquals("Desktop only", byTitle.getValue("Import to Drive").platformNote)
        assertEquals(null, byTitle.getValue("Stream from your PC").platformNote)
    }

    @Test
    fun wrongClaimsFoundByTheAuditNeverComeBack() {
        // F-34: subtitles inside the video are shown (K.1), Dolby/DTS play with the software decoder (G.1),
        // and the desktop's Import to Drive uploads.
        for (platform in GuidePlatform.entries) {
            val text = textOf(SetupGuideContent.sectionsFor(platform))
            for (wrong in listOf("Subtitles are not shown", "never changes or uploads", "video without sound", "usually no sound")) {
                assertFalse("$platform guide says \"$wrong\"", text.contains(wrong, ignoreCase = true))
            }
        }
    }

    @Test
    fun pcStreamingSectionCoversSetupAndTroubleshooting() {
        val pc = textOf(listOf(SetupGuideContent.sections.single { it.title == "Stream from your PC" }))
        for (needed in listOf("Share with my devices", "Private networks", "6-character code", "My PCs", "Tailscale", "Play from the PC anyway", "AP isolation", "subnet", "by hand")) {
            assertTrue("missing: $needed", pc.contains(needed))
        }
    }

    // ---- Task P.4: the Help Center's topics ----

    @Test
    fun topicIdsAreUniqueAndTheLinkedOnesExist() {
        val ids = SetupGuideContent.sections.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.all { it.isNotBlank() })
        for (platform in GuidePlatform.entries) {
            assertNotNull(SetupGuideContent.topic(platform, SetupGuideContent.FOLDERS))
            assertNotNull(SetupGuideContent.topic(platform, SetupGuideContent.ABOUT))
        }
        assertNull(SetupGuideContent.topic(GuidePlatform.DESKTOP, SetupGuideContent.TV_SIGN_IN))
    }

    @Test
    fun everyTopicHasASummaryAndSomethingToRead() {
        SetupGuideContent.sections.forEach { topic ->
            assertTrue("${topic.id} has no summary", topic.summary.isNotBlank())
            assertTrue("${topic.id} is empty", topic.steps.isNotEmpty() || topic.tips.isNotEmpty())
        }
    }

    @Test
    fun topicsComeInCategoryOrderAndEveryCategoryHasTopicsOnEveryPlatform() {
        for (platform in GuidePlatform.entries) {
            val topics = SetupGuideContent.sectionsFor(platform)
            assertEquals(topics.sortedBy { it.category.ordinal }, topics)
            val groups = SetupGuideContent.categoriesFor(platform)
            assertEquals(GuideCategory.entries.toList(), groups.map { it.first })
            assertEquals(topics, groups.flatMap { it.second })
        }
    }

    @Test
    fun theNextAndPreviousTopicsFollowThePlatformsOrder() {
        val phone = SetupGuideContent.sectionsFor(GuidePlatform.PHONE)
        assertEquals(phone[1], SetupGuideContent.neighbour(GuidePlatform.PHONE, phone[0].id, 1))
        assertEquals(phone[0], SetupGuideContent.neighbour(GuidePlatform.PHONE, phone[1].id, -1))
        assertNull(SetupGuideContent.neighbour(GuidePlatform.PHONE, phone.last().id, 1))
        assertNull(SetupGuideContent.neighbour(GuidePlatform.PHONE, phone.first().id, -1))
        // A topic the platform doesn't show has no neighbours there.
        assertNull(SetupGuideContent.neighbour(GuidePlatform.DESKTOP, SetupGuideContent.TV_SIGN_IN, 1))
        // The desktop skips the phone's topics: after its own folders comes PC streaming.
        assertEquals(
            SetupGuideContent.PC_STREAMING,
            SetupGuideContent.neighbour(GuidePlatform.DESKTOP, SetupGuideContent.COMPUTER_FOLDERS, 1)?.id
        )
    }

    @Test
    fun theFolderTreeIsReadIntoLevels() {
        val lines = SetupGuideContent.folderTreeLines
        assertEquals(FolderTreeLine(0, "Streaming", isFolder = true), lines.first())
        assertEquals(FolderTreeLine(1, "Films", isFolder = true), lines[1])
        assertEquals(FolderTreeLine(2, "Dune Part Two (2024).mkv", isFolder = false), lines[2])
        // Streaming / Series / Breaking Bad / Season 02 / the file
        assertEquals(FolderTreeLine(4, "Breaking Bad - S02E01.mkv", isFolder = false), lines.last())
        assertEquals(SetupGuideContent.FOLDER_TREE.lines().size, lines.size)
    }

    @Test
    fun everyProblemSplitsIntoAHeadingAndItsFix() {
        val problems = SetupGuideContent.sections.single { it.id == SetupGuideContent.PROBLEMS }
        problems.tips.forEach { tip -> assertNotNull(tip, SetupGuideContent.problemAndFix(tip)) }
        assertEquals(
            "Wrong or missing poster" to "Rename the file to Title (Year).mkv; details are updated on the next sync.",
            SetupGuideContent.problemAndFix(problems.tips[2])
        )
        assertNull(SetupGuideContent.problemAndFix("No colon here."))
    }

    @Test
    fun everySettingsPathNamesARealLabel() {
        val labels = SettingsLabels::class.java.declaredFields
            .filter { it.type == String::class.java }
            .map { it.isAccessible = true; it.get(null) as String }
            .toSet()
        val path = Regex("${SettingsLabels.SETTINGS}((?: → [^→.:,(]+)+)")
        val all = SetupGuideContent.sections.flatMap { it.text }
        val found = all.flatMap { sentence -> path.findAll(sentence).map { it.groupValues[1] } }
        assertTrue(found.isNotEmpty())
        // Each step starts with a label ("Switch library folder on the phone and TV"); only "enter the code" is an action.
        found.forEach { steps ->
            steps.split(" → ").drop(1).map { it.trim() }.forEach { step ->
                val named = labels.any { step == it || step.startsWith("$it ") } || step.startsWith("enter the code")
                assertTrue("\"$step\" in \"Settings$steps\" doesn't start with a SettingsLabels name", named)
            }
        }
    }

    /**
     * Task P.4's promise: every sentence of the J.7 guide (as it read after P.2 and P.3 renamed the Settings paths) is
     * still there, word for word, in the topic with the same title, on the same platforms.
     */
    @Test
    fun everyJ7SentenceIsStillInItsTopic() {
        val byTitle = SetupGuideContent.sections.associateBy { it.title }
        J7_GUIDE.forEach { (title, sentences) ->
            val topic = byTitle[title]
            assertNotNull("No topic \"$title\"", topic)
            sentences.forEach { sentence -> assertTrue("\"$title\" lost: $sentence", sentence in topic!!.text) }
        }
        val phoneAndTv = setOf(GuidePlatform.PHONE, GuidePlatform.TV)
        assertEquals(phoneAndTv, byTitle.getValue("Sign in on a TV").platforms)
        assertEquals(phoneAndTv, byTitle.getValue("Videos stored on this device").platforms)
        assertEquals(setOf(GuidePlatform.DESKTOP), byTitle.getValue("Videos stored on this computer").platforms)
        assertEquals(setOf(GuidePlatform.DESKTOP), byTitle.getValue("Import to Drive").platforms)
        val everywhere = J7_GUIDE.keys - setOf("Sign in on a TV", "Videos stored on this device", "Videos stored on this computer", "Import to Drive")
        everywhere.forEach { assertEquals(it, GuidePlatform.entries.toSet(), byTitle.getValue(it).platforms) }
        assertEquals(J7_GUIDE.keys, byTitle.keys)
    }

    private companion object {
        /** The J.7 guide's intros and bullets, copied before Task P.4 sorted them into steps and tips. */
        val J7_GUIDE: Map<String, List<String>> = mapOf(
            "Set up your folders" to listOf(
                "DriveCinema reads one library folder in Google Drive. Inside it, put a Films folder and a Series folder.",
                "Name the library folder \"Streaming\" and DriveCinema finds it by itself when you sign in. Any other name works too — just pick it in the folder list.",
                "The two folders inside must be called \"Films\" and \"Series\" (capital letters don't matter).",
                "Films: put movie files straight in Films, or in their own subfolders — both are found.",
                "Series: make one folder per show (the folder name is the show name, e.g. \"Doctor Who (2005)\"), and inside it one folder per season: \"Season 1\", \"Season 01\" or \"S1\".",
                "Season folders are recommended. Episodes may also sit directly in the show folder, but then name them \"Show - S02E05.mkv\" (with the show name) so the season is read from the file — otherwise they count as season 1.",
                "Video files placed directly in the library folder are treated as films."
            ),
            "Name your films" to listOf(
                "Best format:  Title (Year).mkv  — the year is what makes posters and details match.",
                "Dots, underscores and dashes are fine; they become spaces.",
                "Anything after the year (1080p, BluRay, x264…) is ignored.",
                "Without a year, extra tags stay in the title and the poster may not be found."
            ),
            "Name your episodes" to listOf(
                "Best format:  Show - S01E01 - Episode title.mkv",
                "The episode number comes from SxxEyy in the file name — always include it, with two digits (S01E05).",
                "For files inside a season folder, the season number comes from the folder name.",
                "The show name comes from the show's folder, so the name in the file is optional.",
                "Files without an episode number are all treated as episode 1, so they would overlap."
            ),
            "Video, audio and subtitles" to listOf(
                "MP4 or MKV with H.264 or H.265 (HEVC) video plays on phones, TVs and computers.",
                "File types found: .mp4 and .mkv (recommended), also .avi, .mov, .wmv, .m4v, .webm, .ts.",
                "Video: H.264 plays everywhere. H.265 (HEVC) gives smaller files and plays on modern phones and TVs.",
                "Audio: AAC, MP3, Opus, FLAC, Dolby Digital (AC-3, E-AC-3), DTS and TrueHD all play with sound. When a phone or TV can't decode Dolby or DTS itself, DriveCinema decodes it; the desktop plays them with VLC.",
                "A film with several audio tracks (languages, commentary): pick one with the Audio button in the player.",
                "Subtitles inside the video (common in MKV files): pick them with the Subtitles button in the player, or turn them off. Subtitles in separate .srt files next to a video on Drive aren't shown yet."
            ),
            "Sign in on a TV" to listOf(
                "TVs can't use Google sign-in, so a TV signs in through your phone. The phone must be signed in and on the same Wi-Fi.",
                "On the TV: open DriveCinema → Pair with Phone. The TV shows a 6-character code and its address.",
                "On the phone: Home → Pair TV → enter the code. The phone finds the TV on the Wi-Fi; if it doesn't, type the TV's address shown on the TV.",
                "After 3 wrong codes the TV stops pairing: start again on the TV for a new code.",
                "The phone hands its sign-in to the TV, encrypted. The TV then shows the same library as the phone and keeps it after a restart (see \"What paired devices can access\")."
            ),
            "Share your library with someone" to listOf(
                "In Google Drive, right-click your library folder → Share → add the other person's Google account. \"Viewer\" is enough.",
                "They sign in to DriveCinema with that account; your folder appears under \"Shared with you\", next to the folders in their own Drive.",
                "Anyone can switch between libraries later: Settings → Switch library folder on the phone and TV, account menu → Change library folder on the desktop.",
                "People only ever see folders that were shared with their account — nothing else from your Drive."
            ),
            "Videos stored on this device" to listOf(
                "Settings → Local folders → Add folder, then pick a folder on the phone (or a USB drive on a TV that supports it).",
                "Use the same layout and names as on Drive: Films and Series folders inside the folder you pick.",
                "If the same film or episode is on Drive and on the device, it appears once and plays the local copy — no internet needed. Watch progress carries over between both copies.",
                "When DriveCinema isn't sure two titles are the same (for example a file without a year), it adds it separately and asks you: Settings → Review possible duplicates.",
                "Removing a folder hides its videos; your watch history is kept."
            ),
            "Videos stored on this computer" to listOf(
                "Account menu → Local folders → Add folder, then pick a folder on this computer.",
                "Use the same layout and names as on Drive: Films and Series folders inside the folder you pick.",
                "If the same film or episode is on Drive and on this computer, it appears once and plays the local copy — no internet needed. Watch progress carries over between both copies.",
                "Removing a folder hides its videos; your watch history is kept."
            ),
            "Stream from your PC" to listOf(
                "Watch the films in a computer's local folders on your phone, TV or another computer, without uploading them to Drive.",
                "The PC must be switched on, with DriveCinema open, and on the same home network as the phone or TV.",
                "On the PC: add your folders (account menu → Local folders), then account menu → Share with my devices → turn on Share my local folders.",
                "Windows asks once \"Allow DriveCinema to communicate on these networks?\": allow Private networks only. On public networks (cafés, hotels) sharing stays blocked.",
                "Pair each device once. On the PC: Share with my devices → Pair a device shows a 6-character code. On the phone or TV: Settings → My PCs → Add a PC → Search this network → enter the code → Pair. On another computer: account menu → Play from another PC.",
                "The PC's films and series appear in your library. A title that is also on Drive stays one title: at home it plays from the PC, and from Drive when the PC is off.",
                "If the connection to the PC is too slow for a film, DriveCinema asks: Play from Drive (when the film is also on Drive) or Play from the PC anyway.",
                "Away from home: install Tailscale (a free VPN) on the PC and on the phone, signed in to the same Tailscale account. On the PC turn on Allow streaming over a VPN and note the address it shows; on the phone enter it in Settings → My PCs: under Away from home (optional) when adding the PC, or later with Set Tailscale address on the PC's card.",
                "The PC isn't found: check that both are on the same Wi-Fi, not a guest network. Some routers keep devices from seeing each other (\"AP isolation\" or \"client isolation\"): turn it off in the router settings, or type the PC's address by hand (the PC shows it under Pair a device).",
                "A PC on another part of the network (another subnet, e.g. behind a second router) isn't found by Search this network: type its address by hand.",
                "The PC's titles disappeared: DriveCinema is closed on the PC, or the PC is asleep. They come back after a Refresh once the PC is on again."
            ),
            "Import to Drive" to listOf(
                "The desktop app can upload new videos from folders on this computer into your Drive library, so your phone and TV get them too.",
                "Account menu → Import to Drive → Allow uploads to Drive. Google then asks for full access to your Drive: DriveCinema needs it to put files into your existing Films and Series folders. Phones and TVs keep read-only access.",
                "Choose a local Films folder and a Series folder. A new video is uploaded once it has finished copying or downloading: films to Films/, episodes to Series/<Show>/Season NN/ (the show and season folders are made when missing).",
                "Videos already in the folders when you choose them are listed but not uploaded; use Upload or Upload existing.",
                "Files DriveCinema can't place (no SxxEyy, an Extras folder) show \"Needs review\" and are not uploaded.",
                "Your local files are never moved, renamed or deleted, and nothing already on Drive is changed or deleted.",
                "Remove write access (on the same screen) signs you out; sign in again for the normal read-only access."
            ),
            "What paired devices can access" to listOf(
                "A TV paired with your phone gets your phone's Google sign-in: it can read the same Drive library, read-only, like the phone. Signing out on the phone doesn't sign out the TV: sign out on the TV too, or remove DriveCinema's access at myaccount.google.com/permissions (that signs out every device).",
                "A device paired with your PC can only list and play the videos in the folders you share — nothing else on the PC — and it gets no Google sign-in. Each device has its own key; remove one on the PC: Share with my devices → Paired devices → Remove.",
                "Other devices on your network can't connect: they would need a pairing code, and the PC's stream is encrypted. After 3 wrong codes, pairing stops until you start it again.",
                "With Allow streaming over a VPN off, only devices on your home network can reach the PC. With Share my local folders off, the PC doesn't listen at all."
            ),
            "If something doesn't work" to listOf(
                "\"Folder must contain Films, Series…\": rename the folders inside your library to Films and Series, then tap Refresh.",
                "A film plays without sound: try another track with the Audio button in the player. If no track has sound, please report your device model and the audio format shown on the film's details page.",
                "Wrong or missing poster: rename the file to Title (Year).mkv; details are updated on the next sync.",
                "A shared folder is missing: check it was shared with exactly the Google account you signed in with, then tap Refresh.",
                "The PC's films don't appear: see \"Stream from your PC\"."
            ),
            "Skip intro" to listOf(
                "When an episode's intro times are known, a \"Skip intro\" button appears while the intro plays. On the TV, press OK to skip (Back hides the button); on the desktop, press S.",
                "The times come from SkipDB (skipdb.tv), or from IntroDB (introdb.app) when SkipDB has none. DriveCinema sends them the show, season and episode number, plus the video's length to SkipDB, and nothing else: no file names, no account, no watch history.",
                "The button only appears when the times were made for a video of the same length as yours, so it never skips to the wrong place. Many episodes have no times yet.",
                "Turn it off in Settings → Skip intro (phone and TV) or in the account menu → Skip intro (desktop); then nothing is sent."
            ),
            "About & credits" to listOf(
                SetupGuideContent.TMDB_NOTICE,
                "Posters, backdrops and descriptions come from TMDB (The Movie Database, themoviedb.org).",
                SkipIntroTexts.ATTRIBUTION,
                "Your videos stay in your own Google Drive. DriveCinema only reads them; only the desktop's optional Import to Drive uploads new videos, and it never changes or deletes what is already there.",
                "The desktop app plays videos with VLC (libVLC) through vlcj.",
                "DriveCinema is free software under the GNU GPL v3."
            )
        )
    }
}
