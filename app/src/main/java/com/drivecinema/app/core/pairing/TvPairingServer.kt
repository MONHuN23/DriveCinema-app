package com.drivecinema.app.core.pairing

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.drivecinema.app.core.security.AuthCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bouncycastle.util.encoders.Hex
import org.json.JSONObject
import java.io.PrintWriter
import java.math.BigInteger
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom

class TvPairingServer(
    private val context: Context,
    private val onCredentialsReceived: (AuthCredentials) -> Unit
) {
    companion object {
        const val DEFAULT_PORT = 8765
        const val SERVICE_TYPE = "_drivecinema._tcp."
        const val SERVICE_NAME = "DriveCinema-TV"

        /** Longest protocol line accepted (the largest real one, the encrypted credentials, is ~2 KB of hex). */
        const val MAX_LINE_BYTES = 16 * 1024
        /** Wrong PINs allowed before pairing stops; other failed connections (scans, junk) do not count. */
        const val MAX_WRONG_PINS = 3
        /** All the client learns about a failure; details stay in the TV-side log. */
        const val CLIENT_ERROR = "ERROR"
    }

    private enum class HandshakeResult { PAIRED, WRONG_PIN, REJECTED }

    var serverPort: Int = 0
        private set
    var serverIp: String? = null
        private set

    private var serverSocket: ServerSocket? = null
    private var nsdManager: NsdManager? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var activePin: String = ""

    fun generatePin(): String {
        val chars = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
        val random = SecureRandom()
        val pin = (1..6).map { chars[random.nextInt(chars.length)] }.joinToString("")
        activePin = pin
        return pin
    }

    fun getLocalIpAddress(): String? {
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces() ?: return null
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (addr is java.net.Inet4Address && !addr.isLoopbackAddress) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    suspend fun startServer(
        pin: String,
        onListening: (Int) -> Unit = {},
        onSuccess: (AuthCredentials) -> Unit,
        onError: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        activePin = pin
        try {
            val server = try {
                ServerSocket(DEFAULT_PORT)
            } catch (_: Exception) {
                ServerSocket(0)
            }
            serverSocket = server
            val port = server.localPort
            serverPort = port
            serverIp = getLocalIpAddress()

            registerNsdService(port)
            onListening(port)

            var wrongPins = 0
            while (wrongPins < MAX_WRONG_PINS && serverSocket != null && !serverSocket!!.isClosed) {
                try {
                    val clientSocket = server.accept()
                    clientSocket.soTimeout = 15_000

                    when (handleClientHandshake(clientSocket, pin, onSuccess, onError)) {
                        HandshakeResult.PAIRED -> break
                        HandshakeResult.WRONG_PIN -> wrongPins++
                        HandshakeResult.REJECTED -> Unit // not a PIN guess: keep waiting for the phone
                    }
                } catch (e: java.net.SocketException) {
                    // Server socket closed
                    break
                } catch (e: Exception) {
                    onError("Pairing attempt failed: ${e.message}")
                }
            }
        } catch (e: Exception) {
            onError("Pairing server error: ${e.message}")
        } finally {
            stopServer()
        }
    }

    private fun handleClientHandshake(
        socket: Socket,
        pin: String,
        onSuccess: (AuthCredentials) -> Unit,
        onError: (String) -> Unit
    ): HandshakeResult {
        val crypto = SrpPairingServerCrypto(pin)
        val input = java.io.BufferedInputStream(socket.getInputStream())
        val writer = PrintWriter(socket.getOutputStream(), true)

        try {
            // 1. Send challenge to client: salt (hex) and B (hex)
            val challenge = crypto.generateChallenge()
            val challengeJson = JSONObject().apply {
                put("salt", Hex.toHexString(challenge.salt))
                put("serverB", challenge.serverB.toString(16))
            }
            writer.println(challengeJson.toString())

            // 2. Read client public credentials: clientA (hex) and clientM1 (hex)
            val clientMsgStr = readLimitedLine(input) ?: throw IllegalStateException("Connection closed by client")
            val clientJson = JSONObject(clientMsgStr)
            val clientA = BigInteger(clientJson.getString("clientA"), 16)
            val clientM1 = BigInteger(clientJson.getString("clientM1"), 16)

            // 3. Verify client evidence and generate server evidence M2
            val serverM2 = crypto.verifyClientAndGenerateM2(clientA, clientM1)
            val respJson = JSONObject().apply {
                put("serverM2", serverM2.toString(16))
            }
            writer.println(respJson.toString())

            // 4. Derive AES-GCM session key
            val sessionKey = crypto.deriveAesKey(challenge.salt)

            // 5. Read encrypted credentials payload from client
            val encryptedPayloadHex = readLimitedLine(input) ?: throw IllegalStateException("Missing encrypted payload")
            val encryptedBytes = Hex.decode(encryptedPayloadHex)

            val decryptedJsonStr = SrpCryptoHelper.decryptPayload(sessionKey, encryptedBytes)
            val creds = AuthCredentials.fromJson(decryptedJsonStr)
                ?: throw IllegalStateException("Invalid credentials format in decrypted payload")

            // 6. Notify receiver and confirm to client
            onCredentialsReceived(creds)
            writer.println("OK")

            onSuccess(creds)
            return HandshakeResult.PAIRED
        } catch (e: Exception) {
            try {
                writer.println(CLIENT_ERROR)
            } catch (_: Exception) {}
            onError("Pairing failed: ${e.message}")
            return if (e is WrongPairingPinException) HandshakeResult.WRONG_PIN else HandshakeResult.REJECTED
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    /** Reads one `\n`-terminated UTF-8 line of at most [MAX_LINE_BYTES]; null at end of stream. */
    private fun readLimitedLine(input: java.io.InputStream): String? {
        val buffer = java.io.ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b == -1) return if (buffer.size() == 0) null else buffer.toString(Charsets.UTF_8.name())
            if (b == '\n'.code) return buffer.toString(Charsets.UTF_8.name()).removeSuffix("\r")
            if (buffer.size() >= MAX_LINE_BYTES) throw java.net.ProtocolException("Pairing message too long")
            buffer.write(b)
        }
    }

    private fun registerNsdService(port: Int) {
        val nsd = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
        nsdManager = nsd

        val serviceInfo = NsdServiceInfo().apply {
            serviceName = SERVICE_NAME
            serviceType = SERVICE_TYPE
            this.port = port
        }

        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {}
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {}
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
        }
        registrationListener = listener

        try {
            nsd.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (_: Exception) {}
    }

    fun stopServer() {
        try {
            serverSocket?.close()
            serverSocket = null
        } catch (_: Exception) {}

        try {
            val listener = registrationListener
            if (listener != null) {
                nsdManager?.unregisterService(listener)
                registrationListener = null
            }
        } catch (_: Exception) {}
    }
}
