package com.drivecinema.desktop.auth

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.Executors

/**
 * One-shot HTTP server on `127.0.0.1:<random port>` that receives Google's redirect after the user signs in
 * in the browser (RFC 8252 loopback redirect). Only reachable from this computer.
 */
class LoopbackReceiver private constructor(private val server: HttpServer) : AutoCloseable {

    private val redirect = CompletableDeferred<String?>()

    /** e.g. `http://127.0.0.1:41817` — must be registered as-is in the authorization request. */
    val redirectUri: String = "http://127.0.0.1:${server.address.port}"

    init {
        server.createContext("/") { exchange ->
            exchange.use {
                if (exchange.requestURI.path != "/") {
                    // Browsers also ask for /favicon.ico; keep waiting for the real redirect.
                    exchange.sendResponseHeaders(404, -1)
                    return@use
                }
                val page = PAGE.toByteArray(Charsets.UTF_8)
                exchange.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
                exchange.sendResponseHeaders(200, page.size.toLong())
                exchange.responseBody.use { it.write(page) }
                redirect.complete(exchange.requestURI.rawQuery)
            }
        }
        server.executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "oauth-loopback").apply { isDaemon = true }
        }
        server.start()
    }

    /** Waits for the redirect and returns its raw query string (`code=...&state=...`). */
    suspend fun awaitRedirectQuery(timeoutMillis: Long = DEFAULT_TIMEOUT_MS): String? =
        withTimeout(timeoutMillis) { redirect.await() }

    override fun close() {
        server.stop(0)
        (server.executor as? java.util.concurrent.ExecutorService)?.shutdownNow()
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 5 * 60_000L

        fun start(): LoopbackReceiver =
            LoopbackReceiver(HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0))

        private const val PAGE = """<!doctype html><html><head><meta charset="utf-8"><title>DriveCinema</title></head>
<body style="background:#0C0D10;color:#F0F2F5;font-family:sans-serif;text-align:center;padding-top:80px">
<h1 style="color:#E50914">DriveCinema</h1><p>Sign-in finished. You can close this tab and return to the app.</p></body></html>"""
    }
}
