package com.moez.QKSMS.feature.smart.promo

import java.util.regex.Pattern

/**
 * Decides, per message, whether asking the AI is worth paying for.
 *
 * The rules engine reads most offers on its own and says how sure it is. A message goes out
 * only when it could hold a code (a latin token that is not a link or a brand name), may
 * leave the device ([AiPrivacyFilter]), has not been answered before ([PromoMemory]), and
 * the local reading is unsure of the code, the brand or a figure the text plainly states.
 * Everything else — the confident majority, messages with nothing code-shaped in them,
 * anything sensitive — costs nothing.
 *
 * Pure Kotlin, so the policy is unit-tested rather than discovered on a bill.
 */
object AiEscalation {

    enum class Verdict(val send: Boolean) {
        /** Looks like an offer with a code-shaped token, but no code was read. */
        SEND_NO_CODE(true),

        /** A code was read, but without a label saying so. */
        SEND_UNSURE_CODE(true),

        /** The code is sure; which shop it is for is not ("اسنپ" alone, several shops named). */
        SEND_UNSURE_BRAND(true),

        /** The text quotes a figure the parser could not turn into a discount. */
        SEND_MISSING_VALUE(true),

        /** Read confidently, but the user asked for every code to be checked by the AI. */
        SEND_CHECK_ALL(true),

        SKIP_ALREADY_READ(false),
        SKIP_SENSITIVE(false),
        SKIP_NOT_OFFER(false),
        SKIP_NO_CODE_SHAPE(false),
        SKIP_CONFIDENT(false)
    }

    /** At or above this the local code reading is trusted. */
    const val CODE_OK = 75

    /** At or above this the local brand reading is trusted. */
    const val BRAND_OK = 75

    private val FIGURE = Pattern.compile("\\d+\\s*(?:درصد|٪|%|هزار|میلیون|تومان|تومن|ریال)")

    /**
     * @param checkAll send every offer that could hold a code, not only the ones the local
     *   reading is unsure of; privacy, the offer test and the one-answer-per-message rule still apply
     */
    fun judge(sender: String, body: String, date: Long, checkAll: Boolean = false): Verdict {
        if (PromoMemory.findingsFor(PromoMemory.messageKey(sender, date, body)) != null) return Verdict.SKIP_ALREADY_READ
        when (AiPrivacyFilter.judge(sender, body)) {
            AiPrivacyFilter.Verdict.ALLOWED -> Unit
            AiPrivacyFilter.Verdict.BLOCKED_NOT_MARKETING -> return Verdict.SKIP_NOT_OFFER
            else -> return Verdict.SKIP_SENSITIVE
        }

        val normalized = PromoValueParser.normalize(body)
        if (PromoCodeExtractor.isVerificationMessage(normalized) ||
            PromoCodeExtractor.isSpentNotice(normalized) ||
            !PromoCodeExtractor.looksPromotional(normalized)
        ) {
            return Verdict.SKIP_NOT_OFFER
        }
        // The AI may only report a code that is in the text; with nothing code-shaped, it can't
        if (!PromoCodeExtractor.hasCodeShapedToken(normalized)) return Verdict.SKIP_NO_CODE_SHAPE
        if (checkAll) return Verdict.SEND_CHECK_ALL

        val analyses = PromoParser.analyze(sender, body, date)
        if (analyses.isEmpty()) return Verdict.SEND_NO_CODE
        if (analyses.any { it.codeConfidence < CODE_OK }) return Verdict.SEND_UNSURE_CODE
        if (analyses.any { it.brandConfidence < BRAND_OK }) return Verdict.SEND_UNSURE_BRAND
        if (analyses.any { !it.valueFound } &&
            FIGURE.matcher(PromoValueParser.digitizeNumberWords(normalized)).find()
        ) {
            return Verdict.SEND_MISSING_VALUE
        }
        return Verdict.SKIP_CONFIDENT
    }

    fun shouldSend(sender: String, body: String, date: Long, checkAll: Boolean = false): Boolean =
        judge(sender, body, date, checkAll).send
}
