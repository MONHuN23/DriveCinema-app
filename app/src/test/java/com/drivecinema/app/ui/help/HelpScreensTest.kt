package com.drivecinema.app.ui.help

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.drivecinema.app.theme.DriveCinemaTheme
import com.drivecinema.app.ui.setup.GuideCategory
import com.drivecinema.app.ui.setup.GuidePlatform
import com.drivecinema.app.ui.setup.GuideSection
import com.drivecinema.app.ui.setup.SetupGuideContent
import com.drivecinema.app.ui.setup.shownAs
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Task P.4: the phone's Help Center: the Help home lists the phone's topics in groups; a topic page shows it all. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HelpScreensTest {

    @get:Rule
    val rule = createComposeRule()

    private val opened = mutableListOf<String>()
    private val shown = mutableListOf<String>()
    private var backs = 0

    private fun phoneTopic(id: String): GuideSection = SetupGuideContent.topic(GuidePlatform.PHONE, id)!!

    private fun showHome() {
        rule.setContent { DriveCinemaTheme { HelpHomeScreen(GuidePlatform.PHONE, onTopic = { opened += it }, onBack = { backs++ }) } }
    }

    private fun showTopic(id: String) {
        rule.setContent { DriveCinemaTheme { HelpEntry(id, isTv = false, open = { opened += it }, show = { shown += it }, back = { backs++ }) } }
    }

    /** Scrolls the topic page to [text] and returns it. */
    private fun onPage(text: String): SemanticsNodeInteraction {
        rule.onNodeWithTag("help_topic_page").performScrollToNode(hasText(text, substring = true))
        return rule.onNodeWithText(text, substring = true)
    }

    @Test
    fun theHomeListsThePhonesTopicsUnderTheirGroups() {
        showHome()
        val list = rule.onNodeWithTag("help_list")

        for (category in GuideCategory.entries) {
            list.performScrollToNode(hasText(category.label.uppercase()))
        }
        SetupGuideContent.sectionsFor(GuidePlatform.PHONE).forEach { topic ->
            list.performScrollToNode(hasTestTag("help_topic_${topic.id}"))
            rule.onNode(hasText(topic.title) and hasClickAction()).assertExists()
            rule.onNodeWithText(topic.summary).assertExists()
        }
        // The desktop's topics are not on the phone.
        rule.onNodeWithTag("help_topic_${SetupGuideContent.IMPORT}").assertDoesNotExist()
        rule.onNodeWithTag("help_topic_${SetupGuideContent.COMPUTER_FOLDERS}").assertDoesNotExist()
    }

    @Test
    fun tappingATopicOpensIt() {
        showHome()
        rule.onNodeWithTag("help_list").performScrollToNode(hasTestTag("help_topic_${SetupGuideContent.PC_STREAMING}"))

        rule.onNodeWithTag("help_topic_${SetupGuideContent.PC_STREAMING}").performClick()

        assertEquals(listOf(SetupGuideContent.PC_STREAMING), opened)
    }

    @Test
    fun aTopicShowsItsIntroNumberedStepsAndTips() {
        val topic = phoneTopic(SetupGuideContent.PC_STREAMING)
        showTopic(topic.id)

        onPage(topic.title).assertExists()
        onPage(topic.intro!!).assertExists()
        topic.steps.forEachIndexed { index, step ->
            onPage(step).assertExists()
            rule.onNodeWithText("${index + 1}").assertExists()
        }
        onPage(GOOD_TO_KNOW.uppercase()).assertExists()
        topic.tips.forEach { onPage(it).assertExists() }
    }

    @Test
    fun theFoldersTopicDrawsTheFolderTree() {
        showTopic(SetupGuideContent.FOLDERS)

        rule.onNodeWithTag("help_illustration").assertExists()
        SetupGuideContent.folderTreeLines.forEach { line ->
            rule.onNode(hasText(line.name) and hasAnyAncestor(hasTestTag("help_illustration"))).assertExists()
        }
    }

    @Test
    fun theNamingTopicsShowWhatEachFileNameBecomes() {
        val films = phoneTopic(SetupGuideContent.FILM_NAMES)
        showTopic(films.id)

        films.examples.forEach { example ->
            onPage(example.fileName).assertExists()
            onPage("→ ${example.shownAs}").assertExists()
        }
    }

    @Test
    fun theTvSignInTopicDrawsItsFlow() {
        showTopic(SetupGuideContent.TV_SIGN_IN)

        onPage("TV shows a code").assertExists()
        onPage("Phone: enter the code").assertExists()
        onPage("Both on the same Wi-Fi").assertExists()
        onPage("Phone and TV only").assertExists()
    }

    @Test
    fun nextShowsTheFollowingTopic() {
        val first = SetupGuideContent.sectionsFor(GuidePlatform.PHONE).first()
        val second = SetupGuideContent.sectionsFor(GuidePlatform.PHONE)[1]
        showTopic(first.id)

        rule.onNodeWithTag("help_topic_page").performScrollToNode(hasTestTag("help_next"))
        rule.onNodeWithTag("help_next").performClick()

        assertEquals(listOf(second.id), shown)
    }

    @Test
    fun theLastTopicHasNoNextLink() {
        showTopic(SetupGuideContent.sectionsFor(GuidePlatform.PHONE).last().id)

        rule.onNodeWithTag("help_topic_page").assertExists()
        rule.onNodeWithTag("help_next").assertDoesNotExist()
    }

    @Test
    fun aTopicThePhoneDoesntHaveShowsTheHome() {
        showTopic(SetupGuideContent.IMPORT)

        rule.onNodeWithTag("help_list").assertExists()
        rule.onNodeWithTag("help_topic_page").assertDoesNotExist()
    }

    @Test
    fun theTopBarGoesBack() {
        showTopic(SetupGuideContent.ABOUT)

        rule.onNodeWithText(SetupGuideContent.TMDB_NOTICE).assertExists()
        rule.onNode(hasClickAction() and hasContentDescriptionBack()).performClick()

        assertEquals(1, backs)
    }

    private fun hasContentDescriptionBack() = androidx.compose.ui.test.hasContentDescription("Back")
}
