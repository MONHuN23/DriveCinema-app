package com.drivecinema.app.ui.setup

import kotlinx.coroutines.awaitCancellation
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.drivecinema.app.core.pairing.TvPairingServer

@Composable
fun PhonePairingDialog(
    isPairing: Boolean,
    errorMessage: String? = null,
    onPairSubmit: (pin: String, host: String, port: Int) -> Unit,
    onDismissRequest: () -> Unit
) {
    var pinText by remember { mutableStateOf("") }
    var hostText by remember { mutableStateOf("") }
    var discoveredHost by remember { mutableStateOf<String?>(null) }
    var discoveredPort by remember { mutableStateOf(TvPairingServer.DEFAULT_PORT) }

    val context = LocalContext.current
    LaunchedEffect(Unit) {
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
                                val port = resolved.port
                                if (!resolvedIp.isNullOrEmpty()) {
                                    discoveredHost = resolvedIp
                                    discoveredPort = port
                                    if (hostText.isEmpty()) {
                                        hostText = resolvedIp
                                    }
                                }
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
        Box(
            modifier = Modifier
                .width(380.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF16181D))
                .border(1.dp, Color(0xFF2A2D35), RoundedCornerShape(16.dp))
                .padding(24.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Pair with Android TV",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Enter the 6-character code shown on your television:",
                    color = Color(0xFF9AA0A6),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                OutlinedTextField(
                    value = pinText,
                    onValueChange = { input ->
                        if (input.length <= 6) {
                            pinText = input.uppercase()
                        }
                    },
                    label = { Text("6-Character PIN (e.g. 8F3K9A)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF4D88FF),
                        unfocusedBorderColor = Color(0xFF333742),
                        focusedLabelColor = Color(0xFF4D88FF),
                        unfocusedLabelColor = Color(0xFF9AA0A6)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = hostText,
                    onValueChange = { hostText = it },
                    label = { Text("TV IP Address (e.g. 192.168.1.154)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF4D88FF),
                        unfocusedBorderColor = Color(0xFF333742),
                        focusedLabelColor = Color(0xFF4D88FF),
                        unfocusedLabelColor = Color(0xFF9AA0A6)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                if (discoveredHost != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0x224D88FF))
                            .border(1.dp, Color(0xFF4D88FF), RoundedCornerShape(6.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "✓ Found TV on Wi-Fi: $discoveredHost",
                            color = Color(0xFF90CAF9),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                if (!errorMessage.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = errorMessage,
                        color = Color(0xFFFFB4AB),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                if (isPairing) {
                    CircularProgressIndicator(
                        color = Color(0xFFE50914),
                        modifier = Modifier.height(36.dp)
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SetupButton(
                            text = "Cancel",
                            isPrimary = false,
                            onClick = onDismissRequest,
                            modifier = Modifier.weight(1f)
                        )

                        SetupButton(
                            text = "Pair TV",
                            isPrimary = true,
                            onClick = {
                                val target = hostText.trim().ifEmpty { discoveredHost ?: "" }
                                if (pinText.length == 6 && target.isNotEmpty()) {
                                    val parts = target.split(":")
                                    val targetHost = parts[0]
                                    val targetPort = if (parts.size > 1) {
                                        parts[1].toIntOrNull() ?: discoveredPort
                                    } else {
                                        discoveredPort
                                    }
                                    onPairSubmit(pinText, targetHost, targetPort)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}
