package com.drivecinema.app.ui.player

import android.content.Context
import android.view.KeyEvent
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.NativeKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyPress
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.drivecinema.app.core.database.DriveCinemaDatabase
import com.drivecinema.app.core.database.entity.MediaItemEntity
import com.drivecinema.app.core.database.entity.MediaSourceEntity
import com.drivecinema.app.core.database.entity.MediaType
import com.drivecinema.app.core.database.entity.SeriesEntity
import com.drivecinema.app.core.database.entity.SourceKind
import com.drivecinema.app.core.skipintro.SkipIntroEpisode
import com.drivecinema.app.core.skipintro.SkipIntroEpisodes
import com.drivecinema.app.core.skipintro.SkipIntroSetting
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** O.2 on phone and TV: the button, its remote focus, the Account switch and which copy is looked up. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SkipIntroUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun okKey(action: Int) =
        androidx.compose.ui.input.key.KeyEvent(NativeKeyEvent(action, KeyEvent.KEYCODE_DPAD_CENTER))

    @Test
    fun theButtonShowsOnlyInsideTheIntroWindowAndATapSkipsOnce() {
        var visible by mutableStateOf(true)
        var skips = 0
        composeTestRule.setContent {
            SkipIntroButton(
                visible = visible,
                controlsVisible = false,
                focusAfterHiding = remember { FocusRequester() },
                onClick = { skips++ }
            )
        }
        composeTestRule.onNodeWithTag("player_skip_intro_button").performClick()
        assertEquals(1, skips)

        visible = false
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("player_skip_intro_button").assertDoesNotExist()
    }

    @Test
    fun okOnTheFocusedButtonSkipsOnce() {
        var skips = 0
        composeTestRule.setContent {
            SkipIntroButton(
                visible = true,
                controlsVisible = true,
                focusAfterHiding = remember { FocusRequester() },
                onClick = { skips++ }
            )
        }
        val button = composeTestRule.onNodeWithTag("player_skip_intro_button")
        button.requestFocus()
        button.performKeyPress(okKey(KeyEvent.ACTION_DOWN))
        button.performKeyPress(okKey(KeyEvent.ACTION_UP))
        assertEquals(1, skips)
    }

    @Test
    fun whenTheFocusedButtonDisappearsFocusMovesToPlayPause() {
        var visible by mutableStateOf(true)
        composeTestRule.setContent {
            val playPause = remember { FocusRequester() }
            Box {
                Box(Modifier.size(40.dp).focusRequester(playPause).focusable().testTag("play_pause"))
                SkipIntroButton(visible = visible, controlsVisible = true, focusAfterHiding = playPause, onClick = {})
            }
        }
        composeTestRule.onNodeWithTag("player_skip_intro_button").requestFocus()
        composeTestRule.onNodeWithTag("player_skip_intro_button").assertIsFocused()

        visible = false
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("play_pause").assertIsFocused()
    }

    @Test
    fun onTheTvTheButtonTakesTheFocusWhenItAppearsSoOkSkips() {
        var visible by mutableStateOf(false)
        var skips = 0
        composeTestRule.setContent {
            val playPause = remember { FocusRequester() }
            Box {
                Box(Modifier.size(40.dp).focusRequester(playPause).focusable().testTag("play_pause"))
                SkipIntroButton(
                    visible = visible,
                    controlsVisible = true,
                    focusAfterHiding = playPause,
                    onClick = { skips++ },
                    takeFocusOnShow = true
                )
            }
        }
        composeTestRule.onNodeWithTag("play_pause").requestFocus()

        visible = true
        composeTestRule.waitForIdle()
        val button = composeTestRule.onNodeWithTag("player_skip_intro_button")
        button.assertIsFocused()
        button.performKeyPress(okKey(KeyEvent.ACTION_DOWN))
        button.performKeyPress(okKey(KeyEvent.ACTION_UP))
        assertEquals(1, skips)
    }

    @Test
    fun onThePhoneTheButtonDoesNotTakeTheFocus() {
        var visible by mutableStateOf(false)
        composeTestRule.setContent {
            SkipIntroButton(visible = visible, controlsVisible = false, focusAfterHiding = remember { FocusRequester() }, onClick = {})
        }
        visible = true
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("player_skip_intro_button").assertIsNotFocused()
    }

    // The Skip intro switch (Account popup before Task P.2) is tested in SettingsScreenTest.skipIntroFlipsOncePerTapAndPerOkPress.

    @Test
    fun theSettingIsOnByDefaultAndRemembered() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertTrue(SkipIntroSetting(context).isEnabled())
        SkipIntroSetting(context).setEnabled(false)
        assertFalse(SkipIntroSetting(context).isEnabled())
    }

    @Test
    fun thePlayingCopyIsTracedToItsShowSeasonAndEpisode() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), DriveCinemaDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            db.mediaDao().insertSingleSeries(SeriesEntity("s-ted", "Ted", year = 2024, tmdbId = 201834))
            db.mediaDao().insertMediaItem(
                MediaItemEntity("drive-e2", MediaType.EPISODE, "My Two Dads", parentFolderId = "s1",
                    seasonNumber = 1, episodeNumber = 2, seriesId = "s-ted")
            )
            db.mediaDao().insertMediaItem(MediaItemEntity("drive-m1", MediaType.MOVIE, "Dune", parentFolderId = "films"))
            db.mediaDao().insertSourcesIfAbsent(
                listOf(
                    MediaSourceEntity("drive-e2", "drive-e2", SourceKind.DRIVE),
                    MediaSourceEntity("content://local/e2", "drive-e2", SourceKind.LOCAL),
                    MediaSourceEntity("drive-m1", "drive-m1", SourceKind.DRIVE)
                )
            )
            val ted = SkipIntroEpisode(tmdbShowId = 201834, season = 1, episode = 2)
            assertEquals(ted, SkipIntroEpisodes.forSource(db.mediaSourceDao(), db.mediaDao(), "drive-e2"))
            assertEquals("a local copy of the same episode", ted, SkipIntroEpisodes.forSource(db.mediaSourceDao(), db.mediaDao(), "content://local/e2"))
            assertNull("films never", SkipIntroEpisodes.forSource(db.mediaSourceDao(), db.mediaDao(), "drive-m1"))
            assertNull(SkipIntroEpisodes.forSource(db.mediaSourceDao(), db.mediaDao(), "unknown"))
        } finally {
            db.close()
        }
    }
}
