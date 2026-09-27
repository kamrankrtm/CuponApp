package com.moez.QKSMS.feature.smart.promo

import java.util.regex.Pattern

/**
 * Decides which messages may be uploaded to a third-party AI service.
 *
 * The previous filter admitted anything from a non-personal number, which meant every bank
 * balance, transaction alert and one-time password in the inbox was sent to an external server.
 * This inverts the default: a message is withheld unless it looks like marketing and carries
 * nothing sensitive.
 *
 * Pure Kotlin so the rules can be unit-tested — a silent regression here leaks real data.
 */
object AiPrivacyFilter {

    /** Anything that marks a message as an account, payment or identity event. */
    private val SENSITIVE_KEYWORDS = listOf(
        // one-time passwords and login
        "کد تایید", "کد فعالسازی", "کد فعال سازی", "رمز یکبار مصرف", "رمز یکبارمصرف",
        "رمز پویا", "کد ورود", "کد احراز", "کد عبور", "کد امنیتی", "رمز دوم",
        "verification code", "otp", "security code", "login code", "one time password",
        // banking and money movement
        "موجودی", "مانده", "واریز", "برداشت", "انتقال وجه", "تراکنش", "صورتحساب",
        "شاپرک", "ساتنا", "کسر شد", "بدهکار", "بستانکار", "کارمزد",
        "تسهیلات", "چک ", "سفته", "مسدود", "رمز اینترنتی", "سررسید", "معوق", "بدهی", "دیرکرد",
        // identity and legal
        "کد ملی", "شماره شبا", "شماره حساب", "شماره کارت", "ابلاغیه",
        "پرونده", "دادگاه", "شکایت", "احضار", "قبض جریمه", "خلافی",
        // health
        "آزمایش", "نسخه پزشک", "نتیجه تست", "بیمارستان"
    )

    /**
     * Sensitive words short enough to sit inside everyday ones: "پایا" (the interbank transfer)
     * in "تا پایان هفته", "ثنا" in "استثنایی", "وام" in "بادوام". Matched as words, so an offer
     * that runs "until the end of the week" is no longer withheld as a bank message. A word
     * followed by a Persian ending ("وامی", "وام‌تان") still counts, erring towards withholding.
     */
    private val WHOLE_WORD_KEYWORDS = listOf("پایا", "ثنا", "وام")

    private val WORD_ENDINGS = listOf("ی", "ها", "های", "تان", "ت", "م", "ش", "مان", "شان")

    private fun containsWord(text: String, word: String): Boolean {
        var idx = text.indexOf(word)
        while (idx >= 0) {
            val end = idx + word.length
            val startsWord = idx == 0 || !text[idx - 1].isLetter()
            val endsWord = end >= text.length || !text[end].isLetter() || WORD_ENDINGS.any { ending ->
                text.startsWith(ending, end) && (end + ending.length >= text.length || !text[end + ending.length].isLetter())
            }
            if (startsWord && endsWord) return true
            idx = text.indexOf(word, idx + 1)
        }
        return false
    }

    /**
     * "قسط" is a loan instalment in a bank reminder, but "کد تخفیف قسطی" in an advert is a code
     * for paying in instalments. It is withheld unless the message labels a coupon; a message
     * that does is still subject to every other rule here, including the debt words above.
     */
    private const val INSTALMENT = "قسط"

    private val COUPON_LABELS = listOf("کد تخفیف", "کد هدیه", "کوپن", "promo code", "discount code", "coupon")

    /** Structures that betray an account number, card number, IBAN or balance. */
    private val SENSITIVE_PATTERNS = listOf(
        // 16-digit card number, grouped or not
        Pattern.compile("\\b\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}\\b"),
        // IBAN
        Pattern.compile("IR\\s?\\d{2}[\\s-]?\\d", Pattern.CASE_INSENSITIVE),
        // masked card, e.g. 6037-****-****-1234
        Pattern.compile("\\d{4}[\\s-]?\\*{2,}"),
        // national id
        Pattern.compile("\\b\\d{10}\\b")
    )

    /** Signals that a message is marketing rather than a transaction. */
    private val MARKETING_KEYWORDS = listOf(
        "تخفیف", "کد تخفیف", "جشنواره", "ارسال رایگان", "هدیه", "پیشنهاد ویژه",
        "فروش ویژه", "کوپن", "حراج", "شگفت انگیز", "off", "discount", "promo",
        "coupon", "sale", "کمپین", "اشتراک ویژه", "درصد تخفیف"
    )

    /** Iranian personal mobile numbers; their messages never go anywhere. */
    private val PERSONAL_NUMBER_REGEX = Pattern.compile("^(?:\\+98|98|0)?9\\d{9}$")

    /** Why a message was withheld, useful for explaining the filter to the user. */
    enum class Verdict {
        ALLOWED,
        BLOCKED_PERSONAL,
        BLOCKED_SENSITIVE,
        BLOCKED_NOT_MARKETING
    }

    /**
     * Judges one message.
     *
     * @param sender raw sender address
     * @param body raw message body
     */
    fun judge(sender: String, body: String): Verdict {
        val normalizedSender = PromoValueParser.normalize(sender).replace("\\s|-".toRegex(), "")
        val normalizedBody = PromoValueParser.normalize(body).toLowerCase()

        // 1. Never upload a conversation with a human.
        if (PERSONAL_NUMBER_REGEX.matcher(normalizedSender).matches()) {
            return Verdict.BLOCKED_PERSONAL
        }

        // 2. Never upload anything carrying credentials, money or identity.
        if (SENSITIVE_KEYWORDS.any { normalizedBody.contains(it) }) {
            return Verdict.BLOCKED_SENSITIVE
        }
        if (WHOLE_WORD_KEYWORDS.any { containsWord(normalizedBody, it) }) {
            return Verdict.BLOCKED_SENSITIVE
        }
        if (normalizedBody.contains(INSTALMENT) && COUPON_LABELS.none { normalizedBody.contains(it) }) {
            return Verdict.BLOCKED_SENSITIVE
        }
        if (SENSITIVE_PATTERNS.any { it.matcher(normalizedBody).find() }) {
            return Verdict.BLOCKED_SENSITIVE
        }

        // 3. Only upload what actually looks like an offer.
        if (MARKETING_KEYWORDS.none { normalizedBody.contains(it) }) {
            return Verdict.BLOCKED_NOT_MARKETING
        }

        return Verdict.ALLOWED
    }

    fun isAllowed(sender: String, body: String): Boolean = judge(sender, body) == Verdict.ALLOWED
}
