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
    /** Conversation this code arrived in, so a notification can open the actual message. */
    val threadId: Long = 0L,

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
    /** Who sent or sponsors the code when that is not [brand]: a wallet, bank or operator. */
    val issuer: String? = null,
    /** The payment method the offer requires, when it names one. */
    val payWith: String? = null,
    /** Shops a sponsor lists for this one code; [brand] is then the sponsor itself. */
    val usableAt: List<String> = emptyList(),
    /** Most a percentage code takes off, in Tomans; 0 when the message states no cap. */
    val maxDiscountValue: Long = 0L,
    val maxDiscount: String? = null,
    /** Money paid back after the purchase rather than taken off the price. */
    val isCashback: Boolean = false,
    /** How the card was read: [SOURCE_LOCAL], [SOURCE_LEARNED] or [SOURCE_AI]. */
    val source: String = SOURCE_LOCAL,
    /** The message this code came from, so a later, better reading can replace this one. */
    val sourceKey: String = "",

    var isUsed: Boolean = false,
    var isInvalid: Boolean = false,
    var isPinned: Boolean = false
) {

    /** Stable identity of an offer: the same code from the same brand is the same offer. */
    val dedupeKey: String
        get() = "${brand.trim().toLowerCase()}|${code.trim().toUpperCase()}"

    /**
     * The same code read out of the same message. Survives a change of brand, so the user's
     * "used" and "pinned" marks stay put when a better reading renames the shop.
     */
    val messageCodeKey: String
        get() = if (sourceKey.isEmpty()) "" else "$sourceKey|${code.trim().toUpperCase()}"

    /** The line under the brand: the category, plus [sponsorLine] when there is one. */
    fun subtitle(): String = listOf(category, sponsorLine()).filter { it.isNotBlank() }.joinToString(" · ")

    /**
     * Who sponsors the code and how to pay, when that is not the shop itself —
     * "از طرف دیجی‌پی · پرداخت با دیجی‌پی" — or the shops a sponsor's code works at.
     * A name is shown only when it has letters in it, so a line never ends in a bare "پرداخت با".
     */
    fun sponsorLine(): String {
        fun readable(name: String?): String? = name?.trim()?.takeIf { it.any { c -> c.isLetter() } && it != brand }
        val parts = ArrayList<String>()
        val shops = usableAt.mapNotNull { readable(it) }
        if (shops.isNotEmpty()) {
            val names = shops.take(3).joinToString("، ")
            parts.add(if (shops.size > 3) "قابل استفاده در $names و …" else "قابل استفاده در $names")
        }
        readable(issuer)?.let { parts.add("از طرف $it") }
        readable(payWith)?.let { parts.add("پرداخت با $it") }
        return parts.joinToString(" · ")
    }

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

        /** Read on the device by the rules engine. */
        const val SOURCE_LOCAL = "local"

        /** Read on the device, with the brand learned from an earlier AI answer. */
        const val SOURCE_LEARNED = "learned"

        /** Read by the AI tier and checked against the message. */
        const val SOURCE_AI = "ai"
    }
}
