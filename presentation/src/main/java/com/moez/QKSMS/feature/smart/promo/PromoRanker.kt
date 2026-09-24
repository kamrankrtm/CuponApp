package com.moez.QKSMS.feature.smart.promo

import com.moez.QKSMS.feature.smart.model.PromoItem

/** The time buckets the discount list is broken into. */
enum class PromoSection(val title: String) {
    PINNED("نشان‌شده"),
    EXPIRING_TODAY("امروز تمام می‌شود"),
    THIS_WEEK("تا آخر هفته"),
    LATER("مهلت‌دار"),
    NO_DEADLINE("بدون مهلت مشخص")
}

/** One row of the discount list: either a section title or a code. */
sealed class PromoRow {
    data class Header(val section: PromoSection, val count: Int) : PromoRow()
    data class Item(val promo: PromoItem) : PromoRow()
}

/**
 * Decides what the user sees first.
 *
 * Sorting purely by arrival time — what the list used to do — buries a 70,000-Toman code
 * expiring tonight under a worthless one that happened to arrive an hour ago. This scores each
 * code on how soon it dies and how much it is worth, so the thing worth acting on today rises.
 */
object PromoRanker {

    private const val HOUR_MS = 60L * 60 * 1000L
    private const val DAY_MS = 24L * HOUR_MS

    /**
     * Converts a discount into a rough Toman-equivalent worth, so a percentage and a flat
     * amount can be compared at all.
     *
     * A percentage is worth more on a bigger basket, so it is applied to the minimum order
     * when one is stated, and to a modest assumed basket otherwise. This is deliberately a
     * heuristic: it only has to order codes sensibly, not predict a real saving.
     */
    fun estimatedWorth(promo: PromoItem): Long = when (promo.discountType) {
        DiscountType.AMOUNT -> promo.discountValue
        DiscountType.PERCENT -> {
            val basket = if (promo.minOrderValue > 0) promo.minOrderValue else ASSUMED_BASKET
            basket * promo.discountValue / 100
        }
        DiscountType.FREE_SHIPPING -> FREE_SHIPPING_WORTH
        DiscountType.UNKNOWN -> 0L
    }

    /**
     * Higher scores sort first.
     *
     * Urgency dominates: a code with hours left outranks a fatter one that is good for weeks,
     * because the fat one will still be there tomorrow. Worth breaks ties, freshness breaks
     * those, and a low-confidence extraction is pushed down rather than hidden.
     */
    fun score(promo: PromoItem, now: Long = System.currentTimeMillis()): Long {
        if (promo.isPinned) return Long.MAX_VALUE / 2 + promo.receivedAt / 1000

        var score = 0L

        val remaining = promo.remainingMillis(now)
        score += when {
            // Already dead, or about to be: ranked by how little time is left
            remaining == null -> 0L
            remaining <= 0L -> -1_000_000L
            remaining <= 6 * HOUR_MS -> 900_000L
            remaining <= DAY_MS -> 700_000L
            remaining <= 3 * DAY_MS -> 400_000L
            remaining <= 7 * DAY_MS -> 200_000L
            else -> 50_000L
        }

        // A deadline the sender actually wrote is more trustworthy than one we assumed,
        // so assumed deadlines do not get to claim urgency.
        if (!promo.expiryIsExplicit) score -= 150_000L

        // Worth, compressed so a 2-million-Toman code cannot outweigh every deadline.
        val worth = estimatedWorth(promo)
        score += when {
            worth >= 1_000_000L -> 120_000L
            worth >= 300_000L -> 90_000L
            worth >= 100_000L -> 60_000L
            worth >= 30_000L -> 35_000L
            worth > 0L -> 15_000L
            else -> 0L
        }

        // A shaky extraction should not sit at the top of the list.
        score -= (100 - promo.confidence).toLong() * 2_000L

        // Newest first among otherwise equal codes.
        score += (promo.receivedAt / 60_000L) % 10_000L

        return score
    }

    /** Which bucket a code belongs in. */
    fun sectionOf(promo: PromoItem, now: Long = System.currentTimeMillis()): PromoSection {
        if (promo.isPinned) return PromoSection.PINNED
        val remaining = promo.remainingMillis(now) ?: return PromoSection.NO_DEADLINE
        if (!promo.expiryIsExplicit) return PromoSection.NO_DEADLINE
        return when {
            remaining <= DAY_MS -> PromoSection.EXPIRING_TODAY
            remaining <= 7 * DAY_MS -> PromoSection.THIS_WEEK
            else -> PromoSection.LATER
        }
    }

    /** Sorts codes best-first without grouping them. */
    fun rank(promos: List<PromoItem>, now: Long = System.currentTimeMillis()): List<PromoItem> =
        promos.sortedByDescending { score(it, now) }

    /**
     * Builds the final list: ranked codes, split into sections, each preceded by a header.
     *
     * Empty sections are dropped, so the user never sees a title with nothing under it.
     */
    fun buildRows(promos: List<PromoItem>, now: Long = System.currentTimeMillis()): List<PromoRow> {
        if (promos.isEmpty()) return emptyList()

        val grouped = LinkedHashMap<PromoSection, MutableList<PromoItem>>()
        for (section in PromoSection.values()) {
            grouped[section] = ArrayList()
        }
        for (promo in promos) {
            grouped[sectionOf(promo, now)]?.add(promo)
        }

        val rows = ArrayList<PromoRow>()
        for ((section, items) in grouped) {
            if (items.isEmpty()) continue
            items.sortByDescending { score(it, now) }
            rows.add(PromoRow.Header(section, items.size))
            for (item in items) {
                rows.add(PromoRow.Item(item))
            }
        }
        return rows
    }

    /**
     * Codes worth interrupting the user about: expiring within [withinMs], actually worth
     * something, and stated by the sender rather than assumed by us.
     */
    fun expiringSoon(
        promos: List<PromoItem>,
        now: Long = System.currentTimeMillis(),
        withinMs: Long = 12 * HOUR_MS,
        minWorth: Long = 30_000L
    ): List<PromoItem> = promos
        .filter { promo ->
            if (!promo.expiryIsExplicit || promo.isUsed || promo.isInvalid) return@filter false
            val remaining = promo.remainingMillis(now) ?: return@filter false
            remaining in 1..withinMs && estimatedWorth(promo) >= minWorth
        }
        .sortedByDescending { estimatedWorth(it) }

    /** Basket assumed when a percentage code states no minimum, used only for ranking. */
    private const val ASSUMED_BASKET = 400_000L

    /** Nominal worth of free delivery, roughly a typical Iranian courier fee. */
    private const val FREE_SHIPPING_WORTH = 40_000L
}
