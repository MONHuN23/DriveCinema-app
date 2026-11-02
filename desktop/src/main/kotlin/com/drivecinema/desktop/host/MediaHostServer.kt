package com.drivecinema.desktop.host

import com.drivecinema.app.core.host.HostAddressPolicy
import com.drivecinema.app.core.host.HostIdentity
import com.drivecinema.app.core.logging.AppLog
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpsConfigurator
import com.sun.net.httpserver.HttpsServer
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.channels.Channels
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext

/** A `Range` header resolved against a file length. */
sealed interface ByteRange {
    data object Whole : ByteRange
    data class Part(val start: Long, val endInclusive: Long) : ByteRange
    data object Unsatisfiable : ByteRange

    companion object {
        /** One range only (players never ask for several): `bytes=a-b`, `bytes=a-`, `bytes=-n`. */
        fun parse(header: String?, length: Long): ByteRange {
            if (header.isNullOrBlank()) return Whole
            val spec = header.trim().removePrefix("bytes=").takeIf { it != header.trim() && !it.contains(',') } ?: return Unsatisfiable
            val start = spec.substringBefore('-').trim()
            val end = spec.substringAfter('-', "").trim()
            return try {
                when {
                    start.isEmpty() && end.isNotEmpty() -> {
                        val suffix = end.toLong()
                        if (suffix <= 0 || length == 0L) Unsatisfiable else Part(maxOf(0, length - suffix), length - 1)
                    }
                    start.isNotEmpty() -> {
                        val from = start.toLong()
                        val to = if (end.isEmpty()) length - 1 else minOf(end.toLong(), length - 1)
                        if (from < 0 || from >= length || to < from) Unsatisfiable else Part(from, to)
                    }
                    else -> Unsatisfiable
                }
            } catch (e: NumberFormatException) {
                Unsatisfiable
            }
        }
    }
}

/**
 * The PC's HTTPS server for paired devices (Task I.2, design §2.3): read-only folder listings and video files with
 * Range requests (seeking). Every request must come from an allowed address ([HostAddressPolicy]; VPN ranges only
 * with [allowVpn]) and carry a paired device's key; repeated wrong keys from one address are refused for a while.
 * Only GET and HEAD exist. Runs only while sharing is on.
 */
class MediaHostServer(
    identity: HostIdentity,
    private val hostId: String,
    private val hostName: () -> String,
    private val index: HostFileIndex,
    private val devices: HostDeviceRegistry,
    private val allowVpn: () -> Boolean,
    port: Int,
    private val clock: () -> Long = System::currentTimeMillis
) : AutoCloseable {
    private val executor: ExecutorService = Executors.newFixedThreadPool(8) { r -> Thread(r, "media-host").apply { isDaemon = true } }
    private val failures = ConcurrentHashMap<String, MutableList<Long>>()
    private val server: HttpsServer

    init {
        val password = CharArray(16) { 'a' + SecureRandom().nextInt(26) }
        val keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
            .apply { init(identity.toKeyStore(password), password) }.keyManagers
        val tls = SSLContext.getInstance("TLS").apply { init(keyManagers, null, SecureRandom()) }
        server = HttpsServer.create(InetSocketAddress(port), 32)
        server.httpsConfigurator = HttpsConfigurator(tls)
        server.executor = executor
        server.createContext("/") { exchange ->
            try {
                handle(exchange)
            } catch (e: Exception) {
                AppLog.w(TAG, "Request failed: ${e.javaClass.simpleName}")
            } finally {
                exchange.close()
            }
        }
        server.start()
    }

    val port: Int get() = server.address.port

    private fun handle(exchange: HttpExchange) {
        val remote = exchange.remoteAddress.address
        if (!HostAddressPolicy.isAllowed(remote, allowVpn())) return respond(exchange, 403)
        val ip = remote.hostAddress
        if (isLockedOut(ip)) return respond(exchange, 429)
        if (devices.authorize(exchange.requestHeaders.getFirst("Authorization")) == null) {
            recordFailure(ip)
            return respond(exchange, 401)
        }
        if (exchange.requestMethod != "GET" && exchange.requestMethod != "HEAD") return respond(exchange, 405)

        val path = exchange.requestURI.rawPath.trim('/').split('/')
        when {
            path == listOf("v1", "ping") -> json(exchange, JSONObject().put("hostId", hostId).put("name", hostName()))
            path == listOf("v1", "roots") -> json(exchange, JSONObject().put("roots", JSONArray(index.roots().map { JSONObject().put("id", it.id).put("name", it.name) })))
            path.size == 3 && path[0] == "v1" && path[1] == "tree" -> {
                val children = index.children(path[2]) ?: return respond(exchange, 404)
                json(exchange, JSONObject().put("children", JSONArray(children.map { child ->
                    JSONObject().put("id", child.id).put("name", child.name).put("folder", child.isFolder).apply { child.sizeBytes?.let { put("size", it) } }
                })))
            }
            path.size == 3 && path[0] == "v1" && path[1] == "file" -> {
                val file = index.file(path[2]) ?: return respond(exchange, 404)
                sendFile(exchange, file)
            }
            else -> respond(exchange, 404)
        }
    }

    private fun sendFile(exchange: HttpExchange, file: Path) {
        FileChannel.open(file, StandardOpenOption.READ).use { channel ->
            val length = channel.size()
            val headers = exchange.responseHeaders
            headers["Accept-Ranges"] = "bytes"
            headers["Content-Type"] = "application/octet-stream"
            val (status, start, count) = when (val range = ByteRange.parse(exchange.requestHeaders.getFirst("Range"), length)) {
                ByteRange.Whole -> Triple(200, 0L, length)
                is ByteRange.Part -> {
                    headers["Content-Range"] = "bytes ${range.start}-${range.endInclusive}/$length"
                    Triple(206, range.start, range.endInclusive - range.start + 1)
                }
                ByteRange.Unsatisfiable -> {
                    headers["Content-Range"] = "bytes */$length"
                    return respond(exchange, 416)
                }
            }
            if (exchange.requestMethod == "HEAD") {
                headers["Content-Length"] = count.toString()
                exchange.sendResponseHeaders(status, -1)
                return
            }
            exchange.sendResponseHeaders(status, if (count == 0L) -1 else count)
            val out = Channels.newChannel(exchange.responseBody)
            var position = start
            var left = count
            try {
                while (left > 0) {
                    val sent = channel.transferTo(position, minOf(left, CHUNK), out)
                    if (sent <= 0) break
                    position += sent
                    left -= sent
                }
            } catch (e: IOException) {
                // The player closed the connection (seek, stop): normal.
            }
        }
    }

    private fun json(exchange: HttpExchange, body: JSONObject) {
        val bytes = body.toString().toByteArray(Charsets.UTF_8)
        exchange.responseHeaders["Content-Type"] = "application/json; charset=utf-8"
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun respond(exchange: HttpExchange, status: Int) {
        exchange.sendResponseHeaders(status, -1)
    }

    private fun isLockedOut(ip: String): Boolean {
        val recent = failures[ip] ?: return false
        synchronized(recent) {
            recent.removeAll { it < clock() - LOCKOUT_WINDOW_MS }
            return recent.size >= MAX_FAILURES
        }
    }

    private fun recordFailure(ip: String) {
        val list = failures.getOrPut(ip) { mutableListOf() }
        synchronized(list) { list += clock() }
        AppLog.w(TAG, "Refused a request without a valid device key from $ip")
    }

    override fun close() {
        server.stop(0)
        executor.shutdownNow()
    }

    private companion object {
        const val TAG = "MediaHostServer"
        const val CHUNK = 1L shl 20
        const val MAX_FAILURES = 20
        const val LOCKOUT_WINDOW_MS = 10 * 60_000L
    }
}
