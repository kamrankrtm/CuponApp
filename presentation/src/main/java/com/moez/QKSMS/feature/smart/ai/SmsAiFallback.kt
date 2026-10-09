package com.moez.QKSMS.feature.smart.ai

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.os.PersistableBundle
import com.moez.QKSMS.feature.smart.SmartAnalysisCache
import com.moez.QKSMS.feature.smart.SmartMessageProcessor
import com.moez.QKSMS.feature.smart.analysis.Evidence
import com.moez.QKSMS.feature.smart.analysis.SmsAiPolicy
import com.moez.QKSMS.feature.smart.analysis.SmsAiProtocol
import com.moez.QKSMS.feature.smart.analysis.SmsAnalysis
import com.moez.QKSMS.feature.smart.analysis.SmsAnalyzer
import com.moez.QKSMS.feature.smart.analysis.SmsKind
import com.moez.QKSMS.feature.smart.promo.PromoStore
import com.moez.QKSMS.model.Message
import com.moez.QKSMS.util.Preferences
import io.realm.Realm
import org.json.JSONObject
import timber.log.Timber
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * The online fallback for banking, verification-code and unclassifiable messages the local
 * engine left open. Never on the receive path: it is scheduled with JobScheduler, outside the
 * SMS receiver's lifetime, with a network constraint, and the local reading is what the user
 * sees until — and unless — a validated answer improves it.
 *
 * - Sends only with the separate banking/OTP consent ([PromoStore.hasSensitiveAiConsent]),
 *   re-checked immediately before every request; revoking it cancels every queued job.
 * - Sends the message, its sender and the local candidates; nothing else.
 * - At most [SmsAiPolicy.MAX_RETRIES] retries, for transient failures only, each within the
 *   daily budget shared with the coupon tier.
 * - Answers are validated by [SmsAiProtocol]; rejected or missing answers are not cached.
 * - Answers are cached by sender, fingerprint and version. OTP-bearing answers stay in memory;
 *   others are kept on disk without any message text.
 * - Message bodies and credentials are never logged.
 */
object SmsAiFallback {

    private const val JOB_ID_BASE = 0x5A1000
    private const val JOB_ID_RANGE = 0x0FFF
    private const val EXTRA_SOURCE_KEY = "source_key"
    private const val EXTRA_ATTEMPT = "attempt"
    private const val FIRST_DELAY_MS = 2_000L
    private const val RETRY_DELAY_MS = 30_000L
    private const val MAX_DISK_VERDICTS = 300

    /** Readings waiting for their job; memory only, so message text never sits in a job's extras. */
    private val pending = ConcurrentHashMap<String, SmsAnalysis>()

    /** Validated answers. OTP-bearing ones are only ever here. */
    private val answers = ConcurrentHashMap<String, SmsAiProtocol.Verdict>()

    @Volatile
    private var diskLoaded = false

    enum class Outcome { DONE, RETRY, DROPPED }

    /** Queues [analysis] if every rule of [SmsAiPolicy] allows; otherwise does nothing. */
    fun maybeSchedule(context: Context, prefs: Preferences, analysis: SmsAnalysis) {
        // A reading that already carries an answer is settled
        if (analysis.evidence.any { it == Evidence.AI_CONFIRMED || it == Evidence.AI_RECLASSIFIED }) return
        cached(analysis)?.let { verdict ->
            applyVerdict(context, prefs, analysis, verdict)
            return
        }
        val decision = SmsAiPolicy.decide(analysis, prefs.aiApiKey.get().isNotBlank(),
            PromoStore.hasSensitiveAiConsent(), AiPromoExtractor.remainingToday(prefs))
        if (!decision.send) return
        pending[analysis.sourceKey] = analysis
        schedule(context, analysis.sourceKey, attempt = 0, delayMs = FIRST_DELAY_MS)
    }

    private fun schedule(context: Context, sourceKey: String, attempt: Int, delayMs: Long) {
        val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
        val extras = PersistableBundle().apply {
            putString(EXTRA_SOURCE_KEY, sourceKey)
            putInt(EXTRA_ATTEMPT, attempt)
        }
        val job = JobInfo.Builder(jobIdFor(sourceKey), ComponentName(context, SmsAiJobService::class.java))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setMinimumLatency(delayMs)
            .setExtras(extras)
            .build()
        try {
            scheduler.schedule(job)
        } catch (e: Exception) {
            Timber.w("Could not schedule AI fallback: ${e.javaClass.simpleName}")
        }
    }

    private fun jobIdFor(sourceKey: String): Int = JOB_ID_BASE + (sourceKey.hashCode() and JOB_ID_RANGE)

    /** Consent revoked: nothing queued may run. */
    fun cancelAll(context: Context) {
        pending.clear()
        val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
        scheduler.allPendingJobs.filter { it.id in JOB_ID_BASE..(JOB_ID_BASE + JOB_ID_RANGE) }
            .forEach { scheduler.cancel(it.id) }
    }

    /** Runs one queued message; called by [SmsAiJobService] on a worker thread. */
    fun run(context: Context, prefs: Preferences, extras: PersistableBundle): Outcome {
        val sourceKey = extras.getString(EXTRA_SOURCE_KEY) ?: return Outcome.DROPPED
        val attempt = extras.getInt(EXTRA_ATTEMPT, 0)
        val outcome = try {
            attempt(context, prefs, sourceKey)
        } catch (t: Throwable) {
            Timber.w("AI fallback failed: ${t.javaClass.simpleName}")
            Outcome.RETRY
        }
        if (outcome == Outcome.RETRY && attempt < SmsAiPolicy.MAX_RETRIES) {
            schedule(context, sourceKey, attempt + 1, RETRY_DELAY_MS shl attempt)
        } else {
            pending.remove(sourceKey)
        }
        return outcome
    }

    private fun attempt(context: Context, prefs: Preferences, sourceKey: String): Outcome {
        if (!PromoStore.hasSensitiveAiConsent()) return Outcome.DROPPED
        val analysis = pending[sourceKey] ?: SmartAnalysisCache.peek(sourceKey) ?: reread(sourceKey) ?: return Outcome.DROPPED
        cached(analysis)?.let {
            applyVerdict(context, prefs, analysis, it)
            return Outcome.DONE
        }
        val decision = SmsAiPolicy.decide(analysis, prefs.aiApiKey.get().isNotBlank(),
            PromoStore.hasSensitiveAiConsent(), AiPromoExtractor.remainingToday(prefs))
        if (!decision.send) return Outcome.DROPPED

        val payload = SmsAiProtocol.buildRequest(prefs.aiModel.get().ifBlank { AiPromoExtractor.DEFAULT_MODEL },
            analysis.sender, analysis.body, analysis.otp)
        val (status, body) = request(prefs, payload)
        if (status == -1) return Outcome.DROPPED // consent withdrawn just before sending
        PromoStore.recordAiUsage(1, usage(body, "prompt_tokens"), usage(body, "completion_tokens"))
        if (status !in 200..299) return if (SmsAiPolicy.isTransient(status)) Outcome.RETRY else Outcome.DROPPED

        return when (val result = SmsAiProtocol.parse(SmsAiProtocol.contentOf(body ?: ""), analysis.sender, analysis.body, analysis.otp)) {
            is SmsAiProtocol.Result.Valid -> {
                remember(analysis, result.verdict)
                applyVerdict(context, prefs, analysis, result.verdict)
                Outcome.DONE
            }
            // Not an answer: keep the local reading and do not remember anything
            is SmsAiProtocol.Result.Rejected -> {
                Timber.i("AI fallback answer rejected: ${result.reason}")
                Outcome.DROPPED
            }
        }
    }

    /**
     * Applies a validated answer. The message may have been re-read since; a newer code is
     * never displaced (the list orders by arrival), and auto-copy still goes through every
     * rule of the copy policy, with the AI's pick checked against the local candidates.
     */
    private fun applyVerdict(context: Context, prefs: Preferences, local: SmsAnalysis, verdict: SmsAiProtocol.Verdict) {
        val current = SmartAnalysisCache.peek(local.sourceKey)?.takeIf { it.fingerprint == local.fingerprint } ?: local
        val merged = SmsAiProtocol.merge(current, verdict)
        if (!SmartAnalysisCache.replace(merged)) return
        SmartMessageProcessor.apply(context, prefs, merged, aiSelected = merged.kind == SmsKind.OTP && verdict.otp != null)
    }

    private fun cached(analysis: SmsAnalysis): SmsAiProtocol.Verdict? {
        loadDisk()
        return answers[SmsAiPolicy.cacheKey(analysis)]
    }

    private fun remember(analysis: SmsAnalysis, verdict: SmsAiProtocol.Verdict) {
        answers[SmsAiPolicy.cacheKey(analysis)] = verdict
        if (verdict.kind == SmsKind.OTP || verdict.otp != null) return
        val all = JSONObject(PromoStore.getAiVerdicts() ?: "{}")
        all.put(SmsAiPolicy.cacheKey(analysis), JSONObject().apply {
            put("k", verdict.kind.name)
            verdict.amount?.let { put("a", it) }
            verdict.balance?.let { put("b", it) }
            verdict.fee?.let { put("f", it) }
            put("d", verdict.direction.name)
            verdict.bank?.let { put("n", it) }
        })
        val keys = all.keys().asSequence().toList()
        keys.take(maxOf(0, keys.size - MAX_DISK_VERDICTS)).forEach { all.remove(it) }
        PromoStore.saveAiVerdicts(all.toString())
    }

    private fun loadDisk() {
        if (diskLoaded) return
        diskLoaded = true
        try {
            val all = JSONObject(PromoStore.getAiVerdicts() ?: return)
            for (key in all.keys()) {
                val o = all.optJSONObject(key) ?: continue
                answers[key] = SmsAiProtocol.Verdict(
                    kind = SmsKind.valueOf(o.optString("k", "UNKNOWN")),
                    otp = null,
                    amount = if (o.has("a")) o.optLong("a") else null,
                    balance = if (o.has("b")) o.optLong("b") else null,
                    fee = if (o.has("f")) o.optLong("f") else null,
                    direction = com.moez.QKSMS.feature.smart.analysis.Direction.valueOf(o.optString("d", "UNKNOWN")),
                    bank = o.optString("n", "").takeIf { it.isNotEmpty() }
                )
            }
        } catch (e: Exception) {
            Timber.w("Stored AI answers unreadable; ignoring them")
        }
    }

    /** The message behind [sourceKey], read again from the database after a process restart. */
    private fun reread(sourceKey: String): SmsAnalysis? {
        val type = sourceKey.substringBefore(':')
        val contentId = sourceKey.substringAfter(':').toLongOrNull() ?: return null
        return Realm.getDefaultInstance().use { realm ->
            val message = realm.where(Message::class.java).equalTo("type", type).equalTo("contentId", contentId).findFirst()
                ?: return@use null
            SmartAnalysisCache.analyze(message.address, message.body, message.date, message.threadId,
                SmsAnalyzer.sourceKeyOf(message.type, message.contentId))
        }
    }

    /**
     * Posts [payload]. Consent is re-read here, right before the connection opens; a
     * provider that rejects a parameter gets one more try without it.
     *
     * @return HTTP status (0 for no connection, -1 when consent was gone) and body
     */
    private fun request(prefs: Preferences, payload: JSONObject): Pair<Int, String?> {
        var result = post(prefs, payload)
        if (result.first == 400) {
            val error = result.second.orEmpty()
            var changed = false
            for (key in listOf("response_format", "temperature", "max_tokens")) {
                if (error.contains(key) && payload.has(key)) {
                    if (key == "max_tokens") payload.put("max_completion_tokens", payload.optInt(key))
                    payload.remove(key)
                    changed = true
                }
            }
            if (changed) result = post(prefs, payload)
        }
        return result
    }

    private fun post(prefs: Preferences, payload: JSONObject): Pair<Int, String?> {
        if (!PromoStore.hasSensitiveAiConsent()) return -1 to null
        val apiKey = prefs.aiApiKey.get().trim()
        if (apiKey.isEmpty()) return -1 to null
        var conn: HttpURLConnection? = null
        return try {
            val url = prefs.aiBaseUrl.get().ifBlank { "https://api.avalai.ir/v1" }.trim().removeSuffix("/") + "/chat/completions"
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Authorization", "Bearer $apiKey")
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Accept", "application/json")
                connectTimeout = 20_000
                readTimeout = 30_000
                doOutput = true
            }
            OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(payload.toString()) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream ?: conn.inputStream
            code to BufferedReader(InputStreamReader(stream, "UTF-8")).use { it.readText() }
        } catch (e: Exception) {
            0 to null
        } finally {
            conn?.disconnect()
        }
    }

    private fun usage(body: String?, key: String): Int = try {
        JSONObject(body ?: "{}").optJSONObject("usage")?.optInt(key, 0) ?: 0
    } catch (e: Exception) {
        0
    }
}
