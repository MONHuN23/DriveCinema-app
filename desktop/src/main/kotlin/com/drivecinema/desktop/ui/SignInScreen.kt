package com.drivecinema.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivecinema.desktop.auth.SignInUiState

@Composable
fun SignInScreen(
    state: SignInUiState,
    onSignIn: () -> Unit,
    onCancel: () -> Unit,
    onPreviewLayout: () -> Unit,
    /** Shown when no system keyring is available and the sign-in is kept in a private file. */
    storageNotice: String? = null,
    onOpenGuide: (() -> Unit)? = null
) {
    Box(Modifier.fillMaxSize().background(DesktopColors.Background), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(460.dp)) {
            Text("DriveCinema", color = DesktopColors.Accent, fontSize = 40.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(12.dp))
            Text(
                "Direct streaming from your Google Drive",
                color = DesktopColors.TextSecondary,
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(36.dp))

            when (state) {
                SignInUiState.NotConfigured -> {
                    Notice(
                        "Google sign-in is not set up for this build. Add GOOGLE_DESKTOP_CLIENT_ID and " +
                            "GOOGLE_DESKTOP_CLIENT_SECRET to local.properties (see DESKTOP.md) and rebuild."
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(onClick = onPreviewLayout, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                        Text("Preview the layout", color = DesktopColors.TextPrimary)
                    }
                }
                is SignInUiState.Idle -> {
                    Button(
                        onClick = onSignIn,
                        colors = ButtonDefaults.buttonColors(containerColor = DesktopColors.Accent),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) { Text("Sign in with Google", fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Your browser opens Google's sign-in page. DriveCinema only gets read access to Drive.",
                        color = DesktopColors.TextSecondary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    state.errorMessage?.let {
                        Spacer(Modifier.height(20.dp))
                        Notice(it, isError = true)
                    }
                    storageNotice?.let {
                        Spacer(Modifier.height(20.dp))
                        Notice(it)
                    }
                    onOpenGuide?.let {
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(onClick = it, modifier = Modifier.fillMaxWidth().height(44.dp)) {
                            Text("How to set up your Drive folders", color = DesktopColors.TextPrimary)
                        }
                    }
                }
                is SignInUiState.WaitingForBrowser -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = DesktopColors.Accent, modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Finish signing in in your browser…", color = DesktopColors.TextPrimary, fontSize = 16.sp)
                    }
                    state.authorizationUrl?.let { url ->
                        Spacer(Modifier.height(20.dp))
                        Text(
                            if (state.browserOpened) "Browser didn't open? Open this link yourself:"
                            else "Could not open a browser. Open this link yourself:",
                            color = DesktopColors.TextSecondary,
                            fontSize = 13.sp
                        )
                        Spacer(Modifier.height(6.dp))
                        SelectionContainer {
                            Text(url, color = DesktopColors.Focus, fontSize = 11.sp, maxLines = 3)
                        }
                        Spacer(Modifier.height(8.dp))
                        val clipboard = LocalClipboardManager.current
                        OutlinedButton(onClick = { clipboard.setText(AnnotatedString(url)) }) {
                            Text("Copy link", color = DesktopColors.TextPrimary)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().height(44.dp)) {
                        Text("Cancel", color = DesktopColors.TextPrimary)
                    }
                }
                is SignInUiState.SignedIn -> {
                    CircularProgressIndicator(color = DesktopColors.Accent)
                }
            }
        }
    }
}

@Composable
private fun Notice(text: String, isError: Boolean = false) {
    val color = if (isError) DesktopColors.Accent else DesktopColors.Focus
    Box(
        Modifier.fillMaxWidth()
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
            .border(1.dp, color, RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Text(text, color = DesktopColors.TextPrimary, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}
