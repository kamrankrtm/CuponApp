package com.moez.QKSMS.feature.smart.analysis

/**
 * Whether a verification code may be put on the clipboard without the user asking.
 *
 * A wrong automatic copy is worse than none: the user pastes a card number or an old code
 * into a login form. So every condition must hold:
 * - auto-copy is switched on;
 * - the code is exactly one of the message's eligible candidates (an AI answer that names
 *   anything else is ignored) and the message reads as a verification message;
 * - the local reading alone is decisive (score and margin), or the AI picked one of the
 *   locally eligible candidates;
 * - the code has not expired: past a validity the message states, or, when it states none,
 *   more than [UNKNOWN_VALIDITY_WINDOW_MS] after it arrived;
 * - no newer code has arrived since, and this one was not copied already.
 */
object OtpCopyPolicy {

    /** With no stated validity, a code is only copied this soon after it arrived. */
    const val UNKNOWN_VALIDITY_WINDOW_MS = 2 * 60 * 1000L

    enum class Decision(val copy: Boolean) {
        COPY(true),
        DISABLED(false),
        NOT_VERIFICATION(false),
        NOT_ELIGIBLE(false),
        AMBIGUOUS(false),
        EXPIRED(false),
        SUPERSEDED(false),
        ALREADY_COPIED(false)
    }

    /**
     * @param code the code about to be copied
     * @param aiSelected whether the AI, rather than the local ranking, chose [code]
     */
    fun decide(
        otp: OtpResult,
        code: String,
        receivedAt: Long,
        expiresAt: Long?,
        now: Long,
        autoCopyEnabled: Boolean,
        supersededByNewer: Boolean,
        alreadyCopied: Boolean,
        aiSelected: Boolean = false
    ): Decision {
        if (!autoCopyEnabled) return Decision.DISABLED
        if (!otp.verification) return Decision.NOT_VERIFICATION
        if (!otp.isEligible(code)) return Decision.NOT_ELIGIBLE
        if (!aiSelected && (otp.selected?.code != code || !otp.autoCopyEligible)) return Decision.AMBIGUOUS
        val stillValid = if (expiresAt != null) now < expiresAt else now - receivedAt <= UNKNOWN_VALIDITY_WINDOW_MS
        if (!stillValid) return Decision.EXPIRED
        if (supersededByNewer) return Decision.SUPERSEDED
        if (alreadyCopied) return Decision.ALREADY_COPIED
        return Decision.COPY
    }
}
