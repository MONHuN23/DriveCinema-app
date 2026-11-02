package com.drivecinema.app.core.host

import com.drivecinema.app.core.database.DriveCinemaDatabase
import com.drivecinema.app.core.database.entity.SourceKind
import com.drivecinema.app.core.local.LocalLibraryCrawler
import com.drivecinema.app.core.local.LocalLibraryIndexer
import com.drivecinema.app.core.logging.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

/**
 * A PC this device is paired with: the pairing (with its secret device key) and the addresses to try, home first,
 * then the VPN (Tailscale) address for streaming away from home (design §2.7).
 */
data class PairedHost(val pairing: HostPairing, val homeAddress: String?, val vpnAddress: String?) {
    val addresses: List<String> get() = listOfNotNull(homeAddress, vpnAddress)
    override fun toString() = "PairedHost(${pairing.hostName}, home=$homeAddress, vpn=$vpnAddress)"
}

/** Where pairings are kept: encrypted platform storage (Android: AndroidKeyStore), never the database or logs. */
interface PairedHostStore {
    fun hosts(): List<PairedHost>
    fun save(host: PairedHost)
    fun remove(hostId: String)
}

data class HostSyncResult(
    val hostId: String,
    val hostName: String,
    val reachableAt: String?,
    val videosFound: Int,
    /** The PC removed this device (Paired devices → Remove): it was forgotten here too. */
    val removedByPc: Boolean = false
)

/**
 * Task I.2, client side: pairs with PCs, keeps their shared folders in the catalog (as `SourceKind.HOST` copies, with
 * the same crawler, layout rules and duplicate handling as local folders), and gives the player a URL for a PC copy.
 * A PC that can't be reached has its copies marked unavailable (rows kept, nothing deleted), so its titles fall back
 * to Drive or are hidden until it is back. Watch history is never touched.
 */
class HostLibrarySync(
    private val db: DriveCinemaDatabase,
    private val store: PairedHostStore,
    private val runInTransaction: suspend (suspend () -> Unit) -> Unit,
    private val apiFor: (HostPairing) -> HostApi = { HostApi(it) }
) {
    private val apis = ConcurrentHashMap<String, HostApi>()
    private val dao get() = db.mediaSourceDao()

    fun hosts(): List<PairedHost> = store.hosts()

    /**
     * Pairs with the PC at [address] using the PIN it shows. A Tailscale address is kept as the VPN address, any
     * other as the home address; [vpnAddress] can be given too (or added later with [setVpnAddress]).
     */
    suspend fun pair(
        address: String,
        pin: String,
        deviceName: String,
        vpnAddress: String? = null,
        pairingPort: Int = HostProtocol.PAIRING_PORT
    ): PairedHost = withContext(Dispatchers.IO) {
        val pairing = HostPairingClient.pair(address, pin, deviceName, port = pairingPort)
        val viaVpn = runCatching { HostAddressPolicy.isTailscale(InetAddress.getByName(address)) }.getOrDefault(false)
        val existing = store.hosts().firstOrNull { it.pairing.hostId == pairing.hostId }
        val host = PairedHost(
            pairing,
            homeAddress = if (viaVpn) existing?.homeAddress else address,
            vpnAddress = vpnAddress?.ifBlank { null } ?: if (viaVpn) address else existing?.vpnAddress
        )
        store.save(host)
        apis.remove(pairing.hostId)
        host
    }

    fun setVpnAddress(hostId: String, vpnAddress: String?) {
        val host = store.hosts().firstOrNull { it.pairing.hostId == hostId } ?: return
        store.save(host.copy(vpnAddress = vpnAddress?.trim()?.ifEmpty { null }))
    }

    /** Forgets the PC on this device; its titles stop being playable from it (Drive copies are unaffected). */
    suspend fun remove(hostId: String) {
        store.remove(hostId)
        apis.remove(hostId)
        runInTransaction {
            dao.markHostUnavailable(treePrefix(hostId))
            dao.refreshVisibilityFromSources()
        }
    }

    suspend fun syncAll(): List<HostSyncResult> = store.hosts().map { sync(it) }

    suspend fun sync(host: PairedHost): HostSyncResult {
        val hostId = host.pairing.hostId
        val api = api(host)
        val address = withContext(Dispatchers.IO) { api.connect(host.addresses) }
        if (address == null) {
            if (api.removedByPc) {
                // Found on the device: after Remove on the PC, the phone still listed the PC as paired.
                remove(hostId)
                return HostSyncResult(hostId, host.pairing.hostName, null, 0, removedByPc = true)
            }
            runInTransaction {
                dao.markHostUnavailable(treePrefix(hostId))
                dao.refreshVisibilityFromSources()
            }
            return HostSyncResult(hostId, host.pairing.hostName, null, 0)
        }
        return try {
            val roots = withContext(Dispatchers.IO) { api.roots() }
            val indexer = LocalLibraryIndexer(db, LocalLibraryCrawler(HostTreeSource(api)), runInTransaction)
            var videos = 0
            for (root in roots) {
                videos += withContext(Dispatchers.IO) { indexer.indexFolder(HostProtocol.treeUri(hostId, root.id), SourceKind.HOST) }.videosFound
            }
            runInTransaction {
                dao.markHostRootsOutsideUnavailable(treePrefix(hostId), roots.map { HostProtocol.treeUri(hostId, it.id) })
                dao.refreshVisibilityFromSources()
            }
            HostSyncResult(hostId, host.pairing.hostName, address, videos)
        } catch (e: Exception) {
            // A listing failed half-way: nothing was changed for that folder (the crawler throws before writing).
            AppLog.w(TAG, "Syncing ${host.pairing.hostName} failed: ${e.javaClass.simpleName}")
            HostSyncResult(hostId, host.pairing.hostName, address, 0)
        }
    }

    /**
     * For the player: the HTTPS URL of a `dchost://` copy and the pinned client to stream it with; null when the PC
     * isn't paired or can't be reached right now (the player then tries the next copy). Blocking: call on IO.
     */
    fun stream(sourceKey: String, recheck: Boolean = false): Pair<String, HostApi>? {
        val (hostId, _, fileId) = HostProtocol.parse(sourceKey)?.takeIf { it.second == "file" } ?: return null
        val host = store.hosts().firstOrNull { it.pairing.hostId == hostId } ?: return null
        val api = api(host)
        // [recheck] when Play is pressed: the PC may have been switched off (or the phone left home) since the last sync.
        // Streaming requests (every seek) use the address found then, without asking again.
        if ((recheck || api.activeAddress == null) && api.connect(host.addresses) == null) return null
        return api.fileUrl(fileId)?.let { it to api }
    }

    /**
     * Task I.2 (design §2.6): before playing a PC copy, compares the film's bitrate (size over length) with a short
     * measurement of the connection. Null when it can't tell (size unknown, nothing received); the player then just plays.
     */
    suspend fun checkConnection(mediaItemId: String, sourceKey: String): ConnectionVerdict? = withContext(Dispatchers.IO) {
        val size = dao.getSource(sourceKey)?.sizeBytes ?: return@withContext null
        val (hostId, _, fileId) = HostProtocol.parse(sourceKey)?.takeIf { it.second == "file" } ?: return@withContext null
        val api = apis[hostId]?.takeIf { it.activeAddress != null } ?: stream(sourceKey)?.second ?: return@withContext null
        val item = db.mediaDao().getMediaItemById(mediaItemId)
        val watched = dao.getLatestProgressForItem(mediaItemId)?.durationMs
        val duration = ConnectionCheck.durationMs(item?.type, watched, item?.runtimeSeconds)
        val measured = api.measureMbps(fileId) ?: return@withContext null
        ConnectionVerdict(ConnectionCheck.neededMbps(size, duration), measured)
    }

    /** The connected client for a PC (used by the player's data source to stream with the pinned certificate). */
    fun connectedApi(hostId: String): HostApi? = apis[hostId]?.takeIf { it.activeAddress != null }

    private fun api(host: PairedHost): HostApi = apis.getOrPut(host.pairing.hostId) { apiFor(host.pairing) }

    private fun treePrefix(hostId: String) = "${HostProtocol.SCHEME}://$hostId/"

    private companion object {
        const val TAG = "HostLibrarySync"
    }
}
