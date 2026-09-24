package com.moez.QKSMS.feature.smart

import android.content.Context
import android.content.SharedPreferences

/**
 * Senders the user has marked "not spam" from the Spam tab.
 *
 * A trusted sender is never classified as spam again: its conversation is listed under
 * Personal, like a saved contact, and its messages notify normally instead of silently.
 *
 * The set lives in memory so the classifier can consult it without touching Android APIs;
 * [init] loads it from disk and every change is written straight back.
 */
object TrustedSenders {

    private const val PREFS_NAME = "cuponapp_trusted_senders"
    private const val KEY_SENDERS = "senders"

    @Volatile
    private var keys: Set<String> = emptySet()

    @Volatile
    private var prefs: SharedPreferences? = null

    /** Safe to call repeatedly; the first call loads the saved senders. */
    fun init(context: Context) {
        if (prefs != null) return
        synchronized(this) {
            if (prefs != null) return
            val store = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            keys = store.getStringSet(KEY_SENDERS, emptySet())?.toSet() ?: emptySet()
            prefs = store
        }
    }

    fun isTrusted(address: String): Boolean {
        val key = keyOf(address)
        return key.isNotEmpty() && key in keys
    }

    fun trust(addresses: Collection<String>) = update { current -> current + addresses.map { keyOf(it) }.filter { it.isNotEmpty() } }

    fun untrust(addresses: Collection<String>) = update { current -> current - addresses.map { keyOf(it) } }

    @Synchronized
    private fun update(change: (Set<String>) -> Set<String>) {
        keys = change(keys)
        prefs?.edit()?.putStringSet(KEY_SENDERS, HashSet(keys))?.apply()
    }

    /**
     * Normalises a sender so the forms one number arrives in all match: Persian digits,
     * spaces and dashes, and the +98 / 0098 / 0 prefixes of a mobile number. Short codes and
     * alphanumeric senders are kept as they are, minus spacing and case.
     */
    fun keyOf(address: String): String {
        val compact = SmartSmsClassifier.normalizeDigits(address)
            .filter { it.isLetterOrDigit() }
            .toUpperCase()
        if (compact.isEmpty() || !compact.all { it in '0'..'9' }) return compact
        return when {
            compact.startsWith("0098") && compact.length == 14 -> compact.substring(4)
            compact.startsWith("98") && compact.length == 12 -> compact.substring(2)
            compact.startsWith("0") && compact.length == 11 -> compact.substring(1)
            else -> compact
        }
    }
}
