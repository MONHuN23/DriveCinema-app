package com.drivecinema.desktop.host

import com.drivecinema.app.core.host.HostAddressPolicy
import com.drivecinema.app.core.host.HostDiscoveryResponder
import com.drivecinema.app.core.host.HostPairing
import com.drivecinema.app.core.host.HostPairingListener
import com.drivecinema.app.core.host.HostPairingSession
import com.drivecinema.app.core.host.HostProtocol
import com.drivecinema.app.core.logging.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.net.Inet4Address
import java.net.InetAddress
import java.nio.file.Path
import java.security.SecureRandom
import kotlin.concurrent.thread

/** Where the sharing switches are kept (desktop: settings.json). */
interface HostSettings {
    fun sharingEnabled(): Boolean
    fun setSharingEnabled(enabled: Boolean)
    fun allowVpn(): Boolean
    fun setAllowVpn(allowed: Boolean)
}

data class HostStatus(
    val isSharing: Boolean = false,
    val allowVpn: Boolean = false,
    val port: Int? = null,
    /** Home-network addresses of this PC. */
    val homeAddresses: List<String> = emptyList(),
    /** Tailscale addresses of this PC (to enter on the phone for streaming away from home). */
    val vpnAddresses: List<String> = emptyList(),
    /** Shown while "Pair a device" is open. */
    val pairingPin: String? = null,
    val pairingPort: Int? = null,
    val pairingMessage: String? = null,
    val devices: List<PairedDevice> = emptyList(),
    val error: String? = null
)

/**
 * Task I.2: shares this computer's local library folders with paired DriveCinema devices. Nothing listens unless
 * sharing is on; the pairing port is open only while a PIN is shown. Files are only read.
 */
class MediaHost(
    dataDir: File,
    private val settings: HostSettings,
    private val sharedFolders: () -> List<Path>,
    private val computerName: () -> String = { runCatching { InetAddress.getLocalHost().hostName }.getOrDefault("DriveCinema PC") },
    private val httpsPort: Int = HostProtocol.DEFAULT_HTTPS_PORT,
    private val pairingPort: Int = HostProtocol.PAIRING_PORT,
    private val discoveryPort: Int = HostProtocol.DISCOVERY_PORT,
    private val localAddresses: () -> List<InetAddress> = HostAddressPolicy::localAddresses
) : AutoCloseable {
    private val identityStore = HostIdentityStore(dataDir)
    private val registry = HostDeviceRegistry(File(dataDir, "host-devices.json"))
    private val _status = MutableStateFlow(HostStatus(allowVpn = settings.allowVpn(), devices = registry.devices()))
    val status: StateFlow<HostStatus> = _status.asStateFlow()

    private var server: MediaHostServer? = null
    private var discovery: HostDiscoveryResponder? = null
    private var pairing: HostPairingListener? = null

    /** This computer's host ID (so it never pairs with itself as a client). */
    fun hostId(): String = identityStore.info().hostId

    /** At app start: resumes sharing if it was on. */
    fun start() {
        if (settings.sharingEnabled()) startSharing()
    }

    @Synchronized
    fun setSharing(enabled: Boolean) {
        settings.setSharingEnabled(enabled)
        if (enabled) startSharing() else stopSharing()
    }

    /** VPN addresses are checked on every request, so this takes effect at once. */
    fun setAllowVpn(allowed: Boolean) {
        settings.setAllowVpn(allowed)
        _status.update { it.copy(allowVpn = allowed) }
    }

    /** Opens pairing with a new PIN (as on the TV: 3 wrong PINs close it). Returns the PIN to show. */
    @Synchronized
    fun startPairing(): String? {
        if (server == null) return null
        cancelPairing()
        val pin = newPin()
        val info = identityStore.info()
        val fingerprint = identityStore.identity().fingerprint
        val listener = try {
            HostPairingListener(
                pin = pin,
                allowVpn = settings.allowVpn(),
                issue = { _ ->
                    val (deviceId, key) = HostPairing.newDeviceCredentials()
                    HostPairing(info.hostId, computerName(), fingerprint, server?.port ?: httpsPort, deviceId, key)
                },
                commit = { paired, name ->
                    registry.add(paired, name)
                    _status.update { it.copy(devices = registry.devices()) }
                },
                port = pairingPort
            )
        } catch (e: Exception) {
            _status.update { it.copy(pairingMessage = "Pairing can't start: port $pairingPort is in use.") }
            return null
        }
        pairing = listener
        _status.update { it.copy(pairingPin = pin, pairingPort = listener.port, pairingMessage = null) }
        thread(isDaemon = true, name = "host-pairing") {
            val result = listener.run()
            synchronized(this) {
                if (pairing === listener) pairing = null
            }
            _status.update {
                if (it.pairingPin != pin) it else it.copy(
                    pairingPin = null,
                    pairingPort = null,
                    pairingMessage = when (result) {
                        HostPairingSession.Result.PAIRED -> "Paired."
                        HostPairingSession.Result.WRONG_PIN -> "Pairing stopped after 3 wrong codes. Start again for a new code."
                        HostPairingSession.Result.REJECTED -> null
                    }
                )
            }
        }
        return pin
    }

    @Synchronized
    fun cancelPairing() {
        pairing?.close()
        pairing = null
        _status.update { it.copy(pairingPin = null, pairingPort = null) }
    }

    /** The device can't connect any more (its key is forgotten at once). */
    fun removeDevice(deviceId: String) {
        registry.remove(deviceId)
        _status.update { it.copy(devices = registry.devices()) }
    }

    private fun startSharing() {
        if (server != null) return
        try {
            val info = identityStore.info()
            val index = HostFileIndex(sharedFolders, info.fileIdSecret)
            val started = MediaHostServer(identityStore.identity(), info.hostId, computerName, index, registry, settings::allowVpn, httpsPort)
            server = started
            discovery = runCatching {
                HostDiscoveryResponder(info.hostId, computerName, { started.port }, settings::allowVpn, discoveryPort).also {
                    thread(isDaemon = true, name = "host-discovery") { it.run() }
                }
            }.onFailure { AppLog.w(TAG, "Discovery unavailable: ${it.message}") }.getOrNull()
            val addresses = localAddresses().filterIsInstance<Inet4Address>()
            _status.update {
                it.copy(
                    isSharing = true,
                    port = started.port,
                    homeAddresses = addresses.filter { a -> !HostAddressPolicy.isTailscale(a) }.map { a -> a.hostAddress },
                    vpnAddresses = addresses.filter { a -> HostAddressPolicy.isTailscale(a) }.map { a -> a.hostAddress },
                    error = if (discovery == null) "Devices can't find this PC automatically (port $discoveryPort busy); enter its address on the device." else null
                )
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "Sharing couldn't start: ${e.message}")
            stopSharing()
            _status.update { it.copy(error = "Sharing couldn't start (port $httpsPort may be in use by another program).") }
        }
    }

    private fun stopSharing() {
        cancelPairing()
        discovery?.close()
        discovery = null
        server?.close()
        server = null
        _status.update { it.copy(isSharing = false, port = null, error = null) }
    }

    override fun close() = stopSharing()

    private companion object {
        const val TAG = "MediaHost"
        /** No 0/O, 1/I: easy to read off a screen and type on a remote. */
        const val PIN_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
        val random = SecureRandom()

        fun newPin(): String = (1..6).map { PIN_ALPHABET[random.nextInt(PIN_ALPHABET.length)] }.joinToString("")
    }
}
