package com.drivecinema.app.core.logging

/** Receives log lines from shared code; Android sends them to Logcat, desktop to stderr/a log file. */
fun interface LogSink {
    fun log(priority: AppLog.Priority, tag: String, message: String, throwable: Throwable?)
}

/**
 * Logging for code in `:shared`, with the same calls as `android.util.Log`.
 * Never log tokens or other credentials.
 */
object AppLog {

    enum class Priority { DEBUG, INFO, WARN, ERROR }

    /** Defaults to stderr; the app installs its own sink at startup. */
    @Volatile
    var sink: LogSink = LogSink { priority, tag, message, throwable ->
        System.err.println("${priority.name.first()}/$tag: $message")
        throwable?.printStackTrace()
    }

    fun d(tag: String, message: String) = sink.log(Priority.DEBUG, tag, message, null)
    fun i(tag: String, message: String) = sink.log(Priority.INFO, tag, message, null)
    fun w(tag: String, message: String, throwable: Throwable? = null) = sink.log(Priority.WARN, tag, message, throwable)
    fun e(tag: String, message: String, throwable: Throwable? = null) = sink.log(Priority.ERROR, tag, message, throwable)
}
