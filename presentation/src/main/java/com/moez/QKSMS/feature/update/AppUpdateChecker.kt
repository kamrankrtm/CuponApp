package com.moez.QKSMS.feature.update

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AlertDialog
import com.moez.QKSMS.BuildConfig
import com.moez.QKSMS.R
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

object AppUpdateChecker {

    private const val GITHUB_LATEST_RELEASE_URL = "https://api.github.com/repos/kamrankrtm/CuponApp/releases/latest"
    private const val PREFS_NAME = "app_update_prefs"
    private const val KEY_LAST_CHECK = "last_update_check_time"
    private const val KEY_SNOOZED_TAG = "snoozed_release_tag"
    private const val KEY_SNOOZED_AT = "snoozed_release_time"
    // Opening the app looks at most this often, so a new release shows up soon after it is out
    private const val CHECK_INTERVAL_MS = 15 * 60 * 1000L
    // "Later" keeps that same release from asking again on its own for this long
    private const val SNOOZE_MS = 24 * 60 * 60 * 1000L

    data class ReleaseInfo(
        val tagName: String,
        val title: String,
        val notes: String,
        val downloadUrl: String
    )

    fun checkForUpdate(activity: Activity, manualCheck: Boolean = false) {
        val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastCheck = prefs.getLong(KEY_LAST_CHECK, 0L)
        val now = System.currentTimeMillis()

        if (!manualCheck && now - lastCheck < CHECK_INTERVAL_MS) {
            return
        }

        prefs.edit().putLong(KEY_LAST_CHECK, now).apply()

        thread {
            try {
                val url = URL(GITHUB_LATEST_RELEASE_URL)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("User-Agent", "smsPRO-Android-App")
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
                conn.connectTimeout = 8000
                conn.readTimeout = 8000

                if (conn.responseCode == 200) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream))
                    val jsonStr = reader.readText()
                    reader.close()

                    val json = JSONObject(jsonStr)
                    val tagName = json.optString("tag_name", "")
                    val name = json.optString("name", "نسخه جدید")
                    val body = json.optString("body", "")
                    val htmlUrl = json.optString("html_url", "https://github.com/kamrankrtm/CuponApp/releases")

                    var downloadUrl = htmlUrl
                    val assets = json.optJSONArray("assets")
                    if (assets != null && assets.length() > 0) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            val assetName = asset.optString("name", "")
                            if (assetName.endsWith(".apk")) {
                                downloadUrl = asset.optString("browser_download_url", htmlUrl)
                                break
                            }
                        }
                    }

                    val currentVersion = BuildConfig.VERSION_NAME
                    if (isNewerVersion(tagName, currentVersion)) {
                        val snoozed = !manualCheck &&
                                prefs.getString(KEY_SNOOZED_TAG, null) == tagName &&
                                now - prefs.getLong(KEY_SNOOZED_AT, 0L) < SNOOZE_MS
                        if (!snoozed) {
                            val releaseInfo = ReleaseInfo(tagName, name, body, downloadUrl)
                            activity.runOnUiThread {
                                showUpdateDialog(activity, releaseInfo)
                            }
                        }
                    } else if (manualCheck) {
                        activity.runOnUiThread {
                            AlertDialog.Builder(activity)
                                .setTitle("smsPRO به‌روز است")
                                .setMessage("شما در حال استفاده از آخرین نسخه موجود ($currentVersion) هستید.")
                                .setPositiveButton("باشه", null)
                                .show()
                        }
                    }
                } else if (manualCheck) {
                    activity.runOnUiThread {
                        AlertDialog.Builder(activity)
                            .setTitle("بررسی به‌روزرسانی")
                            .setMessage("امکان برقراری ارتباط با سرور GitHub فراهم نشد. لطفاً بعداً تلاش کنید.")
                            .setPositiveButton("باشه", null)
                            .show()
                    }
                }
            } catch (t: Throwable) {
                if (manualCheck) {
                    activity.runOnUiThread {
                        AlertDialog.Builder(activity)
                            .setTitle("خطا در بررسی")
                            .setMessage("خطا در اتصال به اینترنت: ${t.localizedMessage}")
                            .setPositiveButton("باشه", null)
                            .show()
                    }
                }
            }
        }
    }

    /**
     * Compares the numbers in each version, whatever separates them: release v3.0.5.62 is newer
     * than build 3.0.5.61 and than a plain 3.0.5, while the old v2.0.2-60 tags come out older.
     */
    internal fun isNewerVersion(remoteTag: String, currentVer: String): Boolean {
        val remoteParts = versionNumbers(remoteTag)
        val currentParts = versionNumbers(currentVer)

        val maxLen = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }

    private fun versionNumbers(version: String): List<Int> =
            Regex("\\d+").findAll(version).map { it.value.toIntOrNull() ?: 0 }.toList()

    private fun showUpdateDialog(activity: Activity, release: ReleaseInfo) {
        if (activity.isFinishing || activity.isDestroyed) return

        val cleanNotes = if (release.notes.isNotBlank()) {
            "\n\nتغییرات:\n" + release.notes.take(500)
        } else ""

        val message = "نسخه جدید ${release.tagName} برای smsPRO آماده دانلود است.$cleanNotes"

        AlertDialog.Builder(activity)
            .setTitle("🚀 نسخه جدید smsPRO آماده است!")
            .setMessage(message)
            .setPositiveButton("دانلود نسخه جدید") { _, _ ->
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(release.downloadUrl))
                activity.startActivity(intent)
            }
            .setNegativeButton("بعداً") { _, _ ->
                activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                        .putString(KEY_SNOOZED_TAG, release.tagName)
                        .putLong(KEY_SNOOZED_AT, System.currentTimeMillis())
                        .apply()
            }
            .setCancelable(true)
            .show()
    }
}
