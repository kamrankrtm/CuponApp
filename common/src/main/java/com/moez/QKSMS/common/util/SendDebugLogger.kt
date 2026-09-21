package com.moez.QKSMS.common.util

import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedDeque

object SendDebugLogger {
    private const val MAX_LOGS = 60
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val logEntries = ConcurrentLinkedDeque<String>()

    fun log(message: String) {
        val timestamp = timeFormat.format(Date())
        val entry = "[$timestamp] $message"
        Timber.d("SendDebug: %s", message)
        android.util.Log.d("SendDebug", message)
        logEntries.addLast(entry)
        while (logEntries.size > MAX_LOGS) {
            logEntries.pollFirst()
        }
    }

    fun getLogs(): String {
        return if (logEntries.isEmpty()) {
            "هنوز لاگی ثبت نشده است."
        } else {
            logEntries.joinToString("\n")
        }
    }

    fun clear() {
        logEntries.clear()
    }
}
