package com.drivecinema.app.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.drivecinema.app.core.remote.DeviceLink
import com.drivecinema.app.ui.design.DcButton
import com.drivecinema.app.ui.design.DcButtonKind

/** Texts of the two link lists (Milestone D): the phone's TVs and the TV's phones. */
object LinkedDevicesTexts {
    const val PHONE_TITLE = SettingsLabels.MY_TVS
    const val PHONE_EXPLAIN = "TVs this phone can send films and episodes to (Play on TV). DriveCinema must be open on the TV, on the same Wi-Fi."
    const val PHONE_ADD = "Link a TV"
    val PHONE_EMPTY = "No TV linked yet. On the TV: ${SettingsLabels.path(SettingsLabels.LINKED_PHONES, TV_ADD)}, then enter the code here."
    const val TV_TITLE = SettingsLabels.LINKED_PHONES
    const val TV_EXPLAIN = "Phones that can start playback on this TV (Play on TV) while DriveCinema is open here."
    const val TV_ADD = "Link a phone"
    val TV_EMPTY = "No phone linked yet. Choose Link a phone and enter the code on the phone (${SettingsLabels.path(SettingsLabels.PAIR_A_TV)})."
}

/**
 * Milestone D: the linked devices with Remove, and a button to link another one. Used on the phone (TVs) and on the TV
 * (phones); D-pad friendly like the account dialog.
 */
@Composable
fun LinkedDevicesDialog(
    title: String,
    explanation: String,
    emptyText: String,
    addLabel: String,
    devices: List<DeviceLink>,
    onAdd: () -> Unit,
    onRemove: (id: String) -> Unit,
    onDismissRequest: () -> Unit
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    Dialog(onDismissRequest = onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .width(400.dp)
                .heightIn(max = 600.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF16181D))
                .border(1.dp, Color(0xFF2A2D35), RoundedCornerShape(16.dp))
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(explanation, color = Color(0xFF9AA0A6), fontSize = 13.sp, lineHeight = 18.sp)
            if (devices.isEmpty()) {
                Spacer(Modifier.height(14.dp))
                Text(emptyText, color = Color(0xFFB0B3B8), fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.testTag("linked_empty"))
            }
            devices.forEach { device ->
                Spacer(Modifier.height(14.dp))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFF1E2128)).padding(12.dp)) {
                    Text(device.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    device.lastAddress?.let { Text("Last seen at $it", color = Color(0xFF9AA0A6), fontSize = 12.sp) }
                    Spacer(Modifier.height(8.dp))
                    DcButton(text = "Remove", onClick = { onRemove(device.id) }, kind = DcButtonKind.Secondary, small = true)
                }
            }
            Spacer(Modifier.height(18.dp))
            DcButton(text = addLabel, onClick = onAdd, modifier = Modifier.fillMaxWidth().focusRequester(firstFocus))
            Spacer(Modifier.height(10.dp))
            DcButton(text = "Close", onClick = onDismissRequest, modifier = Modifier.fillMaxWidth(), kind = DcButtonKind.Secondary)
        }
    }
}
