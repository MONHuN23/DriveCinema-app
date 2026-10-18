package com.drivecinema.app.core.logging

import android.util.Log

/** Sends logs from `:shared` code to Logcat. */
object AndroidLogSink : LogSink {
    override fun log(priority: AppLog.Priority, tag: String, message: String, throwable: Throwable?) {
        when (priority) {
            AppLog.Priority.DEBUG -> Log.d(tag, message, throwable)
            AppLog.Priority.INFO -> Log.i(tag, message, throwable)
            AppLog.Priority.WARN -> Log.w(tag, message, throwable)
            AppLog.Priority.ERROR -> Log.e(tag, message, throwable)
        }
    }
}
