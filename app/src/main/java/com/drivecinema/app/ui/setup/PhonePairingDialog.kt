package com.drivecinema.app.ui.setup

import kotlinx.coroutines.awaitCancellation
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.drivecinema.app.core.pairing.TvPairingServer
import com.drivecinema.app.theme.DcColors
import com.drivecinema.app.theme.DcTheme
import com.drivecinema.app.ui.design.DcBanner
import com.drivecinema.app.ui.design.DcBannerKind
import com.drivecinema.app.ui.design.DcButton
import com.drivecinema.app.ui.design.DcButtonKind
import com.drivecinema.app.ui.design.DcStatusChip
import com.drivecinema.app.ui.design.DcStatusKind
import com.drivecinema.app.ui.design.DcTextField
import com.drivecinema.app.ui.design.DcTokens
import com.drivecinema.app.ui.settings.PAIRING_CODE_LENGTH
import com.drivecinema.app.ui.settings.pairingCodeInput

/** Why Pair TV can't run yet, said under the disabled button (it used to do nothing, silently); null when it can. */
fun tvPairBlockedReason(address: String, code: String): String? = when {
    code.length < PAIRING_CODE_LENGTH -> "Enter all $PAIRING_CODE_LENGTH characters of the code"
    address.isBlank() -> "Type the TV's address, or wait until it's found on Wi-Fi"
    else -> null
}

/** What the TV's address and port are when Pair TV runs: "host" or "host:port", else the port Wi-Fi discovery found. */
data class TvPairTarget(val host: String, val port: Int)

/**
 * The phone pairing form (Task P.7): the code (letters and digits in capitals, as in My PCs' "Add a PC"), the TV's
 * address, and the TV Wi-Fi discovery found, whose address fills the field unless one was typed.
 */
class PhonePairingForm {
    var code by mutableStateOf("")
        private set
    var address by mutableStateOf("")
    var foundHost by mutableStateOf<String?>(null)
        private set
    private var foundPort by mutableIntStateOf(TvPairingServer.DEFAULT_PORT)

    fun typeCode(raw: String) {
        code = pairingCodeInput(raw)
    }

    fun onTvFound(host: String, port: Int) {
        foundHost = host
        foundPort = port
        if (address.isEmpty()) address = host
    }

    private val targetText: String get() = address.trim().ifEmpty { foundHost.orEmpty() }

    val blockedReason: String? get() = tvPairBlockedReason(targetText, code)

    /** Where to pair, or null while [blockedReason] says what's missing. */
    fun target(): TvPairTarget? {
        if (blockedReason != null) return null
        val parts = targetText.split(":")
        return TvPairTarget(parts[0], parts.getOrNull(1)?.toIntOrNull() ?: foundPort)
    }
}

/**
 * The phone's "Pair with Android TV" dialog (Home's Pair TV, Settings → Pair a TV). Task P.7 moved it to Milestone
 * P's design: [DcTextField]s, the found TV as a status chip, and Pair TV disabled with the reason until it can run.
 */
@Composable
fun PhonePairingDialog(
    isPairing: Boolean,
    errorMessage: String? = null,
    onPairSubmit: (pin: String, host: String, port: Int) -> Unit,
    onDismissRequest: () -> Unit,
    discoverTvs: Boolean = true
) {
    val form = remember { PhonePairingForm() }

    val context = LocalContext.current
    LaunchedEffect(Unit) {
        if (!discoverTvs) return@LaunchedEffect
        val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return@LaunchedEffect
        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {}
            override fun onServiceFound(service: NsdServiceInfo) {
                if (service.serviceType.contains("_drivecinema._tcp")) {
                    try {
                        nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                            override fun onServiceResolved(resolved: NsdServiceInfo) {
                                val resolvedIp = resolved.host?.hostAddress
                                if (!resolvedIp.isNullOrEmpty()) form.onTvFound(resolvedIp, resolved.port)
                            }
                        })
                    } catch (_: Exception) {}
                }
            }
            override fun onServiceLost(service: NsdServiceInfo) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }
        try {
            nsdManager.discoverServices(TvPairingServer.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (_: Exception) {
            return@LaunchedEffect
        }
        // Discovery keeps the Wi-Fi radio busy: stop it when the dialog closes (it used to run until the app was killed).
        try {
            awaitCancellation()
        } finally {
            try {
                nsdManager.stopServiceDiscovery(discoveryListener)
            } catch (_: Exception) {}
        }
    }

    Dialog(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .background(DcColors.Surface1, RoundedCornerShape(DcTokens.Radius.SHEET.dp))
                .padding(24.dp)
                .testTag("phone_pairing_dialog"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Pair with Android TV", style = DcTheme.type.title, color = DcColors.Text1)
            Text("Enter the 6-character code shown on your television:", style = DcTheme.type.body, color = DcColors.Text2)

            DcTextField(
                form.code,
                onValueChange = form::typeCode,
                label = "Code shown on the TV",
                keyboardType = KeyboardType.Ascii,
                capitalization = KeyboardCapitalization.Characters,
                textStyle = DcTheme.type.title.copy(color = DcColors.Text1, letterSpacing = 4.sp),
                supporting = "${form.code.length} of $PAIRING_CODE_LENGTH",
                modifier = Modifier.testTag("phone_pairing_code")
            )
            DcTextField(
                form.address,
                onValueChange = { form.address = it },
                label = "TV IP address (e.g. 192.168.1.154)",
                keyboardType = KeyboardType.Uri,
                modifier = Modifier.testTag("phone_pairing_address")
            )
            form.foundHost?.let {
                DcStatusChip("Found TV on Wi-Fi: $it", kind = DcStatusKind.Success, modifier = Modifier.testTag("phone_pairing_found"))
            }

            if (!errorMessage.isNullOrEmpty()) {
                DcBanner(errorMessage, kind = DcBannerKind.Error, modifier = Modifier.testTag("phone_pairing_error"))
            }

            val blocked = form.blockedReason
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DcButton(
                    "Cancel",
                    onClick = onDismissRequest,
                    kind = DcButtonKind.Secondary,
                    enabled = !isPairing,
                    modifier = Modifier.weight(1f).testTag("phone_pairing_cancel")
                )
                DcButton(
                    if (isPairing) "Pairing…" else "Pair TV",
                    onClick = { form.target()?.let { onPairSubmit(form.code, it.host, it.port) } },
                    enabled = blocked == null,
                    loading = isPairing,
                    modifier = Modifier.weight(1f).testTag("phone_pairing_pair")
                )
            }
            blocked?.let {
                Text(it, style = DcTheme.type.caption, color = DcColors.Text2, modifier = Modifier.testTag("phone_pairing_reason"))
            }
        }
    }
}
