package com.drivecinema.app.core.logging

import android.os.Build
import android.os.Trace

/**
 * Task M.0: sends [Perf] spans to `android.os.Trace` as async sections, so they show up in a Perfetto trace
 * (with `atrace_apps: "com.drivecinema.app"`). Async sections need API 29; older devices only get the log lines.
 */
object AndroidPerfTracer : PerfTracer {
    override fun beginAsync(name: String, cookie: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Trace.beginAsyncSection(name, cookie)
    }

    override fun endAsync(name: String, cookie: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Trace.endAsyncSection(name, cookie)
    }
}
