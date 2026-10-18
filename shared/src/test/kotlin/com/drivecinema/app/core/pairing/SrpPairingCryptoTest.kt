package com.drivecinema.app.core.pairing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SrpPairingCryptoTest {
    @Test
    fun testSuccessfulSrpMutualAuthAndKeyDerivation() {
        val pin = "8F3K9A"
        val server = SrpPairingServerCrypto(pin)
        val client = SrpPairingClientCrypto(pin)

        // 1. TV generates challenge (salt + B)
        val challenge = server.generateChallenge()

        // 2. Client processes challenge and computes A and M1
        val (clientA, clientM1) = client.processChallenge(challenge.salt, challenge.serverB)

        // 3. Server verifies client A and M1, computes server evidence M2
        val serverM2 = server.verifyClientAndGenerateM2(clientA, clientM1)

        // 4. Client verifies server evidence M2
        val clientVerified = client.verifyServer(serverM2)
        assertTrue("Client must verify server evidence M2", clientVerified)

        // 5. Both derive identical 256-bit AES-GCM key via HKDF-SHA256
        val clientKey = client.deriveAesKey(challenge.salt)
        val serverKey = server.deriveAesKey(challenge.salt)
        assertEquals("Both parties must derive identical 32-byte AES key", clientKey.toList(), serverKey.toList())
        assertEquals(32, clientKey.size)

        // 6. Test payload encryption and decryption with derived AES key
        val payload = "{\"refreshToken\":\"test_refresh_123\",\"rootFolderId\":\"folder_abc\"}"
        val encrypted = SrpCryptoHelper.encryptPayload(clientKey, payload)
        val decrypted = SrpCryptoHelper.decryptPayload(serverKey, encrypted)
        assertEquals(payload, decrypted)
    }

    @Test
    fun testWrongPinFailsVerificationAndEnforcesLockout() {
        val server = SrpPairingServerCrypto("CORRECT")
        val client = SrpPairingClientCrypto("WRONG_P")

        val challenge = server.generateChallenge()
        val (clientA, clientM1) = client.processChallenge(challenge.salt, challenge.serverB)

        var failed = false
        try {
            server.verifyClientAndGenerateM2(clientA, clientM1)
        } catch (e: Exception) {
            failed = true
        }
        assertTrue("Server must reject invalid PIN evidence", failed)
        assertEquals(1, server.getFailedAttempts())
    }
}
