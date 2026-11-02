package com.drivecinema.app.core.logging

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** Receives trace sections; Android sends them to `android.os.Trace` (visible in Perfetto), elsewhere they are dropped. */
interface PerfTracer {
    fun beginAsync(name: String, cookie: Int)
    fun endAsync(name: String, cookie: Int)

    companion object {
        val NONE: PerfTracer = object : PerfTracer {
            override fun beginAsync(name: String, cookie: Int) {}
            override fun endAsync(name: String, cookie: Int) {}
        }
    }
}

/**
 * Task M.0: timings for the performance baseline. Each [PerfSpan] is one async trace section and, when it ends,
 * one log line under the tag [TAG]: `name 123 ms key=value …`. Async sections, because a span may start and end
 * on different threads (coroutines) or activities. Counts only: never titles, file names or tokens.
 */
object Perf {
    const val TAG = "Perf"

    @Volatile
    var tracer: PerfTracer = PerfTracer.NONE

    /** Monotonic nanoseconds; tests replace it. */
    @Volatile
    var clock: () -> Long = { System.nanoTime() }

    /**
     * Process-wide counters (Room queries and new connections in debug builds). Each span reports how much every
     * counter that existed when it began grew while it ran, e.g. `db=+12` (counters that did not grow are left out).
     */
    private val counters = ConcurrentHashMap<String, AtomicLong>()
    private val nextCookie = AtomicInteger(1)

    fun counter(key: String): AtomicLong = counters.computeIfAbsent(key) { AtomicLong() }

    fun begin(name: String): PerfSpan {
        val span = PerfSpan(name, clock(), nextCookie.getAndIncrement(), counters.mapValues { it.value.get() })
        tracer.beginAsync(name, span.cookie)
        return span
    }

    /** Continues a span begun elsewhere in this process (Play pressed in one activity, first frame in another). */
    fun resume(name: String, startNanos: Long, cookie: Int): PerfSpan = PerfSpan(name, startNanos, cookie, emptyMap())

    /** Runs [block] inside a span; a span left by an exception (or cancellation) is logged with `aborted=true`. */
    inline fun <T> measure(name: String, block: (PerfSpan) -> T): T {
        val span = begin(name)
        var completed = false
        try {
            return block(span).also { completed = true }
        } finally {
            if (!completed) span.put("aborted", true)
            span.end()
        }
    }

    fun formatLine(name: String, elapsedNanos: Long, values: Map<String, Any>): String = buildString {
        append(name).append(' ').append(elapsedNanos / 1_000_000).append(" ms")
        values.forEach { (key, value) -> append(' ').append(key).append('=').append(value) }
    }

    internal fun counterValue(key: String): Long? = counters[key]?.get()
}

class PerfSpan internal constructor(
    val name: String,
    val startNanos: Long,
    val cookie: Int,
    private val countersAtStart: Map<String, Long>
) {
    private val values = LinkedHashMap<String, Any>()
    private val ended = AtomicBoolean(false)

    val isEnded: Boolean get() = ended.get()

    /** Adds a count (or a short label) to the span's log line. */
    fun put(key: String, value: Any): PerfSpan {
        synchronized(values) { values[key] = value }
        return this
    }

    /** Ends the trace section and logs the line; later calls do nothing. Returns the elapsed milliseconds, or null. */
    fun end(): Long? {
        if (!ended.compareAndSet(false, true)) return null
        val elapsed = Perf.clock() - startNanos
        Perf.tracer.endAsync(name, cookie)
        val line = synchronized(values) { LinkedHashMap(values) }
        countersAtStart.forEach { (key, start) ->
            val grew = (Perf.counterValue(key) ?: start) - start
            if (grew != 0L) line[key] = "+$grew"
        }
        AppLog.i(Perf.TAG, Perf.formatLine(name, elapsed, line))
        return elapsed / 1_000_000
    }
}
