package com.drivecinema.app.core.logging

import android.content.Context
import android.util.Log
import androidx.room.RoomDatabase
import coil.Coil
import coil.EventListener
import coil.ImageLoader
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.drivecinema.app.BuildConfig
import okhttp3.OkHttpClient
import java.util.concurrent.Executors

/**
 * Task M.0, debug builds only: the extra measurements for the TV baseline. In a release build every function
 * here returns what the app used before (a plain `OkHttpClient()`, Coil's default image loader, no query callback).
 */
object DebugPerf {

    const val DB_TAG = "PerfDb"
    const val DB_COUNTER = "db"
    const val IMAGE_COUNTER = "img"
    const val IMAGE_NETWORK_COUNTER = "imgNet"

    /** A client like the one each component builds today, plus a log line per new connection (F-12). */
    fun httpClient(label: String): OkHttpClient =
        if (BuildConfig.DEBUG) {
            OkHttpClient.Builder().eventListenerFactory(ConnectionLogEventListener.factory(label)).build()
        } else {
            OkHttpClient()
        }

    /** Coil's default loader with a log line per image request and its source (memory, disk, network). */
    fun installImageLoader(context: Context) {
        if (!BuildConfig.DEBUG) return
        Perf.counter(IMAGE_COUNTER)
        Perf.counter(IMAGE_NETWORK_COUNTER)
        Coil.setImageLoader(
            ImageLoader.Builder(context)
                .okHttpClient(httpClient("images"))
                .eventListenerFactory(object : EventListener.Factory {
                    override fun create(request: ImageRequest): EventListener = ImageRequestLogger()
                })
                .build()
        )
    }

    /** Counts every Room query (`db=+N` on each `Perf` line) and logs its SQL, without arguments, under [DB_TAG]. */
    fun <T : RoomDatabase> countQueries(builder: RoomDatabase.Builder<T>): RoomDatabase.Builder<T> {
        if (!BuildConfig.DEBUG) return builder
        val counter = Perf.counter(DB_COUNTER)
        return builder.setQueryCallback(
            { sql, _ ->
                val n = counter.incrementAndGet()
                Log.d(DB_TAG, "#$n ${sql.replace(Regex("\\s+"), " ").take(160)}")
            },
            Executors.newSingleThreadExecutor()
        )
    }

    private class ImageRequestLogger : EventListener {
        private var startNanos = 0L

        override fun onStart(request: ImageRequest) {
            startNanos = Perf.clock()
        }

        override fun onSuccess(request: ImageRequest, result: SuccessResult) {
            Perf.counter(IMAGE_COUNTER).incrementAndGet()
            if (result.dataSource == coil.decode.DataSource.NETWORK) Perf.counter(IMAGE_NETWORK_COUNTER).incrementAndGet()
            log(request, mapOf("src" to result.dataSource.name))
        }

        override fun onError(request: ImageRequest, result: ErrorResult) {
            log(request, mapOf("src" to "ERROR"))
        }

        private fun log(request: ImageRequest, values: Map<String, Any>) {
            val elapsed = if (startNanos == 0L) 0L else Perf.clock() - startNanos
            AppLog.i(Perf.TAG, Perf.formatLine("img", elapsed, values + ImageRequestLog.describe(request.data)))
        }
    }
}

/** What an image request's log line says about the image: TMDB size and path (never a title), or just the host. */
object ImageRequestLog {
    private val tmdb = Regex("""image\.tmdb\.org/t/p/([^/]+)(/[^?]+)""")

    fun describe(data: Any?): Map<String, Any> {
        val text = data?.toString() ?: return mapOf("data" to "none")
        tmdb.find(text)?.let { return mapOf("size" to it.groupValues[1], "path" to it.groupValues[2]) }
        val scheme = text.substringBefore("://", missingDelimiterValue = "")
        return if (scheme == "http" || scheme == "https") {
            mapOf("host" to text.substringAfter("://").substringBefore('/'))
        } else {
            mapOf("data" to (scheme.ifEmpty { "other" }))
        }
    }
}
