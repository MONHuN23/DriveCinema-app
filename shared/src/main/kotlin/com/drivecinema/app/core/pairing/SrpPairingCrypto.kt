package com.drivecinema.app.core.pairing

import org.bouncycastle.crypto.agreement.srp.SRP6Client
import org.bouncycastle.crypto.agreement.srp.SRP6Server
import org.bouncycastle.crypto.agreement.srp.SRP6StandardGroups
import org.bouncycastle.crypto.agreement.srp.SRP6VerifierGenerator
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.util.BigIntegers
import java.math.BigInteger
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class ServerChallenge(
    val salt: ByteArray,
    val serverB: BigInteger
)

/** The client's SRP evidence did not match: a wrong PIN. The only failure that counts as a guess (report F-07). */
class WrongPairingPinException(message: String) : IllegalArgumentException(message)

class SrpPairingServerCrypto(
    private val pin: String,
    private val maxAttempts: Int = 3
) {
    private val group = SRP6StandardGroups.rfc5054_2048
    private val random = SecureRandom()
    private val server = SRP6Server()
    private var failedAttempts = 0
    private var sharedSecret: BigInteger? = null
    private var salt: ByteArray? = null

    fun generateChallenge(): ServerChallenge {
        val newSalt = ByteArray(16)
        random.nextBytes(newSalt)
        salt = newSalt

        val verifierGen = SRP6VerifierGenerator()
        verifierGen.init(group.n, group.g, SHA256Digest())
        val verifier = verifierGen.generateVerifier(
            newSalt,
            "DriveCinema".toByteArray(Charsets.UTF_8),
            pin.toByteArray(Charsets.UTF_8)
        )

        server.init(group.n, group.g, verifier, SHA256Digest(), random)
        val serverB = server.generateServerCredentials()

        return ServerChallenge(salt = newSalt, serverB = serverB)
    }

    fun verifyClientAndGenerateM2(clientA: BigInteger, clientM1: BigInteger): BigInteger {
        if (failedAttempts >= maxAttempts) {
            throw IllegalStateException("Too many failed attempts. Lockout enforced.")
        }

        try {
            val s = server.calculateSecret(clientA)
            sharedSecret = s
        } catch (e: Exception) {
            failedAttempts++
            throw IllegalArgumentException("Client public value rejected: ${e.message}")
        }

        val verified = server.verifyClientEvidenceMessage(clientM1)
        if (!verified) {
            failedAttempts++
            throw WrongPairingPinException("Client evidence verification failed. Incorrect PIN.")
        }

        return server.calculateServerEvidenceMessage()
    }

    fun deriveAesKey(salt: ByteArray): ByteArray {
        val s = sharedSecret ?: throw IllegalStateException("Secret not computed")
        return SrpCryptoHelper.deriveAesGcmKey(salt, s)
    }

    fun getFailedAttempts(): Int = failedAttempts
}

class SrpPairingClientCrypto(
    private val pin: String
) {
    private val group = SRP6StandardGroups.rfc5054_2048
    private val random = SecureRandom()
    private val client = SRP6Client()
    private var sharedSecret: BigInteger? = null

    fun processChallenge(salt: ByteArray, serverB: BigInteger): Pair<BigInteger, BigInteger> {
        client.init(group.n, group.g, SHA256Digest(), random)
        val clientA = client.generateClientCredentials(
            salt,
            "DriveCinema".toByteArray(Charsets.UTF_8),
            pin.toByteArray(Charsets.UTF_8)
        )
        val s = client.calculateSecret(serverB)
        sharedSecret = s
        val clientM1 = client.calculateClientEvidenceMessage()
        return Pair(clientA, clientM1)
    }

    fun verifyServer(serverM2: BigInteger): Boolean {
        return client.verifyServerEvidenceMessage(serverM2)
    }

    fun deriveAesKey(salt: ByteArray): ByteArray {
        val s = sharedSecret ?: throw IllegalStateException("Secret not computed")
        return SrpCryptoHelper.deriveAesGcmKey(salt, s)
    }
}

object SrpCryptoHelper {
    private const val HKDF_INFO = "DriveCinema-Pairing-AES-GCM-v1"

    fun deriveAesGcmKey(salt: ByteArray, sharedSecret: BigInteger): ByteArray {
        val sBytes = BigIntegers.asUnsignedByteArray(256, sharedSecret)
        val hkdf = HKDFBytesGenerator(SHA256Digest())
        hkdf.init(HKDFParameters(sBytes, salt, HKDF_INFO.toByteArray(Charsets.UTF_8)))

        val okm = ByteArray(32)
        hkdf.generateBytes(okm, 0, 32)
        return okm
    }

    fun encryptPayload(keyBytes: ByteArray, plaintext: String): ByteArray {
        val random = SecureRandom()
        val iv = ByteArray(12)
        random.nextBytes(iv)

        val key = SecretKeySpec(keyBytes, "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        // Return IV (12 bytes) + Ciphertext with 128-bit Tag
        val result = ByteArray(iv.size + ciphertext.size)
        System.arraycopy(iv, 0, result, 0, iv.size)
        System.arraycopy(ciphertext, 0, result, iv.size, ciphertext.size)
        return result
    }

    fun decryptPayload(keyBytes: ByteArray, encrypted: ByteArray): String {
        require(encrypted.size >= 12 + 16) { "Encrypted payload too short" }
        val iv = encrypted.copyOfRange(0, 12)
        val ciphertext = encrypted.copyOfRange(12, encrypted.size)

        val key = SecretKeySpec(keyBytes, "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        val decrypted = cipher.doFinal(ciphertext)
        return String(decrypted, Charsets.UTF_8)
    }
}
