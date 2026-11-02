package com.drivecinema.app.core.host

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.net.InetAddress
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

/** Task I.2: who may connect, the PC's certificate, PIN pairing and discovery. */
class HostProtocolTest {

    private fun ip(s: String) = InetAddress.getByName(s)

    @Test
    fun onlyHomeNetworkAddressesUnlessTheVpnSwitchIsOn() {
        listOf("192.168.1.20", "10.0.0.5", "172.16.3.4", "169.254.1.1", "127.0.0.1", "fe80::1", "fd12:3456::1")
            .forEach { assertTrue(it, HostAddressPolicy.isAllowed(ip(it), allowVpn = false)) }
        listOf("8.8.8.8", "172.32.0.1", "100.63.255.255", "100.128.0.1", "2001:4860::8888")
            .forEach { assertFalse(it, HostAddressPolicy.isAllowed(ip(it), allowVpn = true)) }
        listOf("100.64.0.1", "100.101.102.103", "100.127.255.254", "fd7a:115c:a1e0::1").forEach {
            assertFalse("$it needs the VPN switch", HostAddressPolicy.isAllowed(ip(it), allowVpn = false))
            assertTrue(it, HostAddressPolicy.isAllowed(ip(it), allowVpn = true))
        }
    }

    @Test
    fun sourceKeysRoundTripAndRejectOtherSchemes() {
        val key = HostProtocol.fileSourceKey("host1", "abc_DEF-9")
        assertEquals(Triple("host1", "file", "abc_DEF-9"), HostProtocol.parse(key))
        assertEquals(Triple("host1", "root", "r1"), HostProtocol.parse(HostProtocol.treeUri("host1", "r1")))
        listOf("file:///x.mkv", "dchost://h/file/../x", "dchost://h/other/x", "content://x").forEach { assertNull(it, HostProtocol.parse(it)) }
    }

    @Test
    fun theGeneratedCertificateIsValidSelfSignedAndRoundTrips() {
        val identity = HostIdentity.generate()
        identity.certificate.checkValidity()
        identity.certificate.verify(identity.certificate.publicKey)
        assertEquals(64, identity.fingerprint.length)
        val password = "pw".toCharArray()
        val restored = HostIdentity.fromKeyStore(identity.toKeyStore(password), password)!!
        assertEquals(identity.fingerprint, restored.fingerprint)
        assertNotEquals("each PC gets its own", identity.fingerprint, HostIdentity.generate().fingerprint)
    }

    private fun pairing(name: String) = HostPairing("host1", "Living room PC", "f".repeat(64), 8768, "dev-$name", "secret-key")

    @Test
    fun theRightPinPairsAndOnlyThenIsTheDeviceKept() {
        val committed = CopyOnWriteArrayList<String>()
        val listener = HostPairingListener("ABC234", allowVpn = false, issue = { pairing(it) }, commit = { _, name -> committed += name }, port = 0)
        val result = arrayOfNulls<HostPairingSession.Result>(1)
        val server = thread { result[0] = listener.run() }

        val received = HostPairingClient.pair("127.0.0.1", "abc234", "Pixel 9a", port = listener.port)
        server.join(10_000)

        assertEquals(pairing("Pixel 9a"), received)
        assertEquals(listOf("Pixel 9a"), committed)
        assertEquals(HostPairingSession.Result.PAIRED, result[0])
        assertFalse("the key is never printed", received.toString().contains("secret-key"))
    }

    @Test
    fun threeWrongPinsStopPairingAndNothingIsKept() {
        val committed = CopyOnWriteArrayList<String>()
        val listener = HostPairingListener("ABC234", allowVpn = false, issue = { pairing(it) }, commit = { _, name -> committed += name }, port = 0)
        val port = listener.port
        val server = thread { listener.run() }
        repeat(3) {
            try {
                HostPairingClient.pair("127.0.0.1", "WRONG1", "Phone", port = port)
                fail("Expected a wrong-PIN error")
            } catch (e: HostPairingException) {
                assertTrue(e.wrongPin)
            }
        }
        server.join(10_000)
        assertFalse("listener stopped after 3 wrong PINs", server.isAlive)
        assertTrue(committed.isEmpty())
        try {
            HostPairingClient.pair("127.0.0.1", "ABC234", "Phone", port = port, timeoutMs = 1_000)
            fail("Expected the PC to be unreachable after the lockout")
        } catch (e: HostPairingException) {
            assertFalse(e.wrongPin)
        }
    }

    @Test
    fun discoveryFindsThePcOnceAndShowsNoSecrets() {
        val responder = HostDiscoveryResponder("host1", { "Living room PC" }, { 8768 }, { false }, port = 0)
        val thread = thread { responder.run() }
        try {
            val found = HostDiscoveryClient.discover(timeoutMs = 1_000, targets = listOf(ip("127.0.0.1"), ip("127.0.0.1")), port = responder.port)
            assertEquals(listOf(DiscoveredHost("host1", "Living room PC", "127.0.0.1", 8768)), found)
        } finally {
            responder.close()
            thread.join(2_000)
        }
    }
}
