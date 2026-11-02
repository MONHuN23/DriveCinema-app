package com.drivecinema.app.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.drivecinema.app.theme.DcColors
import com.drivecinema.app.theme.DcTheme
import com.drivecinema.app.ui.design.DcButton
import com.drivecinema.app.ui.design.DcButtonKind
import com.drivecinema.app.ui.design.DcTokens

/**
 * The TV's "Pair with Phone" dialog: the code to type on the phone. Task P.7 moved it to Milestone P's design
 * (neutral code boxes, no blue); Cancel is a secondary button with the first focus and the white TV focus (D-P1).
 */
@Composable
fun TvPairingDialog(
    pin: String,
    statusText: String,
    serverAddress: String? = null,
    /** Milestone D: "Link a phone" when the TV is already signed in and only links for Play on TV. */
    title: String = "Pair with Phone",
    onDismissRequest: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }

    Dialog(onDismissRequest = onDismissRequest) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .width(480.dp)
                .background(DcColors.Surface1, RoundedCornerShape(DcTokens.Radius.SHEET.dp))
                .padding(32.dp)
                .testTag("tv_pairing_dialog")
        ) {
            Text(title, style = DcTheme.type.headline, color = DcColors.Text1)
            Text(
                "Open DriveCinema on your phone and enter this code:",
                style = DcTheme.type.body,
                color = DcColors.Text2,
                textAlign = TextAlign.Center
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(top = 12.dp).testTag("tv_pairing_code")
            ) {
                pin.forEach { char ->
                    Box(
                        modifier = Modifier
                            .width(46.dp)
                            .height(56.dp)
                            .background(DcColors.Surface2, RoundedCornerShape(DcTokens.Radius.CONTROL.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = char.toString(),
                            color = DcColors.Text1,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            if (!serverAddress.isNullOrEmpty()) {
                Text(
                    "TV IP: $serverAddress",
                    style = DcTheme.type.body.copy(fontFamily = FontFamily.Monospace),
                    color = DcColors.Text2,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                CircularProgressIndicator(color = DcColors.Text2, strokeWidth = 2.5.dp, modifier = Modifier.size(18.dp))
                Text(statusText, style = DcTheme.type.caption, color = DcColors.Text2)
            }

            DcButton(
                "Cancel",
                onClick = onDismissRequest,
                kind = DcButtonKind.Secondary,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .width(160.dp)
                    .focusRequester(focusRequester)
                    .testTag("tv_pairing_cancel")
            )
        }
        LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    }
}
