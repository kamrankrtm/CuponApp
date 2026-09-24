package com.moez.QKSMS.feature.smart

import android.content.Context
import com.moez.QKSMS.feature.smart.model.OtpItem
import com.moez.QKSMS.feature.smart.model.PromoItem
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
        if (promo.isExpired()) return

        val key = promo.dedupeKey
        val existing = promoList.firstOrNull { it.dedupeKey == key }
        if (existing != null) {
            if (existing.isUsed || existing.isInvalid) return
            // Carry the user's own flags onto the newer copy of the same offer.
            promo.isPinned = existing.isPinned
            promoList.remove(existing)
        }
        promoList.add(0, promo)
        persist()
    }

    /** Bulk replace after a full inbox scan, preserving everything the user marked. */
    fun setPromos(promos: List<PromoItem>) {
        val userFlags = HashMap<String, PromoItem>()
        for (promo in promoList) {
            if (promo.isUsed || promo.isInvalid || promo.isPinned) {
                userFlags[promo.dedupeKey] = promo
            }
        }

        val merged = ArrayList<PromoItem>()
        val seen = HashSet<String>()
        val addedKeys = HashSet<String>()

        // Incoming promos are newest-first, so the first sighting of a key is the freshest.
        for (promo in promos) {
            val key = promo.dedupeKey
            if (!seen.add(key)) continue
            val flagged = userFlags[key]
            if (flagged != null) {
                // Re-added below as the user's own record, not as a live code.
                if (flagged.isUsed || flagged.isInvalid) continue
                promo.isPinned = flagged.isPinned
            }
            if (promo.isExpired()) continue
            merged.add(promo)
            addedKeys.add(key)
        }

        // Keep used/broken records so the next scan does not bring them back.
        for (flagged in userFlags.values) {
            if (!flagged.isUsed && !flagged.isInvalid) continue
            if (addedKeys.add(flagged.dedupeKey)) {
                merged.add(flagged)
            }
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
