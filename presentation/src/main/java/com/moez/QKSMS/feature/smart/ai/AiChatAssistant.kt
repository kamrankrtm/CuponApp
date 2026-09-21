package com.moez.QKSMS.feature.smart.ai

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.moez.QKSMS.common.util.JalaliCalendar
import com.moez.QKSMS.model.Conversation
import com.moez.QKSMS.model.Message
import io.realm.Realm
import io.realm.Sort
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar
import java.util.concurrent.Executors

object AiChatAssistant {

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun generateReply(
        context: Context,
        threadId: Long,
        callback: (success: Boolean, replyText: String, threadId: Long, address: String, contactName: String) -> Unit
    ) {
        val sp = android.preference.PreferenceManager.getDefaultSharedPreferences(context)
        val apiKey = sp.getString("aiApiKey", "")?.trim() ?: ""
        val baseUrl = sp.getString("aiBaseUrl", "https://api.avalai.ir/v1")?.trim()?.ifBlank { "https://api.avalai.ir/v1" } ?: "https://api.avalai.ir/v1"
        val model = sp.getString("aiModel", "gemini-2.5-flash-lite")?.trim()?.ifBlank { "gemini-2.5-flash-lite" } ?: "gemini-2.5-flash-lite"

        if (apiKey.isBlank()) {
            callback(false, "Please configure AI API Key in Settings first.", threadId, "", "")
            return
        }

        executor.execute {
            var address = ""
            var contactName = ""
            val conversationHistory = StringBuilder()
            val nowMillis = System.currentTimeMillis()

            val realm = Realm.getDefaultInstance()
            try {
                val conversation = realm.where(Conversation::class.java).equalTo("id", threadId).findFirst()
                val recipient = conversation?.recipients?.firstOrNull()
                address = recipient?.address ?: ""
                contactName = recipient?.contact?.name ?: address

                // Fetch last 10 messages from thread
                val messages = realm.where(Message::class.java)
                    .equalTo("threadId", threadId)
                    .sort("date", Sort.DESCENDING)
                    .limit(10)
                    .findAll()

                val sortedMessages = messages.reversed()

                for (msg in sortedMessages) {
                    if (!msg.isValid) continue
                    val isMe = msg.isMe()
                    val body = msg.body.trim()
                    if (body.isBlank()) continue

                    val timestamp = msg.date
                    val j = JalaliCalendar.fromMillis(timestamp)
                    val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
                    val timeStr = String.format("%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
                    val dateStr = "${j.year}/${String.format("%02d", j.month)}/${String.format("%02d", j.day)}"

                    val diffMinutes = (nowMillis - timestamp) / (1000 * 60)
                    val timeAgo = when {
                        diffMinutes < 1 -> "just now"
                        diffMinutes < 60 -> "$diffMinutes min ago"
                        diffMinutes < 1440 -> "${diffMinutes / 60} hours ago"
                        else -> "${diffMinutes / 1440} days ago"
                    }

                    val senderTag = if (isMe) "Me" else "Sender ($contactName)"
                    conversationHistory.append("[$dateStr $timeStr ($timeAgo)] $senderTag: $body\n")
                }
            } catch (t: Throwable) {
                android.util.Log.e("AiChatAssistant", "Error reading conversation history", t)
            } finally {
                realm.close()
            }

            if (conversationHistory.isBlank()) {
                mainHandler.post {
                    callback(false, "No conversation history found to draft a reply.", threadId, address, contactName)
                }
                return@execute
            }

            var conn: HttpURLConnection? = null
            try {
                val endpoint = baseUrl.removeSuffix("/") + "/chat/completions"
                val url = URL(endpoint)
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Authorization", "Bearer $apiKey")
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    setRequestProperty("Accept", "application/json")
                    connectTimeout = 30000
                    readTimeout = 30000
                    doOutput = true
                }

                val systemPrompt = """
                    You are an intelligent AI Smart Reply assistant for SMS and smartwatch notifications.
                    You will be given the last 10 messages of a text conversation with sender tags and timestamps (including dates and how long ago each message was sent).
                    
                    Your task:
                    1. Carefully analyze the tone, communication style, relationship (casual, formal, affectionate, business), and vocabulary of the user ("Me").
                    2. Check the language: if Persian, reply in fluent Persian. If English, reply in natural English.
                    3. Consider the timestamps and elapsed time (e.g. immediate chat vs response after hours).
                    4. Generate EXACTLY ONE natural, context-appropriate reply that the user would say to the last message.
                    
                    STRICT RULES:
                    - Output ONLY the plain reply text.
                    - Do NOT include any explanations, greetings to the user, quotes, or markdown code blocks.
                    - Keep it crisp and suitable for mobile SMS or smartwatch quick send.
                """.trimIndent()

                val userPrompt = """
                    Contact Name: $contactName
                    Conversation History (last 10 messages with timestamps):
                    $conversationHistory
                    
                    Generate the reply:
                """.trimIndent()

                val payload = JSONObject().apply {
                    put("model", model)
                    put("temperature", 0.3)
                    val messagesArray = JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "system")
                            put("content", systemPrompt)
                        })
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", userPrompt)
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
                    val respText = reader.readText()
                    reader.close()

                    val json = JSONObject(respText)
                    val choices = json.optJSONArray("choices")
                    val rawReply = choices?.optJSONObject(0)?.optJSONObject("message")?.optString("content") ?: ""

                    val cleanReply = rawReply
                        .replace("^```.*".toRegex(RegexOption.MULTILINE), "")
                        .replace("```$".toRegex(RegexOption.MULTILINE), "")
                        .trim()
                        .trim('"', '\'')

                    mainHandler.post {
                        callback(true, cleanReply, threadId, address, contactName)
                    }
                } else {
                    val errReader = BufferedReader(InputStreamReader(conn.errorStream ?: conn.inputStream, "UTF-8"))
                    val err = errReader.readText()
                    errReader.close()
                    mainHandler.post {
                        callback(false, "AI error ($responseCode): $err", threadId, address, contactName)
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    callback(false, "Connection error: ${e.localizedMessage ?: e.message}", threadId, address, contactName)
                }
            } finally {
                conn?.disconnect()
            }
        }
    }
}
