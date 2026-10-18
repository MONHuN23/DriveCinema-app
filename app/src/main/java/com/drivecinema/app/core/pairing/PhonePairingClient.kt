package com.drivecinema.app.core.pairing

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.drivecinema.app.core.security.AuthCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bouncycastle.util.encoders.Hex
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.math.BigInteger
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

/** The TV answered "ERROR": usually a wrong PIN (the TV doesn't say why, see audit F-07). */
class PairingRejectedException :
    Exception("The TV didn't accept the code. Check the code on the TV and try again.")

/** The TV isn't listening: pairing isn't open on the TV, it stopped after 3 wrong codes, or another network. */
class TvUnreachableException(cause: Throwable) :
    Exception("Can't reach the TV. Open pairing on the TV again and make sure both are on the same Wi-Fi.", cause)

class PhonePairingClient(
    private val context: Context
) {
    suspend fun pairWithTv(
        host: String,
        port: Int,
        pin: String,
        credentials: AuthCredentials
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val socket = Socket()
        try {
            try {
                socket.connect(InetSocketAddress(host, port), 5000)
            } catch (e: ConnectException) {
                throw TvUnreachableException(e)
            } catch (e: SocketTimeoutException) {
                throw TvUnreachableException(e)
            }
            socket.soTimeout = 10_000

            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val writer = PrintWriter(socket.getOutputStream(), true)

            // 1. Read server challenge: salt (hex) and B (hex)
            val challengeStr = readReply(reader, "No challenge from TV")
            val challengeJson = JSONObject(challengeStr)
            val salt = Hex.decode(challengeJson.getString("salt"))
            val serverB = BigInteger(challengeJson.getString("serverB"), 16)

            // 2. Compute client public values A and M1
            val crypto = SrpPairingClientCrypto(pin)
            val (clientA, clientM1) = crypto.processChallenge(salt, serverB)

            val clientJson = JSONObject().apply {
                put("clientA", clientA.toString(16))
                put("clientM1", clientM1.toString(16))
            }
            writer.println(clientJson.toString())

            // 3. Read and verify server evidence M2
            val respStr = readReply(reader, "No server evidence returned")
            val respJson = JSONObject(respStr)
            val serverM2 = BigInteger(respJson.getString("serverM2"), 16)

            val verified = crypto.verifyServer(serverM2)
            if (!verified) {
                return@withContext Result.failure(SecurityException("TV server evidence verification failed"))
            }

            // 4. Derive AES-GCM session key
            val sessionKey = crypto.deriveAesKey(salt)

            // 5. Encrypt credentials payload and send
            val payload = credentials.toJson()
            val encryptedBytes = SrpCryptoHelper.encryptPayload(sessionKey, payload)
            writer.println(Hex.toHexString(encryptedBytes))

            // 6. Read confirmation
            val confirmation = readReply(reader, "No confirmation from TV")
            if (confirmation == "OK") {
                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException("Pairing failed on TV: $confirmation"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    /** Next line from the TV; the TV's plain error reply becomes [PairingRejectedException] instead of a JSON error. */
    private fun readReply(reader: BufferedReader, missing: String): String {
        val line = reader.readLine() ?: throw IllegalStateException(missing)
        if (line.startsWith(TvPairingServer.CLIENT_ERROR)) throw PairingRejectedException()
        return line
    }
}
