package com.drivecinema.app.ui.setup

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Task E.3: TVs without Google Play Services (Fire TV, generic boxes) sign in by pairing only. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SignInOptionsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun phoneAlwaysOffersGoogleSignIn() {
        // Phones sign in through the browser, so missing Play Services does not matter.
        val options = SignInOptions.forDevice(isTv = false, playServicesAvailable = false)
        assertTrue(options.showGoogleSignIn)
        assertFalse(options.pairingIsPrimary)
        assertNull(options.notice)
    }

    @Test
    fun tvWithPlayServicesIsUnchanged() {
        val options = SignInOptions.forDevice(isTv = true, playServicesAvailable = true)
        assertTrue(options.showGoogleSignIn)
        assertFalse(options.pairingIsPrimary)
        assertNull(options.notice)
    }

    @Test
    fun tvWithoutPlayServicesOffersPairingOnly() {
        val options = SignInOptions.forDevice(isTv = true, playServicesAvailable = false)
        assertFalse(options.showGoogleSignIn)
        assertTrue(options.pairingIsPrimary)
        assertEquals(SignInOptions.NO_PLAY_SERVICES_NOTICE, options.notice)
    }

    @Test
    fun screenWithoutPlayServicesHidesGoogleAndOffersPairing() {
        var pairingClicks = 0
        composeTestRule.setContent {
            SignInScreen(
                isLoading = false,
                onSignInClick = {},
                onPairingClick = { pairingClicks++ },
                pairingButtonText = "Pair with Phone",
                onOpenGuideClick = {},
                options = SignInOptions.forDevice(isTv = true, playServicesAvailable = false)
            )
        }

        composeTestRule.onAllNodesWithText("Sign in with Google").assertCountEquals(0)
        composeTestRule.onNodeWithText(SignInOptions.NO_PLAY_SERVICES_NOTICE).assertIsDisplayed()
        composeTestRule.onNodeWithText("Pair with Phone").performClick()
        assertEquals(1, pairingClicks)
    }

    @Test
    fun initialFocusGoesToFirstShownButton() {
        val withGoogle = SignInOptions.forDevice(isTv = true, playServicesAvailable = true)
        val pairingOnly = SignInOptions.forDevice(isTv = true, playServicesAvailable = false)

        assertEquals(SignInFocusTarget.GOOGLE, withGoogle.initialFocus(hasPairing = true, hasGuide = true))
        assertEquals(SignInFocusTarget.PAIRING, pairingOnly.initialFocus(hasPairing = true, hasGuide = true))
        assertEquals(SignInFocusTarget.GUIDE, pairingOnly.initialFocus(hasPairing = false, hasGuide = true))
        assertEquals(SignInFocusTarget.NONE, pairingOnly.initialFocus(hasPairing = false, hasGuide = false))
    }

    @Test
    fun screenWithPlayServicesShowsGoogleSignInWithoutNotice() {
        composeTestRule.setContent {
            SignInScreen(
                isLoading = false,
                onSignInClick = {},
                onPairingClick = {},
                pairingButtonText = "Pair with Phone",
                options = SignInOptions.forDevice(isTv = true, playServicesAvailable = true)
            )
        }

        composeTestRule.onNodeWithText("Sign in with Google").assertIsDisplayed()
        composeTestRule.onAllNodesWithText(SignInOptions.NO_PLAY_SERVICES_NOTICE).assertCountEquals(0)
    }

    @Test
    fun screenWithoutAnyButtonDoesNotCrash() {
        // No button shown means no attached FocusRequester; requesting focus would throw.
        composeTestRule.setContent {
            SignInScreen(
                isLoading = false,
                onSignInClick = {},
                options = SignInOptions.forDevice(isTv = true, playServicesAvailable = false)
            )
        }
        composeTestRule.onNodeWithText(SignInOptions.NO_PLAY_SERVICES_NOTICE).assertIsDisplayed()
    }
}
