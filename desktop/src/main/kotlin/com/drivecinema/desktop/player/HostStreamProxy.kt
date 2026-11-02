package com.drivecinema.desktop.player

import com.drivecinema.app.core.host.HostLibrarySync
import com.drivecinema.app.core.host.HostProtocol
import com.drivecinema.app.core.logging.AppLog
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import okhttp3.Request
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Task I.2, desktop as a client: VLC can't pin a certificate or send a device key, so it plays another PC's file
 * from `http://127.0.0.1:<port>/<secret>/host/<key>`, and this proxy forwards each (range) request over the PC's
 * pinned HTTPS client. Loopback only; the random [secret] keeps other programs on this computer out (as in
 * [DriveStreamProxy]).
 */
class HostStreamProxy(private val hosts: HostLibrarySync) : AutoCloseable {
    private val secret: String = ByteArray(24).also { SecureRandom().nextBytes(it) }
        .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }

    private val executor: ExecutorService = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "host-stream").apply { isDaemon = true }
    }

    private val server: HttpServer =
        HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 16).apply {
            createContext("/") { exchange -> handle(exchange) }
            executor = this@HostStreamProxy.executor
            start()
        }

    /** The URL VLC opens for a `dchost://` copy. */
    fun urlFor(sourceKey: String): String =
        "http://127.0.0.1:${server.address.port}/$secret/host/" +
            Base64.getUrlEncoder().withoutPadding().encodeToString(sourceKey.toByteArray(Charsets.UTF_8))

    private fun handle(exchange: HttpExchange) {
        exchange.use {
            val parts = exchange.requestURI.rawPath.trimStart('/').split('/')
            val sourceKey = runCatching { String(Base64.getUrlDecoder().decode(parts.getOrNull(2).orEmpty()), Charsets.UTF_8) }.getOrNull()
            if (parts.size != 3 || parts[0] != secret || parts[1] != "host" || sourceKey == null || HostProtocol.parse(sourceKey) == null) {
                exchange.sendResponseHeaders(404, -1)
                return
            }
            val method = exchange.requestMethod.uppercase()
            if (method != "GET" && method != "HEAD") {
                exchange.sendResponseHeaders(405, -1)
                return
            }
            val (url, api) = hosts.stream(sourceKey) ?: run {
                exchange.sendResponseHeaders(503, -1)
                return
            }
            val request = Request.Builder().url(url)
                .apply { exchange.requestHeaders.getFirst("Range")?.let { header("Range", it) } }
                .method(method, null)
                .build()
            try {
                api.client.newCall(request).execute().use { upstream ->
                    for (name in FORWARDED_HEADERS) upstream.header(name)?.let { exchange.responseHeaders.set(name, it) }
                    val body = upstream.body
                    val length = upstream.header("Content-Length")?.toLongOrNull()
                    if (method == "HEAD" || body == null) {
                        exchange.sendResponseHeaders(upstream.code, -1)
                        return
                    }
                    exchange.sendResponseHeaders(upstream.code, length?.takeIf { it > 0 } ?: 0)
                    body.byteStream().use { input -> exchange.responseBody.use { input.copyTo(it, BUFFER_SIZE) } }
                }
            } catch (e: IOException) {
                // VLC closes connections when it seeks; that is normal.
                AppLog.d(TAG, "PC stream connection ended: ${e.message}")
                runCatching { exchange.sendResponseHeaders(502, -1) }
            }
        }
    }

    override fun close() {
        server.stop(0)
        executor.shutdownNow()
    }

    private companion object {
        const val TAG = "HostStreamProxy"
        const val BUFFER_SIZE = 256 * 1024
        val FORWARDED_HEADERS = listOf("Content-Type", "Content-Range", "Accept-Ranges")
    }
}
