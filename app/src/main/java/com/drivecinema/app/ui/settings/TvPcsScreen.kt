package com.drivecinema.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.drivecinema.app.theme.DcColors
import com.drivecinema.app.theme.DcTheme
import com.drivecinema.app.ui.design.DcFocusRoom
import com.drivecinema.app.ui.design.DcFocusRoomScrolling
import com.drivecinema.app.ui.design.DcBanner
import com.drivecinema.app.ui.design.DcListRow
import com.drivecinema.app.ui.design.DcTokens
import com.drivecinema.app.ui.icons.AppIcons
import com.drivecinema.app.ui.setup.SettingsLabels

/** The left pane's "Add a PC" item (PCs are keyed by their host id). */
const val ADD_PC_KEY = "add"

/** The TV's note under a PC's details. */
const val TV_TAILSCALE_NOTE =
    "Away from home, this TV reaches the PC through Tailscale: install it on the TV and on the PC, signed in to the " +
        "same account, and set the PC's Tailscale address here."

/**
 * Task P.3: the TV's My PCs, two panes (decision D-P4): the PCs and "Add a PC" on the left; the selected PC's details
 * or the add steps on the right, one focus stop per control in step order. The selection follows the focus; Right or
 * OK enters the pane, Left or Back returns to the list; Back from the list returns to Settings.
 */
@Composable
fun TvPcsScreen(state: PcsScreenState, actions: PcsActions, modifier: Modifier = Modifier) {
    val form = rememberPairPcForm(state.discovered, state.pairedCount)
    var selectedKey by rememberSaveable { mutableStateOf(state.rows.firstOrNull()?.hostId ?: ADD_PC_KEY) }
    // A PC removed here or by the PC itself hands the selection to the first PC left, else to "Add a PC".
    val selected = selectedKey.takeIf { key -> key == ADD_PC_KEY || state.rows.any { it.hostId == key } }
        ?: state.rows.firstOrNull()?.hostId ?: ADD_PC_KEY
    val itemFocus = remember { mutableMapOf<String, FocusRequester>() }
    val focusOf = { key: String -> itemFocus.getOrPut(key) { FocusRequester() } }
    val firstRight = remember { FocusRequester() }
    var rightHasFocus by remember { mutableStateOf(false) }
    var removing by rememberSaveable { mutableStateOf<String?>(null) }
    // The list item to focus next: the selected one at the start, the next one after a Remove.
    var focusItem by remember { mutableStateOf<String?>(selected) }

    LaunchedEffect(focusItem) {
        val key = focusItem ?: return@LaunchedEffect
        for (attempt in 1..5) {
            if (runCatching { focusOf(key).requestFocus() }.getOrDefault(false)) break
            withFrameNanos { }
        }
        focusItem = null
    }
    BackHandler(enabled = rightHasFocus) { runCatching { focusOf(selected).requestFocus() } }

    DcFocusRoomScrolling {
        Row(modifier = modifier.fillMaxSize().background(DcColors.Canvas)) {
            Column(
                modifier = Modifier
                    .width(360.dp)
                    .fillMaxHeight()
                    .background(DcColors.Surface1)
                    .padding(start = 48.dp, end = 20.dp, top = 34.dp, bottom = 27.dp)
            ) {
                Text(SettingsLabels.MY_PCS, style = DcTheme.type.headline, color = DcColors.Text1)
                Text("PCs this TV plays videos from", style = DcTheme.type.caption, color = DcColors.Text2)
                Column(
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .testTag("tv_pcs_list")
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = DcFocusRoom),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    state.rows.forEach { row ->
                        key(row.hostId) {
                            PaneItem(
                                row.name,
                                supporting = row.connection.label,
                                icon = AppIcons.DesktopWindows,
                                isSelected = row.hostId == selected,
                                focus = focusOf(row.hostId),
                                firstRight = firstRight,
                                onFocused = { selectedKey = row.hostId },
                                modifier = Modifier.testTag("tv_pcs_item_${row.hostId}")
                            )
                        }
                    }
                    PaneItem(
                        SettingsLabels.ADD_A_PC,
                        supporting = "Pair this TV with a PC",
                        icon = AppIcons.Add,
                        isSelected = selected == ADD_PC_KEY,
                        focus = focusOf(ADD_PC_KEY),
                        firstRight = firstRight,
                        onFocused = { selectedKey = ADD_PC_KEY },
                        modifier = Modifier.testTag("tv_pcs_item_$ADD_PC_KEY")
                    )
                }
            }

            val first = Modifier.focusRequester(firstRight).focusProperties { up = FocusRequester.Cancel }
            val leftEdge = Modifier.focusProperties { left = focusOf(selected) }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .onFocusChanged { rightHasFocus = it.hasFocus }
                    .focusGroup()
                    .testTag("tv_pcs_pane")
                    .verticalScroll(rememberScrollState())
                    .padding(start = 40.dp, end = 48.dp, top = 38.dp, bottom = 27.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                state.notice?.let { DcBanner(it.text, kind = it.kind, modifier = Modifier.widthIn(max = 720.dp).testTag("pcs_notice")) }
                val pc = state.rows.find { it.hostId == selected }
                if (pc != null) {
                    key(pc.hostId) {
                        Text(pc.name, style = DcTheme.type.headline, color = DcColors.Text1)
                        PcCard(
                            pc,
                            onSetVpnAddress = { actions.setVpnAddress(pc.hostId, it) },
                            onRemove = { removing = pc.hostId },
                            onCheckNow = actions.checkNow,
                            isChecking = state.isChecking,
                            firstControl = first,
                            leftEdge = leftEdge,
                            modifier = Modifier.widthIn(max = 720.dp)
                        )
                        Text(TV_TAILSCALE_NOTE, style = DcTheme.type.caption, color = DcColors.Text3, modifier = Modifier.widthIn(max = 720.dp))
                    }
                } else {
                    Text(SettingsLabels.ADD_A_PC, style = DcTheme.type.headline, color = DcColors.Text1)
                    AddPcSteps(
                        form,
                        state,
                        onSearch = actions.search,
                        onPair = actions.pair,
                        firstControl = first,
                        leftEdge = leftEdge,
                        modifier = Modifier.widthIn(max = 720.dp)
                    )
                }
            }
        }
    }

    RemovePcDialog(state.rows.find { it.hostId == removing }, onRemove = { hostId ->
        removing = null
        val next = state.rows.firstOrNull { it.hostId != hostId }?.hostId ?: ADD_PC_KEY
        selectedKey = next
        focusItem = next
        actions.remove(hostId)
    }, onDismiss = { removing = null })
}

/** One item of the left pane; the selection is drawn outside the row so the row's focus look stays the same node. */
@Composable
private fun PaneItem(
    title: String,
    supporting: String,
    icon: ImageVector,
    isSelected: Boolean,
    focus: FocusRequester,
    firstRight: FocusRequester,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier
) {
    DcListRow(
        title,
        supporting = supporting,
        icon = icon,
        onClick = { runCatching { firstRight.requestFocus() } },
        modifier = modifier
            .background(if (isSelected) DcColors.Surface3 else Color.Transparent, RoundedCornerShape(DcTokens.Radius.CONTROL.dp))
            .focusRequester(focus)
            .onFocusChanged { if (it.isFocused) onFocused() }
            .focusProperties { right = firstRight }
    )
}
