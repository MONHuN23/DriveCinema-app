package com.drivecinema.app.core.player

import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.okhttp.OkHttpDataSource
import okhttp3.OkHttpClient

/**
 * Task I.2: opens a paired PC's `https://` URLs with that PC's pinned client (device key + its own certificate), and
 * everything else with the Drive stack. Even a mix-up couldn't leak the Google token: the Drive stack only sends it to
 * Google hosts (report F-02), and the PC client has no token at all.
 */
class HostRoutingDataSource(
    private val fallback: DataSource,
    private val hostClient: (host: String?, port: Int) -> OkHttpClient?
) : DataSource {
    private val listeners = mutableListOf<TransferListener>()
    private var current: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        listeners += transferListener
        fallback.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val uri = dataSpec.uri
        val client = if (uri.scheme == "https") hostClient(uri.host, uri.port) else null
        val source = client?.let { pinned ->
            OkHttpDataSource.Factory(pinned).createDataSource().also { s -> listeners.forEach(s::addTransferListener) }
        } ?: fallback
        current = source
        return source.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        current?.read(buffer, offset, length) ?: throw IllegalStateException("Not opened")

    override fun getUri(): Uri? = current?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = current?.responseHeaders ?: emptyMap()

    override fun close() {
        try {
            current?.close()
        } finally {
            current = null
        }
    }

    class Factory(
        private val fallback: DataSource.Factory,
        private val hostClient: (host: String?, port: Int) -> OkHttpClient?
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource = HostRoutingDataSource(fallback.createDataSource(), hostClient)
    }
}
