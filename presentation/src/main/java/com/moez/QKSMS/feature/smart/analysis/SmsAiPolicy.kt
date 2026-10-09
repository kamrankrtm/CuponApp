package com.moez.QKSMS.feature.smart.analysis

/**
 * Which messages the AI fallback may send. Pure, so the consent rules are unit-tested: a
 * regression here would not show as a bug, it would silently upload a bank receipt.
 *
 * - Only with the separate banking/OTP consent. The advertising consent never covers this path.
 * - Never a personal number's message.
 * - Only readings the local engine left open ([SmsAnalysis.ambiguous]) that carry something
 *   worth settling: a number that could be a code, verification wording or receipt evidence.
 * - Never a message already answered.
 * - Where the user moved a sender plays no part: a correction never widens what is uploaded.
 */
object SmsAiPolicy {

    enum class Decision(val send: Boolean) {
        SEND(true),
        NO_API_KEY(false),
        NO_SENSITIVE_CONSENT(false),
        PERSONAL_SENDER(false),
        NOT_AMBIGUOUS(false),
        NOTHING_TO_SETTLE(false),
        ALREADY_ANSWERED(false),
        OVER_BUDGET(false)
    }

    private val RECEIPT_EVIDENCE = setOf(
        Evidence.BALANCE_STATED, Evidence.TRANSACTION_LABELLED, Evidence.AMOUNT_LABELLED, Evidence.SIGNED_AMOUNT,
        Evidence.FEE_STATED, Evidence.WALLET_MOVEMENT, Evidence.GATEWAY_RECEIPT
    )

    fun decide(
        analysis: SmsAnalysis,
        apiKeySet: Boolean,
        sensitiveConsent: Boolean,
        remainingBudget: Int
    ): Decision {
        if (!apiKeySet) return Decision.NO_API_KEY
        if (!sensitiveConsent) return Decision.NO_SENSITIVE_CONSENT
        if (Evidence.PERSONAL_SENDER in analysis.evidence) return Decision.PERSONAL_SENDER
        if (Evidence.AI_CONFIRMED in analysis.evidence || Evidence.AI_RECLASSIFIED in analysis.evidence) {
            return Decision.ALREADY_ANSWERED
        }
        if (!analysis.ambiguous) return Decision.NOT_AMBIGUOUS
        val worth = analysis.otp.verification || analysis.otp.candidates.isNotEmpty() ||
            analysis.evidence.any { it in RECEIPT_EVIDENCE }
        if (!worth) return Decision.NOTHING_TO_SETTLE
        if (remainingBudget <= 0) return Decision.OVER_BUDGET
        return Decision.SEND
    }

    /** HTTP outcomes worth trying again: no connection, timeouts, rate limits, server errors. */
    fun isTransient(status: Int): Boolean = status == 0 || status == 408 || status == 429 || status in 500..599

    /** At most this many retries after the first attempt, each within the daily budget. */
    const val MAX_RETRIES = 2

    /** Cache key: normalised sender, a strong fingerprint and both versions. */
    fun cacheKey(analysis: SmsAnalysis): String =
        "${SmsText.lower(SmsText.text(analysis.sender)).trim()}|${analysis.fingerprint}|${SmsAiProtocol.VERSION}|${analysis.engineVersion}"
}
