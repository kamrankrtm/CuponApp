package com.moez.QKSMS.common.util

import android.app.Application
import android.content.Intent
import android.os.Process
import android.util.Log
import com.moez.QKSMS.feature.crash.CrashDisplayActivity
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

/**
 * Shows a readable crash screen so the user can screenshot / copy the stack trace.
 * Must be installed as early as possible in Application.onCreate.
 */
object CrashHandler {

    private const val TAG = "CrashHandler"
    private const val CRASH_FILE = "last_crash.txt"
    private var installed = false

    fun install(app: Application) {
        if (installed) return
        installed = true

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val report = buildReport(app, thread, throwable)
                Log.e(TAG, report)

                val file = File(app.filesDir, CRASH_FILE)
                file.writeText(report)

                val intent = Intent(app, CrashDisplayActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra(CrashDisplayActivity.EXTRA_CRASH_TEXT, report.take(12000))
                    putExtra(CrashDisplayActivity.EXTRA_CRASH_FILE, file.absolutePath)
                }
                app.startActivity(intent)

                // Give the crash UI process a moment to start before we die
                try {
                    Thread.sleep(400)
                } catch (ignored: InterruptedException) {
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to show crash UI", e)
            } finally {
                Process.killProcess(Process.myPid())
                exitProcess(10)
            }
        }
    }

    fun readLastCrash(app: Application): String? {
        val file = File(app.filesDir, CRASH_FILE)
        return if (file.exists()) file.readText() else null
    }

    private fun buildReport(app: Application, thread: Thread, throwable: Throwable): String {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val version = try {
            val info = app.packageManager.getPackageInfo(app.packageName, 0)
            "${info.versionName} (${info.versionCode})"
        } catch (e: Exception) {
            "?"
        }

        return StringBuilder().apply {
            append("smsPRO crash report\n")
            append("time: ").append(time).append('\n')
            append("version: ").append(version).append('\n')
            append("device: ").append(android.os.Build.MANUFACTURER).append(' ')
                    .append(android.os.Build.MODEL).append('\n')
            append("android: ").append(android.os.Build.VERSION.RELEASE)
                    .append(" (API ").append(android.os.Build.VERSION.SDK_INT).append(")\n")
            append("thread: ").append(thread.name).append('\n')
            append('\n')
            append(throwable.javaClass.name).append(": ").append(throwable.message ?: "").append('\n')
            append('\n')
            append(sw.toString())
        }.toString()
    }
}
