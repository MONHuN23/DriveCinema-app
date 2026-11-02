package com.drivecinema.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.drivecinema.app.ui.design.DcButtonKind
import com.drivecinema.app.ui.design.DcTokens
import com.drivecinema.desktop.host.PcEntry
import com.drivecinema.desktop.host.PcsState
import com.drivecinema.desktop.ui.design.DesktopBanner
import com.drivecinema.desktop.ui.design.DesktopButton
import com.drivecinema.desktop.ui.design.DesktopConfirmDialog
import com.drivecinema.desktop.ui.design.DesktopGroup
import com.drivecinema.desktop.ui.design.DesktopGroupDivider
import com.drivecinema.desktop.ui.design.DesktopListRow
import com.drivecinema.desktop.ui.design.DesktopSectionHeader

class PcsActions(
    val onSearch: () -> Unit,
    val onPair: (address: String, pin: String, vpnAddress: String) -> Unit,
    val onSetVpnAddress: (hostId: String, address: String) -> Unit,
    val onRemove: (hostId: String) -> Unit,
    val onCheckNow: () -> Unit
)

/** The pairing code as typed: letters and digits, capitals, at most 6 (as on the phone). */
fun pairingCodeInput(raw: String): String = raw.filter { it.isLetterOrDigit() }.uppercase().take(6)

/** Why Pair can't run yet (it used to try anyway), or null. */
fun pairBlockedReason(address: String, code: String): String? = when {
    address.isBlank() -> "Find the PC, or type its address"
    code.length < 6 -> "Enter all 6 characters of the code"
    else -> null
}

/**
 * Task I.2, desktop as a client: play films stored on another PC. Task P.6: the PCs as rows with Tailscale address
 * and Remove (asks first, D-P2); "Add a PC" with the found PCs as rows and Pair as the page's one primary action,
 * which says what is missing until it can run.
 */
@Composable
fun PcsScreen(state: PcsState, actions: PcsActions) {
    var address by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var vpn by remember { mutableStateOf("") }
    var removing by remember { mutableStateOf<PcEntry?>(null) }
    // The first PC the search finds goes into the address field (unless one was typed already).
    LaunchedEffect(state.discovered) {
        if (address.isBlank()) state.discovered.firstOrNull()?.let { address = it.address }
    }
    PageHeader(
        "Play from another PC",
        "Films stored on another computer running DriveCinema join this library, without uploading them. At home it " +
            "works on the network; away from home, run Tailscale on both computers and enter the other PC's Tailscale address."
    )
    state.message?.let { DesktopBanner(it, Modifier.padding(top = 20.dp)) }

    DesktopSectionHeader("Paired PCs") {
        if (state.pcs.isNotEmpty()) {
            DesktopButton("Check now", onClick = actions.onCheckNow, kind = DcButtonKind.Secondary, icon = DesktopIcons.Refresh, small = true)
        }
    }
    if (state.pcs.isEmpty()) {
        Text("None yet.", style = DesktopType.body, color = DesktopColors.TextSecondary)
    } else {
        DesktopGroup {
            state.pcs.forEachIndexed { index, pc ->
                if (index > 0) DesktopGroupDivider()
                PcRow(pc, onSetVpnAddress = { actions.onSetVpnAddress(pc.hostId, it) }, onRemove = { removing = pc })
            }
        }
    }

    DesktopSectionHeader("Add a PC")
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "On the other PC: account menu → Share with my devices → turn sharing on → Pair a device. It shows a " +
                "6-character code.",
            style = DesktopType.body,
            color = DesktopColors.TextSecondary
        )
        Row {
            DesktopButton(
                if (state.isSearching) "Searching this network…" else "Search this network",
                onClick = actions.onSearch,
                kind = DcButtonKind.Secondary,
                icon = DesktopIcons.Wifi,
                loading = state.isSearching
            )
        }
        if (state.discovered.isNotEmpty()) {
            DesktopGroup {
                state.discovered.forEachIndexed { index, found ->
                    if (index > 0) DesktopGroupDivider()
                    DesktopListRow(
                        found.name,
                        supporting = found.address,
                        icon = DesktopIcons.DesktopWindows,
                        onClick = { address = found.address },
                        trailing = if (address == found.address) {
                            { Icon(DesktopIcons.Check, contentDescription = "Chosen", tint = DesktopColors.Success) }
                        } else null
                    )
                }
            }
        }
        Field(address, { address = it.trim() }, "PC address (e.g. 192.168.1.20)")
        Field(pin, { pin = pairingCodeInput(it) }, "Code shown on the PC")
        Field(vpn, { vpn = it.trim() }, "Tailscale address (optional, for away from home)")
        val blocked = pairBlockedReason(address, pin)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DesktopButton("Pair", onClick = { actions.onPair(address, pin, vpn) }, enabled = blocked == null, loading = state.isPairing)
            blocked?.let { Text(it, style = DesktopType.caption, color = DesktopColors.TextSecondary) }
        }
    }

    removing?.let { pc ->
        DesktopConfirmDialog(
            title = "Remove ${pc.name}?",
            text = "This computer forgets the PC: its films stop playing from it here (copies on Drive still play). Pair " +
                "again to get them back.",
            confirmLabel = "Remove",
            onConfirm = {
                removing = null
                actions.onRemove(pc.hostId)
            },
            onDismiss = { removing = null }
        )
    }
}

@Composable
private fun PcRow(pc: PcEntry, onSetVpnAddress: (String) -> Unit, onRemove: () -> Unit) {
    var editing by remember { mutableStateOf(false) }
    var vpn by remember(pc.vpnAddress) { mutableStateOf(pc.vpnAddress.orEmpty()) }
    Column {
        DesktopListRow(
            pc.name,
            supporting = listOf(
                pc.status ?: "Not checked yet",
                listOfNotNull(pc.homeAddress?.let { "Home $it" }, "Tailscale ${pc.vpnAddress ?: "not set"}").joinToString(" · ")
            ).joinToString("\n"),
            icon = DesktopIcons.DesktopWindows,
            trailing = {
                DesktopButton("Tailscale address", onClick = { editing = !editing }, kind = DcButtonKind.Tertiary, small = true)
                DesktopButton("Remove", onClick = onRemove, kind = DcButtonKind.Destructive, small = true)
            }
        )
        if (editing) {
            Row(
                Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Field(vpn, { vpn = it.trim() }, "Tailscale address (e.g. 100.101.102.103); empty clears it", Modifier.weight(1f))
                DesktopButton("Save", onClick = { onSetVpnAddress(vpn); editing = false }, small = true)
                DesktopButton("Cancel", onClick = { editing = false }, kind = DcButtonKind.Tertiary, small = true)
            }
        }
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier.fillMaxWidth()) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        shape = RoundedCornerShape(DcTokens.Radius.CONTROL.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = DesktopColors.TextPrimary,
            unfocusedTextColor = DesktopColors.TextPrimary,
            focusedBorderColor = DesktopColors.FocusRing,
            unfocusedBorderColor = DesktopColors.Outline,
            focusedLabelColor = DesktopColors.TextPrimary,
            unfocusedLabelColor = DesktopColors.TextSecondary,
            cursorColor = DesktopColors.TextPrimary
        ),
        modifier = modifier
    )
}
