package com.moez.QKSMS.feature.smart.analysis

/**
 * Why the engine filed a message where it did. Stable codes, so tests, the AI fallback and
 * any later explanation in the UI can rely on them; never shown raw to the user.
 */
enum class Evidence {
    // verification codes
    VERIFICATION_LABEL,
    VERIFICATION_WORDING,
    OTP_CANDIDATE,
    OTP_AMBIGUOUS,
    OTP_AUTO_COPY_ELIGIBLE,
    OTP_VALIDITY_STATED,

    // bank receipts
    BALANCE_STATED,
    TRANSACTION_LABELLED,
    AMOUNT_LABELLED,
    SIGNED_AMOUNT,
    FEE_STATED,
    ACCOUNT_REFERENCE,
    TRANSACTION_VERB,
    BANK_SENDER,
    BANK_SIGNATURE,
    WALLET_MOVEMENT,
    GATEWAY_RECEIPT,
    DATE_STAMP,
    LENDING_WORDS,

    // advertising
    PROMOTIONAL_WORDS,
    OPT_OUT_LINE,
    COUPON_CODE,

    // sender
    PERSONAL_SENDER,
    COMMERCIAL_SENDER,

    // outcome
    NO_CLEAR_SIGNAL,
    AI_CONFIRMED,
    AI_RECLASSIFIED
}
