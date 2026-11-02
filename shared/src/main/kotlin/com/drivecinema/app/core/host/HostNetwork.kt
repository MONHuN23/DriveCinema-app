package com.drivecinema.app.core.host

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface

/** Task I.2: ports and paths shared by the PC host and its clients. */
object HostProtocol {
    /** UDP: "is there a DriveCinema PC here?" (answers carry nothing secret). */
    const val DISCOVERY_PORT = 8766
    /** TCP: PIN pairing, open only while the PC shows a PIN. */
    const val PAIRING_PORT = 8767
    /** HTTPS: folder listings and files, paired devices only. */
    const val DEFAULT_HTTPS_PORT = 8768
    const val DISCOVERY_REQUEST = "DRIVECINEMA-DISCOVER/1"

    /** The source key of a file on a PC: `dchost://<hostId>/file/<fileId>` (also the watch-progress key). */
    const val SCHEME = "dchost"

    fun fileSourceKey(hostId: String, fileId: String) = "$SCHEME://$hostId/file/$fileId"
    fun treeUri(hostId: String, rootId: String) = "$SCHEME://$hostId/root/$rootId"

    /** Splits a `dchost://` URI into host ID and file or root ID; null for anything else. */
    fun parse(uri: String): Triple<String, String, String>? {
        val match = Regex("""^dchost://([A-Za-z0-9_-]+)/(file|root)/([A-Za-z0-9_-]+)$""").find(uri) ?: return null
        return Triple(match.groupValues[1], match.groupValues[2], match.groupValues[3])
    }
}

/**
 * Which addresses may talk to the PC host (report F-07 spirit: nothing from the internet). Always the home network:
 * private IPv4 (10/8, 172.16/12, 192.168/16), link-local, IPv6 unique-local and link-local, and this computer itself.
 * With [allowVpn] also Tailscale's ranges (100.64.0.0/10, fd7a:115c:a1e0::/48); without it Tailscale's IPv6 range is
 * refused even though it is unique-local.
 */
object HostAddressPolicy {
    fun isAllowed(address: InetAddress, allowVpn: Boolean): Boolean {
        if (address.isLoopbackAddress) return true
        if (isTailscale(address)) return allowVpn
        return when (address) {
            is Inet4Address -> address.isSiteLocalAddress || address.isLinkLocalAddress
            is Inet6Address -> address.isLinkLocalAddress || isUniqueLocal(address)
            else -> false
        }
    }

    fun isTailscale(address: InetAddress): Boolean {
        val b = address.address
        return when (address) {
            // 100.64.0.0/10: first byte 100, top two bits of the second byte 01.
            is Inet4Address -> (b[0].toInt() and 0xFF) == 100 && (b[1].toInt() and 0xC0) == 0x40
            is Inet6Address -> b.size == 16 && b[0] == 0xfd.toByte() && b[1] == 0x7a.toByte() && b[2] == 0x11.toByte() &&
                b[3] == 0x5c.toByte() && b[4] == 0xa1.toByte() && b[5] == 0xe0.toByte()
            else -> false
        }
    }

    private fun isUniqueLocal(address: Inet6Address): Boolean = (address.address[0].toInt() and 0xFE) == 0xFC

    /** This computer's addresses other devices could use (home network and, if any, Tailscale), for showing in the UI. */
    fun localAddresses(): List<InetAddress> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filter { it is Inet4Address && (isAllowed(it, allowVpn = true)) }
    }.getOrDefault(emptyList())

    /** Broadcast addresses of this device's networks (plus 255.255.255.255) for discovery. */
    fun broadcastAddresses(): List<InetAddress> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { iface -> iface.interfaceAddresses.mapNotNull { it.broadcast } }
    }.getOrDefault(emptyList()) + InetAddress.getByName("255.255.255.255")
}
