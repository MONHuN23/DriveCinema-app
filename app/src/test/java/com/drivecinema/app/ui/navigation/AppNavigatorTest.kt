package com.drivecinema.app.ui.navigation

import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Task M.3 (F-38): the back stack's moves, and that a rotation keeps it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppNavigatorTest {

    @Test
    fun backPopsToTheScreenYouCameFrom() {
        val navigator = AppNavigator(listOf(Screen.Home))
        navigator.open(Screen.Grid(GridKind.FILMS))
        navigator.open(Screen.MovieDetails("m1"))

        assertTrue(navigator.back())
        assertEquals(Screen.Grid(GridKind.FILMS), navigator.current)
        assertTrue(navigator.back())
        assertEquals(Screen.Home, navigator.current)
    }

    @Test
    fun backAtTheRootLeavesTheStackAlone() {
        val navigator = AppNavigator(listOf(Screen.Home))

        assertFalse(navigator.back())
        assertEquals(listOf<Screen>(Screen.Home), navigator.backStack.toList())
    }

    @Test
    fun openingTheShownScreenAgainDoesNotStackIt() {
        val navigator = AppNavigator(listOf(Screen.SignIn))
        navigator.open(Screen.SetupGuide)
        navigator.open(Screen.SetupGuide)

        assertEquals(listOf(Screen.SignIn, Screen.SetupGuide), navigator.backStack.toList())
    }

    @Test
    fun resetReplacesTheWholeStack() {
        val navigator = AppNavigator(listOf(Screen.Home))
        navigator.open(Screen.Search)
        navigator.open(Screen.SeriesDetails("s1"))

        navigator.resetTo(Screen.SignIn)

        assertEquals(listOf<Screen>(Screen.SignIn), navigator.backStack.toList())
    }

    @Test
    fun aSavedStackIsRestoredOnlyUnderTheSameRoot() {
        val saved = listOf(Screen.Home, Screen.Grid(GridKind.SERIES), Screen.SeriesDetails("s1"))

        assertEquals(saved, AppNavigator.restoredStack(Screen.Home, saved))
        // Signed out meanwhile: the sign-in state wins.
        assertEquals(listOf<Screen>(Screen.SignIn), AppNavigator.restoredStack(Screen.SignIn, saved))
        assertEquals(listOf<Screen>(Screen.Home), AppNavigator.restoredStack(Screen.Home, null))
    }

    @Test
    fun aLibrarySwitchPickerIsNotRestored() {
        val saved = listOf(Screen.Home, Screen.FolderPicker, Screen.SetupGuide)

        assertEquals(listOf(Screen.Home, Screen.SetupGuide), AppNavigator.restoredStack(Screen.Home, saved))
        // The first-run picker is the root and stays.
        assertEquals(
            listOf(Screen.FolderPicker, Screen.SetupGuide),
            AppNavigator.restoredStack(Screen.FolderPicker, listOf(Screen.FolderPicker, Screen.SetupGuide))
        )
    }

    /** Task P.2: Settings and its Local folders screen are kept like any other screen. */
    @Test
    fun settingsScreensAreRestored() {
        val saved = listOf(Screen.Home, Screen.Settings, Screen.LocalFolders)
        assertEquals(saved, AppNavigator.restoredStack(Screen.Home, saved))
    }

    /** Task P.2: cancelling a library switch closes the picker and shows Settings again; alone, it shows Home. */
    @Test
    fun cancellingALibrarySwitchReturnsToTheScreenUnderThePicker() {
        val fromSettings = AppNavigator(listOf(Screen.Home, Screen.Settings, Screen.FolderPicker))
        fromSettings.backOrResetTo(Screen.Home)
        assertEquals(listOf(Screen.Home, Screen.Settings), fromSettings.backStack.toList())

        val alone = AppNavigator(listOf(Screen.FolderPicker))
        alone.backOrResetTo(Screen.Home)
        assertEquals(listOf<Screen>(Screen.Home), alone.backStack.toList())
    }

    /** Task P.4: a Help topic's "Next" shows the next topic in its place, so Back returns to the Help home. */
    @Test
    fun replaceShowsTheNextScreenInPlaceOfTheTopOne() {
        val navigator = AppNavigator(listOf(Screen.Home, Screen.SetupGuide, Screen.HelpTopic("folders")))
        navigator.replace(Screen.HelpTopic("film-names"))
        assertEquals(listOf(Screen.Home, Screen.SetupGuide, Screen.HelpTopic("film-names")), navigator.backStack.toList())

        navigator.replace(Screen.HelpTopic("film-names"))
        assertEquals(3, navigator.backStack.size)

        val atRoot = AppNavigator(listOf(Screen.Home))
        atRoot.replace(Screen.SetupGuide)
        assertEquals(listOf(Screen.Home, Screen.SetupGuide), atRoot.backStack.toList())
    }

    /** Task P.4: the Help home keeps its old key and a topic is a new one; both survive a rotation. */
    @Test
    fun helpScreensSurviveARotation() {
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val navigator = AppNavigator.attach(activity.savedStateRegistry, Screen.Home)
                navigator.open(Screen.SetupGuide)
                navigator.open(Screen.HelpTopic("pc-streaming"))
            }

            scenario.recreate()

            scenario.onActivity { activity ->
                val restored = AppNavigator.attach(activity.savedStateRegistry, Screen.Home)
                assertEquals(
                    listOf(Screen.Home, Screen.SetupGuide, Screen.HelpTopic("pc-streaming")),
                    restored.backStack.toList()
                )
            }
        }
    }

    /** The phone's rotation recreates the activity; the details page and the grid under it must survive. */
    @Test
    fun rotationKeepsTheBackStack() {
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val navigator = AppNavigator.attach(activity.savedStateRegistry, Screen.Home)
                navigator.open(Screen.Grid(GridKind.FILMS))
                navigator.open(Screen.MovieDetails("m1"))
            }

            scenario.recreate()

            scenario.onActivity { activity ->
                val restored = AppNavigator.attach(activity.savedStateRegistry, Screen.Home)
                assertEquals(
                    listOf(Screen.Home, Screen.Grid(GridKind.FILMS), Screen.MovieDetails("m1")),
                    restored.backStack.toList()
                )
                assertTrue(restored.back())
                assertEquals(Screen.Grid(GridKind.FILMS), restored.current)
            }
        }
    }
}
