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
                    prefs = context.applicationContext
                        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
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
}
