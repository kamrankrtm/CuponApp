package com.moez.QKSMS.feature.smart.ai

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.moez.QKSMS.feature.smart.SmartDataManager
import com.moez.QKSMS.feature.smart.promo.AiEscalation
import com.moez.QKSMS.feature.smart.promo.AiPromoProtocol
import com.moez.QKSMS.feature.smart.promo.PromoMemory
import com.moez.QKSMS.feature.smart.promo.PromoParser
import com.moez.QKSMS.feature.smart.promo.PromoStore
import com.moez.QKSMS.util.Preferences
import io.realm.Realm
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Second-tier extraction: asks a language model about the messages the local engine is unsure of.
 *
 * Cost is controlled at every step:
 * - [AiEscalation] sends a message only when the local reading is unsure of the code, the shop
 *   or a stated figure, the message could hold a code at all, and [com.moez.QKSMS.feature.smart.promo.AiPrivacyFilter]
 *   lets it leave the device;
 * - messages of one campaign (same sender, same wording) go once, and the answer is applied
 *   to the rest through [PromoMemory];
 * - every answer is remembered, so nothing is paid for twice, even after a re-scan;
 * - requests are batched, trimmed ([AiPromoProtocol.compact]) and capped in length;
 * - the automatic pass stops at the user's daily limit.
 *
 * The answer never becomes a card directly: it is stored in [PromoMemory] and the message is
 * re-read by [PromoParser], which checks the code against the text and the shop against the
 * message before believing either.
 */
object AiPromoExtractor {

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val running = AtomicBoolean(false)

    /** Messages per request: enough to share the instructions, small enough to stay reliable. */
    private const val BATCH_SIZE = 15

    /** Ceiling for one manual scan, so a large inbox cannot run up an unbounded bill. */
    private const val MAX_MESSAGES_PER_SCAN = 60

    private const val MANUAL_LOOKBACK_DAYS = 60L
    private const val AUTO_LOOKBACK_DAYS = 14L

    /** Messages that arrive together are sent together. */
    private const val AUTO_DELAY_MS = 20_000L

    const val DEFAULT_MODEL = "gemini-2.5-flash-lite"

    enum class Mode { MANUAL, AUTO }

    /** One inbox message, carried through so the result can keep its provenance. */
    private data class Candidate(
        val messageId: Long,
        val sender: String,
        val body: String,
        val date: Long,
        val threadId: Long
    )

    /** What a scan did, so the UI can be honest about it. */
    data class ScanReport(
        /** Discount cards produced or corrected by this pass. */
        val found: Int,
        /** Messages actually sent to the AI. */
        val analysed: Int,
        val skippedSensitive: Int,
        /** Messages the local engine read confidently, or the AI had already answered. */
        val skippedAlreadyParsed: Int,
        /** Offers with nothing code-shaped in them, which the AI could not help with. */
        val skippedNoCode: Int = 0,
        /** Messages of an already-sent campaign, answered from the same request. */
        val reusedTemplates: Int = 0,
        val promptTokens: Int = 0,
        val completionTokens: Int = 0
    )

    /** Told on the main thread when an automatic pass changed the discount list. */
    @Volatile
    var onPromosUpdated: (() -> Unit)? = null

    @Volatile
    private var pendingAuto: Runnable? = null

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

    /** Messages the automatic pass may still send today. */
    fun remainingToday(prefs: Preferences): Int =
        maxOf(0, prefs.aiDailyLimit.get() - PromoStore.getAiUsage().messagesToday)

    /** Whether the automatic pass is switched on, set up, agreed to, and within today's limit. */
    fun canRunAuto(prefs: Preferences): Boolean =
        prefs.aiAutoRefine.get() && prefs.aiApiKey.get().isNotBlank() &&
            PromoStore.hasAiConsent() && remainingToday(prefs) > 0

    /**
     * Queues an automatic pass over recent messages the local engine was unsure of.
     *
     * Safe to call for every incoming message: calls within [delayMs] of each other collapse
     * into one pass, so a burst of promotional texts becomes one request.
     */
    fun scheduleAutoRefine(context: Context, prefs: Preferences, delayMs: Long = AUTO_DELAY_MS) {
        if (!canRunAuto(prefs)) return
        val appContext = context.applicationContext
        mainHandler.post {
            pendingAuto?.let { mainHandler.removeCallbacks(it) }
            val task = Runnable {
                pendingAuto = null
                launch(appContext, prefs, Mode.AUTO) { _, _, report ->
                    if ((report?.found ?: 0) > 0) onPromosUpdated?.invoke()
                }
            }
            pendingAuto = task
            mainHandler.postDelayed(task, delayMs)
        }
    }

    /**
     * Scans the inbox for offers the local engine could not read with confidence.
     *
     * Requires [PromoStore.hasAiConsent]; the caller is responsible for asking first. Nothing
     * leaves the device until that flag is set.
     */
    fun extractPromos(
        context: Context,
        prefs: Preferences,
        callback: (Boolean, String, ScanReport?) -> Unit
    ) = launch(context, prefs, Mode.MANUAL, callback)

    private fun launch(
        context: Context,
        prefs: Preferences,
        mode: Mode,
        callback: (Boolean, String, ScanReport?) -> Unit
    ) {
        val apiKey = prefs.aiApiKey.get()
        val baseUrl = prefs.aiBaseUrl.get().ifBlank { "https://api.avalai.ir/v1" }
        val model = prefs.aiModel.get().ifBlank { DEFAULT_MODEL }

        if (apiKey.isBlank()) {
            callback(false, "ابتدا کلید API را در تنظیمات وارد کنید", null)
            return
        }
        if (!PromoStore.hasAiConsent()) {
            callback(false, "برای ارسال متن پیامک‌های تبلیغاتی به سرویس هوش مصنوعی، ابتدا باید اجازه بدهید", null)
            return
        }
        if (!running.compareAndSet(false, true)) {
            callback(false, "بررسی دیگری در حال انجام است؛ کمی بعد دوباره امتحان کنید", null)
            return
        }

        executor.execute {
            try {
                scan(prefs, mode, apiKey, baseUrl, model, callback)
            } catch (t: Throwable) {
                android.util.Log.e("AiPromoExtractor", "AI scan failed", t)
                mainHandler.post { callback(false, "خطا در اجرای تحلیل: ${t.localizedMessage ?: t.javaClass.simpleName}", null) }
            } finally {
                running.set(false)
            }
        }
    }

    private fun scan(
        prefs: Preferences,
        mode: Mode,
        apiKey: String,
        baseUrl: String,
        model: String,
        callback: (Boolean, String, ScanReport?) -> Unit
    ) {
        val budget = if (mode == Mode.AUTO) minOf(remainingToday(prefs), MAX_MESSAGES_PER_SCAN) else MAX_MESSAGES_PER_SCAN
        if (budget <= 0) {
            mainHandler.post { callback(true, "سقف روزانه‌ی هوش مصنوعی پر شده است", ScanReport(0, 0, 0, 0)) }
            return
        }

        var skippedSensitive = 0
        var skippedConfident = 0
        var skippedNoCode = 0
        var reused = 0
        val representatives = ArrayList<Pair<String, Candidate>>()
        val lookAlikes = HashMap<String, MutableList<Candidate>>()
        val alreadyScanned = PromoStore.getAiScannedIds()
        val lookbackDays = if (mode == Mode.AUTO) AUTO_LOOKBACK_DAYS else MANUAL_LOOKBACK_DAYS

        val realm = Realm.getDefaultInstance()
        try {
            val since = System.currentTimeMillis() - lookbackDays * 24 * 60 * 60 * 1000L
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

                val verdict = AiEscalation.judge(message.address, body, message.date)
                when {
                    verdict == AiEscalation.Verdict.SKIP_SENSITIVE -> skippedSensitive++
                    verdict == AiEscalation.Verdict.SKIP_CONFIDENT || verdict == AiEscalation.Verdict.SKIP_ALREADY_READ ->
                        skippedConfident++
                    verdict == AiEscalation.Verdict.SKIP_NO_CODE_SHAPE -> skippedNoCode++
                    verdict.send -> {
                        val candidate = Candidate(message.id, message.address, body, message.date, message.threadId)
                        // One campaign, one request: the rest are read from its answer
                        val campaign = PromoMemory.templateKey(message.address, body) ?: "message:${message.id}"
                        val group = lookAlikes[campaign]
                        if (group != null) {
                            group.add(candidate)
                            reused++
                        } else if (representatives.size < budget) {
                            representatives.add(campaign to candidate)
                            lookAlikes[campaign] = ArrayList()
                        }
                    }
                }
            }
        } finally {
            realm.close()
        }

        if (representatives.isEmpty()) {
            val report = ScanReport(0, 0, skippedSensitive, skippedConfident, skippedNoCode, reused)
            mainHandler.post { callback(true, "پیامک نامطمئنی برای بررسی پیدا نشد", report) }
            return
        }

        var updated = 0
        var promptTokens = 0
        var completionTokens = 0
        var sent = 0
        var lastError: String? = null
        val scannedIds = ArrayList<String>()

        for (batch in representatives.chunked(BATCH_SIZE)) {
            val items = batch.mapIndexed { index, (_, c) -> AiPromoProtocol.Item(index, c.sender, c.body) }
            val response = request(apiKey, baseUrl, model, items)
            if (response.second != null) {
                lastError = response.second
                break
            }
            val raw = response.first ?: ""
            val usage = AiPromoProtocol.usageOf(raw)
            promptTokens += usage.promptTokens
            completionTokens += usage.completionTokens
            sent += batch.size
            PromoStore.recordAiUsage(batch.size, usage.promptTokens, usage.completionTokens)

            val findings = AiPromoProtocol.parse(AiPromoProtocol.contentOf(raw)).groupBy { it.index }
            batch.forEachIndexed { index, (campaign, candidate) ->
                // Remembered even when empty: "no code here" is an answer too
                PromoMemory.remember(
                    candidate.sender, candidate.date, candidate.body,
                    findings[index]?.map { it.finding } ?: emptyList()
                )
                for (message in listOf(candidate) + lookAlikes[campaign].orEmpty()) {
                    val promos = PromoParser.parse(message.sender, message.body, message.date, message.threadId)
                    SmartDataManager.replaceForMessage(
                        PromoMemory.messageKey(message.sender, message.date, message.body), promos, save = false
                    )
                    updated += promos.size
                    scannedIds.add(message.messageId.toString())
                }
            }
            PromoStore.saveMemory()
            SmartDataManager.save()
        }

        PromoStore.addAiScannedIds(scannedIds)

        val report = ScanReport(updated, sent, skippedSensitive, skippedConfident, skippedNoCode, reused, promptTokens, completionTokens)
        val error = lastError
        mainHandler.post {
            if (error != null && sent == 0) {
                callback(false, error, report)
            } else {
                callback(true, "هوش مصنوعی $sent پیامک را بررسی کرد و $updated کد تخفیف را خواند یا اصلاح کرد", report)
            }
        }
    }

    /**
     * Sends one batch.
     *
     * Some models refuse `max_tokens` or a fixed temperature; the request is retried once
     * without whatever the provider rejected rather than failing the scan.
     *
     * @return the raw response body, or an error message; exactly one is non-null
     */
    private fun request(
        apiKey: String,
        baseUrl: String,
        model: String,
        items: List<AiPromoProtocol.Item>
    ): Pair<String?, String?> {
        val payload = AiPromoProtocol.buildRequest(model, items)
        var result = post(apiKey, baseUrl, payload)
        val error = result.third
        if (result.first == 400 && error != null) {
            var changed = false
            if (error.contains("max_tokens")) {
                payload.put("max_completion_tokens", payload.optInt("max_tokens"))
                payload.remove("max_tokens")
                changed = true
            }
            if (error.contains("temperature")) {
                payload.remove("temperature")
                changed = true
            }
            if (changed) result = post(apiKey, baseUrl, payload)
        }
        return if (result.first in 200..299) {
            result.second to null
        } else {
            null to (result.third ?: "تحلیل هوش مصنوعی ناموفق بود (${result.first})")
        }
    }

    /** @return HTTP status (0 on a network failure), body on success, error text otherwise */
    private fun post(apiKey: String, baseUrl: String, payload: JSONObject): Triple<Int, String?, String?> {
        var conn: HttpURLConnection? = null
        return try {
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
            OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(payload.toString()) }

            val code = conn.responseCode
            if (code in 200..299) {
                val body = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }
                Triple(code, body, null)
            } else {
                val err = BufferedReader(
                    InputStreamReader(conn.errorStream ?: conn.inputStream, "UTF-8")
                ).use { it.readText() }
                Triple(code, null, "تحلیل هوش مصنوعی ناموفق بود ($code): ${err.take(200)}")
            }
        } catch (e: Exception) {
            Triple(0, null, "خطا در اتصال به سرویس هوش مصنوعی: ${e.localizedMessage ?: e.message}")
        } finally {
            conn?.disconnect()
        }
    }
}
