package com.moez.QKSMS.feature.smart

import android.content.Context
import android.content.SharedPreferences

/**
 * Where the user has said a sender belongs, whatever its messages look like.
 *
 * "Not spam" in the Spam tab and "Move to …" on a long press put a sender in [Tab.PERSONAL],
 * [Tab.BANKING] or [Tab.SPAM]. Its conversation is listed under that tab from then on and its
 * messages notify the way that tab does: Personal and Banking normally, Spam silently.
 * Verification codes are the exception and are always treated as codes.
 *
 * The choices live in memory so the classifier can consult them without touching Android
 * APIs; [init] loads them from disk and every change is written straight back.
 */
object SenderOverrides {

    /** Each tab is stored as its own set; Personal keeps the key the "not spam" list used. */
    enum class Tab(internal val storageKey: String) {
        PERSONAL("senders"),
        BANKING("banking"),
        SPAM("spam")
    }

    private const val PREFS_NAME = "cuponapp_trusted_senders"

    @Volatile
    private var tabs: Map<String, Tab> = emptyMap()

    @Volatile
    private var prefs: SharedPreferences? = null

    /** Safe to call repeatedly; the first call loads the saved choices. */
    fun init(context: Context) {
        if (prefs != null) return
        synchronized(this) {
            if (prefs != null) return
            val store = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val loaded = HashMap<String, Tab>()
            Tab.values().forEach { tab ->
                store.getStringSet(tab.storageKey, emptySet())?.forEach { key -> loaded[key] = tab }
            }
            tabs = loaded
            prefs = store
        }
    }

    /** The tab the user chose for [address], or null while its messages decide. */
    fun tabFor(address: String): Tab? {
        val key = keyOf(address)
        return if (key.isEmpty()) null else tabs[key]
    }

    /** Marked "not spam" or moved to Personal: listed and notified like a contact. */
    fun isTrusted(address: String): Boolean = tabFor(address) == Tab.PERSONAL

    /** Puts all of [addresses] in [tab], or hands them back to the classifier when it is null. */
    fun move(addresses: Collection<String>, tab: Tab?) = update { current ->
        current.toMutableMap().apply {
            addresses.map { keyOf(it) }.filter { it.isNotEmpty() }.forEach { key ->
                if (tab == null) remove(key) else put(key, tab)
            }
        }
    }

    /** What [addresses] are set to now, for [restore] to put back when a move is undone. */
    fun snapshot(addresses: Collection<String>): Map<String, Tab?> = addresses.associateWith { tabFor(it) }

    fun restore(snapshot: Map<String, Tab?>) = update { current ->
        current.toMutableMap().apply {
            snapshot.forEach { (address, tab) ->
                val key = keyOf(address)
                if (key.isNotEmpty()) {
                    if (tab == null) remove(key) else put(key, tab)
                }
            }
        }
    }

    @Synchronized
    private fun update(change: (Map<String, Tab>) -> Map<String, Tab>) {
        val updated = change(tabs)
        tabs = updated
        prefs?.edit()?.apply {
            Tab.values().forEach { tab ->
                putStringSet(tab.storageKey, updated.filterValues { it == tab }.keys.toHashSet())
            }
        }?.apply()
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
