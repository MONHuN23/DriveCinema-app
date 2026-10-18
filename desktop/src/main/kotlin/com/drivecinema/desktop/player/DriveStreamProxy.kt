package com.drivecinema.desktop.player

import com.drivecinema.app.core.logging.AppLog
import com.drivecinema.app.core.player.DriveStreamUrls
import com.drivecinema.app.core.player.TokenProvider
import com.drivecinema.app.core.player.TokenRefreshInterceptor
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Lets VLC stream Drive files: VLC reads `http://127.0.0.1:<port>/<secret>/drive/<fileId>` and the proxy forwards
 * each (range) request to Drive with the access token — the same OkHttp + [TokenRefreshInterceptor] stack as the
 * Android player (refresh on 401, no `Authorization` header on redirects to other hosts). The token never appears
 * in a URL, and the random [secret] keeps other programs on this computer from using the signed-in session.
 */
class DriveStreamProxy(
    tokenProvider: TokenProvider,
    baseClient: OkHttpClient = OkHttpClient(),
    private val mediaUrl: (String) -> String = DriveStreamUrls::media,
    private val clock: () -> Long = System::currentTimeMillis
) : AutoCloseable {

    /**
     * Drive answers every media request with a redirect to a signed download link on another server. Each VLC
     * start or seek is a new request, so remembering that link per file saves one full connection setup (TLS)
     * per seek — noticeable on slow or high-latency networks such as a phone hotspot.
     */
    private data class DirectLink(val url: String, val expiresAt: Long)
    private val directLinks = ConcurrentHashMap<String, DirectLink>()

    /**
     * Streaming gets its own connections over HTTP/1.1. Sharing one HTTP/2 connection (with the library sync or
     * with VLC's own parallel range requests) lets a stream VLC is not reading at the moment fill the connection's
     * flow-control window, which stalls every other request on it: slow start, then timeouts.
     */
    internal val client = baseClient.newBuilder()
        .connectionPool(ConnectionPool())
        .protocols(listOf(Protocol.HTTP_1_1))
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(TokenRefreshInterceptor(tokenProvider))
        .build()

    private val secret: String = ByteArray(24).also { SecureRandom().nextBytes(it) }
        .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }

    private val executor: ExecutorService = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "drive-stream").apply { isDaemon = true }
    }

    private val server: HttpServer =
        HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 16).apply {
            createContext("/") { exchange -> handle(exchange) }
            executor = this@DriveStreamProxy.executor
            start()
        }

    /** The URL VLC opens for a Drive file. */
    fun urlFor(fileId: String): String =
        "http://127.0.0.1:${server.address.port}/$secret/drive/${URLEncoder.encode(fileId, Charsets.UTF_8)}"

    private fun handle(exchange: HttpExchange) {
        exchange.use {
            val parts = exchange.requestURI.rawPath.trimStart('/').split('/')
            if (parts.size != 3 || parts[0] != secret || parts[1] != "drive" || parts[2].isEmpty()) {
                exchange.sendResponseHeaders(404, -1)
                return
            }
            val method = exchange.requestMethod.uppercase()
            if (method != "GET" && method != "HEAD") {
                exchange.sendResponseHeaders(405, -1)
                return
            }
            val fileId = URLDecoder.decode(parts[2], Charsets.UTF_8)
            val range = exchange.requestHeaders.getFirst("Range")
            val started = clock()

            try {
                openUpstream(fileId, range).use { (upstream, viaDirectLink) ->
                    AppLog.d(
                        TAG,
                        "${range ?: "full file"} -> ${upstream.code} after ${clock() - started} ms" +
                            if (viaDirectLink) " (remembered download link)" else ""
                    )
                    for (name in FORWARDED_HEADERS) {
                        upstream.header(name)?.let { exchange.responseHeaders.set(name, it) }
                    }
                    val body = upstream.body
                    val length = upstream.header("Content-Length")?.toLongOrNull()
                    if (!upstream.isSuccessful) {
                        AppLog.w(TAG, "Drive answered ${upstream.code} for a stream request")
                    }
                    if (method == "HEAD" || body == null) {
                        exchange.sendResponseHeaders(upstream.code, -1)
                        return
                    }
                    // 0 = chunked when Drive did not send a length.
                    exchange.sendResponseHeaders(upstream.code, length?.takeIf { it > 0 } ?: 0)
                    val copyStarted = clock()
                    var copied = 0L
                    try {
                        body.byteStream().use { input ->
                            exchange.responseBody.use { output ->
                                val buffer = ByteArray(BUFFER_SIZE)
                                while (true) {
                                    val read = input.read(buffer)
                                    if (read < 0) break
                                    output.write(buffer, 0, read)
                                    copied += read
                                }
                            }
                        }
                    } finally {
                        AppLog.d(TAG, StreamStats.describe(copied, clock() - copyStarted))
                    }
                }
            } catch (e: IOException) {
                // VLC closes connections when it seeks; that is normal.
                AppLog.d(TAG, "Stream connection ended: ${e.message}")
                runCatching { exchange.sendResponseHeaders(502, -1) }
            }
        }
    }

    /** Opens the Drive response for [fileId], through the remembered download link when there is a fresh one. */
    private fun openUpstream(fileId: String, range: String?): UpstreamResponse {
        directLinks[fileId]?.takeIf { it.expiresAt > clock() }?.let { link ->
            val response = client.newCall(get(link.url, range)).execute()
            if (response.isSuccessful) return UpstreamResponse(response, viaDirectLink = true)
            // Expired or refused: forget it and go through Drive again.
            response.close()
            directLinks.remove(fileId)
        }
        val response = client.newCall(get(mediaUrl(fileId), range)).execute()
        val finalUrl = response.request.url
        if (response.isSuccessful && finalUrl.toString() != mediaUrl(fileId)) {
            directLinks[fileId] = DirectLink(finalUrl.toString(), clock() + DriveStreamProxyTimings.DIRECT_LINK_TTL_MS)
        }
        return UpstreamResponse(response, viaDirectLink = false)
    }

    private fun get(url: String, range: String?): Request =
        Request.Builder().url(url).get().apply { range?.let { header("Range", it) } }.build()

    private class UpstreamResponse(val response: Response, val viaDirectLink: Boolean) : java.io.Closeable {
        operator fun component1() = response
        operator fun component2() = viaDirectLink
        override fun close() = response.close()
    }

    override fun close() {
        server.stop(0)
        executor.shutdownNow()
    }

    private companion object {
        const val TAG = "DriveStreamProxy"
        const val BUFFER_SIZE = 256 * 1024
        val FORWARDED_HEADERS = listOf("Content-Type", "Content-Length", "Content-Range", "Accept-Ranges", "Last-Modified", "ETag")
    }
}

object DriveStreamProxyTimings {
    /** Drive's signed download links stay valid for longer; on any failure the proxy asks Drive again. */
    const val DIRECT_LINK_TTL_MS = 10 * 60_000L
}

/** Download speed of one stream request, for the log: tells a slow network apart from a slow computer. */
object StreamStats {
    fun describe(bytes: Long, millis: Long): String {
        val mb = bytes / 1_000_000.0
        val seconds = millis / 1000.0
        val mbit = if (millis > 0) bytes * 8 / 1_000.0 / millis else 0.0
        return String.format(java.util.Locale.US, "streamed %.1f MB in %.1f s (%.1f Mbit/s)", mb, seconds, mbit)
    }
}
