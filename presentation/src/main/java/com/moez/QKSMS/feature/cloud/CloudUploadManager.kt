/*
 * Copyright (C) 2026 CuponApp
 *
 * This file is part of CuponApp / QKSMS.
 */
package com.moez.QKSMS.feature.cloud

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.MimeTypeMap
import com.moez.QKSMS.util.Preferences
import org.json.JSONObject
import timber.log.Timber
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudUploadManager @Inject constructor(
    private val prefs: Preferences
) {
    private val mainHandler = Handler(Looper.getMainLooper())

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
        var isCancelled = false

        val thread = Thread {
            try {
                val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
                val isVideo = mimeType.startsWith("video")
                val isImage = mimeType.startsWith("image")
                val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: if (isVideo) "mp4" else "jpg"
                val fileName = "upload_${System.currentTimeMillis()}.$extension"

                postProgress(onProgress, 10)

                val filesToken = prefs.filesIrToken.get().trim()
                val endpoint = prefs.filesIrEndpoint.get().trim().ifBlank { "https://my.files.ir" }

                val rawUrl: String = if (filesToken.isNotEmpty()) {
                    uploadToFilesIr(context, uri, endpoint, filesToken, fileName, mimeType) { p ->
                        if (!isCancelled) postProgress(onProgress, 10 + (p * 0.7).toInt())
                    }
                } else {
                    // Fallback to public fast upload service when no custom token is configured
                    uploadToTmpFiles(context, uri, fileName, mimeType) { p ->
                        if (!isCancelled) postProgress(onProgress, 10 + (p * 0.7).toInt())
                    }
                }

                if (isCancelled) return@Thread

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
                    else -> "📎 فایل: $finalUrl"
                }

                mainHandler.post { onSuccess(label) }

            } catch (e: Exception) {
                Timber.e(e, "Upload failed")
                if (!isCancelled) {
                    mainHandler.post { onError(e.localizedMessage ?: "خطا در آپلود فایل") }
                }
            }
        }

        thread.start()

        return object : UploadCancellable {
            override fun cancel() {
                isCancelled = true
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

        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Cannot read media file")

        val fileSize = inputStream.available().coerceAtLeast(1)
        val buffer = ByteArray(8192)
        var bytesRead: Int
        var totalBytesRead = 0

        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            outputStream.write(buffer, 0, bytesRead)
            totalBytesRead += bytesRead
            progress(((totalBytesRead.toDouble() / fileSize) * 100).toInt().coerceAtMost(99))
        }

        outputStream.writeBytes(lineEnd)
        outputStream.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd)
        outputStream.flush()
        outputStream.close()
        inputStream.close()

        val responseCode = conn.responseCode
        val responseText = readResponse(conn)

        if (responseCode in 200..299) {
            return parseUrlFromFilesIr(responseText, endpoint)
        } else {
            throw IOException("Files.ir error: $responseCode - $responseText")
        }
    }

    private fun uploadToTmpFiles(
        context: Context,
        uri: Uri,
        fileName: String,
        mimeType: String,
        progress: (Int) -> Unit
    ): String {
        val boundary = "===CuponAppTmpUpload==="
        val lineEnd = "\r\n"
        val twoHyphens = "--"

        val conn = URL("https://tmpfiles.org/api/v1/upload").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.doInput = true
        conn.useCaches = false
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        conn.connectTimeout = 30000
        conn.readTimeout = 60000

        val outputStream = DataOutputStream(conn.outputStream)
        outputStream.writeBytes(twoHyphens + boundary + lineEnd)
        outputStream.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"$fileName\"$lineEnd")
        outputStream.writeBytes("Content-Type: $mimeType$lineEnd$lineEnd")

        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Cannot read file")
        val fileSize = inputStream.available().coerceAtLeast(1)
        val buffer = ByteArray(8192)
        var bytesRead: Int
        var totalBytesRead = 0

        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            outputStream.write(buffer, 0, bytesRead)
            totalBytesRead += bytesRead
            progress(((totalBytesRead.toDouble() / fileSize) * 100).toInt().coerceAtMost(99))
        }

        outputStream.writeBytes(lineEnd)
        outputStream.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd)
        outputStream.flush()
        outputStream.close()
        inputStream.close()

        val responseCode = conn.responseCode
        val responseText = readResponse(conn)

        if (responseCode in 200..299) {
            val json = JSONObject(responseText)
            val url = json.getJSONObject("data").getString("url")
            // convert tmpfiles.org/123/file to direct download tmpfiles.org/dl/123/file
            return url.replace("tmpfiles.org/", "tmpfiles.org/dl/")
        } else {
            throw IOException("Upload error ($responseCode): $responseText")
        }
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
            return json.optJSONObject("data")?.optString("short_url")
                ?: json.optString("short_url")
                ?: longUrl
        }
        return longUrl
    }

    private fun parseUrlFromFilesIr(response: String, endpoint: String): String {
        try {
            val json = JSONObject(response)
            if (json.has("fileEntry")) {
                val fe = json.getJSONObject("fileEntry")
                return fe.optString("url", fe.optString("share_url", "${endpoint.trimEnd('/')}/drive/s/${fe.optString("hash")}"))
            }
            if (json.has("url")) return json.getString("url")
            if (json.has("data") && json.getJSONObject("data").has("url")) {
                return json.getJSONObject("data").getString("url")
            }
        } catch (e: Exception) {
            Timber.w(e, "Error parsing files.ir response: $response")
        }
        return "${endpoint.trimEnd('/')}/file"
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
                sb.append("• فضای ابری Files.ir: توکن وارد نشده (آپلود عمومی رایگان فعال است)\n")
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
