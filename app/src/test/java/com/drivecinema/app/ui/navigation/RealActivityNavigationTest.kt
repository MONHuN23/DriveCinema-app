package com.drivecinema.app.ui.navigation

import android.os.Bundle
import android.os.Parcel
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import com.drivecinema.app.ui.search.SearchViewModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/** An activity wired like MainActivity: the navigator attached to its saved state, the real [AppNavDisplay]. */
class NavHostTestActivity : ComponentActivity() {
    lateinit var navigator: AppNavigator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        navigator = AppNavigator.attach(savedStateRegistry, Screen.Home)
        val search = SearchViewModel(NavTestLibrary.mediaDao(), debounceMs = 0L)
        setContent { NavTestLibrary.Screens(navigator, isTv = false, searchViewModel = search) }
    }
}

/**
 * Task M.3 follow-up: the device check saw a phone rotation and the TV search's Back land on Home. These run the
 * same paths closer to a device than the other tests: a real activity recreate with the screens shown, a restart from
 * a parcelled state (process death), and the remote's real Back and OK key events.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp")
class RealActivityNavigationTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun recreatingTheActivityKeepsTheDetailsPage() {
        val controller = Robolectric.buildActivity(NavHostTestActivity::class.java).setup()
        controller.get().navigator.open(Screen.MovieDetails("m2"))
        ShadowLooper.idleMainLooper()

        controller.recreate()
        ShadowLooper.idleMainLooper()

        assertEquals(listOf(Screen.Home, Screen.MovieDetails("m2")), controller.get().navigator.backStack.toList())
    }

    @Test
    fun aRestartFromParcelledStateKeepsTheDetailsPage() {
        val controller = Robolectric.buildActivity(NavHostTestActivity::class.java).setup()
        controller.get().navigator.open(Screen.MovieDetails("m2"))
        ShadowLooper.idleMainLooper()
        val saved = Bundle()
        controller.pause().stop().saveInstanceState(saved)
        val parcel = Parcel.obtain()
        saved.writeToParcel(parcel, 0)
        parcel.setDataPosition(0)
        val restoredState = Bundle.CREATOR.createFromParcel(parcel)
        parcel.recycle()

        val restarted = Robolectric.buildActivity(NavHostTestActivity::class.java).setup(restoredState)
        ShadowLooper.idleMainLooper()

        assertEquals(listOf(Screen.Home, Screen.MovieDetails("m2")), restarted.get().navigator.backStack.toList())
    }

    private fun pressKey(code: Int) {
        rule.runOnUiThread {
            rule.activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
            rule.activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        }
        rule.waitForIdle()
    }

    @Test
    fun tvSearchResultOpenedWithOkAndClosedWithTheBackKeyReturnsToTheSearch() {
        val navigator = AppNavigator(listOf(Screen.Home))
        val search = SearchViewModel(NavTestLibrary.mediaDao(), debounceMs = 0L)
        rule.setContent { NavTestLibrary.Screens(navigator, isTv = true, searchViewModel = search) }
        rule.onAllNodes(hasText("Search") and hasClickAction()).onFirst().performClick()
        rule.onNode(hasSetTextAction()).performTextInput("Dun")
        rule.waitForIdle()
        rule.onAllNodes(hasText("Dune") and hasClickAction()).onFirst().performSemanticsAction(SemanticsActions.RequestFocus)
        rule.waitForIdle()

        pressKey(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(Screen.MovieDetails(NavTestLibrary.movie("Dune").id), navigator.current)
        pressKey(KeyEvent.KEYCODE_BACK)

        assertEquals(listOf(Screen.Home, Screen.Search), navigator.backStack.toList())
    }
}
