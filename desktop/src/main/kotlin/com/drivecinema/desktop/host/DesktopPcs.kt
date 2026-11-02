package com.drivecinema.desktop.host

import com.drivecinema.app.core.host.DiscoveredHost
import com.drivecinema.app.core.host.HostDiscoveryClient
import com.drivecinema.app.core.host.HostLibrarySync
import com.drivecinema.app.core.host.HostPairingException
import com.drivecinema.app.core.host.HostSyncResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetAddress

/** A paired PC as the desktop screen shows it. */
data class PcEntry(val hostId: String, val name: String, val homeAddress: String?, val vpnAddress: String?, val status: String?)

data class PcsState(
    val pcs: List<PcEntry> = emptyList(),
    val discovered: List<DiscoveredHost> = emptyList(),
    val isSearching: Boolean = false,
    val isPairing: Boolean = false,
    val message: String? = null
)

/**
 * Task I.2, desktop as a client: "Play from another PC". The same pairing and sync as on the phone; this computer's
 * own host is never offered (it would pair with itself).
 */
class DesktopPcs(
    private val scope: CoroutineScope,
    private val hosts: HostLibrarySync,
    private val ownHostId: () -> String?,
    private val deviceName: () -> String = { runCatching { InetAddress.getLocalHost().hostName }.getOrDefault("DriveCinema desktop") },
    private val discover: () -> List<DiscoveredHost> = { HostDiscoveryClient.discover() },
    /** Tests use a random port; the real PCs always use the default. */
    private val pairingPort: () -> Int = { com.drivecinema.app.core.host.HostProtocol.PAIRING_PORT },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val _state = MutableStateFlow(PcsState())
    val state: StateFlow<PcsState> = _state.asStateFlow()
    private val statuses = mutableMapOf<String, String>()

    init {
        refreshRows()
    }

    /** Re-reads every paired PC's folders (at sign-in, on "Check PCs now", after pairing). */
    fun syncAll() = scope.launch {
        val results = withContext(ioDispatcher) { runCatching { hosts.syncAll() }.getOrDefault(emptyList()) }
        results.filterNot { it.removedByPc }.forEach { statuses[it.hostId] = describe(it) }
        results.filter { it.removedByPc }.forEach { removed ->
            statuses.remove(removed.hostId)
            _state.update { it.copy(message = "${removed.hostName} removed this computer. Pair again to play its films.") }
        }
        refreshRows()
    }

    fun search() {
        _state.update { it.copy(isSearching = true, message = null) }
        scope.launch {
            val own = ownHostId()
            val found = withContext(ioDispatcher) { runCatching { discover() }.getOrDefault(emptyList()) }.filter { it.hostId != own }
            _state.update {
                it.copy(
                    discovered = found, isSearching = false,
                    message = if (found.isEmpty()) "No other PC found. Is sharing on there? You can also type its address." else null
                )
            }
        }
    }

    fun pair(address: String, pin: String, vpnAddress: String) {
        if (address.isBlank() || pin.length != 6) {
            _state.update { it.copy(message = "Enter the PC's address and the 6-character code it shows.") }
            return
        }
        _state.update { it.copy(isPairing = true, message = null) }
        scope.launch {
            val result = withContext(ioDispatcher) { runCatching { hosts.pair(address.trim(), pin, deviceName(), vpnAddress, pairingPort()) } }
            val paired = result.getOrNull()
            val message = when {
                paired != null && paired.pairing.hostId == ownHostId() -> {
                    withContext(ioDispatcher) { hosts.remove(paired.pairing.hostId) }
                    "That is this computer. Pair with another PC."
                }
                paired != null -> "Paired with ${paired.pairing.hostName}. Loading its films…"
                else -> (result.exceptionOrNull() as? HostPairingException)?.message ?: "Pairing failed. Try again."
            }
            _state.update { it.copy(isPairing = false, message = message) }
            refreshRows()
            if (paired != null && paired.pairing.hostId != ownHostId()) syncAll()
        }
    }

    fun setVpnAddress(hostId: String, address: String) {
        hosts.setVpnAddress(hostId, address)
        refreshRows()
        syncAll()
    }

    fun remove(hostId: String) = scope.launch {
        withContext(ioDispatcher) { hosts.remove(hostId) }
        statuses.remove(hostId)
        refreshRows()
    }

    private fun describe(result: HostSyncResult): String {
        val host = hosts.hosts().firstOrNull { it.pairing.hostId == result.hostId }
        return when {
            result.reachableAt == null -> "Not reachable (off, or away from home without the VPN)"
            result.reachableAt == host?.vpnAddress -> "Connected through the VPN · ${result.videosFound} videos"
            else -> "Connected at home · ${result.videosFound} videos"
        }
    }

    private fun refreshRows() {
        _state.update { state ->
            state.copy(pcs = hosts.hosts().map { PcEntry(it.pairing.hostId, it.pairing.hostName, it.homeAddress, it.vpnAddress, statuses[it.pairing.hostId]) })
        }
    }
}
