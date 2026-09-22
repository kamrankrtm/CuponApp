package com.moez.QKSMS.feature.smart

import com.moez.QKSMS.feature.smart.model.OtpItem
import com.moez.QKSMS.feature.smart.model.PromoItem
import com.moez.QKSMS.feature.smart.model.SmsCategory
import com.moez.QKSMS.feature.smart.promo.Brand
import com.moez.QKSMS.feature.smart.promo.BrandRegistry
import com.moez.QKSMS.feature.smart.promo.DiscountType
import com.moez.QKSMS.feature.smart.promo.PromoCodeExtractor
import com.moez.QKSMS.feature.smart.promo.PromoValueParser
import java.util.regex.Pattern

object SmartSmsClassifier {

    // Regex for Iranian personal phone numbers: 0912..., +98912..., 98912...
    private val PERSONAL_NUMBER_REGEX = Pattern.compile("^(?:\\+98|98|0)?9\\d{9}$")

    // OTP detection patterns
    private val OTP_KEYWORDS = listOf(
        "کد تایید", "کد فعال‌سازی", "کد فعالسازی", "رمز یکبار مصرف", "رمز یکبارمصرف",
        "رمز پویا", "کد ورود", "کد احراز", "کد شما", "کد عبور", "کد امنیتی",
        "verification code", "otp", "security code", "login code"
    )

    // Banking transaction keywords
    private val BANKING_KEYWORDS = listOf(
        "واریز", "برداشت", "مانده حساب", "موجودی:", "موجودی", "مانده:", "مانده",
        "انتقال وجه", "خرید از:", "خرید با کارت", "خرید شارژ", "شاپرک", "پایا", "ساتنا",
        "حساب:", "کارت:", "رمز اینترنتی", "صورتحساب", "تراکنش", "کسر شد", "افزایش موجودی",
        "بدهکار", "بستانکار", "کارمزد", "تسهیلات", "قسط", "وام"
    )

    // Discount keywords
    private val DISCOUNT_KEYWORDS = listOf(
        "کد تخفیف", "تخفیف", "جشنواره", "ارسال رایگان", "درصد تخفیف", "هدیه خرید",
        "off", "discount", "promo"
    )

    /**
     * Main classification method
     */
    fun classify(sender: String, body: String, date: Long = System.currentTimeMillis()): SmsCategory {
        val cleanSender = sender.trim()
        val cleanBody = body.trim()

        // 1. Check for OTP / Verification Code first
        if (isOtpMessage(cleanBody)) {
            val code = extractOtpCode(cleanBody)
            if (code != null) {
                val service = extractServiceName(cleanSender, cleanBody)
                val otp = OtpItem(
                    id = "otp-$date-${code.hashCode()}",
                    code = code,
                    serviceName = service,
                    sender = cleanSender,
                    body = cleanBody,
                    receivedAt = date
                )
                return SmsCategory.Otp(otp)
            }
        }

        // 2. Check for Discount Code / Promotion
        if (hasDiscountCode(cleanBody)) {
            val promo = extractPromo(cleanSender, cleanBody, date)
            if (promo != null) {
                return SmsCategory.Promo(promo)
            }
        }

        // 3. Check for Banking transaction
        if (isBankingMessage(cleanSender, cleanBody)) {
            val bankName = extractBankName(cleanSender, cleanBody)
            val isDeposit = cleanBody.contains("واریز")
            val amount = extractBankingAmount(cleanBody)
            return SmsCategory.Banking(bankName, amount, isDeposit)
        }

        // 4. Check for Personal Contact / 09...
        if (isPersonalNumber(cleanSender)) {
            return SmsCategory.Personal
        }

        // 5. Commercial sender or bulk promotion without discount -> Spam
        return SmsCategory.Spam
    }

    fun normalizeDigits(input: String): String {
        return normalizeText(input)
    }

    fun normalizeText(input: String): String {
        val chars = input.replace('ي', 'ی').replace('ك', 'ک').replace('ة', 'ه').replace('ۀ', 'ه').toCharArray()
        for (i in chars.indices) {
            val c = chars[i]
            when (c) {
                in '۰'..'۹' -> chars[i] = ('0' + (c.toInt() - '۰'.toInt()))
                in '٠'..'٩' -> chars[i] = ('0' + (c.toInt() - '٠'.toInt()))
            }
        }
        return String(chars)
    }

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
            normalized.startsWith("98") -> normalized.substring(2)
            normalized.startsWith("0") -> normalized.substring(1)
            else -> normalized
        }
        // 0998 (Shatel Mobile) and 0999 (MVNOs) are heavily used for bulk commercial ads/spam
        if (plain.startsWith("998") || plain.startsWith("999")) {
            return false
        }
        return PERSONAL_NUMBER_REGEX.matcher(normalized).matches()
    }

    fun isOtpMessage(body: String): Boolean {
        val lower = normalizeText(body).toLowerCase()
        return OTP_KEYWORDS.any { lower.contains(it.toLowerCase()) }
    }

    fun extractOtpCode(body: String): String? {
        val normalizedBody = normalizeDigits(body)
        // Look for digit sequences (4 to 8 digits)
        val patterns = listOf(
            Pattern.compile("(?:کد(?:\\s*تایید|\\s*ورود|\\s*فعالسازی|\\s*احراز)?|رمز(?:\\s*پویا|\\s*یکبار\\s*مصرف)?|code|otp)[:\\s]+([0-9]{4,8})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("([0-9]{4,8})(?:\\s*کد(?:\\s*تایید|\\s*ورود|\\s*شما))", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b([0-9]{4,8})\\b")
        )

        for (pattern in patterns) {
            val matcher = pattern.matcher(normalizedBody)
            if (matcher.find()) {
                val candidate = matcher.group(1) ?: matcher.group(0)
                // Ensure candidate is between 4 and 8 digits and not a phone prefix
                if (candidate != null && candidate.length in 4..8 && !candidate.startsWith("09")) {
                    return candidate
                }
            }
        }
        return null
    }

    val IRANIAN_BANKS = listOf(
        "بلوبانک" to "بلوبانک",
        "بلو بانک" to "بلوبانک",
        "ویپاد" to "ویپاد (ترابانک پاسارگاد)",
        "بانک ملی" to "بانک ملی ایران",
        "ملی ایران" to "بانک ملی ایران",
        "بانک ملت" to "بانک ملت",
        "بانک صادرات" to "بانک صادرات ایران",
        "صادرات ایران" to "بانک صادرات ایران",
        "بانک تجارت" to "بانک تجارت",
        "بانک سپه" to "بانک سپه",
        "بانک پاسارگاد" to "بانک پاسارگاد",
        "پاسارگاد" to "بانک پاسارگاد",
        "بانک سامان" to "بانک سامان",
        "بانک پارسیان" to "بانک پارسیان",
        "بانک آینده" to "بانک آینده",
        "بانک کشاورزی" to "بانک کشاورزی",
        "بانک مسکن" to "بانک مسکن",
        "بانک رفاه" to "بانک رفاه کارگران",
        "رفاه کارگران" to "بانک رفاه کارگران",
        "بانک شهر" to "بانک شهر",
        "بانک سینا" to "بانک سینا",
        "بانک دی" to "بانک دی",
        "اقتصاد نوین" to "بانک اقتصاد نوین",
        "بانک کارآفرین" to "بانک کارآفرین",
        "کارآفرین" to "بانک کارآفرین",
        "بانک گردشگری" to "بانک گردشگری",
        "گردشگری" to "بانک گردشگری",
        "ایران زمین" to "بانک ایران زمین",
        "بانک سرمایه" to "بانک سرمایه",
        "پست بانک" to "پست بانک ایران",
        "بانک رسالت" to "بانک قرض‌الحسنه رسالت",
        "رسالت" to "بانک قرض‌الحسنه رسالت",
        "مهر ایران" to "بانک قرض‌الحسنه مهر ایران",
        "خاورمیانه" to "بانک خاورمیانه",
        "توسعه تعاون" to "بانک توسعه تعاون",
        "صنعت و معدن" to "بانک صنعت و معدن",
        "توسعه صادرات" to "بانک توسعه صادرات",
        "بانک انصار" to "بانک سپه (انصار)",
        "بانک قوامین" to "بانک سپه (قوامین)",
        "مهر اقتصاد" to "بانک سپه (مهر اقتصاد)",
        "حکمت ایرانیان" to "بانک سپه (حکمت)",
        "کوثر" to "بانک سپه (کوثر)",
        "موسسه ملل" to "موسسه اعتباری ملل",
        "موسسه نور" to "موسسه اعتباری نور",
        "آبانک" to "آبانک (بانک آینده)",
        "باجت" to "باجت (بانک تجارت)",
        "زیپاد" to "زیپاد",
        "توبانک" to "توبانک",
        "شاپرک" to "شاپرک"
    )

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

    private fun extractServiceName(sender: String, body: String): String {
        for ((keyword, name) in IRANIAN_BANKS) {
            if (body.contains(keyword, ignoreCase = true) || sender.contains(keyword, ignoreCase = true)) {
                return name
            }
        }
        for ((keyword, name) in SERVICE_BRANDS) {
            if (body.contains(keyword, ignoreCase = true) || sender.contains(keyword, ignoreCase = true)) {
                return name
            }
        }
        return if (sender.isNotBlank()) sender else "سرویس تایید ورود"
    }

    private fun hasDiscountCode(body: String): Boolean {
        val lower = body.toLowerCase()
        return DISCOUNT_KEYWORDS.any { lower.contains(it.toLowerCase()) }
    }

    /**
     * Builds a [PromoItem] from a promotional SMS, or returns null when the message carries no
     * usable coupon code.
     *
     * Brand identification, value parsing and code extraction each live in their own object
     * now. The previous version inlined all three as a 240-line `when` chain whose result
     * depended on branch order, so a message mentioning "گوگل کروم" was filed under a clothing
     * shop and "تپسی فود" was filed under "تپسی".
     */
    fun extractPromo(sender: String, body: String, date: Long = System.currentTimeMillis()): PromoItem? {
        val normalizedBody = PromoValueParser.normalize(body)
        val normalizedSender = PromoValueParser.normalize(sender)

        val candidate = PromoCodeExtractor.extract(normalizedBody) ?: return null

        val brand = BrandRegistry.match(
            normalizedSender.toLowerCase(),
            normalizedBody.toLowerCase()
        ) ?: unknownBrandFor(sender)

        val minOrderParsed = PromoValueParser.parseMinOrder(normalizedBody)
        val discount = PromoValueParser.parseDiscount(normalizedBody, minOrderParsed?.second)
        val expiry = PromoValueParser.parseExpiry(normalizedBody, date)

        // A message with neither a recognisable brand nor a stated saving is probably not an
        // offer at all, so lower the confidence rather than presenting it as a sure thing.
        var confidence = candidate.confidence
        if (brand.categorySlug == BrandRegistry.SLUG_OTHER) confidence -= 15
        if (discount.type == DiscountType.UNKNOWN) confidence -= 15
        confidence = confidence.coerceIn(10, 100)

        val description = when (discount.type) {
            DiscountType.FREE_SHIPPING -> "ارسال رایگان از ${brand.fa}"
            DiscountType.UNKNOWN -> "کد تخفیف ${brand.fa}"
            else -> "${discount.display} تخفیف ${brand.fa}"
        }

        return PromoItem(
            id = "promo-$date-${candidate.code.hashCode()}",
            brand = brand.fa,
            brandEn = brand.en,
            category = brand.category,
            categorySlug = brand.categorySlug,
            code = candidate.code,
            discountAmount = discount.display,
            description = description,
            minOrder = minOrderParsed?.first?.display,
            instructions = "در صفحه پرداخت ${brand.fa} کد ${candidate.code} را وارد کنید.",
            expiryDateText = expiry.display,
            sender = sender,
            body = body,
            receivedAt = date,
            discountType = discount.type,
            discountValue = discount.value,
            minOrderValue = minOrderParsed?.first?.value ?: 0L,
            expiresAt = expiry.atMillis,
            expiryIsExplicit = expiry.isExplicit,
            confidence = confidence,
            brandColor = brand.color,
            appPackage = brand.appPackage,
            website = brand.website
        )
    }

    /**
     * Falls back to the sender as a brand name when the registry does not recognise it, so a
     * card from an unknown shop still shows something better than "Store".
     */
    private fun unknownBrandFor(sender: String): Brand {
        val trimmed = sender.trim()
        val usableAsName = trimmed.isNotBlank() &&
            !trimmed.startsWith("09") &&
            !trimmed.startsWith("+98") &&
            !trimmed.all { it.isDigit() }

        return if (usableAsName) {
            BrandRegistry.UNKNOWN.copy(
                fa = trimmed,
                en = trimmed,
                color = BrandRegistry.fallbackColor(trimmed)
            )
        } else {
            BrandRegistry.UNKNOWN
        }
    }

    private fun isBankingMessage(sender: String, body: String): Boolean {
        val cleanBody = normalizeText(body)
        // 1. If message contains commercial advertisement signals, it is NOT a banking transaction!
        val adSignals = listOf("تخفیف", "جشنواره", "لغو11", "لغو۱۱", "لغو ۱۱", "ارسال رایگان", "فروشگاه", "off", "discount")
        if (adSignals.any { cleanBody.contains(it, ignoreCase = true) }) {
            return false
        }

        val hasBankKeyword = IRANIAN_BANKS.any { (kw, _) ->
            cleanBody.contains(kw, ignoreCase = true) || sender.contains(kw, ignoreCase = true)
        } || sender.contains("bank", ignoreCase = true) || sender.contains("بانک")

        val strictBankingKeywords = listOf(
            "واریز", "برداشت", "مانده حساب", "موجودی:", "مانده فعلی",
            "انتقال وجه", "انتقال پل", "انتقال پایا", "حواله پایا", "انتقال ساتنا", "خرید با کارت",
            "خرید از:", "صورتحساب", "کسر شد", "افزایش موجودی",
            "از حساب شما پرید", "به حساب شما نشست", "رمز اینترنتی"
        )
        val matchedKws = strictBankingKeywords.count { cleanBody.contains(it, ignoreCase = true) }

        if (hasBankKeyword && matchedKws >= 1) return true

        // Financial pairs like (واریز and موجودی), (برداشت and موجودی)
        val financialPairs = listOf(
            "واریز" to "موجودی",
            "برداشت" to "موجودی",
            "واریز" to "حساب",
            "برداشت" to "حساب",
            "خرید با کارت" to "مانده حساب"
        )
        return financialPairs.any { (p1, p2) -> cleanBody.contains(p1, ignoreCase = true) && cleanBody.contains(p2, ignoreCase = true) }
    }

    private fun extractBankName(sender: String, body: String): String {
        for ((kw, name) in IRANIAN_BANKS) {
            if (body.contains(kw, ignoreCase = true) || sender.contains(kw, ignoreCase = true)) {
                return name
            }
        }
        return if (sender.isNotBlank()) sender else "پیامک بانکی"
    }

    private fun extractBankingAmount(body: String): String? {
        val normalized = normalizeDigits(body)
        val pattern = Pattern.compile("([0-9,]{4,})\\s*(?:ریال|تومان)")
        val matcher = pattern.matcher(normalized)
        return if (matcher.find()) matcher.group(0) else null
    }
}
