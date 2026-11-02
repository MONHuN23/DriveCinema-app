package com.drivecinema.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivecinema.app.ui.design.DcBannerKind
import com.drivecinema.app.ui.design.DcButtonKind
import com.drivecinema.app.ui.design.DcTokens
import com.drivecinema.app.ui.setup.SettingsLabels
import com.drivecinema.desktop.host.HostStatus
import com.drivecinema.desktop.host.PairedDevice
import com.drivecinema.desktop.ui.design.DesktopBanner
import com.drivecinema.desktop.ui.design.DesktopButton
import com.drivecinema.desktop.ui.design.DesktopConfirmDialog
import com.drivecinema.desktop.ui.design.DesktopGroup
import com.drivecinema.desktop.ui.design.DesktopGroupDivider
import com.drivecinema.desktop.ui.design.DesktopListRow
import com.drivecinema.desktop.ui.design.DesktopSectionHeader
import com.drivecinema.desktop.ui.design.DesktopSwitchRow

class SharingActions(
    val onSharing: (Boolean) -> Unit,
    val onAllowVpn: (Boolean) -> Unit,
    val onStartPairing: () -> Unit,
    val onCancelPairing: () -> Unit,
    val onRemoveDevice: (String) -> Unit,
    val onLocalFolders: () -> Unit
)

/** The Windows firewall question, as a tip (it was in brand red and read as an error). */
const val WINDOWS_FIREWALL_TIP =
    "Windows may ask \"Allow DriveCinema to communicate on these networks?\": allow Private networks only. On public " +
        "networks (cafés, hotels) sharing stays blocked."

/**
 * Task I.2: share this computer's local folders with paired phones and TVs. Task P.6: the two switches as one group,
 * the firewall tip as an info banner, "Paired devices" with Pair a device beside it, a device icon and friendly dates
 * per device, Remove asks first (D-P2).
 */
@Composable
fun SharingScreen(status: HostStatus, sharedFolderCount: Int, isWindows: Boolean, actions: SharingActions) {
    var removing by remember { mutableStateOf<PairedDevice?>(null) }
    PageHeader(
        "Share with my devices",
        "Your phone and TV can play the films in this computer's local folders without uploading them. This computer " +
            "must be on, with DriveCinema open. Files are only read, and only devices you pair can see them."
    )

    DesktopGroup(Modifier.padding(top = 24.dp)) {
        DesktopSwitchRow(
            "Share my local folders",
            checked = status.isSharing,
            onCheckedChange = actions.onSharing,
            supporting = when {
                sharedFolderCount == 0 -> "No local folders yet: add some first."
                status.isSharing -> "Sharing $sharedFolderCount folder${if (sharedFolderCount == 1) "" else "s"} on this network" +
                    (status.homeAddresses.takeIf { it.isNotEmpty() }?.joinToString(prefix = " (", postfix = ")") ?: "")
                else -> "Off: nothing on this computer listens for other devices."
            }
        )
        DesktopGroupDivider()
        DesktopSwitchRow(
            "Allow streaming over a VPN (Tailscale)",
            checked = status.allowVpn,
            onCheckedChange = actions.onAllowVpn,
            supporting = when {
                !status.allowVpn -> "Off: only devices on this home network can connect, even when Tailscale is running."
                status.vpnAddresses.isEmpty() ->
                    "On, but Tailscale isn't running on this computer. Install it from tailscale.com and sign in on this " +
                        "computer and on the phone or TV."
                else -> "On. Away from home, turn on Tailscale on the phone or TV and enter this address as this PC's " +
                    "Tailscale address in ${SettingsLabels.path(SettingsLabels.MY_PCS)}:"
            }
        )
        if (status.allowVpn && status.vpnAddresses.isNotEmpty()) {
            SelectionContainer {
                Text(
                    status.vpnAddresses.joinToString("   "),
                    style = DesktopType.title,
                    fontFamily = FontFamily.Monospace,
                    color = DesktopColors.TextPrimary,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
                )
            }
        }
    }
    if (sharedFolderCount == 0) {
        DesktopButton(
            "Local folders",
            onClick = actions.onLocalFolders,
            kind = DcButtonKind.Secondary,
            icon = DesktopIcons.Folder,
            modifier = Modifier.padding(top = 12.dp)
        )
    }
    if (isWindows && status.isSharing) DesktopBanner(WINDOWS_FIREWALL_TIP, Modifier.padding(top = 12.dp))
    status.error?.let { DesktopBanner(it, Modifier.padding(top = 12.dp), kind = DcBannerKind.Error) }

    DesktopSectionHeader("Paired devices") {
        if (status.pairingPin == null) {
            DesktopButton("Pair a device", onClick = actions.onStartPairing, icon = DesktopIcons.Add, enabled = status.isSharing)
        }
    }
    status.pairingPin?.let { pin -> PairingCode(pin, status.homeAddresses.firstOrNull(), actions.onCancelPairing) }
    if (status.pairingPin == null && !status.isSharing) {
        Text("Turn sharing on to pair a device.", style = DesktopType.caption, color = DesktopColors.TextSecondary)
    }
    status.pairingMessage?.let { DesktopBanner(it, Modifier.padding(top = 10.dp)) }
    if (status.devices.isEmpty()) {
        Text("None yet.", style = DesktopType.body, color = DesktopColors.TextSecondary, modifier = Modifier.padding(top = 10.dp))
    } else {
        DesktopGroup(Modifier.padding(top = 10.dp)) {
            status.devices.forEachIndexed { index, device ->
                if (index > 0) DesktopGroupDivider()
                DesktopListRow(
                    device.name,
                    supporting = pairedDeviceLine(device.pairedAt, device.lastSeenAt),
                    icon = deviceKind(device.name).icon,
                    trailing = { DesktopButton("Remove", onClick = { removing = device }, kind = DcButtonKind.Destructive, small = true) }
                )
            }
        }
    }

    removing?.let { device ->
        DesktopConfirmDialog(
            title = "Remove ${device.name}?",
            text = "It can no longer play this computer's films. Pair it again to get them back.",
            confirmLabel = "Remove",
            onConfirm = {
                removing = null
                actions.onRemoveDevice(device.deviceId)
            },
            onDismiss = { removing = null }
        )
    }
}

/** While "Pair a device" is open: the code to type on the phone or TV, and where. */
@Composable
private fun PairingCode(pin: String, address: String?, onCancel: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(DesktopColors.Surface, RoundedCornerShape(DcTokens.Radius.CARD.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            "On the phone or TV: ${SettingsLabels.path(SettingsLabels.MY_PCS, SettingsLabels.ADD_A_PC)} → enter this code:",
            style = DesktopType.body,
            color = DesktopColors.TextSecondary
        )
        Text(pin, color = DesktopColors.TextPrimary, fontSize = 40.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, letterSpacing = 8.sp)
        address?.let {
            Text("If the device doesn't find this computer, type its address: $it", style = DesktopType.caption, color = DesktopColors.TextSecondary)
        }
        DesktopButton("Cancel", onClick = onCancel, kind = DcButtonKind.Secondary, small = true, modifier = Modifier.padding(top = 6.dp))
    }
}

/** The icon of a device kind ([deviceKind]). */
val DeviceKind.icon: ImageVector
    get() = when (this) {
        DeviceKind.PHONE -> DesktopIcons.Smartphone
        DeviceKind.TV -> DesktopIcons.Tv
        DeviceKind.PC -> DesktopIcons.DesktopWindows
    }
