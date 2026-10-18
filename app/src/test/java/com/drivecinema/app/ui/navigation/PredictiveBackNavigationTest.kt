package com.drivecinema.app.ui.navigation

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.drivecinema.app.AppScreen
import com.drivecinema.app.core.database.entity.MediaItemEntity
import com.drivecinema.app.core.database.entity.MediaType
import com.drivecinema.app.core.database.entity.SeriesEntity
import com.drivecinema.app.ui.mobile.navigation.MobileTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PredictiveBackNavigationTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun testBackFromMovieDetailsPopsToHome() {
        var currentScreen by mutableStateOf<AppScreen>(
            AppScreen.MovieDetails(
                MediaItemEntity(
                    id = "m1",
                    type = MediaType.MOVIE,
                    title = "Dune",
                    parentFolderId = "f1"
                )
            )
        )

        composeTestRule.setContent {
            when (currentScreen) {
                is AppScreen.MovieDetails -> {
                    BackHandler { currentScreen = AppScreen.Home }
                }
                is AppScreen.Home -> {}
                else -> {}
            }
        }

        assertTrue(currentScreen is AppScreen.MovieDetails)
        assertTrue(composeTestRule.activity.onBackPressedDispatcher.hasEnabledCallbacks())

        composeTestRule.activity.runOnUiThread {
            composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        }

        assertEquals(AppScreen.Home, currentScreen)
    }

    @Test
    fun testBackFromSeriesDetailsPopsToHome() {
        var currentScreen by mutableStateOf<AppScreen>(
            AppScreen.SeriesDetails(
                SeriesEntity(id = "s1", title = "Severance")
            )
        )

        composeTestRule.setContent {
            when (currentScreen) {
                is AppScreen.SeriesDetails -> {
                    BackHandler { currentScreen = AppScreen.Home }
                }
                is AppScreen.Home -> {}
                else -> {}
            }
        }

        assertTrue(currentScreen is AppScreen.SeriesDetails)
        assertTrue(composeTestRule.activity.onBackPressedDispatcher.hasEnabledCallbacks())

        composeTestRule.activity.runOnUiThread {
            composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        }

        assertEquals(AppScreen.Home, currentScreen)
    }

    @Test
    fun testBackFromTvSearchPopsToHome() {
        var currentScreen by mutableStateOf<AppScreen>(AppScreen.TvSearch)

        composeTestRule.setContent {
            when (currentScreen) {
                is AppScreen.TvSearch -> {
                    BackHandler { currentScreen = AppScreen.Home }
                }
                is AppScreen.Home -> {}
                else -> {}
            }
        }

        assertTrue(currentScreen is AppScreen.TvSearch)

        composeTestRule.activity.runOnUiThread {
            composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        }

        assertEquals(AppScreen.Home, currentScreen)
    }

    @Test
    fun testBackFromMobileSearchTabReturnsToHomeTab() {
        var selectedTab by mutableStateOf(MobileTab.SEARCH)

        composeTestRule.setContent {
            if (selectedTab != MobileTab.HOME) {
                BackHandler { selectedTab = MobileTab.HOME }
            }
        }

        assertEquals(MobileTab.SEARCH, selectedTab)
        assertTrue(composeTestRule.activity.onBackPressedDispatcher.hasEnabledCallbacks())

        composeTestRule.activity.runOnUiThread {
            composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        }

        assertEquals(MobileTab.HOME, selectedTab)
    }

    @Test
    fun testRootScreenHasNoActiveBackCallbacksAllowingAppExit() {
        var currentScreen by mutableStateOf<AppScreen>(AppScreen.Home)
        var selectedTab by mutableStateOf(MobileTab.HOME)

        composeTestRule.setContent {
            when {
                currentScreen is AppScreen.MovieDetails -> {
                    BackHandler { currentScreen = AppScreen.Home }
                }
                selectedTab != MobileTab.HOME -> {
                    BackHandler { selectedTab = MobileTab.HOME }
                }
            }
        }

        assertEquals(AppScreen.Home, currentScreen)
        assertEquals(MobileTab.HOME, selectedTab)
        // At root, no custom BackHandler callback should intercept, allowing clean exit
        assertFalse(composeTestRule.activity.onBackPressedDispatcher.hasEnabledCallbacks())
    }
}
