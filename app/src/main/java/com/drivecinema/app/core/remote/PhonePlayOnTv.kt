package com.drivecinema.app.core.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * Milestone D, phone: sends a [PlayRequest] to a linked TV (design §3): the last address that worked first, then the TV
 * found by NSD under its link ID. Never throws; every failure is a [PlayOnTvOutcome].
 */
class PhonePlayOnTv(
    private val context: Context,
    private val store: DeviceLinkStore = DeviceLinkStore.tvs(context)
) {
    suspend fun send(tv: DeviceLink, request: PlayRequest): PlayOnTvOutcome = withContext(Dispatchers.IO) {
        if (!onLocalNetwork()) return@withContext PlayOnTvOutcome.NoWifi
        val tried = mutableSetOf<Pair<String, Int>>()
        tv.lastAddress?.let { address ->
            tried += address to tv.port
            attempt(tv, address, tv.port, request)?.let { return@withContext it }
        }
        val found = withTimeoutOrNull(DISCOVERY_TIMEOUT_MS) { discover(tv.id) }
        if (found != null && found !in tried) {
            attempt(tv, found.first, found.second, request)?.let { return@withContext it }
        }
        PlayOnTvOutcome.NotFound
    }

    /** The outcome, or null when nothing (or another device) answered at [address], so the next way is tried. */
    private fun attempt(tv: DeviceLink, address: String, port: Int, request: PlayRequest): PlayOnTvOutcome? {
        Socket().use { socket ->
            try {
                socket.connect(InetSocketAddress(address, port), CONNECT_TIMEOUT_MS)
            } catch (e: IOException) {
                return null
            }
            socket.soTimeout = READ_TIMEOUT_MS
            return try {
                val status = PlayOnTvClient.exchange(socket.getInputStream(), socket.getOutputStream(), store.selfId(), tv, request)
                store.save(tv.copy(lastAddress = address, port = port))
                PlayOnTvOutcome.Answered(status)
            } catch (e: PlayOnTvException) {
                when (e.kind) {
                    PlayOnTvException.Kind.WRONG_TV -> null
                    PlayOnTvException.Kind.UNKNOWN_PHONE -> PlayOnTvOutcome.UnknownPhone
                    PlayOnTvException.Kind.PROTOCOL -> PlayOnTvOutcome.Failed
                }
            } catch (e: IOException) {
                PlayOnTvOutcome.Failed
            }
        }
    }

    private fun onLocalNetwork(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    /** (address, port) of the TV with link ID [tvId], resolving only that TV's service; waits until found or cancelled. */
    private suspend fun discover(tvId: String): Pair<String, Int>? {
        val nsd = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return null
        val resolving = AtomicBoolean(false)
        val done = AtomicBoolean(false)
        var discovery: NsdManager.DiscoveryListener? = null
        try {
            return suspendCancellableCoroutine { cont ->
                fun finish(result: Pair<String, Int>?) {
                    if (done.compareAndSet(false, true)) cont.resume(result)
                }
                val listener = object : NsdManager.DiscoveryListener {
                    override fun onServiceFound(service: NsdServiceInfo) {
                        if (!service.serviceName.startsWith(PlayOnTvProtocol.SERVICE_PREFIX + tvId)) return
                        if (!resolving.compareAndSet(false, true)) return
                        runCatching {
                            @Suppress("DEPRECATION")
                            nsd.resolveService(service, object : NsdManager.ResolveListener {
                                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = resolving.set(false)

                                override fun onServiceResolved(resolved: NsdServiceInfo) {
                                    @Suppress("DEPRECATION")
                                    val address = resolved.host?.hostAddress
                                    if (address != null) finish(address to resolved.port) else resolving.set(false)
                                }
                            })
                        }.onFailure { resolving.set(false) }
                    }

                    override fun onDiscoveryStarted(regType: String) = Unit
                    override fun onServiceLost(service: NsdServiceInfo) = Unit
                    override fun onDiscoveryStopped(serviceType: String) = Unit
                    override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = finish(null)
                    override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
                }
                try {
                    nsd.discoverServices(PlayOnTvProtocol.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
                    discovery = listener
                } catch (e: Exception) {
                    finish(null)
                }
            }
        } finally {
            // Discovery keeps the Wi-Fi radio busy: always stop it (found, failed, timed out or cancelled).
            discovery?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 1_500
        const val READ_TIMEOUT_MS = 5_000
        const val DISCOVERY_TIMEOUT_MS = 4_000L
    }
}
