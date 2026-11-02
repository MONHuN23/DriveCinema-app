package com.drivecinema.app.ui.settings

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableStateOf
import com.drivecinema.app.core.host.DiscoveredHost
import com.drivecinema.app.ui.design.DcBannerKind
import com.drivecinema.app.ui.design.DcStatusKind

/** What the last check found for a paired PC (Task I.2; structured since Task P.3, it was a sentence before). */
sealed interface PcConnection {
    data object NotChecked : PcConnection
    data class AtHome(val videos: Int) : PcConnection
    data class OverVpn(val videos: Int) : PcConnection
    data object Unreachable : PcConnection
}

/** The status chip's words. */
val PcConnection.label: String
    get() = when (this) {
        PcConnection.NotChecked -> "Not checked yet"
        is PcConnection.AtHome -> "Connected at home"
        is PcConnection.OverVpn -> "Connected through the VPN"
        PcConnection.Unreachable -> "Not reachable"
    }

/** The line under the chip: how many videos, or why the PC can't be reached. */
val PcConnection.detail: String?
    get() = when (this) {
        PcConnection.NotChecked -> null
        is PcConnection.AtHome -> videoCount(videos)
        is PcConnection.OverVpn -> videoCount(videos)
        PcConnection.Unreachable -> "Off, or away from home without the VPN"
    }

val PcConnection.statusKind: DcStatusKind
    get() = when (this) {
        PcConnection.NotChecked -> DcStatusKind.Neutral
        is PcConnection.AtHome, is PcConnection.OverVpn -> DcStatusKind.Success
        PcConnection.Unreachable -> DcStatusKind.Warning
    }

private fun videoCount(n: Int) = if (n == 1) "1 video" else "$n videos"

/** A paired PC as My PCs shows it. */
@Immutable
data class PcRow(
    val hostId: String,
    val name: String,
    val homeAddress: String?,
    val vpnAddress: String?,
    val connection: PcConnection = PcConnection.NotChecked
)

/** A note at the top of My PCs: "Paired with …", "No PC found…", a pairing error. */
@Immutable
data class PcsNotice(val text: String, val kind: DcBannerKind = DcBannerKind.Info)

/** Task P.3: the My PCs screen's state (was `PcsDialog`'s, kept in `MainActivity`); the activity's PC flows write it. */
class PcsUiState {
    val rows = mutableStateOf<List<PcRow>>(emptyList())
    val discovered = mutableStateOf<List<DiscoveredHost>>(emptyList())
    val isSearching = mutableStateOf(false)
    val isPairing = mutableStateOf(false)
    /** "Check now" (or a check after pairing) is reading the PCs. */
    val isChecking = mutableStateOf(false)
    val notice = mutableStateOf<PcsNotice?>(null)
    /** Goes up after each successful pairing; the screen then empties its form (the code is used up). */
    val pairedCount = mutableStateOf(0)
}

/** What My PCs shows at one moment, built from [PcsUiState]. */
data class PcsScreenState(
    val rows: List<PcRow> = emptyList(),
    val discovered: List<DiscoveredHost> = emptyList(),
    val isSearching: Boolean = false,
    val isPairing: Boolean = false,
    val isChecking: Boolean = false,
    val notice: PcsNotice? = null,
    val pairedCount: Int = 0
)

/** What My PCs asks of the activity; pairing, the VPN address and removal work as before (`HostLibrarySync`). */
class PcsActions(
    val search: () -> Unit,
    val pair: (address: String, code: String, vpnAddress: String) -> Unit,
    val setVpnAddress: (hostId: String, address: String) -> Unit,
    /** Forgets the PC on this device; the screen asks first (decision D-P2). */
    val remove: (hostId: String) -> Unit,
    val checkNow: () -> Unit,
    /** My PCs opened: clear the last screen's note and show the PCs as last checked. */
    val onOpen: () -> Unit,
    val back: () -> Unit
)

/** The address to fill in after a search: the first PC found, unless the user already typed one. */
fun firstFoundAddress(current: String, discovered: List<DiscoveredHost>): String? =
    discovered.firstOrNull()?.address?.takeIf { current.isBlank() }

/** The pairing code as typed: letters and digits only, capitals, at most 6 (the PC shows 6). */
fun pairingCodeInput(raw: String): String = raw.filter { it.isLetterOrDigit() }.uppercase().take(PAIRING_CODE_LENGTH)

const val PAIRING_CODE_LENGTH = 6

/** Why Pair can't run yet, said next to the disabled button (it used to do nothing, silently); null when it can. */
fun pairBlockedReason(address: String, code: String): String? = when {
    address.isBlank() -> "Find the PC, or type its address"
    code.length < PAIRING_CODE_LENGTH -> "Enter all $PAIRING_CODE_LENGTH characters of the code"
    else -> null
}
