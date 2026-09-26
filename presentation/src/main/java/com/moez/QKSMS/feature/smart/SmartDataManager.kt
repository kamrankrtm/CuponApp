package com.moez.QKSMS.feature.smart

import android.content.Context
import com.moez.QKSMS.feature.smart.model.OtpItem
import com.moez.QKSMS.feature.smart.model.PromoItem
import com.moez.QKSMS.feature.smart.promo.PromoParser
import com.moez.QKSMS.feature.smart.promo.PromoRanker
import com.moez.QKSMS.feature.smart.promo.PromoRow
import com.moez.QKSMS.feature.smart.promo.PromoStore
import java.util.concurrent.CopyOnWriteArrayList

/**
 * In-memory cache of the smart tabs, backed by [PromoStore] for discount codes.
 *
 * OTPs stay memory-only on purpose: they are single-use and worthless after a day, so writing
 * them to disk would be a liability rather than a feature.
 */
object SmartDataManager {

    private val promoList = CopyOnWriteArrayList<PromoItem>()
    private val otpList = CopyOnWriteArrayList<OtpItem>()

    @Volatile
    private var loadedFromDisk = false

    private fun getYesterdayCutoff(): Long {
        val cal = java.util.Calendar.getInstance().apply {
            add(java.util.Calendar.DAY_OF_YEAR, -1)
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    /**
     * Wires up storage and restores the saved codes.
     *
     * Call once on app start, before the discounts tab is shown.
     */
    fun init(context: Context) {
        PromoStore.init(context)
        SenderOverrides.init(context)
        if (loadedFromDisk) return
        synchronized(this) {
            if (loadedFromDisk) return
            val restored = PromoStore.loadPromos()
            promoList.clear()
            promoList.addAll(restored)
            loadedFromDisk = true
        }
    }

    private fun persist() {
        // Dead codes are dropped, but ones the user marked used or broken are kept so they do
        // not come back from the inbox on the next scan.
        val keep = promoList.filter { promo ->
            promo.isUsed || promo.isInvalid || !promo.isExpired()
        }
        PromoStore.savePromos(keep)
    }

    /** Live codes, best-first. */
    fun getPromos(): List<PromoItem> =
        PromoRanker.rank(promoList.filter { !it.isUsed && !it.isInvalid && !it.isExpired() })

    /** Live codes grouped into sections with headers, ready for the list adapter. */
    fun getPromoRows(): List<PromoRow> = PromoRanker.buildRows(getPromos())

    /** Codes the user has already marked used or reported broken. */
    fun getArchivedPromos(): List<PromoItem> =
        promoList.filter { it.isUsed || it.isInvalid }.sortedByDescending { it.receivedAt }

    fun getOtps(): List<OtpItem> {
        val cutoff = getYesterdayCutoff()
        return otpList.filter { it.receivedAt >= cutoff }
    }

    /**
     * Adds a freshly extracted code.
     *
     * A repeat of a code the user already used or reported is ignored rather than resurrected
     * — brands re-send reminder texts for the same offer constantly, and the old cache had no
     * way to remember that the user was done with it.
     */
    fun addPromo(promo: PromoItem) {
        if (addWithoutSaving(promo)) persist()
    }

    /** Adds every card of one message, saving once. */
    fun addPromos(promos: List<PromoItem>) {
        var changed = false
        for (promo in promos) changed = addWithoutSaving(promo) || changed
        if (changed) persist()
    }

    @Synchronized
    private fun addWithoutSaving(promo: PromoItem): Boolean {
        if (promo.isExpired()) return false

        val existing = promoList.firstOrNull { sameOffer(it, promo) }
        if (existing != null) {
            if (existing.isUsed || existing.isInvalid) return false
            // Carry the user's own flags onto the newer copy of the same offer.
            promo.isPinned = existing.isPinned
            promoList.remove(existing)
        }
        promoList.add(0, promo)
        return true
    }

    /**
     * Swaps every card read from one message for a better reading of it — the AI's, or the
     * rules engine's once it has learned from the AI — keeping what the user marked.
     */
    @Synchronized
    fun replaceForMessage(sourceKey: String, promos: List<PromoItem>, save: Boolean = true) {
        if (sourceKey.isEmpty()) {
            for (promo in promos) addWithoutSaving(promo)
            if (save) persist()
            return
        }
        val previous = promoList.filter { it.sourceKey == sourceKey }
        for (promo in promos) {
            val before = previous.firstOrNull { it.code.equals(promo.code, ignoreCase = true) } ?: continue
            promo.isPinned = before.isPinned
            promo.isUsed = before.isUsed
            promo.isInvalid = before.isInvalid
        }
        promoList.removeAll(previous)
        for (promo in promos) {
            promoList.removeAll { it !== promo && sameOffer(it, promo) && !it.isUsed && !it.isInvalid }
            if (promo.isUsed || promo.isInvalid || !promo.isExpired()) promoList.add(0, promo)
        }
        if (save) persist()
    }

    /** Writes the list to disk after a run of [replaceForMessage] calls made with `save = false`. */
    fun save() = persist()

    /**
     * Reads every saved card again from the message it was saved with.
     *
     * Cards are stored with the reading that produced them, and the inbox scan only re-reads
     * recent messages, so without this a card keeps whatever an older build made of it — the
     * "اسنپ / تاکسی اینترنتی" a user kept seeing after the rules had learned "فروشگاه اسنپ".
     * The user's marks carry over by code; a used or broken record the new reading no longer
     * finds is kept, so it never comes back; a code only the AI could read is kept as it is.
     */
    @Synchronized
    fun refileAll() {
        val groups = promoList.filter { it.body.isNotBlank() }
            .groupBy { "${it.sender}\u0000${it.receivedAt}\u0000${it.body}" }
        for (cards in groups.values) {
            val first = cards.first()
            val fresh = try {
                PromoParser.parse(first.sender, first.body, first.receivedAt, first.threadId)
            } catch (e: Exception) {
                continue
            }
            if (fresh.isEmpty() && cards.any { it.source == PromoItem.SOURCE_AI || it.id.startsWith("ai-") }) continue
            for (promo in fresh) {
                val before = cards.firstOrNull { it.code.equals(promo.code, ignoreCase = true) } ?: continue
                promo.isPinned = before.isPinned
                promo.isUsed = before.isUsed
                promo.isInvalid = before.isInvalid
            }
            val records = cards.filter { old ->
                (old.isUsed || old.isInvalid) && fresh.none { it.code.equals(old.code, ignoreCase = true) }
            }
            promoList.removeAll(cards)
            promoList.addAll(fresh.filter { it.isUsed || it.isInvalid || !it.isExpired() })
            promoList.addAll(records)
        }

        // Reminder texts repeat one offer; keep its newest card, and none past a used record
        val closed = promoList.filter { it.isUsed || it.isInvalid }.map { it.dedupeKey }.toSet()
        val seen = HashSet<String>()
        val kept = promoList.sortedByDescending { it.receivedAt }.filter { promo ->
            promo.isUsed || promo.isInvalid || (promo.dedupeKey !in closed && seen.add(promo.dedupeKey))
        }
        promoList.clear()
        promoList.addAll(kept)
        persist()
    }

    /** Two cards are one offer when they share a brand and code, or come from one message. */
    private fun sameOffer(a: PromoItem, b: PromoItem): Boolean =
        a.dedupeKey == b.dedupeKey || flagKeys(a).any { it in flagKeys(b) }

    /**
     * The ways a stored card is recognised again after a re-scan. A better reading may rename
     * the brand ("اسنپ" → "اسنپ‌فود"), so the message and code identify it as well; cards saved
     * before messages were tracked fall back to sender, time and code.
     */
    private fun flagKeys(promo: PromoItem): List<String> {
        val code = promo.code.trim().toUpperCase()
        return listOfNotNull(
            promo.messageCodeKey.takeIf { it.isNotEmpty() }?.let { "m:$it" },
            "l:${promo.sender.trim()}|${promo.receivedAt}|$code"
        )
    }

    /** Bulk replace after a full inbox scan, preserving everything the user marked. */
    fun setPromos(promos: List<PromoItem>) {
        val userFlags = HashMap<String, PromoItem>()
        for (promo in promoList) {
            if (promo.isUsed || promo.isInvalid || promo.isPinned) {
                userFlags[promo.dedupeKey] = promo
                flagKeys(promo).forEach { userFlags[it] = promo }
            }
        }

        val merged = ArrayList<PromoItem>()
        val seen = HashSet<String>()

        // Incoming promos are newest-first, so the first sighting of a key is the freshest.
        for (promo in promos) {
            val key = promo.dedupeKey
            if (!seen.add(key)) continue
            val flagged = userFlags[key] ?: flagKeys(promo).mapNotNull { userFlags[it] }.firstOrNull()
            if (flagged != null) {
                // Re-added below as the user's own record, not as a live code.
                if (flagged.isUsed || flagged.isInvalid) continue
                promo.isPinned = flagged.isPinned
            }
            if (promo.isExpired()) continue
            merged.add(promo)
        }

        // Keep used/broken records so the next scan does not bring them back.
        for (flagged in userFlags.values.toSet()) {
            if (!flagged.isUsed && !flagged.isInvalid) continue
            if (merged.none { it.dedupeKey == flagged.dedupeKey }) {
                merged.add(flagged)
            }
        }

        // Codes the AI read for a message the rules still cannot: paid for once, never re-sent,
        // so a re-scan must not throw them away
        for (promo in promoList) {
            val fromAi = promo.source == PromoItem.SOURCE_AI || promo.id.startsWith("ai-")
            if (!fromAi || promo.isUsed || promo.isInvalid || promo.isExpired()) continue
            if (merged.none { sameOffer(it, promo) }) merged.add(promo)
        }

        promoList.clear()
        promoList.addAll(merged)
        persist()
    }

    fun markUsed(promo: PromoItem, used: Boolean) {
        promoList.firstOrNull { it.dedupeKey == promo.dedupeKey }?.isUsed = used
        promo.isUsed = used
        persist()
    }

    fun markInvalid(promo: PromoItem, invalid: Boolean) {
        promoList.firstOrNull { it.dedupeKey == promo.dedupeKey }?.let {
            it.isInvalid = invalid
            it.isUsed = invalid
        }
        promo.isInvalid = invalid
        promo.isUsed = invalid
        persist()
    }

    fun setPinned(promo: PromoItem, pinned: Boolean) {
        promoList.firstOrNull { it.dedupeKey == promo.dedupeKey }?.isPinned = pinned
        promo.isPinned = pinned
        persist()
    }

    /** Undo for the "used" / "doesn't work" buttons. */
    fun restore(promo: PromoItem) {
        promoList.firstOrNull { it.dedupeKey == promo.dedupeKey }?.let {
            it.isUsed = false
            it.isInvalid = false
        }
        promo.isUsed = false
        promo.isInvalid = false
        persist()
    }

    fun addOtp(otp: OtpItem) {
        if (otp.receivedAt < getYesterdayCutoff()) return
        val key = "${otp.sender.trim()}_${otp.code.trim()}"
        otpList.removeAll { "${it.sender.trim()}_${it.code.trim()}" == key }
        otpList.add(0, otp)
    }

    /** Replaces the OTP cache; promos are untouched. */
    fun setOtps(otps: List<OtpItem>) {
        otpList.clear()
        val cutoff = getYesterdayCutoff()
        val seenOtpKeys = HashSet<String>()
        for (o in otps) {
            if (o.receivedAt < cutoff) continue
            val key = "${o.sender.trim()}_${o.code.trim()}"
            if (seenOtpKeys.add(key)) {
                otpList.add(o)
            }
        }
    }

    fun setPromosAndOtps(promos: List<PromoItem>, otps: List<OtpItem>) {
        setPromos(promos)
        setOtps(otps)
    }
}
