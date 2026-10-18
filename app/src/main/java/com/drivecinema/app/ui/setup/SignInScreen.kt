package com.drivecinema.app.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
        if (initialFocus != SignInFocusTarget.NONE) {
            focusRequester.requestFocus()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0C0D10))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.width(420.dp)
        ) {
            Text(
                text = "DriveCinema",
                color = Color(0xFFE50914),
                fontSize = 36.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Direct streaming from your Google Drive\nwith zero custom backends or relay servers",
                color = Color(0xFFB0B3B8),
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(36.dp))

            if (isLoading) {
                CircularProgressIndicator(
                    color = Color(0xFFE50914),
                    modifier = Modifier.height(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Signing in...",
                    color = Color.White,
                    fontSize = 15.sp
                )
            } else {
                if (options.notice != null) {
                    Text(
                        text = options.notice,
                        color = Color(0xFFB0B3B8),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                }

                if (showGoogle) {
                    SetupButton(
                        text = "Sign in with Google",
                        isPrimary = true,
                        onClick = onSignInClick,
                        modifier = Modifier.focusRequester(focusRequester)
                    )
                }

                if (onPairingClick != null) {
                    if (showGoogle) Spacer(modifier = Modifier.height(16.dp))
                    SetupButton(
                        text = pairingButtonText,
                        isPrimary = options.pairingIsPrimary,
                        onClick = onPairingClick,
                        modifier = if (initialFocus == SignInFocusTarget.PAIRING) Modifier.focusRequester(focusRequester) else Modifier
                    )
                }

                if (onOpenGuideClick != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    SetupButton(
                        text = "How to set up your Drive folders",
                        isPrimary = false,
                        onClick = onOpenGuideClick,
                        modifier = if (initialFocus == SignInFocusTarget.GUIDE) Modifier.focusRequester(focusRequester) else Modifier
                    )
                }
            }

            if (!errorMessage.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x33E50914))
                        .border(1.dp, Color(0xFFE50914), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = errorMessage,
                        color = Color(0xFFFFB4AB),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
fun SetupButton(
    text: String,
    isPrimary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val containerColor = when {
        isFocused -> Color(0xFF4D88FF)
        isPrimary -> Color(0xFFE50914)
        else -> Color(0xFF1E2128)
    }

    val contentColor = Color.White
    val borderStrokeColor = if (isFocused) Color.White else Color.Transparent

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(containerColor)
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = borderStrokeColor,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = contentColor,
            fontSize = 16.sp,
            fontWeight = if (isFocused || isPrimary) FontWeight.Bold else FontWeight.Medium
        )
    }
}
