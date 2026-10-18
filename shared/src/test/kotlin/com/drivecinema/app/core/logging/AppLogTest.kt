package com.drivecinema.app.core.logging

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class AppLogTest {

    private val original = AppLog.sink

    @After
    fun restoreSink() {
        AppLog.sink = original
    }

    @Test
    fun forwardsEveryPriorityToTheInstalledSink() {
        val lines = mutableListOf<String>()
        var lastThrowable: Throwable? = null
        AppLog.sink = LogSink { priority, tag, message, throwable ->
            lines += "$priority $tag $message"
            lastThrowable = throwable
        }
        val error = IllegalStateException("boom")

        AppLog.d("T", "debug")
        AppLog.i("T", "info")
        AppLog.w("T", "warn")
        AppLog.e("T", "error", error)

        assertEquals(listOf("DEBUG T debug", "INFO T info", "WARN T warn", "ERROR T error"), lines)
        assertSame(error, lastThrowable)
    }
}
