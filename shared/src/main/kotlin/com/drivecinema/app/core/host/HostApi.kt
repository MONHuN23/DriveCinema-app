package com.drivecinema.app.core.host

import com.drivecinema.app.core.local.DocumentTreeSource
import com.drivecinema.app.core.local.LocalDocument
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/** A library folder the PC shares. */
data class HostRoot(val id: String, val name: String)

/**
 * Trusts exactly one certificate: the one the PC sent inside the PIN-protected pairing (design §2.3). No CA, no
 * "trust all"; a different certificate (another computer, a man in the middle) fails the TLS handshake.
 */
class PinnedCertificateTrustManager(private val fingerprint: String) : X509TrustManager {
    override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
        if (chain.isEmpty() || HostIdentity.fingerprintOf(chain[0]) != fingerprint) {
            throw CertificateException("Not the paired DriveCinema PC")
        }
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) =
        throw CertificateException("Client certificates are not used")

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}

/**
 * Talks to one paired PC (Task I.2). Every request carries the device key and goes over TLS pinned to that PC's
 * certificate. The Google token is never involved. [addresses] are tried in order (home address first, then the VPN
 * address), see [connect].
 */
class HostApi(
    val pairing: HostPairing,
    baseClient: OkHttpClient = OkHttpClient()
) {
    private val trustManager = PinnedCertificateTrustManager(pairing.certFingerprint)

    /** For listings and for the player (the same pinned client). */
    val client: OkHttpClient = baseClient.newBuilder()
        .sslSocketFactory(
            SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustManager), SecureRandom()) }.socketFactory,
            trustManager
        )
        // The certificate is pinned, so the host name (an IP address) needs no separate check.
        .hostnameVerifier(HostnameVerifier { _, session ->
            runCatching { HostIdentity.fingerprintOf(session.peerCertificates[0]) == pairing.certFingerprint }.getOrDefault(false)
        })
        .addInterceptor { chain -> chain.proceed(chain.request().newBuilder().header("Authorization", pairing.authorization).build()) }
        .followRedirects(false)
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    @Volatile var activeAddress: String? = null
        private set

    /** Finds an address that answers (home first, then VPN); null when the PC can't be reached. */
    fun connect(addresses: List<String>): String? {
        removedByPc = false
        for (address in addresses.filter { it.isNotBlank() }.distinct()) {
            val code = runCatching {
                client.newCall(Request.Builder().url(url(address, "v1", "ping")).build()).execute().use { it.code }
            }.getOrNull()
            if (code != null && code in 200..299) {
                activeAddress = address
                return address
            }
            // The pinned PC itself answered "unknown device key": it removed this device (not a lockout, not offline).
            if (code == 401) removedByPc = true
        }
        activeAddress = null
        return null
    }

    /** After [connect]: true when the PC (proven by its pinned certificate) no longer accepts this device's key. */
    @Volatile var removedByPc: Boolean = false
        private set

    fun roots(): List<HostRoot> {
        val json = getJson("v1", "roots")
        val array = json.getJSONArray("roots")
        return (0 until array.length()).map { array.getJSONObject(it).let { r -> HostRoot(r.getString("id"), r.getString("name")) } }
    }

    fun children(folderId: String): List<LocalDocument> {
        val array = getJson("v1", "tree", folderId).getJSONArray("children")
        return (0 until array.length()).map { i ->
            val c = array.getJSONObject(i)
            LocalDocument(
                documentId = c.getString("id"),
                name = c.getString("name"),
                mimeType = if (c.optBoolean("folder")) LocalDocument.FOLDER_MIME_TYPE else "",
                sizeBytes = if (c.has("size")) c.getLong("size") else null
            )
        }
    }

    /** The HTTPS URL the player streams (with [client]); null while the PC isn't reachable. */
    fun fileUrl(fileId: String): String? = activeAddress?.let { url(it, "v1", "file", fileId).toString() }

    /**
     * Task I.2 slow-connection check: reads the first [sampleBytes] of a file and returns Mbit/s, timed from the first
     * byte (connection setup not counted). Stops after [timeoutMs] and uses what arrived; null if nothing did.
     */
    fun measureMbps(fileId: String, sampleBytes: Int = ConnectionCheck.SAMPLE_BYTES, timeoutMs: Long = 4_000): Double? {
        val url = fileUrl(fileId) ?: return null
        val request = Request.Builder().url(url).header("Range", "bytes=0-${sampleBytes - 1}").build()
        return runCatching {
            client.newBuilder().readTimeout(timeoutMs, TimeUnit.MILLISECONDS).build().newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val input = response.body!!.byteStream()
                val buffer = ByteArray(64 * 1024)
                val first = input.read(buffer)
                if (first <= 0) return null
                val start = System.nanoTime()
                val deadline = start + timeoutMs * 1_000_000
                var total = 0L
                while (total < sampleBytes - first && System.nanoTime() < deadline) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                }
                if (total == 0L) null else ConnectionCheck.measuredMbps(total, System.nanoTime() - start)
            }
        }.getOrNull()
    }

    private fun getJson(vararg path: String): JSONObject {
        val address = activeAddress ?: throw IOException("The PC is not reachable")
        client.newCall(Request.Builder().url(url(address, *path)).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("The PC answered ${response.code}")
            return JSONObject(response.body!!.string())
        }
    }

    private fun url(address: String, vararg path: String): HttpUrl = HttpUrl.Builder()
        .scheme("https")
        .host(address)
        .port(pairing.httpsPort)
        .apply { path.forEach { addPathSegment(it) } }
        .build()
}

/**
 * The PC's shared folders as a [DocumentTreeSource], so the existing local crawler and indexer (same layout rules,
 * same safe matching, "Review possible duplicates") handle them unchanged (design §2.4 option a).
 */
class HostTreeSource(private val api: HostApi) : DocumentTreeSource {
    override fun rootDocumentId(treeUri: String): String =
        HostProtocol.parse(treeUri)?.takeIf { it.second == "root" }?.third ?: throw IOException("Not a PC folder: $treeUri")

    override fun listChildren(treeUri: String, parentDocumentId: String): List<LocalDocument> = api.children(parentDocumentId)

    override fun documentUri(treeUri: String, documentId: String): String = HostProtocol.fileSourceKey(api.pairing.hostId, documentId)
}
