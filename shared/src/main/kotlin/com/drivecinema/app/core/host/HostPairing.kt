package com.drivecinema.app.core.host

import com.drivecinema.app.core.logging.AppLog
import com.drivecinema.app.core.pairing.SrpCryptoHelper
import com.drivecinema.app.core.pairing.SrpPairingClientCrypto
import com.drivecinema.app.core.pairing.SrpPairingServerCrypto
import com.drivecinema.app.core.pairing.WrongPairingPinException
import org.bouncycastle.util.encoders.Hex
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.ProtocolException
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.security.SecureRandom
import java.util.Base64

/** What a device keeps after pairing with a PC (Task I.2). [deviceKey] is a secret: credential store only, never logged. */
data class HostPairing(
    val hostId: String,
    val hostName: String,
    /** SHA-256 of the PC's certificate; the only certificate the device trusts for this PC. */
    val certFingerprint: String,
    val httpsPort: Int,
    val deviceId: String,
    val deviceKey: String
) {
    /** The `Authorization` header value for requests to this PC. */
    val authorization: String get() = "Bearer $deviceId.$deviceKey"

    fun toJson(): String = JSONObject()
        .put("hostId", hostId).put("hostName", hostName).put("certFingerprint", certFingerprint)
        .put("httpsPort", httpsPort).put("deviceId", deviceId).put("deviceKey", deviceKey)
        .toString()

    override fun toString() = "HostPairing(hostId=$hostId, hostName=$hostName, deviceId=$deviceId)"

    companion object {
        fun fromJson(json: String): HostPairing? = runCatching {
            val o = JSONObject(json)
            HostPairing(
                o.getString("hostId"), o.getString("hostName"), o.getString("certFingerprint"),
                o.getInt("httpsPort"), o.getString("deviceId"), o.getString("deviceKey")
            )
        }.getOrNull()

        private val random = SecureRandom()

        /** A new device ID (not secret) and a 256-bit device key. */
        fun newDeviceCredentials(): Pair<String, String> {
            val id = ByteArray(8).also(random::nextBytes).joinToString("") { "%02x".format(it) }
            val key = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(random::nextBytes))
            return id to key
        }
    }
}

/** Pairing failed. [wrongPin]: the PC refused the PIN; otherwise the PC couldn't be reached or answered nonsense. */
class HostPairingException(message: String, val wrongPin: Boolean = false, cause: Throwable? = null) : IOException(message, cause)

/** Line protocol shared by both ends: one JSON object (or hex) per line, at most [MAX_LINE_BYTES]. */
internal object PairingLines {
    const val MAX_LINE_BYTES = 16 * 1024
    const val ERROR = "ERROR"
    const val OK = "OK"

    fun write(output: OutputStream, line: String) {
        output.write((line + "\n").toByteArray(Charsets.UTF_8))
        output.flush()
    }

    fun read(input: InputStream): String? {
        val buffer = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b == -1) return if (buffer.size() == 0) null else buffer.toString(Charsets.UTF_8.name())
            if (b == '\n'.code) return buffer.toString(Charsets.UTF_8.name()).removeSuffix("\r")
            if (buffer.size() >= MAX_LINE_BYTES) throw ProtocolException("Pairing message too long")
            buffer.write(b)
        }
    }
}

/**
 * The PC's side of one pairing attempt. SRP-6a with the PIN (the same audited crypto as phone → TV pairing, F-07);
 * inside the PIN-authenticated, AES-GCM-encrypted channel the PC sends its certificate fingerprint and a new device key.
 * The device is only kept ([commit]) after the client confirmed it stored the pairing.
 */
class HostPairingSession(private val pin: String) {
    enum class Result { PAIRED, WRONG_PIN, REJECTED }

    fun handle(
        input: InputStream,
        output: OutputStream,
        issue: (deviceName: String) -> HostPairing,
        commit: (pairing: HostPairing, deviceName: String) -> Unit
    ): Result {
        val crypto = SrpPairingServerCrypto(pin, maxAttempts = 1)
        return try {
            val hello = JSONObject(PairingLines.read(input) ?: throw ProtocolException("No hello"))
            val deviceName = hello.getString("deviceName").take(MAX_DEVICE_NAME).ifBlank { "Device" }

            val challenge = crypto.generateChallenge()
            PairingLines.write(output, JSONObject().put("salt", Hex.toHexString(challenge.salt)).put("serverB", challenge.serverB.toString(16)).toString())

            val proof = JSONObject(PairingLines.read(input) ?: throw ProtocolException("No proof"))
            val serverM2 = crypto.verifyClientAndGenerateM2(BigInteger(proof.getString("clientA"), 16), BigInteger(proof.getString("clientM1"), 16))
            PairingLines.write(output, JSONObject().put("serverM2", serverM2.toString(16)).toString())

            val pairing = issue(deviceName)
            val sessionKey = crypto.deriveAesKey(challenge.salt)
            PairingLines.write(output, Hex.toHexString(SrpCryptoHelper.encryptPayload(sessionKey, pairing.toJson())))

            if (PairingLines.read(input) != PairingLines.OK) throw ProtocolException("The device did not confirm")
            commit(pairing, deviceName)
            // Only now may the device use its key: it waits for this line, so its first request is never refused.
            PairingLines.write(output, PairingLines.OK)
            Result.PAIRED
        } catch (e: Exception) {
            runCatching { PairingLines.write(output, PairingLines.ERROR) }
            AppLog.w(TAG, "Pairing attempt failed: ${e.javaClass.simpleName}")
            if (e is WrongPairingPinException) Result.WRONG_PIN else Result.REJECTED
        }
    }

    private companion object {
        const val TAG = "HostPairing"
        const val MAX_DEVICE_NAME = 60
    }
}

/**
 * Listens for pairing while the PC shows a PIN. Stops after a device paired, after [maxWrongPins] wrong PINs (as for
 * the TV, F-07), or on [close]. Connections from addresses [HostAddressPolicy] refuses are dropped unanswered.
 */
class HostPairingListener(
    private val pin: String,
    private val allowVpn: Boolean,
    private val issue: (deviceName: String) -> HostPairing,
    private val commit: (pairing: HostPairing, deviceName: String) -> Unit,
    port: Int = HostProtocol.PAIRING_PORT,
    private val maxWrongPins: Int = 3
) : AutoCloseable {
    private val server = ServerSocket().apply {
        reuseAddress = true
        bind(InetSocketAddress(port))
    }
    val port: Int get() = server.localPort

    /** Blocks until paired, locked out or closed; returns the last result. */
    fun run(): HostPairingSession.Result {
        var wrongPins = 0
        var last = HostPairingSession.Result.REJECTED
        try {
            while (!server.isClosed && wrongPins < maxWrongPins) {
                val socket = try {
                    server.accept()
                } catch (e: SocketException) {
                    break
                }
                socket.use {
                    if (!HostAddressPolicy.isAllowed(it.inetAddress, allowVpn)) return@use
                    it.soTimeout = 15_000
                    last = HostPairingSession(pin).handle(it.getInputStream(), it.getOutputStream(), issue, commit)
                }
                when (last) {
                    HostPairingSession.Result.PAIRED -> break
                    HostPairingSession.Result.WRONG_PIN -> wrongPins++
                    HostPairingSession.Result.REJECTED -> Unit
                }
            }
        } finally {
            close()
        }
        return last
    }

    override fun close() {
        runCatching { server.close() }
    }
}

/** The device's side: pairs with the PC at [address]:[port] using the PIN shown on the PC. */
object HostPairingClient {
    fun pair(address: String, pin: String, deviceName: String, port: Int = HostProtocol.PAIRING_PORT, timeoutMs: Int = 10_000): HostPairing {
        val socket = Socket()
        try {
            socket.connect(InetSocketAddress(address, port), timeoutMs)
            socket.soTimeout = 15_000
        } catch (e: IOException) {
            socket.close()
            throw HostPairingException("Can't reach the PC. Is it on, with \"Pair a device\" open?", cause = e)
        }
        socket.use {
            val input = it.getInputStream()
            val output = it.getOutputStream()
            try {
                PairingLines.write(output, JSONObject().put("deviceName", deviceName).toString())
                val challenge = JSONObject(readOrFail(input))
                val crypto = SrpPairingClientCrypto(pin.trim().uppercase())
                val salt = Hex.decode(challenge.getString("salt"))
                val (clientA, clientM1) = crypto.processChallenge(salt, BigInteger(challenge.getString("serverB"), 16))
                PairingLines.write(output, JSONObject().put("clientA", clientA.toString(16)).put("clientM1", clientM1.toString(16)).toString())

                val answer = readOrFail(input)
                if (answer == PairingLines.ERROR) throw HostPairingException("The PC didn't accept the code. Check it and try again.", wrongPin = true)
                // M2 proves the PC knows the PIN too: nobody in between can pose as the PC.
                if (!crypto.verifyServer(BigInteger(JSONObject(answer).getString("serverM2"), 16))) {
                    throw HostPairingException("The PC's answer could not be verified.")
                }
                val encrypted = readOrFail(input)
                val pairing = HostPairing.fromJson(SrpCryptoHelper.decryptPayload(crypto.deriveAesKey(salt), Hex.decode(encrypted)))
                    ?: throw HostPairingException("The PC sent an unreadable pairing.")
                PairingLines.write(output, PairingLines.OK)
                if (readOrFail(input) != PairingLines.OK) throw HostPairingException("The PC didn't finish pairing. Try again.")
                return pairing
            } catch (e: HostPairingException) {
                throw e
            } catch (e: Exception) {
                throw HostPairingException("Pairing with the PC failed. Try again.", cause = e)
            }
        }
    }

    private fun readOrFail(input: InputStream): String =
        PairingLines.read(input) ?: throw HostPairingException("The PC closed the connection.")
}
