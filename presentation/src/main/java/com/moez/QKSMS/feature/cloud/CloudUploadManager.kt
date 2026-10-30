/*
 * Copyright (C) 2026 CuponApp
 *
 * This file is part of CuponApp / QKSMS.
 */
package com.moez.QKSMS.feature.cloud

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.os.Handler
import android.os.Looper
import android.webkit.MimeTypeMap
import java.util.concurrent.atomic.AtomicBoolean
import com.moez.QKSMS.util.Preferences
import org.json.JSONObject
import timber.log.Timber
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudUploadManager @Inject constructor(
    private val prefs: Preferences
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    /** A Catbox rate-limit/server error can be retried on a backup provider. */
    private class HostUnavailableException(message: String) : IOException(message)


    interface UploadCancellable {
        fun cancel()
    }

    /**
     * Uploads the media from URI in a background thread and returns the formatted link string
     */
    fun upload(
        context: Context,
        uri: Uri,
        onProgress: (Int) -> Unit,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ): UploadCancellable {
        val isCancelled = AtomicBoolean(false)
        val activeConnection = AtomicReference<HttpURLConnection?>()

        val thread = Thread {
            try {
                val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
                val isVideo = mimeType.startsWith("video/")
                val isImage = mimeType.startsWith("image/")
                val fileName = resolveFileName(context, uri, mimeType)

                postProgress(onProgress, 10)

                val filesToken = prefs.filesIrToken.get().trim()
                val endpoint = prefs.filesIrEndpoint.get().trim().ifBlank { "https://my.files.ir" }

                var temporaryLink = false
                val rawUrl: String = if (filesToken.isNotEmpty()) {
                    uploadToFilesIr(context, uri, endpoint, filesToken, fileName, mimeType) { p ->
                        if (!isCancelled.get()) postProgress(onProgress, 10 + (p * 0.7).toInt())
                    }
                } else {
                    try {
                        uploadToCatbox(context, uri, fileName, mimeType, isCancelled, activeConnection) { p ->
                            if (!isCancelled.get()) postProgress(onProgress, 10 + (p * 0.7).toInt())
                        }
                    } catch (catboxError: IOException) {
                        if (isCancelled.get() || !shouldUseFallback(catboxError)) throw catboxError
                        Timber.w(catboxError, "Catbox unavailable; trying Gofile temporary guest upload")
                        postProgress(onProgress, 10)
                        try {
                            val backupUrl = uploadToGofile(
                                context, uri, fileName, mimeType, isCancelled, activeConnection
                            ) { p ->
                                if (!isCancelled.get()) postProgress(onProgress, 10 + (p * 0.7).toInt())
                            }
                            temporaryLink = true
                            backupUrl
                        } catch (backupError: Exception) {
                            if (isCancelled.get()) return@Thread
                            Timber.w(backupError, "Gofile backup also unavailable")
                            throw IOException(
                                "اتصال به هر دو سرویس آپلود عمومی ناموفق بود (Catbox و Gofile). " +
                                "اینترنت، VPN و DNS را بررسی کنید یا در تنظیمات برنامه توکن Files.ir را وارد کنید.",
                                backupError
                            )
                        }
                    }
                }

                if (isCancelled.get()) return@Thread

                postProgress(onProgress, 85)

                var finalUrl = rawUrl
                val zayaKey = prefs.zayaApiKey.get().trim()
                if (prefs.shortenCloudLinks.get() && zayaKey.isNotEmpty()) {
                    try {
                        val shortUrl = shortenWithZaya(rawUrl, zayaKey)
                        if (shortUrl.isNotBlank()) {
                            finalUrl = shortUrl
                        }
                    } catch (e: Exception) {
                        Timber.w(e, "Zaya shortening failed, falling back to original URL")
                    }
                }

                postProgress(onProgress, 100)

                val label = when {
                    isImage -> "📷 عکس: $finalUrl"
                    isVideo -> "🎬 ویدیو: $finalUrl"
                    else -> "📎 فایل ($fileName): $finalUrl"
                }

                mainHandler.post {
                    if (!isCancelled.get()) {
                        onSuccess(if (temporaryLink) "⏳ لینک موقت Gofile (امکان حذف پس از مدتی): $label" else label)
                    }
                }

            } catch (e: Exception) {
                Timber.e(e, "Upload failed")
                if (!isCancelled.get()) {
                    mainHandler.post { onError(e.localizedMessage ?: "خطا در آپلود فایل") }
                }
            }
        }

        thread.start()

        return object : UploadCancellable {
            override fun cancel() {
                isCancelled.set(true)
                activeConnection.getAndSet(null)?.disconnect()
                thread.interrupt()
            }
        }
    }

    private fun uploadToFilesIr(
        context: Context,
        uri: Uri,
        endpoint: String,
        token: String,
        fileName: String,
        mimeType: String,
        progress: (Int) -> Unit
    ): String {
        val boundary = "===CuponAppUploadBoundary==="
        val lineEnd = "\r\n"
        val twoHyphens = "--"

        val uploadUrl = URL("${endpoint.trimEnd('/')}/api/v1/uploads")
        val conn = uploadUrl.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.doInput = true
        conn.useCaches = false
        conn.setRequestProperty("Connection", "Keep-Alive")
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        conn.connectTimeout = 30000
        conn.readTimeout = 60000

        val outputStream = DataOutputStream(conn.outputStream)
        outputStream.writeBytes(twoHyphens + boundary + lineEnd)
        outputStream.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"$fileName\"$lineEnd")
        outputStream.writeBytes("Content-Type: $mimeType$lineEnd$lineEnd")

        copyDocument(context, uri, outputStream, contentLength(context, uri), progress)

        outputStream.writeBytes(lineEnd)
        outputStream.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd)
        outputStream.flush()
        outputStream.close()

        val responseCode = conn.responseCode
        val responseText = readResponse(conn)

        if (responseCode in 200..299) {
            return processFilesIrUpload(responseText, endpoint, token)
        } else {
            throw IOException("Files.ir error: $responseCode - $responseText")
        }
    }

    private fun processFilesIrUpload(response: String, endpoint: String, token: String): String {
        val baseEndpoint = endpoint.trimEnd('/')
        try {
            val json = JSONObject(response)
            val fe = json.optJSONObject("fileEntry")
                ?: (if (json.has("data")) json.getJSONObject("data") else null)
                ?: json
            val id = fe.optLong("id")
            val fileHash = fe.optString("hash").takeIf { it.isNotBlank() }

            if (id > 0 && token.isNotBlank()) {
                // Set 30-day expiration date: "yyyy-MM-dd HH:mm:ss"
                val cal = java.util.Calendar.getInstance()
                cal.add(java.util.Calendar.DAY_OF_YEAR, 30)
                val expiresAt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(cal.time)

                val shareLinkHash = createShareableLink(baseEndpoint, token, id, expiresAt)
                if (!shareLinkHash.isNullOrBlank()) {
                    Timber.i("Files.ir: created shareable link with 30-day expiration: $shareLinkHash")
                    return "$baseEndpoint/drive/s/$shareLinkHash"
                }
            }

            if (!fileHash.isNullOrBlank()) return "$baseEndpoint/drive/s/$fileHash"
            if (id > 0) return "$baseEndpoint/api/v1/file-entries/$id"
        } catch (e: Throwable) {
            Timber.w(e, "Error processing files.ir response: $response")
        }
        return parseUrlFromFilesIr(response, endpoint)
    }

    private fun createShareableLink(baseEndpoint: String, token: String, entryId: Long, expiresAt: String): String? {
        try {
            val url = URL("$baseEndpoint/api/v1/file-entries/$entryId/shareable-link")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/json")
            conn.connectTimeout = 15000
            conn.readTimeout = 15000

            val body = JSONObject().apply {
                put("allow_download", true)
                put("allow_edit", false)
                put("expires_at", expiresAt)
            }

            OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }

            val code = conn.responseCode
            val respText = readResponse(conn)
            Timber.d("createShareableLink: code=$code, resp=$respText")

            if (code in 200..299) {
                val respJson = JSONObject(respText)
                val linkObj = respJson.optJSONObject("link")
                    ?: (if (respJson.has("data")) respJson.getJSONObject("data") else null)
                val hash = linkObj?.optString("hash")?.takeIf { it.isNotBlank() }
                if (hash != null) return hash
            }
        } catch (e: Throwable) {
            Timber.e(e, "Failed to create shareable link for entry $entryId")
        }
        return null
    }

    /** Catbox: public anonymous uploads, 200 MB max and some extensions restricted. */
    private fun uploadToCatbox(
        context: Context, uri: Uri, filename: String, mime: String,
        cancelled: AtomicBoolean,
        activeConnection: AtomicReference<HttpURLConnection?>,
        progress: (Int) -> Unit
    ): String {
        val length = contentLength(context, uri)
        if (length > 200L * 1024L * 1024L) {
            throw IOException("حداکثر حجم آپلود عمومی ۲۰۰ مگابایت است. برای حجم بیشتر فضای خصوصی را تنظیم کنید.")
        }
        val boundary = "CuponApp-" + java.util.UUID.randomUUID().toString()
        val conn = (URL("https://catbox.moe/user/api.php").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            useCaches = false
            connectTimeout = 30_000
            readTimeout = 120_000
            setChunkedStreamingMode(64 * 1024)
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            setRequestProperty("Accept", "text/plain")
        }
        activeConnection.set(conn)
        try {
            DataOutputStream(conn.outputStream).use { output ->
                output.writeBytes("--$boundary\r\n")
                output.writeBytes("Content-Disposition: form-data; name=\"reqtype\"\r\n\r\nfileupload\r\n")
                output.writeBytes("--$boundary\r\n")
                output.writeBytes("Content-Disposition: form-data; name=\"fileToUpload\"; filename=\"$filename\"\r\n")
                output.writeBytes("Content-Type: $mime\r\n\r\n")
                copyDocument(context, uri, output, length, progress, cancelled)
                output.writeBytes("\r\n--$boundary--\r\n")
            }
            val status = conn.responseCode
            val value = readResponse(conn).trim()
            if (status == 429 || status >= 500) {
                throw HostUnavailableException("Catbox HTTP $status")
            }
            if (status !in 200..299 || !value.startsWith("https://files.catbox.moe/")) {
                throw IOException(
                    "سرور آپلود عمومی فایل را نپذیرفت ($status): ${value.take(160)}. " +
                    "برخی پسوندها از طرف میزبان ممنوع هستند؛ در این حالت از Files.ir استفاده کنید."
                )
            }
            return value
        } finally {
            activeConnection.compareAndSet(conn, null)
            conn.disconnect()
        }
    }

    /** Public guest links from Gofile can expire after inactivity. */
    private fun uploadToGofile(
        context: Context,
        uri: Uri,
        filename: String,
        mime: String,
        cancelled: AtomicBoolean,
        activeConnection: AtomicReference<HttpURLConnection?>,
        progress: (Int) -> Unit
    ): String {
        val boundary = "MessagePlus-" + java.util.UUID.randomUUID().toString()
        val conn = (URL("https://upload.gofile.io/uploadfile").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            useCaches = false
            connectTimeout = 30_000
            readTimeout = 120_000
            setChunkedStreamingMode(64 * 1024)
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            setRequestProperty("Accept", "application/json")
        }
        activeConnection.set(conn)
        try {
            DataOutputStream(conn.outputStream).use { output ->
                output.writeBytes("--$boundary\r\n")
                output.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"${asciiUploadName(filename)}\"\r\n")
                output.writeBytes("Content-Type: $mime\r\n\r\n")
                copyDocument(context, uri, output, contentLength(context, uri), progress, cancelled)
                output.writeBytes("\r\n--$boundary--\r\n")
            }
            val status = conn.responseCode
            val body = readResponse(conn)
            if (status !in 200..299) throw IOException("Gofile HTTP $status")
            val json = JSONObject(body)
            val link = json.optJSONObject("data")?.optString("downloadPage").orEmpty()
            if (json.optString("status") != "ok" || !link.startsWith("https://gofile.io/d/")) {
                throw IOException("پاسخ نامعتبر از سرور پشتیبان Gofile")
            }
            return link
        } finally {
            activeConnection.compareAndSet(conn, null)
            conn.disconnect()
        }
    }

    private fun asciiUploadName(filename: String): String =
        filename.replace(Regex("[^A-Za-z0-9._-]"), "_").take(120).ifBlank { "upload.bin" }

    private fun shouldUseFallback(error: IOException): Boolean =
        error is HostUnavailableException ||
        error is UnknownHostException ||
        error is ConnectException ||
        error is SocketTimeoutException ||
        error is SocketException ||
        error is SSLException ||
        error is EOFException

    private fun contentLength(context: Context, uri: Uri): Long {
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use {
                if (it.moveToFirst() && !it.isNull(0)) return it.getLong(0)
            }
        } catch (_: Exception) { }
        return -1L
    }

    private fun resolveFileName(context: Context, uri: Uri, mime: String): String {
        var original: String? = null
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst() && !it.isNull(0)) original = it.getString(0)
            }
        } catch (_: Exception) { }
        val safe = original?.substringAfterLast('/')?.substringAfterLast('\\')
            ?.replace(Regex("[\\r\\n\"\\u0000-\\u001f]"), "_")?.take(120)?.trim()
        if (!safe.isNullOrBlank() && safe.contains('.')) return safe
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
        val base = safe?.takeIf { it.isNotBlank() } ?: "file_${System.currentTimeMillis()}"
        return base + (extension?.let { ".$it" } ?: "")
    }

    private fun copyDocument(
        context: Context, uri: Uri, output: OutputStream, length: Long,
        progress: (Int) -> Unit, cancelled: AtomicBoolean? = null
    ) {
        context.contentResolver.openInputStream(uri)?.use { source ->
            val buffer = ByteArray(64 * 1024)
            var copied = 0L
            while (true) {
                if (cancelled?.get() == true || Thread.currentThread().isInterrupted) {
                    throw InterruptedIOException("آپلود توسط کاربر لغو شد")
                }
                val read = source.read(buffer)
                if (read == -1) break
                output.write(buffer, 0, read)
                copied += read
                if (length > 0) progress(((copied.toDouble() / length) * 100).toInt().coerceIn(0, 99))
            }
        } ?: throw IOException("امکان خواندن فایل انتخاب‌شده وجود ندارد")
    }

    private fun shortenWithZaya(longUrl: String, apiKey: String): String {
        val conn = URL("https://zaya.io/api/v1/links").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.doInput = true
        conn.setRequestProperty("Authorization", "Bearer $apiKey")
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Accept", "application/json")
        conn.connectTimeout = 15000
        conn.readTimeout = 15000

        val body = JSONObject().apply {
            put("url", longUrl)
        }

        OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }

        val responseCode = conn.responseCode
        val response = readResponse(conn)
        if (responseCode in 200..299) {
            val json = JSONObject(response)
            val dataObj = json.optJSONObject("data")
            return dataObj?.optString("short_url")?.takeIf { it.isNotBlank() }
                ?: dataObj?.optString("url")?.takeIf { it.isNotBlank() }
                ?: dataObj?.optString("short_link")?.takeIf { it.isNotBlank() }
                ?: dataObj?.optString("link")?.takeIf { it.isNotBlank() }
                ?: json.optString("short_url")?.takeIf { it.isNotBlank() }
                ?: json.optString("url")?.takeIf { it.isNotBlank() }
                ?: longUrl
        }
        return longUrl
    }

    private fun parseUrlFromFilesIr(response: String, endpoint: String): String {
        val baseEndpoint = endpoint.trimEnd('/')
        try {
            val json = JSONObject(response)
            if (json.has("fileEntry")) {
                val fe = json.getJSONObject("fileEntry")
                val hash = fe.optString("hash").takeIf { it.isNotBlank() }
                val rawUrl = fe.optString("url").takeIf { it.isNotBlank() }
                val id = fe.optLong("id")

                return when {
                    hash != null -> "$baseEndpoint/drive/s/$hash"
                    rawUrl != null -> if (rawUrl.startsWith("http")) rawUrl else "$baseEndpoint/${rawUrl.trimStart('/')}"
                    id > 0 -> "$baseEndpoint/api/v1/file-entries/$id"
                    else -> "$baseEndpoint/file"
                }
            }
            if (json.has("url")) {
                val u = json.getString("url")
                return if (u.startsWith("http")) u else "$baseEndpoint/${u.trimStart('/')}"
            }
            if (json.has("data") && json.getJSONObject("data").has("url")) {
                val u = json.getJSONObject("data").getString("url")
                return if (u.startsWith("http")) u else "$baseEndpoint/${u.trimStart('/')}"
            }
        } catch (e: Exception) {
            Timber.w(e, "Error parsing files.ir response: $response")
        }
        return "$baseEndpoint/file"
    }

    private fun readResponse(conn: HttpURLConnection): String {
        val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
        return stream?.bufferedReader()?.use { it.readText() } ?: ""
    }

    private fun postProgress(callback: (Int) -> Unit, percent: Int) {
        mainHandler.post { callback(percent) }
    }

    /**
     * Test connection to Files.ir and Zaya.io
     */
    fun testConnection(
        endpoint: String,
        filesToken: String,
        zayaToken: String,
        callback: (success: Boolean, message: String) -> Unit
    ) {
        Thread {
            val sb = StringBuilder()
            var overallSuccess = true

            // 1. Files.ir
            if (filesToken.isBlank()) {
                sb.append("• Files.ir: توکن تنظیم نشده (آپلود عمومی استفاده می‌شود)\n")
                for ((name, address) in listOf(
                    "Catbox اصلی" to "https://catbox.moe/user/api.php",
                    "Gofile پشتیبان موقت" to "https://upload.gofile.io/"
                )) {
                    try {
                        val conn = URL(address).openConnection() as HttpURLConnection
                        conn.connectTimeout = 10_000
                        conn.readTimeout = 10_000
                        conn.instanceFollowRedirects = false
                        try {
                            val code = conn.responseCode
                            if (code in 200..499 && code != 429) {
                                sb.append("• $name: سرور پاسخ می‌دهد (HTTP $code)\n")
                            } else {
                                sb.append("• $name: پاسخ نامناسب (HTTP $code)\n")
                                overallSuccess = false
                            }
                        } finally {
                            conn.disconnect()
                        }
                    } catch (e: Exception) {
                        Timber.w(e, "Upload connectivity check failed: $name")
                        sb.append("• $name: اتصال شبکه برقرار نشد\n")
                        overallSuccess = false
                    }
                }
            } else {
                try {
                    val conn = URL("${endpoint.trimEnd('/')}/api/v1/user").openConnection() as HttpURLConnection
                    conn.requestMethod = "GET"
                    conn.setRequestProperty("Authorization", "Bearer $filesToken")
                    conn.setRequestProperty("Accept", "application/json")
                    conn.connectTimeout = 10000
                    conn.readTimeout = 10000
                    val code = conn.responseCode
                    if (code in 200..299) {
                        sb.append("• فضای ابری Files.ir: اتصال موفق (۲۰۰ OK)\n")
                    } else {
                        sb.append("• فضای ابری Files.ir: خطا ($code)\n")
                        overallSuccess = false
                    }
                } catch (e: Exception) {
                    sb.append("• فضای ابری Files.ir: خطا در برقراری ارتباط (${e.localizedMessage})\n")
                    overallSuccess = false
                }
            }

            // 2. Zaya.io
            if (zayaToken.isBlank()) {
                sb.append("• کوتاه‌کننده زایا: کلید وارد نشده (ارسال لینک بدون کوتاه‌سازی)")
            } else {
                try {
                    val conn = URL("https://zaya.io/api/v1/links").openConnection() as HttpURLConnection
                    conn.requestMethod = "GET"
                    conn.setRequestProperty("Authorization", "Bearer $zayaToken")
                    conn.setRequestProperty("Accept", "application/json")
                    conn.connectTimeout = 10000
                    conn.readTimeout = 10000
                    val code = conn.responseCode
                    if (code in 200..299 || code == 405) {
                        sb.append("• کوتاه‌کننده زایا: اتصال موفق")
                    } else {
                        sb.append("• کوتاه‌کننده زایا: خطا ($code)")
                        overallSuccess = false
                    }
                } catch (e: Exception) {
                    sb.append("• کوتاه‌کننده زایا: خطا در برقراری ارتباط (${e.localizedMessage})")
                    overallSuccess = false
                }
            }

            mainHandler.post {
                callback(overallSuccess, sb.toString())
            }
        }.start()
    }
}
