package com.drivecinema.app.ui.tv.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

@Composable
fun TvStorageUnsupportedDialog(
    onDismissRequest: () -> Unit
) {
    val okFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        try {
            okFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    Dialog(onDismissRequest = onDismissRequest) {
        Box(
            modifier = Modifier
                .width(420.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF16181D))
                .border(2.dp, Color(0xFF2A2E39), RoundedCornerShape(16.dp))
                .padding(28.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Local Storage Not Supported",
                    color = Color(0xFFF0F2F5),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Local folder selection is not supported by this TV firmware.\n\nYour Android TV device does not include the system document picker (DocumentsUI), which is required to grant folder access.",
                    color = Color(0xFF9AA0A6),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                val okInteractionSource = remember { MutableInteractionSource() }
                val isOkFocused by okInteractionSource.collectIsFocusedAsState()

                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isOkFocused) Color(0xFF2B4C8C) else Color(0x334D88FF))
                        .border(
                            width = if (isOkFocused) 2.dp else 1.dp,
                            color = if (isOkFocused) Color.White else Color(0xFF4D88FF),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .focusRequester(okFocusRequester)
                        .clickable(
                            interactionSource = okInteractionSource,
                            indication = null,
                            onClick = onDismissRequest
                        )
                        .focusable(interactionSource = okInteractionSource)
                        .onKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown &&
                                (keyEvent.key == Key.DirectionCenter ||
                                 keyEvent.key == Key.Enter ||
                                 keyEvent.key == Key.NumPadEnter)
                            ) {
                                onDismissRequest()
                                true
                            } else false
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "OK",
                        color = if (isOkFocused) Color.White else Color(0xFF8AB4FF),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
