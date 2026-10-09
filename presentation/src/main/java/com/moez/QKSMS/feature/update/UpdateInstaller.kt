package com.moez.QKSMS.feature.update

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import com.moez.QKSMS.BuildConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * Downloads a release APK inside the app and hands it straight to the system installer.
 *
 * The browser used to download the file to wherever it liked, and the user then had to find
 * and open it. Now the progress shows in the banner that announced the update, and the
 * installer opens by itself when the file is complete. Android still asks the user to confirm
 * the install once: an app installed from a file cannot update itself silently.
 *
 * Before installing, the file is checked: its size matches the release, and it is this app
 * (same package) at a higher version, so a truncated or wrong file never reaches the installer.
 */
object UpdateInstaller {

    sealed class State {
        object Idle : State()
        data class Downloading(val tag: String, val percent: Int, val doneBytes: Long, val totalBytes: Long) : State()
        data class Ready(val tag: String, val file: File) : State()
        /** Waiting for the user to allow installs from this app in system settings. */
        data class NeedsPermission(val tag: String, val file: File) : State()
        data class Failed(val tag: String, val message: String) : State()
    }

    @Volatile
    var state: State = State.Idle
        private set

    /** The screen showing the banner; told of every change on the main thread. */
    @Volatile
    var listener: ((State) -> Unit)? = null

    /** The activity on screen, set in its onResume and cleared in onPause; never kept past that. */
    @Volatile
    var host: Activity? = null

    private val main = Handler(Looper.getMainLooper())

    private fun publish(next: State) {
        state = next
        main.post { listener?.invoke(next) }
    }

    /** Starts the download, or goes straight to installing when this release is already here. */
    fun start(activity: Activity, release: AppUpdateChecker.ReleaseInfo) {
        val current = state
        if (current is State.Downloading && current.tag == release.tagName) return
        if ((current is State.Ready || current is State.NeedsPermission) && tagOf(current) == release.tagName) {
            install(activity, fileOf(current)!!, release.tagName)
            return
        }

        val app = activity.applicationContext
        val dir = File(app.cacheDir, "updates").apply { mkdirs() }
        val target = File(dir, "smsPRO-${release.tagName.removePrefix("v")}.apk")
        // Older downloads are never needed again
        dir.listFiles()?.filter { it != target }?.forEach { it.delete() }

        if (target.exists() && verify(app, target, release.sizeBytes) == null) {
            publish(State.Ready(release.tagName, target))
            install(activity, target, release.tagName)
            return
        }

        publish(State.Downloading(release.tagName, 0, 0L, release.sizeBytes))
        thread(name = "update-download") {
            val error = download(release, target)
                ?: verify(app, target, release.sizeBytes)
            if (error != null) {
                target.delete()
                publish(State.Failed(release.tagName, error))
                return@thread
            }
            publish(State.Ready(release.tagName, target))
            // Straight to the installer if the app is still open; otherwise the banner offers it
            main.post { host?.let { install(it, target, release.tagName) } }
        }
    }

    /** @return null on success, otherwise a message for the user */
    private fun download(release: AppUpdateChecker.ReleaseInfo, target: File): String? {
        val partial = File(target.path + ".part")
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(release.downloadUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "smsPRO-Android-App")
                connectTimeout = 15_000
                readTimeout = 30_000
            }
            if (conn.responseCode !in 200..299) return "دانلود ناموفق بود (کد ${conn.responseCode})"
            val total = conn.contentLength.toLong().takeIf { it > 0 } ?: release.sizeBytes
            var done = 0L
            var lastPercent = -1
            conn.inputStream.use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        val percent = if (total > 0) ((done * 100) / total).toInt().coerceIn(0, 100) else 0
                        if (percent != lastPercent) {
                            lastPercent = percent
                            publish(State.Downloading(release.tagName, percent, done, total))
                        }
                    }
                }
            }
            if (!partial.renameTo(target)) "ذخیره‌ی فایل نسخه‌ی جدید ممکن نشد" else null
        } catch (e: Exception) {
            "اتصال قطع شد؛ دوباره بزنید تا دانلود از نو شروع شود"
        } finally {
            conn?.disconnect()
            partial.delete()
        }
    }

    /** @return null when [file] is a complete, newer build of this app, otherwise why not */
    private fun verify(context: Context, file: File, expectedSize: Long): String? {
        if (expectedSize > 0 && file.length() != expectedSize) return "فایل دانلودشده ناقص است؛ دوباره امتحان کنید"
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageArchiveInfo(file.path, 0) ?: return "فایل دانلودشده سالم نیست"
        if (info.packageName != context.packageName) return "این فایل مربوط به این برنامه نیست"
        @Suppress("DEPRECATION")
        if (info.versionCode <= BuildConfig.VERSION_CODE) return "این نسخه از نسخه‌ی نصب‌شده جدیدتر نیست"
        return null
    }

    /** Opens the system installer for [file], asking for the install permission first if needed. */
    fun install(activity: Activity, file: File, tag: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !activity.packageManager.canRequestPackageInstalls()) {
            publish(State.NeedsPermission(tag, file))
            Toast.makeText(activity, "اجازه‌ی نصب از smsPRO را روشن کنید؛ سپس نصب خودش ادامه پیدا می‌کند", Toast.LENGTH_LONG).show()
            try {
                activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${activity.packageName}")))
            } catch (e: Exception) {
                publish(State.Failed(tag, "صفحه‌ی اجازه‌ی نصب باز نشد"))
            }
            return
        }
        publish(State.Ready(tag, file))
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            activity.startActivity(intent)
        } catch (e: Exception) {
            publish(State.Failed(tag, "نصب‌کننده‌ی اندروید باز نشد"))
        }
    }

    /** Back from the permission screen: continue the install if it was granted. */
    fun resume(activity: Activity) {
        val current = state as? State.NeedsPermission ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || activity.packageManager.canRequestPackageInstalls()) {
            install(activity, current.file, current.tag)
        }
    }

    private fun tagOf(state: State): String? = when (state) {
        is State.Ready -> state.tag
        is State.NeedsPermission -> state.tag
        else -> null
    }

    private fun fileOf(state: State): File? = when (state) {
        is State.Ready -> state.file
        is State.NeedsPermission -> state.file
        else -> null
    }
}
