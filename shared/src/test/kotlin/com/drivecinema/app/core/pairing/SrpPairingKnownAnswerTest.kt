package com.drivecinema.app.core.pairing

import org.bouncycastle.crypto.agreement.srp.SRP6StandardGroups
import org.bouncycastle.crypto.agreement.srp.SRP6VerifierGenerator
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.util.encoders.Hex
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigInteger

/**
 * Report F-15: phone and TV may run different app versions, and so different Bouncy Castle versions. Pairing only
 * works if both derive the same PIN verifier and session key. These values were computed with Bouncy Castle 1.78.1
 * (the version before the upgrade) and must stay identical with any later version.
 */
class SrpPairingKnownAnswerTest {

    private val salt = ByteArray(16) { it.toByte() }

    @Test
    fun sessionKeyDerivationMatchesBouncyCastle1781() {
        val sharedSecret = BigInteger("1234567890abcdef".repeat(32), 16)
        val key = SrpCryptoHelper.deriveAesGcmKey(salt, sharedSecret)
        assertEquals("59ab251233432b18de959e8c270d5d1ae667f0f7827dff9722eadb27bcdf496b", Hex.toHexString(key))
    }

    @Test
    fun pinVerifierMatchesBouncyCastle1781() {
        // Same calls as SrpPairingServerCrypto.generateChallenge, with a fixed salt.
        val generator = SRP6VerifierGenerator().apply {
            init(SRP6StandardGroups.rfc5054_2048.n, SRP6StandardGroups.rfc5054_2048.g, SHA256Digest())
        }
        val verifier = generator.generateVerifier(salt, "DriveCinema".toByteArray(), "8F3K9A".toByteArray())
        val digest = SHA256Digest().run {
            val bytes = verifier.toByteArray()
            update(bytes, 0, bytes.size)
            ByteArray(32).also { doFinal(it, 0) }
        }
        assertEquals("25037855ab87e3b5cc76b33ca79b6ca53884d8cc20255b19974c5e7561c35790", Hex.toHexString(digest))
    }
}
