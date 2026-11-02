package com.drivecinema.app.core.host

import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketException
import java.net.SocketTimeoutException

/** A PC that answered discovery. Nothing here is secret; trust comes from pairing. */
data class DiscoveredHost(val hostId: String, val name: String, val address: String, val httpsPort: Int)

/**
 * The PC answers "is there a DriveCinema PC here?" on the home network (design option A). Only addresses that
 * [HostAddressPolicy] allows get an answer; broadcasts don't cross a VPN, so away from home the device uses the
 * address the user entered.
 */
class HostDiscoveryResponder(
    private val hostId: String,
    private val name: () -> String,
    private val httpsPort: () -> Int,
    private val allowVpn: () -> Boolean,
    port: Int = HostProtocol.DISCOVERY_PORT
) : AutoCloseable {
    private val socket = DatagramSocket(null).apply {
        reuseAddress = true
        bind(InetSocketAddress(port))
    }
    val port: Int get() = socket.localPort

    /** Blocks until [close]. */
    fun run() {
        val buffer = ByteArray(256)
        while (!socket.isClosed) {
            val packet = DatagramPacket(buffer, buffer.size)
            try {
                socket.receive(packet)
            } catch (e: SocketException) {
                break
            }
            val text = String(packet.data, 0, packet.length, Charsets.UTF_8)
            if (text != HostProtocol.DISCOVERY_REQUEST || !HostAddressPolicy.isAllowed(packet.address, allowVpn())) continue
            val answer = JSONObject().put("v", 1).put("hostId", hostId).put("name", name()).put("httpsPort", httpsPort())
                .toString().toByteArray(Charsets.UTF_8)
            runCatching { socket.send(DatagramPacket(answer, answer.size, packet.socketAddress)) }
        }
    }

    override fun close() = socket.close()
}

object HostDiscoveryClient {
    /** Asks every network this device is on; returns the PCs that answered within [timeoutMs], one entry per PC. */
    fun discover(
        timeoutMs: Int = 1_500,
        targets: List<InetAddress> = HostAddressPolicy.broadcastAddresses(),
        port: Int = HostProtocol.DISCOVERY_PORT
    ): List<DiscoveredHost> {
        val found = LinkedHashMap<String, DiscoveredHost>()
        DatagramSocket().use { socket ->
            socket.broadcast = true
            val request = HostProtocol.DISCOVERY_REQUEST.toByteArray(Charsets.UTF_8)
            targets.forEach { runCatching { socket.send(DatagramPacket(request, request.size, it, port)) } }
            val deadline = System.currentTimeMillis() + timeoutMs
            val buffer = ByteArray(1024)
            while (true) {
                val left = deadline - System.currentTimeMillis()
                if (left <= 0) break
                socket.soTimeout = left.toInt()
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(packet)
                } catch (e: SocketTimeoutException) {
                    break
                }
                val json = runCatching { JSONObject(String(packet.data, 0, packet.length, Charsets.UTF_8)) }.getOrNull() ?: continue
                val hostId = json.optString("hostId").takeIf { it.matches(Regex("[A-Za-z0-9_-]{4,64}")) } ?: continue
                found.putIfAbsent(hostId, DiscoveredHost(hostId, json.optString("name").take(60), packet.address.hostAddress, json.optInt("httpsPort")))
            }
        }
        return found.values.toList()
    }
}
