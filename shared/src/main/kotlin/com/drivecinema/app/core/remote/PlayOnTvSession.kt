package com.drivecinema.app.core.remote

import com.drivecinema.app.core.host.PairingLines
import com.drivecinema.app.core.logging.AppLog
import com.drivecinema.app.core.pairing.SrpCryptoHelper
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.util.encoders.Hex
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.ProtocolException
import java.security.SecureRandom

/**
 * The per-connection key (design §4): HKDF-SHA256 of the link key, salted with the TV's fresh nonce. Only a holder of
 * the link key can make a box the TV opens, and a box recorded on an earlier connection doesn't open under a new nonce.
 */
internal object PlayOnTvCrypto {
    private const val INFO = "DriveCinema-PlayOnTV-v1"

    fun sessionKey(linkKey: String, nonce: ByteArray): ByteArray {
        val hkdf = HKDFBytesGenerator(SHA256Digest())
        hkdf.init(HKDFParameters(DeviceLink.decodeKey(linkKey), nonce, INFO.toByteArray(Charsets.UTF_8)))
        return ByteArray(32).also { hkdf.generateBytes(it, 0, 32) }
    }

    fun seal(key: ByteArray, text: String): String = Hex.toHexString(SrpCryptoHelper.encryptPayload(key, text))

    /** Null when the box doesn't open (wrong key, tampered, or not hex). */
    fun open(key: ByteArray, box: String): String? = runCatching { SrpCryptoHelper.decryptPayload(key, Hex.decode(box)) }.getOrNull()
}

/**
 * The TV's side of one connection: hello with a fresh nonce, one sealed request from a linked phone, one sealed reply.
 * [linkFor] finds a linked phone by ID; [dispatch] acts on a valid request and says what to answer.
 */
class PlayOnTvServerSession(
    private val tvId: String,
    private val linkFor: (phoneId: String) -> DeviceLink?,
    private val random: SecureRandom = SecureRandom()
) {
    sealed interface Result {
        data class Delivered(val request: PlayRequest, val phone: DeviceLink, val status: PlayStatus) : Result
        /** A phone this TV doesn't know, or a box that doesn't open: counts toward [AuthFailureThrottle]. */
        data object Unauthorized : Result
        /** Linked and authentic, but not a valid request. */
        data object BadRequest : Result
        /** Not our protocol at all (scan, junk, closed early). */
        data object Rejected : Result
    }

    fun handle(input: InputStream, output: OutputStream, dispatch: (PlayRequest, DeviceLink) -> PlayStatus): Result = try {
        val nonce = ByteArray(16).also(random::nextBytes)
        PairingLines.write(output, JSONObject().put("v", PlayOnTvProtocol.VERSION).put("tv", tvId).put("nonce", Hex.toHexString(nonce)).toString())

        val message = JSONObject(PairingLines.read(input) ?: throw ProtocolException("No request"))
        val phone = message.optString("phone").takeIf(PlayOnTvProtocol::isValidId)?.let(linkFor)
        val key = phone?.let { PlayOnTvCrypto.sessionKey(it.key, nonce) }
        val plain = key?.let { PlayOnTvCrypto.open(it, message.optString("box")) }
        if (phone == null || key == null || plain == null) {
            PairingLines.write(output, JSONObject().put("error", ERROR_UNKNOWN_PHONE).toString())
            Result.Unauthorized
        } else {
            val request = PlayRequest.parse(plain)
            val status = if (request == null) PlayStatus.BAD_REQUEST else dispatch(request, phone)
            PairingLines.write(output, JSONObject().put("box", PlayOnTvCrypto.seal(key, JSONObject().put("status", status.name).toString())).toString())
            if (request == null) Result.BadRequest else Result.Delivered(request, phone, status)
        }
    } catch (e: Exception) {
        AppLog.w(TAG, "Play on TV connection refused: ${e.javaClass.simpleName}")
        Result.Rejected
    }

    companion object {
        const val ERROR_UNKNOWN_PHONE = "UNKNOWN_PHONE"
        private const val TAG = "PlayOnTv"
    }
}

/** Why sending to the TV failed after a connection was made. */
class PlayOnTvException(val kind: Kind, message: String) : IOException(message) {
    enum class Kind {
        /** Something else answered at that address (another TV, or the TV after a reset): look the TV up again. */
        WRONG_TV,
        /** The TV doesn't know this phone's link any more. */
        UNKNOWN_PHONE,
        /** An answer that isn't the protocol, or a reply that doesn't open. */
        PROTOCOL
    }
}

/** The phone's side of one connection to the TV in [tv]. */
object PlayOnTvClient {
    fun exchange(input: InputStream, output: OutputStream, phoneId: String, tv: DeviceLink, request: PlayRequest): PlayStatus {
        val hello = runCatching { JSONObject(PairingLines.read(input)!!) }.getOrNull()
            ?: throw PlayOnTvException(PlayOnTvException.Kind.PROTOCOL, "No hello from the TV")
        if (hello.optString("tv") != tv.id) throw PlayOnTvException(PlayOnTvException.Kind.WRONG_TV, "Another device answered")
        val nonce = runCatching { Hex.decode(hello.getString("nonce")) }.getOrNull()?.takeIf { it.size == 16 }
            ?: throw PlayOnTvException(PlayOnTvException.Kind.PROTOCOL, "Bad hello from the TV")

        val key = PlayOnTvCrypto.sessionKey(tv.key, nonce)
        PairingLines.write(output, JSONObject().put("phone", phoneId).put("box", PlayOnTvCrypto.seal(key, request.toJson())).toString())

        val reply = runCatching { JSONObject(PairingLines.read(input)!!) }.getOrNull()
            ?: throw PlayOnTvException(PlayOnTvException.Kind.PROTOCOL, "No answer from the TV")
        if (reply.optString("error") == PlayOnTvServerSession.ERROR_UNKNOWN_PHONE) {
            throw PlayOnTvException(PlayOnTvException.Kind.UNKNOWN_PHONE, "The TV doesn't know this phone")
        }
        // The reply opens only with the link key: it proves this was the linked TV, not just something at its address.
        val plain = PlayOnTvCrypto.open(key, reply.optString("box"))
            ?: throw PlayOnTvException(PlayOnTvException.Kind.PROTOCOL, "The TV's answer could not be verified")
        return runCatching { PlayStatus.valueOf(JSONObject(plain).getString("status")) }.getOrNull()
            ?: throw PlayOnTvException(PlayOnTvException.Kind.PROTOCOL, "Unknown answer from the TV")
    }
}

/**
 * After [maxFailures] unauthorized connections within [windowMs], the TV ignores connections for [pauseMs] (the F-07
 * spirit: guessing is slow). Thread-safe; [clock] is injectable for tests.
 */
class AuthFailureThrottle(
    private val clock: () -> Long = System::currentTimeMillis,
    private val maxFailures: Int = 5,
    private val windowMs: Long = 60_000,
    private val pauseMs: Long = 60_000
) {
    private val failures = ArrayDeque<Long>()
    private var pausedUntil = 0L

    @Synchronized
    fun allowed(): Boolean = clock() >= pausedUntil

    @Synchronized
    fun recordFailure() {
        val now = clock()
        failures.addLast(now)
        while (failures.isNotEmpty() && now - failures.first() > windowMs) failures.removeFirst()
        if (failures.size >= maxFailures) {
            pausedUntil = now + pauseMs
            failures.clear()
        }
    }
}
