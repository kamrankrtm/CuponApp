package com.moez.QKSMS.feature.smart.promo

import android.content.Context
import android.content.SharedPreferences
import com.moez.QKSMS.feature.smart.model.PromoItem

/**
 * Disk storage for discount codes.
 *
 * The discount list used to live only in memory, so every restart resurrected codes the user
 * had marked used or broken, threw away paid-for AI results, and re-parsed the whole inbox.
 * This keeps all three across restarts.
 */
object PromoStore {

    private const val PREFS_NAME = "cuponapp_promos"
    private const val KEY_PROMOS = "promos_json"
    private const val KEY_LAST_SCANNED_ID = "last_scanned_message_id"
    private const val KEY_AI_SCANNED_IDS = "ai_scanned_message_ids"
    private const val KEY_AI_CONSENT = "ai_consent_granted"
    private const val KEY_MEMORY = "ai_memory_json"
    private const val KEY_ENGINE_VERSION = "engine_version"
    private const val KEY_REFILED_FOR = "refiled_for_version"
    private const val KEY_USAGE_DAY = "ai_usage_day"
    private const val KEY_USAGE_DAY_MESSAGES = "ai_usage_day_messages"
    private const val KEY_USAGE_MONTH = "ai_usage_month"
    private const val KEY_USAGE_MONTH_MESSAGES = "ai_usage_month_messages"
    private const val KEY_USAGE_MONTH_PROMPT = "ai_usage_month_prompt_tokens"
    private const val KEY_USAGE_MONTH_COMPLETION = "ai_usage_month_completion_tokens"

    /**
     * Version of the parsing rules. Raising it makes the next startup re-read the inbox, so
     * codes already on the list are re-filed by the new rules — "اسنپ" codes that were really
     * for اسنپ‌فود move to the right brand. User marks survive, keyed by message and code.
     */
    const val ENGINE_VERSION = 5

    /** Upper bound on stored codes, so the preference blob cannot grow without limit. */
    private const val MAX_STORED = 400

    /** Upper bound on remembered AI-scanned message ids. */
    private const val MAX_AI_IDS = 2000

    @Volatile
    private var prefs: SharedPreferences? = null

    /** Safe to call repeatedly; the first call wins. */
    fun init(context: Context) {
        if (prefs == null) {
            synchronized(this) {
                if (prefs == null) {
                    val store = context.applicationContext
                        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    prefs = store

                    // What the AI has already answered, so nothing is paid for twice
                    PromoMemory.import(store.getString(KEY_MEMORY, null))

                    if (store.getInt(KEY_ENGINE_VERSION, 0) < ENGINE_VERSION) {
                        store.edit()
                            .putLong(KEY_LAST_SCANNED_ID, 0L)
                            .putInt(KEY_ENGINE_VERSION, ENGINE_VERSION)
                            .apply()
                    }
                }
            }
        }
    }

    private fun requirePrefs(): SharedPreferences? = prefs

    // ---------------------------------------------------------------- promos

    /**
     * Restores the saved codes.
     *
     * When the stored blob cannot be used — written by an older version, or corrupt — the scan
     * cursor is rewound as well, so the next startup re-reads the inbox and rebuilds the list
     * instead of leaving the user with nothing.
     */
    fun loadPromos(): List<PromoItem> {
        val store = requirePrefs() ?: return emptyList()
        return when (val result = PromoCodec.decode(store.getString(KEY_PROMOS, null))) {
            is PromoCodec.DecodeResult.Ok -> result.promos
            PromoCodec.DecodeResult.Unusable -> {
                store.edit()
                    .remove(KEY_PROMOS)
                    .putLong(KEY_LAST_SCANNED_ID, 0L)
                    .apply()
                emptyList()
            }
        }
    }

    fun savePromos(promos: List<PromoItem>) {
        val store = requirePrefs() ?: return
        // Keep the newest codes when trimming; older ones are almost certainly dead anyway.
        val trimmed = if (promos.size <= MAX_STORED) {
            promos
        } else {
            promos.sortedByDescending { it.receivedAt }.take(MAX_STORED)
        }
        store.edit().putString(KEY_PROMOS, PromoCodec.encodeList(trimmed)).apply()
    }

    // ---------------------------------------------------------------- incremental scan

    /**
     * Id of the newest inbox message already parsed.
     *
     * Lets the startup scan look only at what arrived since last time instead of re-running
     * regexes over the last 300 messages on every launch.
     */
    fun getLastScannedMessageId(): Long = requirePrefs()?.getLong(KEY_LAST_SCANNED_ID, 0L) ?: 0L

    fun setLastScannedMessageId(id: Long) {
        val store = requirePrefs() ?: return
        store.edit().putLong(KEY_LAST_SCANNED_ID, id).apply()
    }

    /**
     * Whether the saved cards were read by an older build than [appVersion], and should be read
     * again from the messages they were saved with. Every update counts, not only a raised
     * [ENGINE_VERSION]: an old card must never keep what an older build made of it.
     */
    fun needsRefile(appVersion: String): Boolean =
        requirePrefs()?.getString(KEY_REFILED_FOR, null) != "$ENGINE_VERSION/$appVersion"

    fun markRefiled(appVersion: String) {
        val store = requirePrefs() ?: return
        store.edit().putString(KEY_REFILED_FOR, "$ENGINE_VERSION/$appVersion").apply()
    }

    /** Forces the next scan to re-read the whole inbox, e.g. after the parser changes. */
    fun resetScanCursor() {
        val store = requirePrefs() ?: return
        store.edit().putLong(KEY_LAST_SCANNED_ID, 0L).apply()
    }

    // ---------------------------------------------------------------- AI bookkeeping

    /**
     * Message ids already sent to the AI, successfully or not.
     *
     * Remembering these stops the app paying to analyse the same message on every scan.
     */
    fun getAiScannedIds(): MutableSet<String> {
        val store = requirePrefs() ?: return HashSet()
        return HashSet(store.getStringSet(KEY_AI_SCANNED_IDS, emptySet()) ?: emptySet())
    }

    fun addAiScannedIds(ids: Collection<String>) {
        val store = requirePrefs() ?: return
        if (ids.isEmpty()) return
        val merged = getAiScannedIds()
        merged.addAll(ids)
        val capped = if (merged.size <= MAX_AI_IDS) merged else merged.toList().takeLast(MAX_AI_IDS).toMutableSet()
        store.edit().putStringSet(KEY_AI_SCANNED_IDS, capped).apply()
    }

    /**
     * Whether the user has agreed to let message text leave the device for AI analysis.
     *
     * Asked once, stored here, and checked before every upload.
     */
    fun hasAiConsent(): Boolean = requirePrefs()?.getBoolean(KEY_AI_CONSENT, false) ?: false

    fun setAiConsent(granted: Boolean) {
        val store = requirePrefs() ?: return
        store.edit().putBoolean(KEY_AI_CONSENT, granted).apply()
    }

    /** Writes [PromoMemory] to disk; called once per AI batch rather than per message. */
    fun saveMemory() {
        val store = requirePrefs() ?: return
        store.edit().putString(KEY_MEMORY, PromoMemory.export()).apply()
    }

    // ---------------------------------------------------------------- AI usage

    /** What the AI tier has cost: messages sent today, and messages and tokens this month. */
    data class AiUsage(
        val messagesToday: Int,
        val messagesThisMonth: Int,
        val promptTokensThisMonth: Long,
        val completionTokensThisMonth: Long
    )

    private fun dayStamp(now: Long): String {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = now }
        return "${cal.get(java.util.Calendar.YEAR)}-${cal.get(java.util.Calendar.DAY_OF_YEAR)}"
    }

    private fun monthStamp(now: Long): String {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = now }
        return "${cal.get(java.util.Calendar.YEAR)}-${cal.get(java.util.Calendar.MONTH)}"
    }

    fun getAiUsage(now: Long = System.currentTimeMillis()): AiUsage {
        val store = requirePrefs() ?: return AiUsage(0, 0, 0L, 0L)
        val today = store.getString(KEY_USAGE_DAY, null) == dayStamp(now)
        val month = store.getString(KEY_USAGE_MONTH, null) == monthStamp(now)
        return AiUsage(
            messagesToday = if (today) store.getInt(KEY_USAGE_DAY_MESSAGES, 0) else 0,
            messagesThisMonth = if (month) store.getInt(KEY_USAGE_MONTH_MESSAGES, 0) else 0,
            promptTokensThisMonth = if (month) store.getLong(KEY_USAGE_MONTH_PROMPT, 0L) else 0L,
            completionTokensThisMonth = if (month) store.getLong(KEY_USAGE_MONTH_COMPLETION, 0L) else 0L
        )
    }

    /** Adds one request's cost to today's and this month's totals. */
    @Synchronized
    fun recordAiUsage(messages: Int, promptTokens: Int, completionTokens: Int, now: Long = System.currentTimeMillis()) {
        val store = requirePrefs() ?: return
        val usage = getAiUsage(now)
        store.edit()
            .putString(KEY_USAGE_DAY, dayStamp(now))
            .putInt(KEY_USAGE_DAY_MESSAGES, usage.messagesToday + messages)
            .putString(KEY_USAGE_MONTH, monthStamp(now))
            .putInt(KEY_USAGE_MONTH_MESSAGES, usage.messagesThisMonth + messages)
            .putLong(KEY_USAGE_MONTH_PROMPT, usage.promptTokensThisMonth + promptTokens)
            .putLong(KEY_USAGE_MONTH_COMPLETION, usage.completionTokensThisMonth + completionTokens)
            .apply()
    }
}
