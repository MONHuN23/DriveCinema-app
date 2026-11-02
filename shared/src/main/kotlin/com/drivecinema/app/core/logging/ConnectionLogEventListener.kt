package com.drivecinema.app.core.logging

import okhttp3.Call
import okhttp3.EventListener
import okhttp3.Protocol
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy

/**
 * Task M.0 / report F-12 (debug builds only): one [Perf.TAG] line for every new connection a client opens,
 * `net.connect 85 ms client=drive host=www.googleapis.com protocol=h2`. A reused pooled connection logs nothing,
 * so the lines count TCP + TLS handshakes per client. Also counts them in the `net` counter.
 */
class ConnectionLogEventListener private constructor(private val client: String) : EventListener() {

    private var connectStartNanos = 0L

    override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) {
        connectStartNanos = Perf.clock()
    }

    override fun connectEnd(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy, protocol: Protocol?) {
        Perf.counter(COUNTER).incrementAndGet()
        log(call, mapOf("protocol" to (protocol?.toString() ?: "?")))
    }

    override fun connectFailed(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy, protocol: Protocol?, ioe: IOException) {
        log(call, mapOf("failed" to true))
    }

    private fun log(call: Call, extra: Map<String, Any>) {
        val values = linkedMapOf<String, Any>("client" to client, "host" to call.request().url.host) + extra
        AppLog.i(Perf.TAG, Perf.formatLine("net.connect", Perf.clock() - connectStartNanos, values))
    }

    companion object {
        const val COUNTER = "net"

        /** A listener per call, labelled with the [client] that made it (e.g. "drive", "tmdb", "images"). */
        fun factory(client: String): EventListener.Factory {
            Perf.counter(COUNTER)
            return EventListener.Factory { ConnectionLogEventListener(client) }
        }
    }
}
