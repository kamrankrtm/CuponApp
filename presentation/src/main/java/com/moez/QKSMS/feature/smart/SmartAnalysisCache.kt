package com.moez.QKSMS.feature.smart

import com.moez.QKSMS.feature.smart.analysis.SmsAnalysis
import com.moez.QKSMS.feature.smart.analysis.SmsAnalyzer
import com.moez.QKSMS.feature.smart.analysis.SmsText

/**
 * The readings every consumer shares: the incoming-message processor, notifications, the
 * inbox scan, the tabs and the banking rows all ask here, so an unchanged message is analysed
 * once per engine version instead of once per caller.
 *
 * Entries are keyed by the provider's message identity ("sms:<contentId>"), which survives a
 * full re-sync where Realm ids do not, and are trusted only while the message's fingerprint
 * and the engine version still match. Memory only: readings carry OTP values, which never
 * touch the disk.
 *
 * [generation] moves whenever every reading must be redone (a full sync rebuilt the local
 * records); conversation-level caches fold it into their own keys.
 */
object SmartAnalysisCache {

    private const val MAX_ENTRIES = 4_000

    private val entries = object : LinkedHashMap<String, SmsAnalysis>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, SmsAnalysis>?): Boolean =
            size > MAX_ENTRIES
    }

    @Volatile
    var generation: Int = 0
        private set

    /** Readings actually computed, as opposed to served from the cache; for tests and the benchmark. */
    @Volatile
    var analysesRun: Long = 0L
        private set

    /** The reading of a message, computed only when there is none for this exact content and engine. */
    fun analyze(sender: String, body: String, date: Long, threadId: Long, sourceKey: String?): SmsAnalysis {
        val key = sourceKey?.takeIf { it.isNotEmpty() }
        if (key != null) {
            val fingerprint = SmsAnalyzer.fingerprint(sender.trim(), date, SmsText.text(body.trim()))
            synchronized(entries) {
                val cached = entries[key]
                if (cached != null && cached.fingerprint == fingerprint && cached.engineVersion == SmsAnalyzer.ENGINE_VERSION) {
                    return cached
                }
            }
        }
        val fresh = SmsAnalyzer.analyze(sender, body, date, threadId, key)
        synchronized(entries) {
            analysesRun++
            entries[fresh.sourceKey] = fresh
        }
        return fresh
    }

    /** The cached reading for [sourceKey], if any, whatever its age. */
    fun peek(sourceKey: String): SmsAnalysis? = synchronized(entries) { entries[sourceKey] }

    /**
     * Replaces a reading with a better one (the AI's), unless the message changed since or a
     * newer engine has already re-read it.
     */
    fun replace(analysis: SmsAnalysis): Boolean = synchronized(entries) {
        val current = entries[analysis.sourceKey]
        if (current != null && current.fingerprint != analysis.fingerprint) return false
        entries[analysis.sourceKey] = analysis
        true
    }

    /** Forgets every reading: local records were rebuilt, or the rules changed under them. */
    fun invalidateAll() {
        synchronized(entries) {
            entries.clear()
            generation++
        }
    }

    fun size(): Int = synchronized(entries) { entries.size }
}
