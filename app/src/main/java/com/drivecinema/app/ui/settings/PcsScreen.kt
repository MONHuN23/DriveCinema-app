package com.drivecinema.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivecinema.app.core.host.DiscoveredHost
import com.drivecinema.app.theme.DcColors
import com.drivecinema.app.theme.DcTheme
import com.drivecinema.app.ui.design.DcBanner
import com.drivecinema.app.ui.design.DcButton
import com.drivecinema.app.ui.design.DcButtonKind
import com.drivecinema.app.ui.design.DcConfirmDialog
import com.drivecinema.app.ui.design.DcGroup
import com.drivecinema.app.ui.design.DcGroupDivider
import com.drivecinema.app.ui.design.DcGroupTitle
import com.drivecinema.app.ui.design.DcListRow
import com.drivecinema.app.ui.design.DcStatusChip
import com.drivecinema.app.ui.design.DcStep
import com.drivecinema.app.ui.design.DcTextField
import com.drivecinema.app.ui.design.DcTokens
import com.drivecinema.app.ui.design.DcTopBar
import com.drivecinema.app.ui.icons.AppIcons
import com.drivecinema.app.ui.setup.SettingsLabels

/** What My PCs says first (was the popup's intro). */
const val PCS_INTRO =
    "Play videos stored on a PC running DriveCinema, without uploading them. At home it works on the Wi-Fi; away " +
        "from home, through Tailscale (a free VPN)."

/** Step 1 of "Add a PC": what to do on the PC, in the desktop app's own words. */
const val PAIR_ON_THE_PC =
    "In DriveCinema on the PC: account menu → Share with my devices → turn on Share my local folders → Pair a " +
        "device. It shows a 6-character code."

/** What Remove asks (decision D-P2): what this device loses, and that Drive copies are not touched. */
const val REMOVE_PC_TEXT =
    "This device forgets the PC: its videos stop playing from it here (copies on Drive still play). Pair again to " +
        "get them back."

/** The "Add a PC" form as typed, kept across rotation. */
@Stable
internal class PairPcForm(address: String = "", code: String = "", vpn: String = "") {
    var address by mutableStateOf(address)
    var code by mutableStateOf(code)
    var vpn by mutableStateOf(vpn)

    fun clear() {
        address = ""
        code = ""
        vpn = ""
    }

    companion object {
        val Saver: Saver<PairPcForm, Any> = listSaver(
            save = { listOf(it.address, it.code, it.vpn) },
            restore = { PairPcForm(it[0], it[1], it[2]) }
        )
    }
}

/**
 * The "Add a PC" form. A search fills in the first PC found unless an address was typed ([firstFoundAddress]); after
 * a pairing it empties, since the code is used up.
 */
@Composable
internal fun rememberPairPcForm(discovered: List<DiscoveredHost>, pairedCount: Int): PairPcForm {
    val form = rememberSaveable(saver = PairPcForm.Saver) { PairPcForm() }
    LaunchedEffect(discovered) {
        firstFoundAddress(form.address, discovered)?.let { form.address = it }
    }
    var pairingsSeen by rememberSaveable { mutableIntStateOf(pairedCount) }
    LaunchedEffect(pairedCount) {
        if (pairedCount != pairingsSeen) {
            pairingsSeen = pairedCount
            form.clear()
        }
    }
    return form
}

/**
 * Task P.3: the phone's My PCs (was the `PcsDialog` popup): the paired PCs as cards with their state, addresses,
 * Tailscale address and Remove (asks first, decision D-P2), then "Add a PC" in three numbered steps. Pairing, the
 * VPN address and removal work as before.
 */
@Composable
fun PcsScreen(state: PcsScreenState, actions: PcsActions, modifier: Modifier = Modifier) {
    val form = rememberPairPcForm(state.discovered, state.pairedCount)
    var removing by rememberSaveable { mutableStateOf<String?>(null) }
    Column(modifier = modifier.fillMaxSize().background(DcColors.Canvas)) {
        DcTopBar(SettingsLabels.MY_PCS, onBack = actions.back)
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("pcs_list"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp)
        ) {
            item(key = "intro", contentType = "text") {
                Text(PCS_INTRO, style = DcTheme.type.body, color = DcColors.Text2)
            }
            state.notice?.let { notice ->
                item(key = "notice", contentType = "notice") {
                    DcBanner(notice.text, kind = notice.kind, modifier = Modifier.padding(top = 14.dp).testTag("pcs_notice"))
                }
            }
            if (state.rows.isNotEmpty()) {
                item(key = "yourPcs", contentType = "title") {
                    Row(Modifier.fillMaxWidth()) {
                        DcGroupTitle("Your PCs", Modifier.weight(1f).alignByBaseline())
                        DcButton(
                            SettingsLabels.CHECK_NOW,
                            onClick = actions.checkNow,
                            kind = DcButtonKind.Tertiary,
                            icon = AppIcons.Refresh,
                            loading = state.isChecking,
                            small = true,
                            modifier = Modifier.alignByBaseline().testTag("pcs_check_now")
                        )
                    }
                }
                items(state.rows, key = { it.hostId }, contentType = { "pc" }) { row ->
                    PcCard(
                        row,
                        onSetVpnAddress = { actions.setVpnAddress(row.hostId, it) },
                        onRemove = { removing = row.hostId },
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
            }
            item(key = "addTitle", contentType = "title") { DcGroupTitle(SettingsLabels.ADD_A_PC) }
            item(key = "add", contentType = "add") {
                AddPcSteps(form, state, onSearch = actions.search, onPair = actions.pair)
            }
        }
    }
    RemovePcDialog(state.rows.find { it.hostId == removing }, onRemove = { hostId ->
        removing = null
        actions.remove(hostId)
    }, onDismiss = { removing = null })
}

/** Remove asks first (decision D-P2); nothing shows while [pc] is null. */
@Composable
internal fun RemovePcDialog(pc: PcRow?, onRemove: (hostId: String) -> Unit, onDismiss: () -> Unit) {
    if (pc == null) return
    DcConfirmDialog(
        title = "Remove ${pc.name}?",
        text = REMOVE_PC_TEXT,
        confirmLabel = "Remove",
        onConfirm = { onRemove(pc.hostId) },
        onDismiss = onDismiss
    )
}

/**
 * One paired PC: name, status, addresses, its Tailscale address (set, change or clear) and Remove. On the TV,
 * [onCheckNow] adds Check now to the buttons, [firstControl] marks the control Right from the list lands on, and
 * [leftEdge] goes on the controls Left leaves the pane from.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PcCard(
    row: PcRow,
    onSetVpnAddress: (String) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    onCheckNow: (() -> Unit)? = null,
    isChecking: Boolean = false,
    firstControl: Modifier = Modifier,
    leftEdge: Modifier = Modifier
) {
    var editing by rememberSaveable(row.hostId) { mutableStateOf(false) }
    var draft by rememberSaveable(row.hostId) { mutableStateOf("") }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(DcColors.Surface1, RoundedCornerShape(DcTokens.Radius.CARD.dp))
            .padding(16.dp)
            .testTag("pc_card_${row.hostId}"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier.size(44.dp).background(DcColors.Surface3, RoundedCornerShape(DcTokens.Radius.CONTROL.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(AppIcons.DesktopWindows, contentDescription = null, tint = DcColors.Text1, modifier = Modifier.size(24.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(row.name, style = DcTheme.type.rowTitle, color = DcColors.Text1, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DcStatusChip(row.connection.label, kind = row.connection.statusKind)
                    row.connection.detail?.let { Text(it, style = DcTheme.type.caption, color = DcColors.Text2) }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            row.homeAddress?.let { AddressLine("Home", it) }
            AddressLine("Tailscale", row.vpnAddress ?: "Not set; needed away from home")
        }
        if (editing) {
            DcTextField(
                draft,
                onValueChange = { draft = it.trim() },
                label = SettingsLabels.TAILSCALE_ADDRESS,
                keyboardType = KeyboardType.Uri,
                supporting = "The address the PC shows under Allow streaming over a VPN, e.g. 100.101.102.103. " +
                    "Leave it empty to clear it.",
                modifier = firstControl.then(leftEdge).testTag("pc_vpn_field")
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DcButton(
                    "Save",
                    onClick = {
                        editing = false
                        onSetVpnAddress(draft)
                    },
                    small = true,
                    modifier = leftEdge.testTag("pc_vpn_save")
                )
                DcButton("Cancel", onClick = { editing = false }, kind = DcButtonKind.Tertiary, small = true)
            }
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DcButton(
                    if (row.vpnAddress == null) SettingsLabels.SET_TAILSCALE_ADDRESS else "Change Tailscale address",
                    onClick = {
                        draft = row.vpnAddress.orEmpty()
                        editing = true
                    },
                    kind = DcButtonKind.Secondary,
                    icon = AppIcons.VpnKey,
                    small = true,
                    modifier = firstControl.then(leftEdge).testTag("pc_vpn_edit")
                )
                onCheckNow?.let {
                    DcButton(
                        SettingsLabels.CHECK_NOW,
                        onClick = it,
                        kind = DcButtonKind.Secondary,
                        icon = AppIcons.Refresh,
                        loading = isChecking,
                        small = true,
                        modifier = Modifier.testTag("pc_check_now")
                    )
                }
                DcButton(
                    "Remove",
                    onClick = onRemove,
                    kind = DcButtonKind.Destructive,
                    icon = AppIcons.DeleteOutline,
                    small = true,
                    modifier = Modifier.testTag("pc_remove")
                )
            }
        }
    }
}

@Composable
private fun AddressLine(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, style = DcTheme.type.caption, color = DcColors.Text3, modifier = Modifier.widthIn(min = 80.dp))
        Text(value, style = DcTheme.type.caption, color = DcColors.Text2)
    }
}

/**
 * "Add a PC" in three numbered steps (Task P.3): what to do on the PC, find it, enter its code. Pair is enabled only
 * with an address and all 6 characters and says why otherwise ([pairBlockedReason]); it used to do nothing, silently.
 * The Tailscale address is folded under "Away from home (optional)". On the TV, [firstControl] and [leftEdge] work as
 * on [PcCard].
 */
@Composable
internal fun AddPcSteps(
    form: PairPcForm,
    state: PcsScreenState,
    onSearch: () -> Unit,
    onPair: (address: String, code: String, vpnAddress: String) -> Unit,
    modifier: Modifier = Modifier,
    firstControl: Modifier = Modifier,
    leftEdge: Modifier = Modifier
) {
    val tv = DcTheme.formFactor.isTv
    var awayOpen by rememberSaveable { mutableStateOf(form.vpn.isNotEmpty()) }
    val blocked = pairBlockedReason(form.address, form.code)
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        DcStep(1, "On the PC, open Pair a device") {
            Text(PAIR_ON_THE_PC, style = DcTheme.type.body, color = DcColors.Text2)
        }
        DcStep(2, "Find the PC") {
            DcButton(
                if (state.isSearching) "Searching this network…" else SettingsLabels.SEARCH_THIS_NETWORK,
                onClick = onSearch,
                kind = DcButtonKind.Secondary,
                icon = AppIcons.Wifi,
                loading = state.isSearching,
                modifier = firstControl.then(leftEdge).testTag("pcs_search")
            )
            if (state.discovered.isNotEmpty()) {
                DcGroup {
                    state.discovered.forEachIndexed { index, found ->
                        if (index > 0) DcGroupDivider()
                        DcListRow(
                            found.name,
                            supporting = found.address,
                            icon = AppIcons.DesktopWindows,
                            onClick = { form.address = found.address },
                            modifier = leftEdge.testTag("pcs_found_${found.address}"),
                            trailing = if (form.address == found.address) {
                                { Icon(AppIcons.Check, contentDescription = "Chosen", tint = DcColors.Success) }
                            } else null
                        )
                    }
                }
            }
            DcTextField(
                form.address,
                onValueChange = { form.address = it.trim() },
                label = "PC address",
                keyboardType = KeyboardType.Uri,
                supporting = "Filled in by the search. If it finds nothing, type the address the PC shows under Pair a device.",
                modifier = leftEdge.testTag("pcs_address")
            )
        }
        DcStep(3, "Enter the code") {
            DcTextField(
                form.code,
                onValueChange = { form.code = pairingCodeInput(it) },
                label = "Code shown on the PC",
                keyboardType = KeyboardType.Ascii,
                capitalization = KeyboardCapitalization.Characters,
                textStyle = DcTheme.type.title.copy(color = DcColors.Text1, letterSpacing = 4.sp),
                supporting = "${form.code.length} of $PAIRING_CODE_LENGTH",
                modifier = leftEdge.testTag("pcs_code")
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DcListRow(
                SettingsLabels.AWAY_FROM_HOME,
                supporting = "The PC's Tailscale address, to watch away from home",
                icon = AppIcons.VpnKey,
                onClick = { awayOpen = !awayOpen },
                container = DcColors.Surface1,
                modifier = leftEdge.testTag("pcs_away"),
                trailing = {
                    Icon(
                        AppIcons.ExpandMore,
                        contentDescription = if (awayOpen) "Fold" else "Unfold",
                        tint = DcColors.Text2,
                        modifier = Modifier.rotate(if (awayOpen) 180f else 0f)
                    )
                }
            )
            if (awayOpen) {
                Text(
                    "With Tailscale on this device and on the PC, signed in to the same account: on the PC turn on Allow " +
                        "streaming over a VPN and type the address it shows. You can also add it later on the PC's card.",
                    style = DcTheme.type.caption,
                    color = DcColors.Text2
                )
                DcTextField(
                    form.vpn,
                    onValueChange = { form.vpn = it.trim() },
                    label = SettingsLabels.TAILSCALE_ADDRESS,
                    keyboardType = KeyboardType.Uri,
                    modifier = leftEdge.testTag("pcs_vpn")
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DcButton(
                SettingsLabels.PAIR,
                onClick = { onPair(form.address, form.code, form.vpn) },
                enabled = blocked == null,
                loading = state.isPairing,
                modifier = leftEdge.then(if (tv) Modifier else Modifier.fillMaxWidth()).testTag("pcs_pair")
            )
            blocked?.let {
                Text(it, style = DcTheme.type.caption, color = DcColors.Text2, modifier = Modifier.testTag("pcs_pair_reason"))
            }
        }
    }
}
