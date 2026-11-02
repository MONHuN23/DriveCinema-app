package com.drivecinema.app.ui.setup

import androidx.activity.ComponentActivity
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.drivecinema.app.core.pairing.TvPairingServer
import com.drivecinema.app.theme.DriveCinemaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Task P.7: the pairing screens in Milestone P's design. The phone's dialog takes the code like My PCs' "Add a PC" and
 * says what's missing until Pair TV can run; the TV's dialog and sign-in screen keep their first focus.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PairingScreensTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var focusManager: FocusManager

    @Test
    fun theCodeKeepsLettersAndDigitsInCapitalsUpToSix() {
        val form = PhonePairingForm()
        form.typeCode("8f3-k9a7")
        assertEquals("8F3K9A", form.code)
    }

    @Test
    fun aFoundTvFillsTheAddressOnlyWhenNoneWasTyped() {
        val found = PhonePairingForm()
        found.onTvFound("192.168.1.154", 8800)
        assertEquals("192.168.1.154", found.address)
        found.typeCode("8F3K9A")
        assertEquals(TvPairTarget("192.168.1.154", 8800), found.target())

        val typed = PhonePairingForm()
        typed.address = "10.0.0.5:9000"
        typed.onTvFound("192.168.1.154", 8800)
        typed.typeCode("8F3K9A")
        assertEquals("10.0.0.5:9000", typed.address)
        assertEquals(TvPairTarget("10.0.0.5", 9000), typed.target())
    }

    @Test
    fun pairTvSaysWhatIsMissing() {
        assertEquals("Enter all 6 characters of the code", tvPairBlockedReason("192.168.1.154", "8F3"))
        assertEquals("Type the TV's address, or wait until it's found on Wi-Fi", tvPairBlockedReason(" ", "8F3K9A"))
        assertNull(tvPairBlockedReason("192.168.1.154", "8F3K9A"))
        assertNull(PhonePairingForm().target())
    }

    @Test
    fun thePhoneDialogEnablesPairTvOnceTheCodeAndAddressAreThere() {
        val submitted = mutableListOf<Triple<String, String, Int>>()
        rule.setContent {
            DriveCinemaTheme {
                PhonePairingDialog(
                    isPairing = false,
                    onPairSubmit = { pin, host, port -> submitted += Triple(pin, host, port) },
                    onDismissRequest = {},
                    discoverTvs = false
                )
            }
        }

        rule.onNodeWithTag("phone_pairing_pair").assertIsNotEnabled()
        rule.onNodeWithTag("phone_pairing_reason").assertTextEquals("Enter all 6 characters of the code")

        rule.onNodeWithTag("phone_pairing_code").performTextInput("8f3k9a")
        rule.onNodeWithTag("phone_pairing_code").assertTextContains("8F3K9A")
        rule.onNodeWithText("6 of 6").assertExists()
        rule.onNodeWithTag("phone_pairing_reason").assertTextEquals("Type the TV's address, or wait until it's found on Wi-Fi")

        rule.onNodeWithTag("phone_pairing_address").performTextInput("192.168.1.154")
        rule.onNodeWithTag("phone_pairing_reason").assertDoesNotExist()
        rule.onNodeWithTag("phone_pairing_pair").assertIsEnabled().performClick()

        assertEquals(listOf(Triple("8F3K9A", "192.168.1.154", TvPairingServer.DEFAULT_PORT)), submitted)
    }

    @Test
    fun thePhoneDialogShowsTheErrorAndIgnoresPairTvWhilePairing() {
        var submits = 0
        rule.setContent {
            DriveCinemaTheme {
                PhonePairingDialog(
                    isPairing = true,
                    errorMessage = "Wrong code",
                    onPairSubmit = { _, _, _ -> submits++ },
                    onDismissRequest = {},
                    discoverTvs = false
                )
            }
        }

        rule.onNodeWithTag("phone_pairing_error").assertExists()
        rule.onNodeWithText("Wrong code").assertExists()
        rule.onNodeWithTag("phone_pairing_cancel").assertIsNotEnabled()
        rule.onNodeWithTag("phone_pairing_pair").assertTextEquals("Pairing…")
        assertEquals(0, submits)
    }

    @Config(qualifiers = "w960dp-h540dp")
    @Test
    fun theTvCodeDialogStartsOnCancel() {
        var dismissed = 0
        rule.setContent {
            DriveCinemaTheme(isTv = true) {
                TvPairingDialog(pin = "8F3K9A", statusText = "Waiting for the phone…", serverAddress = "192.168.1.154", onDismissRequest = { dismissed++ })
            }
        }
        rule.waitForIdle()

        rule.onNodeWithTag("tv_pairing_cancel").assertIsFocused()
        rule.onNodeWithText("K").assertExists()
        rule.onNodeWithText("TV IP: 192.168.1.154").assertExists()
        rule.onNodeWithTag("tv_pairing_cancel").performClick()
        assertEquals(1, dismissed)
    }

    @Config(qualifiers = "w960dp-h540dp")
    @Test
    fun theTvSignInStartsOnPairWithPhoneAndDownReachesTheGuide() {
        rule.setContent {
            DriveCinemaTheme(isTv = true) {
                focusManager = LocalFocusManager.current
                SignInScreen(
                    isLoading = false,
                    onSignInClick = {},
                    onPairingClick = {},
                    pairingButtonText = "Pair with Phone",
                    onOpenGuideClick = {},
                    options = SignInOptions.forDevice(isTv = true, playServicesAvailable = true)
                )
            }
        }
        rule.waitForIdle()

        rule.onNodeWithTag("sign_in_pair").assertIsFocused()
        rule.runOnUiThread { focusManager.moveFocus(FocusDirection.Down) }
        rule.waitForIdle()
        rule.onNodeWithTag("sign_in_guide").assertIsFocused()
    }
}
