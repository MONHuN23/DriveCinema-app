package com.drivecinema.app.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivecinema.app.theme.DcColors
import com.drivecinema.app.theme.DcTheme
import com.drivecinema.app.ui.design.DcBanner
import com.drivecinema.app.ui.design.DcBannerKind
import com.drivecinema.app.ui.design.DcButton
import com.drivecinema.app.ui.design.DcButtonKind

/**
 * The sign-in screen (phone and TV). Task P.7 moved it to Milestone P's design: [DcButton]s with the white TV focus
 * (decision D-P1) instead of the blue focus fill, and the error as a [DcBanner]. Buttons, wording and first focus
 * ([SignInOptions.initialFocus]) are unchanged.
 */
@Composable
fun SignInScreen(
    isLoading: Boolean,
    errorMessage: String? = null,
    onSignInClick: () -> Unit,
    onPairingClick: (() -> Unit)? = null,
    pairingButtonText: String = "Pair with Phone",
    modifier: Modifier = Modifier,
    onOpenGuideClick: (() -> Unit)? = null,
    options: SignInOptions = SignInOptions.forDevice(isTv = false, playServicesAvailable = true)
) {
    val showGoogle = options.showGoogleSignIn
    val initialFocus = options.initialFocus(hasPairing = onPairingClick != null, hasGuide = onOpenGuideClick != null)
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        if (initialFocus == SignInFocusTarget.NONE) return@LaunchedEffect
        // The button can be placed a frame late (as Play on the TV details page).
        repeat(5) {
            if (runCatching { focusRequester.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
            withFrameNanos { }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DcColors.Canvas)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.width(420.dp)
        ) {
            // The wordmark: large enough for brand red on the canvas (3:1 for large text).
            Text(
                text = "DriveCinema",
                color = DcColors.Brand,
                fontSize = 36.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Direct streaming from your Google Drive\nwith zero custom backends or relay servers",
                style = DcTheme.type.body,
                color = DcColors.Text2,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(36.dp))

            if (isLoading) {
                CircularProgressIndicator(color = DcColors.Brand, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "Signing in...", style = DcTheme.type.body, color = DcColors.Text1)
            } else {
                if (options.notice != null) {
                    Text(
                        text = options.notice,
                        style = DcTheme.type.body,
                        color = DcColors.Text2,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                }

                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (showGoogle) {
                        DcButton(
                            "Sign in with Google",
                            onClick = onSignInClick,
                            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester).testTag("sign_in_google")
                        )
                    }

                    if (onPairingClick != null) {
                        DcButton(
                            pairingButtonText,
                            onClick = onPairingClick,
                            kind = if (options.pairingIsPrimary) DcButtonKind.Primary else DcButtonKind.Secondary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (initialFocus == SignInFocusTarget.PAIRING) Modifier.focusRequester(focusRequester) else Modifier)
                                .testTag("sign_in_pair")
                        )
                    }

                    if (onOpenGuideClick != null) {
                        DcButton(
                            "How to set up your Drive folders",
                            onClick = onOpenGuideClick,
                            kind = DcButtonKind.Secondary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (initialFocus == SignInFocusTarget.GUIDE) Modifier.focusRequester(focusRequester) else Modifier)
                                .testTag("sign_in_guide")
                        )
                    }
                }
            }

            if (!errorMessage.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))
                DcBanner(errorMessage, kind = DcBannerKind.Error, modifier = Modifier.testTag("sign_in_error"))
            }
        }
    }
}
