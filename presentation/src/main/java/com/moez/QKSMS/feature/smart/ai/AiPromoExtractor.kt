package com.moez.QKSMS.feature.smart.ai

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.moez.QKSMS.feature.smart.SmartDataManager
import com.moez.QKSMS.feature.smart.model.PromoItem
import com.moez.QKSMS.model.Conversation
import com.moez.QKSMS.util.Preferences
import io.realm.Realm
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

object AiPromoExtractor {

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun testConnection(apiKey: String, baseUrl: String, callback: (Boolean, String) -> Unit) {
        if (apiKey.isBlank()) {
            callback(false, "API key cannot be empty")
            return
        }

        executor.execute {
            var conn: HttpURLConnection? = null
            try {
                val cleanUrl = baseUrl.trim().removeSuffix("/") + "/models"
                val url = URL(cleanUrl)
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
                    setRequestProperty("Accept", "application/json")
                    connectTimeout = 15000
                    readTimeout = 15000
                }

                val responseCode = conn.responseCode
                if (responseCode in 200..299) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream))
                    val response = reader.readText()
                    reader.close()
                    val json = JSONObject(response)
                    val data = json.optJSONArray("data")
                    val count = data?.length() ?: 0
                    mainHandler.post {
                        callback(true, "Connected successfully! $count models available.")
                    }
                } else {
                    val errReader = BufferedReader(InputStreamReader(conn.errorStream ?: conn.inputStream))
                    val errText = errReader.readText()
                    errReader.close()
                    mainHandler.post {
                        callback(false, "Connection failed (HTTP $responseCode): $errText")
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    callback(false, "Error: ${e.localizedMessage ?: e.message}")
                }
            } finally {
                conn?.disconnect()
            }
        }
    }

    fun extractPromos(
        context: Context,
        prefs: Preferences,
        callback: (Boolean, String, Int) -> Unit
    ) {
        val apiKey = prefs.aiApiKey.get()
        val baseUrl = prefs.aiBaseUrl.get()
        val model = prefs.aiModel.get()

        if (apiKey.isBlank()) {
            callback(false, "Please configure AI API Key first", 0)
            return
        }

        executor.execute {
            val realm = Realm.getDefaultInstance()
            val messagesToScan = mutableListOf<Pair<String, String>>()
            try {
                val conversations = realm.where(Conversation::class.java)
                    .notEqualTo("id", 0L)
                    .equalTo("archived", false)
                    .isNotEmpty("recipients")
                    .isNotNull("lastMessage")
                    .findAll()

                for (conv in conversations) {
                    if (!conv.isValid) continue
                    val sender = conv.recipients.firstOrNull()?.address ?: ""
                    val body = conv.lastMessage?.body ?: ""
                    if (body.isNotBlank()) {
                        messagesToScan.add(Pair(sender, body))
                    }
                }
            } catch (t: Throwable) {
                // Ignore query error
            } finally {
                realm.close()
            }

            extractPromos(apiKey, baseUrl, model, messagesToScan) { results, error ->
                mainHandler.post {
                    if (error == null) {
                        callback(true, "AI found ${results?.size ?: 0} discount codes!", results?.size ?: 0)
                    } else {
                        callback(false, error, 0)
                    }
                }
            }
        }
    }

    fun extractPromos(
        apiKey: String,
        baseUrl: String,
        model: String,
        smsList: List<Pair<String, String>>, // sender, body
        callback: (List<PromoItem>?, String?) -> Unit
    ) {
        if (apiKey.isBlank()) {
            callback(null, "Please configure your AI API key in Settings first.")
            return
        }

        if (smsList.isEmpty()) {
            callback(emptyList(), null)
            return
        }

        executor.execute {
            var conn: HttpURLConnection? = null
            try {
                val cleanUrl = baseUrl.trim().removeSuffix("/") + "/chat/completions"
                val url = URL(cleanUrl)
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    setRequestProperty("Accept", "application/json")
                    connectTimeout = 30000
                    readTimeout = 30000
                    doOutput = true
                }

                val systemPrompt = """
                    You are an Iranian discount promo code extraction engine.
                    Analyze promotional SMS messages and extract active discount coupon codes.
                    For each message with a promo code, output JSON with fields:
                    - brand (Persian name e.g. اسنپ‌فود, دیجی‌کالا, تپسی, اکالا)
                    - code (The exact alphanumeric discount code to copy)
                    - discountAmount (e.g. 50,000 تومان, 30%)
                    - description (Brief 1-line description)
                    - expiryDateText (Expiry date if mentioned, or 'نامشخص')
                    - category (one of: food, shopping, travel, entertainment, other)
                    Return a JSON object: {"results": [...]}
                """.trimIndent()

                val userContentBuilder = StringBuilder("Messages:\n")
                smsList.take(20).forEachIndexed { index, pair ->
                    userContentBuilder.append("[Msg $index] Sender: ${pair.first}\nBody: ${pair.second}\n\n")
                }

                val payload = JSONObject().apply {
                    put("model", if (model.isNotBlank()) model else "gemini-2.5-flash-lite")
                    put("temperature", 0.1)
                    val messagesArray = JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "system")
                            put("content", systemPrompt)
                        })
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", userContentBuilder.toString())
                        })
                    }
                    put("messages", messagesArray)
                }

                val writer = OutputStreamWriter(conn.outputStream, "UTF-8")
                writer.write(payload.toString())
                writer.flush()
                writer.close()

                val responseCode = conn.responseCode
                if (responseCode in 200..299) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8"))
                    val response = reader.readText()
                    reader.close()

                    val json = JSONObject(response)
                    val choices = json.optJSONArray("choices")
                    val content = choices?.optJSONObject(0)?.optJSONObject("message")?.optString("content") ?: ""

                    // Clean markdown fences
                    val cleanJson = content
                        .replace("^```(?:json)?".toRegex(RegexOption.MULTILINE), "")
                        .replace("```$".toRegex(RegexOption.MULTILINE), "")
                        .trim()

                    val parsedPromos = mutableListOf<PromoItem>()
                    val root = try {
                        JSONObject(cleanJson)
                    } catch (e: Exception) {
                        val arrayStart = cleanJson.indexOf('[')
                        val arrayEnd = cleanJson.lastIndexOf(']')
                        if (arrayStart != -1 && arrayEnd != -1) {
                            JSONObject("{\"results\":${cleanJson.substring(arrayStart, arrayEnd + 1)}}")
                        } else JSONObject()
                    }

                    val resultsArray = root.optJSONArray("results") ?: root.optJSONArray("data")
                    if (resultsArray != null) {
                        for (i in 0 until resultsArray.length()) {
                            val item = resultsArray.optJSONObject(i) ?: continue
                            val code = item.optString("code").trim()
                            if (code.isBlank() || code.equals("null", ignoreCase = true)) continue

                            val promo = PromoItem(
                                id = "ai-${System.currentTimeMillis()}-$i",
                                brand = item.optString("brand", "تخفیف"),
                                code = code,
                                discountAmount = item.optString("discountAmount", "تخفیف ویژه"),
                                description = item.optString("description", ""),
                                expiryDateText = item.optString("expiryDateText", "نامشخص"),
                                originalBody = "",
                                receivedAt = System.currentTimeMillis()
                            )
                            parsedPromos.add(promo)
                            SmartDataManager.addPromo(promo)
                        }
                    }

                    mainHandler.post {
                        callback(parsedPromos, null)
                    }
                } else {
                    val errReader = BufferedReader(InputStreamReader(conn.errorStream ?: conn.inputStream, "UTF-8"))
                    val err = errReader.readText()
                    errReader.close()
                    mainHandler.post {
                        callback(null, "AI extraction failed ($responseCode): $err")
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    callback(null, "Error running AI extraction: ${e.localizedMessage ?: e.message}")
                }
            } finally {
                conn?.disconnect()
            }
        }
    }
}
