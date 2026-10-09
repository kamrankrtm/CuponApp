package com.moez.QKSMS.feature.smart

import com.moez.QKSMS.feature.smart.analysis.BankDirectory
import com.moez.QKSMS.feature.smart.analysis.BankingParser
import com.moez.QKSMS.feature.smart.analysis.OtpExtractor
import com.moez.QKSMS.feature.smart.analysis.SmsAnalysis
import com.moez.QKSMS.feature.smart.analysis.SmsAnalyzer
import com.moez.QKSMS.feature.smart.analysis.SmsKind
import com.moez.QKSMS.feature.smart.analysis.SmsText
import com.moez.QKSMS.feature.smart.analysis.toCategory
import com.moez.QKSMS.feature.smart.model.PromoItem
import com.moez.QKSMS.feature.smart.model.SmsCategory
import com.moez.QKSMS.feature.smart.promo.PromoParser
import java.util.regex.Pattern

/**
 * The tab-level view of [SmsAnalyzer]: what a message is ([classify]), where its conversation
 * is listed ([classifyConversation]) and what the user's own choices change ([classifyForUser]).
 *
 * The reading itself lives in `feature/smart/analysis`; this keeps the API the rest of the app
 * already calls, and the placement rules that depend on contacts and sender overrides.
 */
object SmartSmsClassifier {

    // An Iranian mobile once its +98 / 0098 / 98 / 0 prefix is gone: 912..., ten digits
    private val PERSONAL_NUMBER_REGEX = Pattern.compile("^9\\d{9}$")

    /** The full reading of one message. Callers that can should go through [SmartAnalysisCache]. */
    fun analyze(
        sender: String,
        body: String,
        date: Long = System.currentTimeMillis(),
        threadId: Long = 0L,
        sourceKey: String? = null
    ): SmsAnalysis = SmsAnalyzer.analyze(sender, body, date, threadId, sourceKey)

    /**
     * What the message is for the tabs; [SmsCategory.Unknown] when nothing marks it. A sender
     * the user marked "not spam" reads as Personal unless its message is a code, an offer or a
     * receipt, as before. The [SmsAnalysis] itself never sees that choice.
     */
    fun classify(
        sender: String,
        body: String,
        date: Long = System.currentTimeMillis(),
        threadId: Long = 0L
    ): SmsCategory = withTrust(analyze(sender, body, date, threadId).toCategory(), sender)

    private fun withTrust(category: SmsCategory, sender: String): SmsCategory =
        if ((category is SmsCategory.Spam || category is SmsCategory.Unknown) && SenderOverrides.isTrusted(sender)) {
            SmsCategory.Personal
        } else category

    /**
     * The category of a conversation for the tabs. People saved as contacts are Personal, but
     * a saved service number (a bank saved as "Blu Bank") still goes to Banking when its
     * messages are transactions. Where the user moved the sender is applied by the caller.
     */
    fun classifyConversation(
        sender: String,
        body: String,
        hasSavedContact: Boolean,
        date: Long = System.currentTimeMillis(),
        threadId: Long = 0L
    ): SmsCategory = classifyConversation(analyze(sender, body, date, threadId), sender, hasSavedContact)

    /** [classifyConversation] for a message already read. */
    fun classifyConversation(analysis: SmsAnalysis, sender: String, hasSavedContact: Boolean): SmsCategory {
        if (SenderOverrides.isTrusted(sender)) return SmsCategory.Personal
        val category = placementOf(analysis, sender)
        if (!hasSavedContact) return category
        if (isPersonalNumber(sender)) return SmsCategory.Personal
        return if (category is SmsCategory.Banking) category else SmsCategory.Personal
    }

    /**
     * Where a message puts its conversation. That is what the message is, except that a bank's
     * verification code keeps the bank's conversation under Banking: the same sender writes
     * the receipts, and a code arriving must not pull the thread out of the tab.
     */
    private fun placementOf(analysis: SmsAnalysis, sender: String): SmsCategory {
        if (analysis.kind == SmsKind.OTP) {
            val bank = BankDirectory.senderBank(SmsText.lower(SmsText.text(sender)).trim())
            if (bank != null) return SmsCategory.Banking(bank, null, null, analysis.banking)
        }
        return analysis.toCategory()
    }

    /**
     * [classify], with the user's own choice for the sender (Move to …, Not spam) on top.
     * Verification codes are left as they are, so a code from a moved sender is still copied
     * and announced.
     */
    fun classifyForUser(
        sender: String,
        body: String,
        date: Long = System.currentTimeMillis(),
        threadId: Long = 0L
    ): SmsCategory = classifyForUser(analyze(sender, body, date, threadId))

    /** [classifyForUser] for a message already read. */
    fun classifyForUser(analysis: SmsAnalysis): SmsCategory {
        val category = withTrust(analysis.toCategory(), analysis.sender)
        if (category is SmsCategory.Otp) return category
        return when (SenderOverrides.tabFor(analysis.sender)) {
            SenderOverrides.Tab.SPAM -> SmsCategory.Spam
            SenderOverrides.Tab.BANKING -> category as? SmsCategory.Banking
                ?: BankingParser.parse(analysis.sender, SmsText.text(analysis.body)).toCategory(analysis.sender)
            SenderOverrides.Tab.PERSONAL, null -> category
        }
    }

    fun normalizeDigits(input: String): String = SmsText.text(input)

    fun normalizeText(input: String): String = SmsText.text(input)

    fun toPersianDigits(input: String): String {
        val chars = input.toCharArray()
        for (i in chars.indices) {
            val c = chars[i]
            if (c in '0'..'9') {
                chars[i] = ('۰' + (c.toInt() - '0'.toInt()))
            }
        }
        return String(chars)
    }

    fun isPersonalNumber(sender: String): Boolean {
        val normalized = normalizeDigits(sender).replace("\\s+".toRegex(), "").replace("-", "")
        val plain = when {
            normalized.startsWith("+98") -> normalized.substring(3)
            normalized.startsWith("0098") -> normalized.substring(4)
            normalized.startsWith("98") -> normalized.substring(2)
            normalized.startsWith("0") -> normalized.substring(1)
            else -> normalized
        }
        // 0998 (Shatel Mobile) and 0999 (MVNOs) are heavily used for bulk commercial ads/spam
        if (plain.startsWith("998") || plain.startsWith("999")) {
            return false
        }
        // Matched after the prefix is gone: no mobile starts 098, so "9830005513" is the short
        // code 30005513 behind the country code, not the mobile 0983 0005513
        return PERSONAL_NUMBER_REGEX.matcher(plain).matches()
    }

    /** Whether the message is about a verification code; see [OtpExtractor.isVerification]. */
    fun isOtpMessage(body: String): Boolean = OtpExtractor.isVerification(SmsText.text(body))

    /** The verification code in [body], or null when no number stands out as one. */
    fun extractOtpCode(body: String): String? = OtpExtractor.extract(body).selected?.code

    val SERVICE_BRANDS = listOf(
        // Fintech & Payment
        "آپ" to "آپ (آسان پرداخت)",
        "آسان پرداخت" to "آپ (آسان پرداخت)",
        "تاپ" to "تاپ",
        "همراه کارت" to "همراه کارت",
        "سکه" to "سکه (بانک سامان)",
        "ایوا" to "ایوا (سداد)",
        "سداد" to "سداد",
        "۷۲۴" to "۷۲۴ (سامان کیش)",
        "724" to "۷۲۴ (سامان کیش)",
        "دیجی‌پی" to "دیجی‌پی",
        "دیجی پی" to "دیجی‌پی",
        "اسنپ‌پی" to "اسنپ‌پی",
        "اسنپ پی" to "اسنپ‌پی",
        "زرین‌پال" to "زرین‌پال",
        "زرین پال" to "زرین‌پال",
        "پی‌پینگ" to "پی‌پینگ",
        "جیبیت" to "جیبیت",
        "ازکی‌وام" to "ازکی‌وام",
        "ازکی وام" to "ازکی‌وام",
        "ازکی" to "ازکی",
        "تارا" to "تارا",
        "قسطا" to "قسطا",
        "لندو" to "لندو",
        "بیمه دات کام" to "بیمه دات کام",

        // E-commerce, Food & Taxi
        "اسنپ‌فود" to "اسنپ‌فود",
        "اسنپ فود" to "اسنپ‌فود",
        "تپسی‌فود" to "تپسی‌فود",
        "تپسی فود" to "تپسی‌فود",
        "اسنپ‌مارکت" to "اسنپ‌مارکت",
        "اسنپ مارکت" to "اسنپ‌مارکت",
        "اکالا" to "اکالا (افق کوروش)",
        "افق کوروش" to "اکالا (افق کوروش)",
        "دیجی‌کالا" to "دیجی‌کالا",
        "دیجی کالا" to "دیجی‌کالا",
        "دیجیکالا" to "دیجی‌کالا",
        "دیجی پلاس" to "دیجی‌کالا",
        "باسلام" to "باسلام",
        "تپسی" to "تپسی",
        "اسنپ" to "اسنپ",
        "ماکسیم" to "ماکسیم",
        "علی‌بابا" to "علی‌بابا",
        "علی بابا" to "علی‌بابا",
        "مستربلیط" to "مستر بلیط",
        "فلای‌تودی" to "فلای‌تودی",
        "فلای تودی" to "فلای‌تودی",
        "دیوار" to "دیوار",
        "شیپور" to "شیپور",
        "ترب" to "ترب",
        "ایمالز" to "ایمالز",
        "کافه‌بازار" to "کافه‌بازار",
        "کافه بازار" to "کافه‌بازار",
        "مایکت" to "مایکت",

        // Streaming & Cinema
        "فیلیمو" to "فیلیمو",
        "نماوا" to "نماوا",
        "فیلم‌نت" to "فیلم‌نت",
        "فیلم نت" to "فیلم‌نت",
        "سینماتیکت" to "سینماتیکت",
        "سینما تیکت" to "سینماتیکت",
        "ایران کنسرت" to "ایران کنسرت",
        "تیوال" to "تیوال",

        // Messengers & Social
        "روبیکا" to "روبیکا",
        "ایتا" to "ایتا",
        "بله" to "بله",
        "سروش" to "سروش پلاس",
        "سروش+" to "سروش پلاس",
        "splus" to "سروش پلاس",
        "آی‌گپ" to "آی‌گپ",
        "شاد" to "شاد",
        "نشان" to "نشان",
        "بلد" to "بلد",
        "تلگرام" to "تلگرام",
        "telegram" to "تلگرام",
        "واتساپ" to "واتس‌اپ",
        "whatsapp" to "واتس‌اپ",
        "اینستاگرام" to "اینستاگرام",
        "instagram" to "اینستاگرام",
        "گوگل" to "گوگل",
        "google" to "گوگل",

        // Telecom & Government
        "همراه اول" to "همراه اول",
        "mci" to "همراه اول",
        "ایرانسل" to "ایرانسل",
        "irancell" to "ایرانسل",
        "mtn" to "ایرانسل",
        "رایتل" to "رایتل",
        "rightel" to "رایتل",
        "مخابرات" to "مخابرات",
        "عدل ایران" to "عدل ایران",
        "ثنا" to "سامانه ثنا",
        "تامین اجتماعی" to "تامین اجتماعی",
        "دولت من" to "دولت من"
    )

    /**
     * The service a verification code is for: the bank that sent it, else a brand named as a
     * whole word in the sender or the text, else the sender itself.
     */
    fun serviceName(sender: String, normalizedBody: String, issuer: String? = null): String {
        if (issuer != null) return issuer
        val lowerBody = SmsText.lower(normalizedBody)
        val lowerSender = SmsText.lower(SmsText.text(sender))
        for ((keyword, name) in SERVICE_BRANDS) {
            val word = SmsText.lower(SmsText.text(keyword))
            if (BankDirectory.indexOfWord(lowerBody, word) >= 0 || BankDirectory.indexOfWord(lowerSender, word) >= 0) {
                return name
            }
        }
        return if (sender.isNotBlank()) sender else "سرویس تایید ورود"
    }

    /**
     * The best discount card in a promotional SMS, or null when it carries no usable code.
     *
     * Brand identification, value parsing and code extraction live in `feature/smart/promo`;
     * [PromoParser] puts them together, including anything the AI tier already answered for
     * this message.
     */
    fun extractPromo(
        sender: String,
        body: String,
        date: Long = System.currentTimeMillis(),
        threadId: Long = 0L
    ): PromoItem? = PromoParser.parse(sender, body, date, threadId).maxBy { it.confidence }

    /** Every discount card in the message: some carry one code per shop or per basket size. */
    fun extractPromos(
        sender: String,
        body: String,
        date: Long = System.currentTimeMillis(),
        threadId: Long = 0L
    ): List<PromoItem> = PromoParser.parse(sender, body, date, threadId)
}
