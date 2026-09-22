package com.moez.QKSMS.feature.smart.ai

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.moez.QKSMS.common.util.JalaliCalendar
import com.moez.QKSMS.feature.smart.SmartDataManager
import com.moez.QKSMS.feature.smart.SmartSmsClassifier
import com.moez.QKSMS.feature.smart.model.PromoItem
import com.moez.QKSMS.feature.smart.promo.AiPrivacyFilter
import com.moez.QKSMS.feature.smart.promo.BrandRegistry
import com.moez.QKSMS.feature.smart.promo.DiscountType
import com.moez.QKSMS.feature.smart.promo.PromoCodeExtractor
import com.moez.QKSMS.feature.smart.promo.PromoStore
import com.moez.QKSMS.feature.smart.promo.PromoValueParser
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

/**
 * Second-tier extraction: asks a language model about the messages the local engine could not
 * read.
 *
 * Three things changed from the original implementation, all of which mattered:
 * - it no longer uploads everything from a short code, which previously included every bank
 *   alert and one-time password in the inbox;
 * - it only looks at messages the regex engine failed on, instead of re-analysing (and
 *   re-charging for) the same 30 messages every scan;
 * - the results keep the original sender, body and timestamp, so search, expiry and the
 *   "original message" dialog work on AI-derived codes too.
 */
object AiPromoExtractor {

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Messages per request — small enough to stay well inside the model's context. */
    private const val BATCH_SIZE = 20

    /** Hard ceiling per scan, so a large inbox cannot run up an unbounded bill. */
    private const val MAX_MESSAGES_PER_SCAN = 60

    /** How far back a scan looks. */
    private const val LOOKBACK_DAYS = 60L

    /** One inbox message, carried through so the parsed result can keep its provenance. */
    private data class Candidate(
        val messageId: Long,
        val sender: String,
        val body: String,
        val date: Long,
        val threadId: Long
    )

    /** What a scan did, so the UI can be honest about it. */
    data class ScanReport(
        val found: Int,
        val analysed: Int,
        val skippedSensitive: Int,
        val skippedAlreadyParsed: Int
    )

    fun testConnection(apiKey: String, baseUrl: String, callback: (Boolean, String) -> Unit) {
        if (apiKey.isBlank()) {
            callback(false, "API key cannot be empty")
            return
        }

        executor.execute {
            var conn: HttpURLConnection? = null
            try {
                val cleanUrl = baseUrl.trim().removeSuffix("/") + "/models"
                conn = (URL(cleanUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
                    setRequestProperty("Accept", "application/json")
                    connectTimeout = 15000
                    readTimeout = 15000
                }

                val responseCode = conn.responseCode
                if (responseCode in 200..299) {
                    val response = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                    val count = JSONObject(response).optJSONArray("data")?.length() ?: 0
                    mainHandler.post { callback(true, "Connected successfully! $count models available.") }
                } else {
                    val errText = BufferedReader(
                        InputStreamReader(conn.errorStream ?: conn.inputStream)
                    ).use { it.readText() }
                    mainHandler.post { callback(false, "Connection failed (HTTP $responseCode): $errText") }
                }
            } catch (e: Exception) {
                mainHandler.post { callback(false, "Error: ${e.localizedMessage ?: e.message}") }
            } finally {
                conn?.disconnect()
            }
        }
    }

    /**
     * Scans the inbox for offers the local engine missed.
     *
     * Requires [PromoStore.hasAiConsent]; the caller is responsible for asking first. Nothing
     * leaves the device until that flag is set.
     *
     * @param onlyUnparsed when true (the default) only messages the regex engine failed on are
     *   uploaded, which is the whole point of a fallback tier
     */
    fun extractPromos(
        context: Context,
        prefs: Preferences,
        onlyUnparsed: Boolean = true,
        callback: (Boolean, String, ScanReport?) -> Unit
    ) {
        val apiKey = prefs.aiApiKey.get()
        val baseUrl = prefs.aiBaseUrl.get()
        val model = prefs.aiModel.get()

        if (apiKey.isBlank()) {
            callback(false, "ابتدا کلید API را در تنظیمات وارد کنید", null)
            return
        }
        if (!PromoStore.hasAiConsent()) {
            callback(false, "برای ارسال متن پیامک‌های تبلیغاتی به سرویس هوش مصنوعی، ابتدا باید اجازه بدهید", null)
            return
        }

        executor.execute {
            var skippedSensitive = 0
            var skippedAlreadyParsed = 0
            val candidates = ArrayList<Candidate>()
            val alreadyScanned = PromoStore.getAiScannedIds()

            val realm = Realm.getDefaultInstance()
            try {
                val since = System.currentTimeMillis() - LOOKBACK_DAYS * 24 * 60 * 60 * 1000L
                val messages = realm.where(com.moez.QKSMS.model.Message::class.java)
                    .greaterThanOrEqualTo("date", since)
                    .equalTo("type", "sms")
                    .equalTo("boxId", android.provider.Telephony.Sms.MESSAGE_TYPE_INBOX)
                    .sort("date", io.realm.Sort.DESCENDING)
                    .findAll()

                val seenBodies = HashSet<String>()
                for (message in messages) {
                    if (!message.isValid) continue
                    val body = message.body.trim()
                    if (body.isBlank() || !seenBodies.add(body)) continue
                    if (alreadyScanned.contains(message.id.toString())) continue

                    // Privacy gate first: a message that must not leave is never a candidate,
                    // whatever else it looks like.
                    if (!AiPrivacyFilter.isAllowed(message.address, body)) {
                        skippedSensitive++
                        continue
                    }

                    // Fallback gate: the local engine is free and instant, so only pay for
                    // what it could not read.
                    if (onlyUnparsed &&
                        SmartSmsClassifier.extractPromo(message.address, body, message.date) != null
                    ) {
                        skippedAlreadyParsed++
                        continue
                    }

                    candidates.add(Candidate(message.id, message.address, body, message.date, message.threadId))
                    if (candidates.size >= MAX_MESSAGES_PER_SCAN) break
                }
            } catch (t: Throwable) {
                android.util.Log.e("AiPromoExtractor", "Failed to read inbox", t)
            } finally {
                realm.close()
            }

            if (candidates.isEmpty()) {
                mainHandler.post {
                    callback(
                        true,
                        "پیامک جدیدی برای تحلیل پیدا نشد",
                        ScanReport(0, 0, skippedSensitive, skippedAlreadyParsed)
                    )
                }
                return@execute
            }

            var totalFound = 0
            var lastError: String? = null
            val scannedIds = ArrayList<String>()

            for (batch in candidates.chunked(BATCH_SIZE)) {
                val result = requestBatch(apiKey, baseUrl, model, batch)
                if (result.second != null) {
                    lastError = result.second
                    break
                }
                result.first?.forEach { promo ->
                    SmartDataManager.addPromo(promo)
                    totalFound++
                }
                // Mark as scanned even when the model found nothing, so it is not re-sent.
                batch.forEach { scannedIds.add(it.messageId.toString()) }
            }

            PromoStore.addAiScannedIds(scannedIds)

            val report = ScanReport(totalFound, scannedIds.size, skippedSensitive, skippedAlreadyParsed)
            mainHandler.post {
                if (lastError != null && totalFound == 0) {
                    callback(false, lastError!!, report)
                } else {
                    callback(true, "هوش مصنوعی $totalFound کد تخفیف جدید پیدا کرد", report)
                }
            }
        }
    }

    /**
     * Sends one batch and parses the reply.
     *
     * @return parsed promos, or an error message; exactly one is non-null.
     */
    private fun requestBatch(
        apiKey: String,
        baseUrl: String,
        model: String,
        batch: List<Candidate>
    ): Pair<List<PromoItem>?, String?> {
        var conn: HttpURLConnection? = null
        try {
            val cleanUrl = baseUrl.trim().removeSuffix("/") + "/chat/completions"
            conn = (URL(cleanUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Accept", "application/json")
                connectTimeout = 30000
                readTimeout = 45000
                doOutput = true
            }

            val systemPrompt = """
                You extract Iranian discount coupon codes from Persian promotional SMS.
                For each message that contains a usable coupon code, return one object with:
                - index: the [Msg N] number of the message
                - brand: the Persian brand name (e.g. اسنپ‌فود, دیجی‌کالا, تپسی)
                - code: the exact alphanumeric code the user must type
                - discountAmount: the saving as written (e.g. "۵۰ هزار تومان", "۳۰٪", "ارسال رایگان")
                - minOrder: minimum basket if stated, otherwise null
                - expiryDateText: the deadline if stated (e.g. "تا ۵ مهر", "۴۸ ساعت"), otherwise null
                - category: one of food, supermarket, ecommerce, transport, entertainment, fintech, telecom, services, other
                Skip any message with no coupon code. Return strictly {"results": [...]} and nothing else.
            """.trimIndent()

            val userContent = StringBuilder("Messages:\n")
            batch.forEachIndexed { index, candidate ->
                val jalali = JalaliCalendar.fromMillis(candidate.date)
                val dateStr = "${jalali.year}/${pad(jalali.month)}/${pad(jalali.day)}"
                userContent.append("[Msg $index] Sender: ${candidate.sender} | Date: $dateStr\n")
                userContent.append("Body: ${candidate.body}\n\n")
            }

            val payload = JSONObject().apply {
                put("model", if (model.isNotBlank()) model else "gemini-2.5-flash-lite")
                put("temperature", 0.1)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", systemPrompt)
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", userContent.toString())
                    })
                })
            }

            OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(payload.toString()) }

            val responseCode = conn.responseCode
            if (responseCode !in 200..299) {
                val err = BufferedReader(
                    InputStreamReader(conn.errorStream ?: conn.inputStream, "UTF-8")
                ).use { it.readText() }
                return null to "تحلیل هوش مصنوعی ناموفق بود ($responseCode): ${err.take(200)}"
            }

            val response = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }
            val content = JSONObject(response)
                .optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                ?: ""

            return parseResults(content, batch) to null
        } catch (e: Exception) {
            return null to "خطا در اجرای تحلیل: ${e.localizedMessage ?: e.message}"
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * Turns the model's reply into promos, re-attaching the original message each one came
     * from so nothing downstream has to treat AI results as second-class.
     */
    private fun parseResults(content: String, batch: List<Candidate>): List<PromoItem> {
        val cleanJson = content
            .replace("^```(?:json)?".toRegex(RegexOption.MULTILINE), "")
            .replace("```$".toRegex(RegexOption.MULTILINE), "")
            .trim()

        val root = try {
            JSONObject(cleanJson)
        } catch (e: Exception) {
            val start = cleanJson.indexOf('[')
            val end = cleanJson.lastIndexOf(']')
            if (start != -1 && end > start) {
                try {
                    JSONObject("{\"results\":${cleanJson.substring(start, end + 1)}}")
                } catch (e2: Exception) {
                    return emptyList()
                }
            } else {
                return emptyList()
            }
        }

        val results = root.optJSONArray("results") ?: root.optJSONArray("data") ?: return emptyList()
        val promos = ArrayList<PromoItem>()

        for (i in 0 until results.length()) {
            val item = results.optJSONObject(i) ?: continue
            val code = item.optString("code").trim()
            if (code.isBlank() || code.equals("null", ignoreCase = true)) continue

            // Tie the result back to the message it came from; fall back to position.
            val index = item.optInt("index", i)
            val source = batch.getOrNull(index) ?: batch.getOrNull(i) ?: continue

            val normalizedBody = PromoValueParser.normalize(source.body)

            // The message is the ground truth. A code the model reports but that is not in the
            // text it was given either belongs to a different message (a wrong "index") or was
            // invented, and attaching it to this message produces a card whose code, brand and
            // amount all contradict the SMS shown under them.
            if (!PromoCodeExtractor.appearsIn(code, normalizedBody)) continue

            val normalizedSender = PromoValueParser.normalize(source.sender)

            // Identify the brand from the message itself. The model's brand name is accepted
            // only when the registry recognises nothing AND that name actually occurs in the
            // message, because a brand carries its own colour, app package and website: a
            // wrong one sends the "open app" button to an unrelated app.
            val brand = BrandRegistry.match(
                normalizedSender.toLowerCase(),
                normalizedBody.toLowerCase()
            )
            val claimedBrand = item.optString("brand").trim()
            val brandIsSupported = claimedBrand.isNotBlank() && (
                normalizedBody.contains(claimedBrand) || normalizedSender.contains(claimedBrand)
                )

            val minOrderParsed = PromoValueParser.parseMinOrder(normalizedBody)
            val discount = PromoValueParser.parseDiscount(normalizedBody, minOrderParsed?.second)
            val expiry = PromoValueParser.parseExpiry(normalizedBody, source.date)

            val displayBrand = brand?.fa
                ?: claimedBrand.takeIf { brandIsSupported }
                ?: "سایر فروشگاه‌ها"

            // Prefer the locally parsed amount: it is normalized Persian ("۶ میلیون تومان")
            // rather than whatever raw fragment the model echoed back ("+400ت تخفیف").
            val displayAmount = if (discount.type != DiscountType.UNKNOWN) {
                discount.display
            } else {
                item.optStringOrNull("discountAmount") ?: discount.display
            }

            promos.add(
                PromoItem(
                    id = "ai-${source.messageId}-${code.hashCode()}",
                    brand = displayBrand,
                    brandEn = brand?.en ?: "",
                    category = brand?.category ?: "سایر",
                    categorySlug = brand?.categorySlug ?: slugFor(item.optString("category")),
                    code = code,
                    discountAmount = displayAmount,
                    description = "کد تخفیف $displayBrand",
                    minOrder = minOrderParsed?.first?.display ?: item.optStringOrNull("minOrder"),
                    instructions = "در صفحه پرداخت $displayBrand کد $code را وارد کنید.",
                    expiryDateText = expiry.display,
                    sender = source.sender,
                    body = source.body,
                    receivedAt = source.date,
                    threadId = source.threadId,
                    discountType = discount.type,
                    discountValue = discount.value,
                    minOrderValue = minOrderParsed?.first?.value ?: 0L,
                    expiresAt = expiry.atMillis,
                    expiryIsExplicit = expiry.isExplicit,
                    // AI results are inherently less certain than a labelled regex match.
                    confidence = 70,
                    brandColor = brand?.color ?: BrandRegistry.fallbackColor(displayBrand),
                    appPackage = brand?.appPackage,
                    website = brand?.website
                )
            )
        }

        return promos
    }

    private fun slugFor(category: String): String = when (category.trim().toLowerCase()) {
        "food" -> BrandRegistry.SLUG_FOOD
        "supermarket" -> BrandRegistry.SLUG_SUPERMARKET
        "ecommerce", "shopping" -> BrandRegistry.SLUG_ECOMMERCE
        "transport", "travel" -> BrandRegistry.SLUG_TRANSPORT
        "entertainment" -> BrandRegistry.SLUG_ENTERTAINMENT
        "fintech" -> BrandRegistry.SLUG_FINTECH
        "telecom" -> BrandRegistry.SLUG_TELECOM
        "services" -> BrandRegistry.SLUG_SERVICES
        else -> BrandRegistry.SLUG_OTHER
    }

    private fun pad(value: Int): String = if (value < 10) "0$value" else value.toString()

    private fun JSONObject.optStringOrNull(key: String): String? {
        if (isNull(key)) return null
        val value = optString(key).trim()
        return if (value.isBlank() || value.equals("null", ignoreCase = true)) null else value
    }
}
