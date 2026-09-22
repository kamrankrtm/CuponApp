package com.moez.QKSMS.feature.smart.model

import com.moez.QKSMS.feature.smart.promo.DiscountType
import com.moez.QKSMS.feature.smart.promo.PromoValueParser

/**
 * A discount code lifted out of a promotional SMS.
 *
 * The `*Value` fields carry the machine-readable version of what the message said, while the
 * neighbouring text fields carry the Persian the user actually reads. Keeping both means the
 * list can sort and filter on real numbers without re-parsing strings on every bind.
 */
data class PromoItem(
    val id: String,
    val brand: String,
    val brandEn: String = "",
    val category: String = "سایر",
    val categorySlug: String = "other",
    val code: String,
    val discountAmount: String,
    val description: String,
    val minOrder: String? = null,
    val instructions: String = "",
    val expiryDateText: String = "مهلت اعلام نشده",
    val sender: String = "",
    val body: String = "",
    val receivedAt: Long = System.currentTimeMillis(),

    /** Percent for [DiscountType.PERCENT], Tomans for [DiscountType.AMOUNT]. */
    val discountType: DiscountType = DiscountType.UNKNOWN,
    val discountValue: Long = 0L,
    /** Minimum basket in Tomans, 0 when the message set no condition. */
    val minOrderValue: Long = 0L,
    /** When the offer stops working, or null if genuinely open-ended. */
    val expiresAt: Long? = null,
    /** True when the sender stated a deadline; false when the app assumed a default window. */
    val expiryIsExplicit: Boolean = false,
    /** 0..100 — how sure the extractor is that [code] really is a usable coupon. */
    val confidence: Int = 100,
    /** ARGB colour for the brand avatar. */
    val brandColor: Int = 0,
    /** Package of the brand's app, so the card can jump straight into it. */
    val appPackage: String? = null,
    val website: String? = null,

    var isUsed: Boolean = false,
    var isInvalid: Boolean = false,
    var isPinned: Boolean = false
) {

    /** Stable identity of an offer: the same code from the same brand is the same offer. */
    val dedupeKey: String
        get() = "${brand.trim().toLowerCase()}|${code.trim().toUpperCase()}"

    /**
     * Whether the code can no longer be used.
     *
     * A user who marked the code used or broken counts as expired, and so does a stated
     * deadline that has passed. An offer with no stated deadline falls back to
     * [PromoValueParser.DEFAULT_VALIDITY_DAYS] from when it arrived — the old code applied that
     * 30-day cutoff to *every* promo, which silently deleted long-lived and referral codes that
     * explicitly said they ran for longer.
     */
    fun isExpired(nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (isInvalid || isUsed) return true
        val deadline = expiresAt ?: return false
        return nowMillis > deadline
    }

    /** Milliseconds left, or null when the offer has no deadline. */
    fun remainingMillis(nowMillis: Long = System.currentTimeMillis()): Long? {
        val deadline = expiresAt ?: return null
        return deadline - nowMillis
    }

    /**
     * Short Persian countdown for the card: "۴ ساعت مانده", "فردا", "۶ روز مانده".
     *
     * A live countdown makes the urgent code obvious at a glance, which a raw Shamsi date
     * never did.
     */
    fun remainingLabel(nowMillis: Long = System.currentTimeMillis()): String {
        val remaining = remainingMillis(nowMillis) ?: return "بدون مهلت اعلام‌شده"
        if (remaining <= 0L) return "منقضی شده"

        val minutes = remaining / (60 * 1000L)
        val hours = remaining / (60 * 60 * 1000L)
        val days = remaining / (24 * 60 * 60 * 1000L)

        return when {
            minutes < 60 -> "${PromoValueParser.toPersianDigits(maxOf(minutes, 1L).toString())} دقیقه مانده"
            hours < 24 -> "${PromoValueParser.toPersianDigits(hours.toString())} ساعت مانده"
            days <= 1L -> "تا فردا"
            days < 30 -> "${PromoValueParser.toPersianDigits(days.toString())} روز مانده"
            else -> expiryDateText
        }
    }

    /** True when the code is close enough to expiry to deserve a warning colour. */
    fun isUrgent(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val remaining = remainingMillis(nowMillis) ?: return false
        return remaining in 1..URGENT_WINDOW_MS
    }

    companion object {
        /** Under 24 hours left counts as urgent. */
        const val URGENT_WINDOW_MS = 24L * 60 * 60 * 1000L
    }
}
