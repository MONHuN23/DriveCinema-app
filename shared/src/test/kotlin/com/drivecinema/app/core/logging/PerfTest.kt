package com.drivecinema.app.core.logging

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** Task M.0: the timing helper behind every `Perf` log line and trace section. */
class PerfTest {

    private val originalSink = AppLog.sink
    private val originalTracer = Perf.tracer
    private val originalClock = Perf.clock

    private var now = 0L
    private val lines = mutableListOf<String>()
    private val sections = mutableListOf<String>()

    @Before
    fun setUp() {
        Perf.clock = { now }
        AppLog.sink = LogSink { priority, tag, message, _ -> lines += "$priority $tag $message" }
        Perf.tracer = object : PerfTracer {
            override fun beginAsync(name: String, cookie: Int) { sections += "begin $name $cookie" }
            override fun endAsync(name: String, cookie: Int) { sections += "end $name $cookie" }
        }
    }

    @After
    fun tearDown() {
        AppLog.sink = originalSink
        Perf.tracer = originalTracer
        Perf.clock = originalClock
    }

    private fun ms(value: Long) = value * 1_000_000

    @Test
    fun spanLogsElapsedMillisecondsAndCountsInOrder() {
        now = ms(100)
        val span = Perf.begin("sync.crawl")
        now = ms(1_334)
        span.put("requests", 42).put("movies", 7)

        assertEquals(1_234L, span.end())
        assertEquals(listOf("INFO Perf sync.crawl 1234 ms requests=42 movies=7"), lines)
    }

    @Test
    fun endIsIdempotent() {
        val span = Perf.begin("screen.home")
        span.end()
        assertNull(span.end())
        assertTrue(span.isEnded)
        assertEquals(1, lines.size)
        assertEquals(2, sections.size)
    }

    @Test
    fun traceSectionBeginsAndEndsWithTheSameCookie() {
        val a = Perf.begin("a")
        val b = Perf.begin("b")
        b.end()
        a.end()
        assertTrue("cookies are unique", a.cookie != b.cookie)
        assertEquals(
            listOf("begin a ${a.cookie}", "begin b ${b.cookie}", "end b ${b.cookie}", "end a ${a.cookie}"),
            sections
        )
    }

    @Test
    fun measureReturnsTheResultAndLogsOnce() {
        now = ms(10)
        val result = Perf.measure("sync.dbWrite") {
            now = ms(35)
            "done"
        }
        assertEquals("done", result)
        assertEquals(listOf("INFO Perf sync.dbWrite 25 ms"), lines)
    }

    @Test
    fun measureMarksASpanLeftByAnExceptionAsAborted() {
        try {
            Perf.measure<Unit>("sync.folderCheck") { throw IllegalStateException("offline") }
            fail("the exception must propagate")
        } catch (_: IllegalStateException) {
        }
        assertEquals(listOf("INFO Perf sync.folderCheck 0 ms aborted=true"), lines)
    }

    @Test
    fun spanReportsHowMuchEachCounterGrew() {
        val counter = Perf.counter("perfTestQueries")
        counter.addAndGet(5)
        val span = Perf.begin("screen.seriesDetails")
        counter.addAndGet(3)
        span.end()
        // Counters registered by other tests in this JVM may appear too; only this one matters here.
        assertTrue(lines.single(), lines.single().startsWith("INFO Perf screen.seriesDetails 0 ms"))
        assertTrue(lines.single(), " perfTestQueries=+3" in lines.single())
    }

    @Test
    fun counterCreatedAfterASpanBeganIsNotReported() {
        val span = Perf.begin("screen.home")
        Perf.counter("perfTestLateCounter").incrementAndGet()
        span.end()
        assertFalse(lines.single(), "perfTestLateCounter" in lines.single())
    }

    @Test
    fun counterThatDidNotGrowIsLeftOut() {
        Perf.counter("perfTestIdleCounter").incrementAndGet()
        Perf.begin("screen.home").end()
        assertFalse(lines.single(), "perfTestIdleCounter" in lines.single())
    }

    @Test
    fun resumedSpanEndsTheSectionBegunElsewhere() {
        now = ms(1_000)
        val pressed = Perf.begin("play.firstFrame")
        sections.clear()

        now = ms(3_500)
        val resumed = Perf.resume("play.firstFrame", pressed.startNanos, pressed.cookie)
        resumed.put("source", "Drive").end()

        assertEquals(listOf("end play.firstFrame ${pressed.cookie}"), sections)
        assertEquals(listOf("INFO Perf play.firstFrame 2500 ms source=Drive"), lines)
        assertFalse(pressed.isEnded)
    }

    @Test
    fun formatLineHasNoValuesWhenThereAreNone() {
        assertEquals("screen.grid.films 16 ms", Perf.formatLine("screen.grid.films", ms(16) + 999_999, emptyMap()))
    }
}
