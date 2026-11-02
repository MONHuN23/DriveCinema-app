package com.drivecinema.app.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.drivecinema.app.theme.DcColors
import com.drivecinema.app.theme.DcTheme

/**
 * Milestone P: "are you sure?" before an action that is hard to undo (sign out, remove; decision D-P2). On the TV the
 * remote starts on Cancel, so a stray OK changes nothing; the phone gets no focus ring.
 */
@Composable
fun DcConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = true
) {
    val isTv = DcTheme.formFactor.isTv
    val cancelFocus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .background(DcColors.Surface1, RoundedCornerShape(DcTokens.Radius.SHEET.dp))
                .padding(24.dp)
                .testTag("dc_confirm_dialog"),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(title, style = DcTheme.type.title, color = DcColors.Text1)
            Text(text, style = DcTheme.type.body, color = DcColors.Text2)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
            ) {
                DcButton(
                    "Cancel",
                    onClick = onDismiss,
                    kind = DcButtonKind.Secondary,
                    modifier = Modifier.focusRequester(cancelFocus)
                )
                DcButton(
                    confirmLabel,
                    onClick = onConfirm,
                    kind = if (destructive) DcButtonKind.Destructive else DcButtonKind.Primary
                )
            }
        }
        if (isTv) {
            LaunchedEffect(Unit) { runCatching { cancelFocus.requestFocus() } }
        }
    }
}
