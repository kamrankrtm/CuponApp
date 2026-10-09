package com.moez.QKSMS.feature.smart.analysis

/**
 * The vocabulary every part of the engine agrees on. Classification, coupon extraction and the
 * outbound privacy check used to keep their own lists of "verification" and "money" words, and
 * they drifted apart: a phrase that made the classifier file an OTP did not stop the coupon
 * extractor, or the AI upload. All three read these lists now.
 *
 * Phrases are written in [SmsText] form: ZWNJ is a space, letters are Persian, digits ASCII.
 * Matching is on lower-cased text; phrases that start or end with a Latin letter must stand
 * as whole words ("otp" is not in "hotpot").
 */
object SmsSignals {

    /** Phrases that name a one-time or verification code. */
    val VERIFICATION_LABELS: List<String> = listOf(
        "کد تایید", "کد تاییدیه", "کد تائید", "کد ورود", "رمز ورود", "کد فعالسازی", "کد فعال سازی",
        "رمز یکبار مصرف", "رمز یک بار مصرف", "رمز یکبارمصرف", "کد یکبار مصرف", "کد یک بار مصرف",
        "رمز پویا", "رمز دوم", "کد احراز", "کد امنیتی", "کد عبور", "کد اعتبارسنجی",
        "کد اعتبار سنجی", "رمز موقت", "کد موقت", "رمز عبور یکبار",
        "verification code", "verify code", "login code", "sign in code", "sign-in code",
        "security code", "confirmation code", "activation code", "authentication code",
        "one time password", "one-time password", "one time code", "one-time code", "passcode", "otp"
    )

    /** Phrases that put a code to signing in or confirming, without naming the code. */
    val VERIFICATION_ENTRY: List<String> = listOf("جهت ورود", "برای ورود")

    /** Warnings that only travel with credentials. */
    val VERIFICATION_WARNINGS: List<String> = listOf(
        "در اختیار دیگران قرار ندهید", "در اختیار کسی قرار ندهید", "به هیچ عنوان", "do not share",
        "don't share", "never share"
    )

    /** A stated balance. */
    val BALANCE_LABELS: List<String> = listOf(
        "مانده حساب", "مانده فعلی", "مانده قابل برداشت", "مانده", "موجودی حساب", "موجودی", "balance"
    )

    /** A fee charged on a transaction. */
    val FEE_LABELS: List<String> = listOf("کارمزد", "fee")

    /** Labels for the money that moved, strongest first. */
    val MOVEMENT_LABELS: List<String> = listOf(
        "برداشت", "واریز", "انتقال", "خرید", "کسر", "برگشت", "شارژ", "withdrawal", "withdraw",
        "deposit", "debit", "credit", "purchase", "transfer"
    )

    /** Labels that name an amount without saying where it went: "مبلغ", "بمبلغ". */
    val AMOUNT_LABELS: List<String> = listOf("مبلغ", "amount", "پرداخت", "تراکنش")

    /** Completed movements: what a receipt says happened. */
    val TRANSACTION_VERBS: List<String> = listOf(
        "واریز شد", "برداشت شد", "کسر شد", "کسر گردید", "انتقال یافت", "انجام شد", "انجام گردید",
        "پرداخت شد", "شارژ شد", "افزایش موجودی", "به حساب شما نشست", "از حساب شما پرید",
        "انتقال وجه", "خرید با کارت", "خرید از", "واریز", "برداشت"
    )

    /** Money arriving. */
    val CREDIT_WORDS: List<String> = listOf(
        "واریز", "دریافت", "شارژ شد", "افزایش موجودی", "به حساب شما نشست", "بستانکار", "سود",
        "برگشت", "deposit", "credited", "credit"
    )

    /** Money leaving. */
    val DEBIT_WORDS: List<String> = listOf(
        "برداشت", "خرید", "پرداخت", "کسر", "کارمزد", "از حساب شما پرید", "انتقال از", "بدهکار",
        "withdraw", "debited", "debit", "purchase"
    )

    /** Lending and debt: a financial message, never on its own a receipt. */
    val LENDING_WORDS: List<String> = listOf(
        "وام", "قسط", "اقساط", "تسهیلات", "سررسید", "معوق", "بدهی", "دیرکرد", "چک", "سفته"
    )

    /** Payment gateway receipts. */
    val GATEWAY_RECEIPTS: List<String> = listOf(
        "خرید از درگاه", "پرداخت از درگاه", "درگاه پرداخت", "پرداخت موفق", "تراکنش موفق"
    )

    /** Words that make a message an advertisement. Evidence, never a verdict on their own. */
    val AD_WORDS: List<String> = listOf(
        "تخفیف", "جشنواره", "ارسال رایگان", "فروشگاه", "قرعه", "جایزه", "هدیه", "حراج",
        "فروش ویژه", "پیشنهاد ویژه", "کد تخفیف", "off", "discount", "sale"
    )

    /** Whether [lowerText] contains [phrase]; Latin-edged phrases must stand as words. */
    fun containsPhrase(lowerText: String, phrase: String): Boolean = indexOfPhrase(lowerText, phrase, 0) >= 0

    /** The first index of [phrase] at or after [from], respecting Latin word edges, or -1. */
    fun indexOfPhrase(lowerText: String, phrase: String, from: Int): Int {
        var idx = lowerText.indexOf(phrase, from)
        val latinStart = SmsText.isLatinLetter(phrase.first())
        val latinEnd = SmsText.isLatinLetter(phrase.last())
        while (idx >= 0) {
            val end = idx + phrase.length
            val startOk = !latinStart || idx == 0 || !lowerText[idx - 1].isLetterOrDigit()
            val endOk = !latinEnd || end >= lowerText.length || !lowerText[end].isLetterOrDigit()
            if (startOk && endOk) return idx
            idx = lowerText.indexOf(phrase, idx + 1)
        }
        return -1
    }

    fun containsAny(lowerText: String, phrases: List<String>): Boolean =
        phrases.any { containsPhrase(lowerText, it) }

    /** Whether the message names a verification code or its use. */
    fun hasVerificationLabel(lowerText: String): Boolean = containsAny(lowerText, VERIFICATION_LABELS)

    fun hasVerificationEntry(lowerText: String): Boolean = containsAny(lowerText, VERIFICATION_ENTRY)
}
