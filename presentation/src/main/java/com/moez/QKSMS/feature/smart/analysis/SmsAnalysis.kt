package com.moez.QKSMS.feature.smart.analysis

import com.moez.QKSMS.feature.smart.model.OtpItem
import com.moez.QKSMS.feature.smart.model.PromoItem
import com.moez.QKSMS.feature.smart.model.SmsCategory
import com.moez.QKSMS.feature.smart.promo.PromoParser

/**
 * What a message is, by itself. Where its conversation is listed is decided separately, from
 * this and from the sender ([SmartPlacement]): a bank sends both receipts and codes.
 *
 * [UNKNOWN] has no tab. Such a message stays in All and notifies normally; not knowing what a
 * message is never makes it spam.
 */
enum class SmsKind { PERSONAL, PROMO, OTP, BANKING, SPAM, UNKNOWN }

/**
 * The one reading of one message that notifications, the smart lists and the tabs all share.
 *
 * Immutable: a better reading (the AI's) is a new instance via [copy], never an edit.
 */
data class SmsAnalysis(
    /** Stable identity of the source message: "sms:<provider id>", or "fp:<fingerprint>". */
    val sourceKey: String,
    /** SHA-256 of the normalised sender, timestamp and body; a changed message is a new reading. */
    val fingerprint: String,
    /** [SmsAnalyzer.ENGINE_VERSION] that produced it; a newer engine re-reads the message. */
    val engineVersion: String,
    val sender: String,
    /** The message as received, trimmed. Memory only, like the reading itself. */
    val body: String,
    val receivedAt: Long,
    val kind: SmsKind,
    /** How strongly the evidence supports [kind], 0..100; a ranking weight, not a probability. */
    val confidence: Int,
    val evidence: Set<Evidence>,
    val otp: OtpResult,
    /** The code as a list item, when [kind] is OTP. Memory only: OTPs are never written to disk. */
    val otpItem: OtpItem?,
    val banking: BankingDetails?,
    val promos: List<PromoItem>,
    /** The coupon parser's view of each card, for [com.moez.QKSMS.feature.smart.promo.PromoReadPolicy]. */
    val promoAnalyses: List<PromoParser.Analysis> = emptyList()
) {
    /** The tab-level category the rest of the app already speaks. */
    fun toCategory(): SmsCategory = when (kind) {
        SmsKind.OTP -> otpItem?.let { SmsCategory.Otp(it) } ?: SmsCategory.Unknown
        SmsKind.PROMO -> promos.maxBy { it.confidence }?.let { SmsCategory.Promo(it) } ?: SmsCategory.Unknown
        SmsKind.BANKING -> banking.toCategory(sender)
        SmsKind.PERSONAL -> SmsCategory.Personal
        SmsKind.SPAM -> SmsCategory.Spam
        SmsKind.UNKNOWN -> SmsCategory.Unknown
    }

    /** Whether the reading leaves room for the AI fallback to settle it. */
    val ambiguous: Boolean
        get() = when (kind) {
            SmsKind.UNKNOWN -> true
            SmsKind.OTP -> !otp.autoCopyEligible
            SmsKind.BANKING -> (banking?.strength ?: 0) < BankingParser.STRONG_RECEIPT || banking?.headline == null
            else -> false
        }
}

/** The legacy category for a banking reading; the sender stands in for an unknown bank. */
fun BankingDetails?.toCategory(sender: String): SmsCategory.Banking {
    val name = this?.issuer ?: sender.takeIf { it.isNotBlank() } ?: "پیامک بانکی"
    return SmsCategory.Banking(name, this?.headline?.label(), this?.isDeposit, this)
}
