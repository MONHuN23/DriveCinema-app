package com.drivecinema.app.ui.help

import androidx.activity.ComponentActivity
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.drivecinema.app.theme.DriveCinemaTheme
import com.drivecinema.app.ui.setup.GuidePlatform
import com.drivecinema.app.ui.setup.SetupGuideContent
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.requestFocus
import com.drivecinema.app.ui.design.assertFocusFitsVertically
import com.drivecinema.app.ui.design.DcTokens
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Task P.4: the TV's two-pane Help Center and the remote (decision D-P4): the topics on the left with the focus, the
 * selected topic on the right; Right enters it, each block is one stop, Left or Back returns to the same topic.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp")
class TvHelpFocusTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var focusManager: FocusManager

    private val tvTopics = SetupGuideContent.sectionsFor(GuidePlatform.TV)

    private fun show(topicId: String? = null) {
        rule.setContent {
            DriveCinemaTheme(isTv = true) {
                focusManager = LocalFocusManager.current
                TvHelpScreen(initialTopicId = topicId)
            }
        }
        rule.waitForIdle()
    }

    private fun move(direction: FocusDirection) {
        rule.runOnUiThread { focusManager.moveFocus(direction) }
        rule.waitForIdle()
    }

    private fun pressBack() {
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
    }

    private fun topic(id: String): SemanticsNodeInteraction = rule.onNodeWithTag("tv_help_topic_$id")

    private fun block(index: Int): SemanticsNodeInteraction = rule.onNodeWithTag("tv_help_block_$index")

    @Test
    fun theFirstTopicStartsFocusedWithItsContentShown() {
        show()

        topic(tvTopics.first().id).assertIsFocused()
        rule.onNodeWithText(tvTopics.first().intro!!).assertExists()
    }

    @Test
    fun aLinkedTopicStartsSelected() {
        show(SetupGuideContent.ABOUT)

        topic(SetupGuideContent.ABOUT).assertIsFocused()
        rule.onNodeWithText(SetupGuideContent.TMDB_NOTICE).assertExists()
    }

    @Test
    fun movingThroughTheTopicsShowsEachOne() {
        show()

        move(FocusDirection.Down)
        topic(tvTopics[1].id).assertIsFocused()
        rule.onNodeWithText(tvTopics[1].intro!!).assertExists()
        rule.onNodeWithText(tvTopics[0].intro!!).assertDoesNotExist()
    }

    @Test
    fun theTvShowsOnlyItsTopics() {
        show()

        topic(SetupGuideContent.TV_SIGN_IN).assertExists()
        topic(SetupGuideContent.IMPORT).assertDoesNotExist()
        topic(SetupGuideContent.COMPUTER_FOLDERS).assertDoesNotExist()
    }

    @Test
    fun rightEntersTheTopicAndTheRemoteMovesBlockByBlock() {
        val folders = SetupGuideContent.topic(GuidePlatform.TV, SetupGuideContent.FOLDERS)!!
        val blocks = 1 + folders.steps.size + folders.tips.size    // the overview, then each step and tip
        show(SetupGuideContent.FOLDERS)

        move(FocusDirection.Right)
        block(0).assertIsFocused()
        move(FocusDirection.Up)
        block(0).assertIsFocused()

        for (index in 1 until blocks) {
            move(FocusDirection.Down)
            block(index).assertIsFocused()
        }
        move(FocusDirection.Down)
        block(blocks - 1).assertIsFocused()

        move(FocusDirection.Left)
        topic(SetupGuideContent.FOLDERS).assertIsFocused()
    }

    @Test
    fun backFromTheTopicReturnsToItInTheList() {
        show(SetupGuideContent.PC_STREAMING)
        move(FocusDirection.Right)
        move(FocusDirection.Down)
        block(1).assertIsFocused()

        pressBack()

        topic(SetupGuideContent.PC_STREAMING).assertIsFocused()
    }

    @Test
    fun aTopicWithoutAnIntroStartsOnItsFirstStep() {
        show(SetupGuideContent.SHARE)

        move(FocusDirection.Right)

        block(0).assertIsFocused()
        rule.onNodeWithText(SetupGuideContent.topic(GuidePlatform.TV, SetupGuideContent.SHARE)!!.steps.first()).assertExists()
    }

    @Test
    fun focusedTopicsAndBlocksAreNotCutOffAtTheScrollEdges() {
        // P.7: the same clipping as in Settings, in both panes; the blocks keep their size (scale 1) but not the ring.
        show()
        repeat(tvTopics.size) {
            rule.onNode(isFocused()).assertFocusFitsVertically(rule.onNodeWithTag("tv_help_topics"), what = "topic ${it + 1}")
            move(FocusDirection.Down)
        }

        topic(SetupGuideContent.FOLDERS).requestFocus()
        rule.waitForIdle()
        move(FocusDirection.Right)
        val folders = SetupGuideContent.topic(GuidePlatform.TV, SetupGuideContent.FOLDERS)!!
        repeat(1 + folders.steps.size + folders.tips.size) {
            rule.onNode(isFocused()).assertFocusFitsVertically(rule.onNodeWithTag("tv_help_pane"), scale = 1f, what = "block $it")
            move(FocusDirection.Down)
        }
    }
}
