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

                for ((index, msg) in sortedMessages.withIndex()) {
                    if (!msg.isValid) continue
                    val isMe = msg.isMe()
                    val body = msg.body.trim()
                    if (body.isBlank()) continue

                    val timestamp = msg.date
                    val j = JalaliCalendar.fromMillis(timestamp)
                    val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
                    val timeStr = String.format("%02d:%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), cal.get(Calendar.SECOND))
                    val dateJalali = "${j.year}/${String.format("%02d", j.month)}/${String.format("%02d", j.day)}"
                    val weekday = JalaliCalendar.getWeekdayName(j.dayOfWeek)
                    val monthName = JalaliCalendar.getMonthName(j.month)
                    val dateGregorian = String.format("%04d-%02d-%02d %02d:%02d:%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), cal.get(Calendar.SECOND))

                    val diffMinutes = (nowMillis - timestamp) / (1000 * 60)
                    val timeAgo = when {
                        diffMinutes < 1 -> "همین الان (کمتر از یک دقیقه پیش / just now)"
                        diffMinutes < 60 -> "$diffMinutes دقیقه پیش ($diffMinutes min ago)"
                        diffMinutes < 1440 -> "${diffMinutes / 60} ساعت و ${diffMinutes % 60} دقیقه پیش (${diffMinutes / 60}h ${diffMinutes % 60}m ago)"
                        else -> "${diffMinutes / 1440} روز پیش (${diffMinutes / 1440} days ago)"
                    }

                    val senderTag = if (isMe) "کاربر / من (Me)" else "مخاطب: $contactName (Sender)"
                    conversationHistory.append("[پیام ${index + 1}] $senderTag\n")
                    conversationHistory.append("  - تاریخ و زمان ارسال: $weekday، ${j.day} $monthName ${j.year} ساعت $timeStr شمسی (میلادی: $dateGregorian)\n")
                    conversationHistory.append("  - فاصله زمانی تا اکنون: $timeAgo\n")
                    conversationHistory.append("  - متن پیام: $body\n\n")
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

            val nowCal = Calendar.getInstance().apply { timeInMillis = nowMillis }
            val nowJ = JalaliCalendar.fromMillis(nowMillis)
            val nowWeekday = JalaliCalendar.getWeekdayName(nowJ.dayOfWeek)
            val nowMonthName = JalaliCalendar.getMonthName(nowJ.month)
            val nowTimeStr = String.format("%02d:%02d:%02d", nowCal.get(Calendar.HOUR_OF_DAY), nowCal.get(Calendar.MINUTE), nowCal.get(Calendar.SECOND))
            val nowJalaliFull = "$nowWeekday، ${nowJ.day} $nowMonthName ${nowJ.year} ساعت $nowTimeStr"
            val nowGregorian = String.format("%04d-%02d-%02d %s", nowCal.get(Calendar.YEAR), nowCal.get(Calendar.MONTH) + 1, nowCal.get(Calendar.DAY_OF_MONTH), nowTimeStr)

            val hourOfDay = nowCal.get(Calendar.HOUR_OF_DAY)
            val timeOfDayContext = when (hourOfDay) {
                in 5..11 -> "صبح (Morning)"
                in 12..14 -> "ظهر (Noon)"
                in 15..18 -> "عصر (Afternoon)"
                in 19..23 -> "شب (Evening / Night)"
                else -> "نیمه‌شب و بامداد (Late Night / Midnight)"
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
                    You are an intelligent, context-aware AI Smart Reply assistant for SMS messages and smartwatch quick replies.
                    
                    You will receive:
                    1. CURRENT SEND TIME (تاریخ و زمان فعلی ارسال): Exact current Jalali date, Gregorian date, time, weekday, and period of day (e.g. morning, afternoon, night).
                    2. CONVERSATION HISTORY (تاریخچه پیام‌ها): Up to the last 10 messages with exact timestamps of when each was sent (Jalali, Gregorian, time, and elapsed time relative to the current moment).
                    
                    CRITICAL INSTRUCTIONS:
                    1. TEMPORAL & TIMING AWARENESS (آگاهی زمانی):
                       - Observe when the last message was received vs the current time.
                       - If received just now (a few minutes ago): this is an active live conversation; reply promptly and directly.
                       - If received many hours or days ago: acknowledge the time gap naturally if suitable for the user's style (e.g. "سلام ببخشید دیر پاسخ دادم" or keep casual if user is informal).
                       - If appropriate to greet, align with the CURRENT TIME OF DAY (e.g., "صبح بخیر", "عصر بخیر", "شب بخیر").
                    2. TONE & VOCABULARY MATCHING:
                       - Strictly analyze the user's past sent messages ("کاربر / من (Me)").
                       - Adopt the exact tone (formal, friendly, intimate, slang, casual, or business).
                       - Match abbreviation style, emoji usage (only if user uses emojis), and punctuation.
                    3. LANGUAGE:
                       - If the conversation is in Persian, reply in fluent, natural Persian (avoid robotic, literal translations).
                       - If in English or Pinglish, reply in the same language.
                    4. OUTPUT FORMAT:
                       - Return ONLY the exact reply text to send to the recipient.
                       - NO introductory text, NO explanations, NO quotes, NO markdown formatting.
                """.trimIndent()

                val userPrompt = """
                    [اطلاعات زمان فعلی ارسال - CURRENT SENDING TIME]
                    - تاریخ و زمان فعلی شمسی: $nowJalaliFull
                    - تاریخ و زمان فعلی میلادی: $nowGregorian
                    - موقعیت زمانی فعلی: $timeOfDayContext
                    - مخاطب پیام: $contactName ($address)
                    
                    [تاریخچه پیام‌های قبلی به همراه تاریخ و زمان ارسال هر پیام - PREVIOUS MESSAGES WITH TIMESTAMPS]
                    $conversationHistory
                    
                    متن پاسخ مناسب برای ارسال در این لحظه (Reply text to send right now):
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
