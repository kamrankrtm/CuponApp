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
        "شاپرک", "پایا", "ساتنا", "کسر شد", "بدهکار", "بستانکار", "کارمزد",
        "تسهیلات", "قسط", "وام", "چک ", "سفته", "مسدود", "رمز اینترنتی",
        // identity and legal
        "کد ملی", "شماره شبا", "شماره حساب", "شماره کارت", "ابلاغیه", "ثنا",
        "پرونده", "دادگاه", "شکایت", "احضار", "قبض جریمه", "خلافی",
        // health
        "آزمایش", "نسخه پزشک", "نتیجه تست", "بیمارستان"
    )

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
