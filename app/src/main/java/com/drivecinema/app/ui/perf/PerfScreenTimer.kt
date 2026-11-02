package com.drivecinema.app.ui.perf

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import com.drivecinema.app.core.logging.Perf

/**
 * Task M.0: one `Perf` line `screen.<name> N ms` from the screen's first composition (or a new [key]) until
 * [ready] is true and the next frame has started, i.e. the content has been drawn once. A screen left before
 * that logs its line with `abandoned=true`. No behaviour change: it only measures.
 */
@Composable
fun PerfScreenTimer(name: String, key: Any?, ready: Boolean, onReady: () -> Unit = {}) {
    val span = remember(key) { Perf.begin("screen.$name") }
    DisposableEffect(span) {
        onDispose {
            if (!span.isEnded) span.put("abandoned", true).end()
        }
    }
    LaunchedEffect(span, ready) {
        if (ready && !span.isEnded) {
            withFrameNanos { }
            span.end()
            onReady()
        }
    }
}
