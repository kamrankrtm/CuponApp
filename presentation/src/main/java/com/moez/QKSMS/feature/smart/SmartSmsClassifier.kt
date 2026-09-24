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

    // An Iranian mobile once its +98 / 0098 / 98 / 0 prefix is gone: 912..., ten digits
    private val PERSONAL_NUMBER_REGEX = Pattern.compile("^9\\d{9}$")

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
    fun classify(
        sender: String,
        body: String,
        date: Long = System.currentTimeMillis(),
        threadId: Long = 0L
    ): SmsCategory {
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
            val promo = extractPromo(cleanSender, cleanBody, date, threadId)
            if (promo != null) {
                return SmsCategory.Promo(promo)
            }
        }

        // 3. Check for Banking transaction
        if (isBankingMessage(cleanSender, cleanBody)) {
            val bankName = extractBankName(cleanSender, cleanBody)
            val (amount, isDeposit) = readTransaction(cleanBody)
            return SmsCategory.Banking(bankName, amount, isDeposit)
        }

        // 4. Check for Personal Contact / 09..., or a sender the user marked "not spam"
        if (isPersonalNumber(cleanSender) || SenderOverrides.isTrusted(cleanSender)) {
            return SmsCategory.Personal
        }

        // 5. Commercial sender or bulk promotion without discount -> Spam
        return SmsCategory.Spam
    }

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
    ): SmsCategory {
        if (SenderOverrides.isTrusted(sender)) return SmsCategory.Personal
        if (!hasSavedContact) return classify(sender, body, date, threadId)
        if (isPersonalNumber(sender)) return SmsCategory.Personal
        val category = classify(sender, body, date, threadId)
        return if (category is SmsCategory.Banking) category else SmsCategory.Personal
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
    ): SmsCategory {
        val category = classify(sender, body, date, threadId)
        if (category is SmsCategory.Otp) return category
        return when (SenderOverrides.tabFor(sender)) {
            SenderOverrides.Tab.SPAM -> SmsCategory.Spam
            SenderOverrides.Tab.BANKING -> category as? SmsCategory.Banking ?: movedToBanking(sender.trim(), body.trim())
            SenderOverrides.Tab.PERSONAL, null -> category
        }
    }

    /** A message from a sender moved to Banking that reads like no transaction we know. */
    private fun movedToBanking(sender: String, body: String): SmsCategory.Banking {
        val (amount, isDeposit) = readTransaction(body)
        return SmsCategory.Banking(extractBankName(sender, body), amount, isDeposit)
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

    /**
     * Patterns for verification texts that name no fixed keyword phrase.
     *
     * "کد 36330 را جهت ورود به سامانه بام وارد نمایید" contains both "کد" and "ورود" but never
     * the contiguous phrase "کد ورود", so keyword matching alone missed it and the message fell
     * through to the promotional path.
     */
    private val OTP_PATTERNS = listOf(
        Pattern.compile("کد\\s*[0-9]{4,8}\\s*(?:را|رو)\\b"),
        Pattern.compile("(?:جهت|برای)\\s*ورود"),
        Pattern.compile("رمز\\s*ورود"),
        Pattern.compile("(?:کد|رمز)\\s*یک\\s*بار\\s*مصرف")
    )

    fun isOtpMessage(body: String): Boolean {
        val normalized = normalizeText(body)
        val lower = normalized.toLowerCase()
        if (OTP_KEYWORDS.any { lower.contains(it.toLowerCase()) }) return true
        return OTP_PATTERNS.any { it.matcher(normalized).find() }
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
    fun extractPromo(
        sender: String,
        body: String,
        date: Long = System.currentTimeMillis(),
        threadId: Long = 0L
    ): PromoItem? {
        val normalizedBody = PromoValueParser.normalize(body)
        val normalizedSender = PromoValueParser.normalize(sender)

        // Both gates live here rather than in the callers. MainActivity used to call this
        // straight from the inbox scan, skipping the promotional check that only `classify`
        // applied, so a bank's login SMS was filed under discounts and the SMS Retriever hash
        // on its last line was offered to the user as a coupon.
        if (PromoCodeExtractor.isVerificationMessage(normalizedBody)) return null
        if (!PromoCodeExtractor.looksPromotional(normalizedBody)) return null

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
            threadId = threadId,
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

    /** Words that make a message an advertisement, whatever amounts it quotes. */
    private val AD_SIGNALS = listOf(
        "تخفیف", "جشنواره", "ارسال رایگان", "فروشگاه", "قرعه", "جایزه", "هدیه", "off", "discount"
    )

    /** The opt-out line the law requires under every advertisement: "لغو11". */
    private val OPT_OUT = Pattern.compile("لغو\\s*\\d")

    /** An amount written with thousands separators: 400,000 (digits already normalised). */
    private const val GROUPED_AMOUNT = "\\d{1,3}(?:[,٬]\\d{3})+"

    /** The way statements write money leaving or arriving: "400,000-", "+7,000,000". */
    private val SIGNED_AMOUNT = Pattern.compile("[+\\-−]\\s?$GROUPED_AMOUNT|$GROUPED_AMOUNT\\s?[+\\-−]")

    /** A stated balance: "مانده:1,234,567", "موجودی حساب 1,234,567". */
    private val BALANCE = Pattern.compile("(?:مانده|موجودی)(?:\\s*حساب)?\\s*[:：]?\\s*[+\\-−]?\\s?$GROUPED_AMOUNT")

    /** An account or card number, which statements lead with. */
    private val ACCOUNT_NUMBER = Pattern.compile("\\d{10,}")

    private val WALLET_MOVES = listOf("شارژ شد", "واریز", "برداشت", "پرداخت", "کسر شد", "افزایش")

    /** Receipts from payment gateways such as Zarinpal. */
    private val GATEWAY_RECEIPTS = listOf("خرید از درگاه", "پرداخت از درگاه", "درگاه پرداخت", "پرداخت موفق", "تراکنش موفق")

    private val STRICT_BANKING_KEYWORDS = listOf(
        "واریز", "برداشت", "مانده حساب", "موجودی:", "مانده فعلی", "مانده:", "انتقال:", "حساب:",
        "انتقال وجه", "انتقال پل", "انتقال پایا", "حواله پایا", "انتقال ساتنا", "خرید با کارت",
        "خرید از:", "صورتحساب", "کسر شد", "افزایش موجودی",
        "از حساب شما پرید", "به حساب شما نشست", "رمز اینترنتی"
    )

    /** Pairs that together only a bank would write, e.g. (واریز and موجودی). */
    private val FINANCIAL_PAIRS = listOf(
        "واریز" to "موجودی",
        "برداشت" to "موجودی",
        "واریز" to "حساب",
        "برداشت" to "حساب",
        "کسر" to "حساب",
        "پرداخت" to "حساب",
        "خرید با کارت" to "مانده حساب"
    )

    /** Latin sender ids banks send from ("B.QMEHRIRAN", "BLUBANK"), with the name to show. */
    private val BANK_SENDER_IDS = listOf(
        "mehriran" to "بانک قرض‌الحسنه مهر ایران",
        "resalat" to "بانک قرض‌الحسنه رسالت",
        "blubank" to "بلوبانک",
        "blu" to "بلوبانک",
        "wepod" to "ویپاد (ترابانک پاسارگاد)",
        "melli" to "بانک ملی ایران",
        "mellat" to "بانک ملت",
        "saderat" to "بانک صادرات ایران",
        "tejarat" to "بانک تجارت",
        "sepah" to "بانک سپه",
        "pasargad" to "بانک پاسارگاد",
        "saman" to "بانک سامان",
        "parsian" to "بانک پارسیان",
        "ayandeh" to "بانک آینده",
        "keshavarzi" to "بانک کشاورزی",
        "maskan" to "بانک مسکن",
        "eghtesad" to "بانک اقتصاد نوین",
        "karafarin" to "بانک کارآفرین",
        "gardeshgari" to "بانک گردشگری",
        "iranzamin" to "بانک ایران زمین",
        "sarmayeh" to "بانک سرمایه",
        "postbank" to "پست بانک ایران",
        "bank" to ""
    )

    /** Wallets and gateways, named when a transaction comes from one rather than a bank. */
    private val PAYMENT_SERVICES = listOf(
        "زرین پال" to "زرین‌پال",
        "زرینپال" to "زرین‌پال",
        "بازارپی" to "بازارپی",
        "بازار پی" to "بازارپی",
        "دیجی پی" to "دیجی‌پی",
        "دیجیپی" to "دیجی‌پی",
        "اسنپ پی" to "اسنپ‌پی",
        "اسنپپی" to "اسنپ‌پی",
        "کیف پول" to "کیف پول"
    )

    /** "بلو" opens every Blu Bank message but also sits inside ordinary words, so it must stand alone. */
    private val BLU_WORD = Pattern.compile("(?<!\\p{L})بلو(?!\\p{L})")

    /** Arabic letters and digits made Persian/ASCII, and the zero-width joiner made a space. */
    private fun bankingText(text: String): String = normalizeText(text).replace('‌', ' ')

    private fun isBankingMessage(sender: String, body: String): Boolean {
        val text = bankingText(body)
        val lower = text.toLowerCase()
        if (AD_SIGNALS.any { lower.contains(it) } || OPT_OUT.matcher(text).find()) {
            return false
        }

        val senderText = bankingText(sender).toLowerCase()
        val hasBankKeyword = IRANIAN_BANKS.any { (kw, _) -> text.contains(kw) || senderText.contains(kw) } ||
                BANK_SENDER_IDS.any { (id, _) -> senderText.contains(id) } ||
                senderText.contains("بانک") ||
                BLU_WORD.matcher(text).find()

        // The shape of a transaction: a signed amount against an account, a stated balance, a
        // wallet movement or a gateway receipt. Advertisements never write money this way.
        val signedAmount = SIGNED_AMOUNT.matcher(text).find()
        val balance = BALANCE.matcher(text).find()
        val accountish = text.contains("حساب") || text.contains("کارت") || ACCOUNT_NUMBER.matcher(text).find()
        val wallet = text.contains("کیف پول") && WALLET_MOVES.any { text.contains(it) }
        val gateway = GATEWAY_RECEIPTS.any { text.contains(it) }
        if ((signedAmount && (accountish || hasBankKeyword)) || balance || wallet || gateway) return true

        // Someone texting about money is not a bank: the looser word rules are for service senders
        if (isPersonalNumber(sender)) return false

        val matchedKws = STRICT_BANKING_KEYWORDS.count { text.contains(it) }
        if (hasBankKeyword && matchedKws >= 1) return true
        return FINANCIAL_PAIRS.any { (p1, p2) -> text.contains(p1) && text.contains(p2) }
    }

    private fun extractBankName(sender: String, body: String): String {
        val text = bankingText(body)
        val senderText = bankingText(sender).toLowerCase()
        for ((kw, name) in IRANIAN_BANKS) {
            if (text.contains(kw) || senderText.contains(kw)) {
                return name
            }
        }
        for ((id, name) in BANK_SENDER_IDS) {
            if (name.isNotEmpty() && senderText.contains(id)) return name
        }
        if (BLU_WORD.matcher(text).find()) return "بلوبانک"
        for ((kw, name) in PAYMENT_SERVICES) {
            if (text.contains(kw)) return name
        }
        return if (sender.isNotBlank()) sender else "پیامک بانکی"
    }

    /** "+7,000,000" or "400,000-": the sign says which way the money went. */
    private val SIGNED_CAPTURE = Pattern.compile("([+\\-−])\\s?($GROUPED_AMOUNT)|($GROUPED_AMOUNT)\\s?([+\\-−])")

    /** "مبلغ2,319", "بمبلغ 94,076,000", "برداشت:300,000" — the balance (مانده) is never the amount. */
    private val LABELLED_AMOUNT = Pattern.compile("(?:مبلغ|برداشت|واریز|انتقال|کارمزد|خرید|پرداخت)\\s*[:：]?\\s*($GROUPED_AMOUNT|\\d{4,})")

    private val AMOUNT_WITH_UNIT = Pattern.compile("($GROUPED_AMOUNT|\\d{4,})\\s*(ریال|تومان)")

    private val UNIT_AFTER = Pattern.compile("^\\s*(ریال|تومان)")

    private val DEPOSIT_WORDS = listOf("واریز", "شارژ شد", "افزایش موجودی", "به حساب شما نشست", "دریافت")
    private val WITHDRAWAL_WORDS = listOf("برداشت", "کسر", "خرید", "پرداخت", "کارمزد", "از حساب شما پرید", "انتقال از")

    /**
     * The amount of a transaction ("7,000,000 ریال") and whether money came in. Banks often
     * write neither unit nor direction ("انتقال:+7,000,000", "مبلغ2,319"), so a signed amount
     * wins, then one named by its label, then any amount with a unit; statements are in rials.
     */
    private fun readTransaction(body: String): Pair<String?, Boolean?> {
        val text = bankingText(body)
        var amount: String? = null
        var end = -1
        var deposit: Boolean? = null

        val signed = SIGNED_CAPTURE.matcher(text)
        if (signed.find()) {
            val sign = signed.group(1) ?: signed.group(4)
            amount = signed.group(2) ?: signed.group(3)
            end = signed.end()
            deposit = sign == "+"
        } else {
            val labelled = LABELLED_AMOUNT.matcher(text)
            val withUnit = AMOUNT_WITH_UNIT.matcher(text)
            if (labelled.find()) {
                amount = labelled.group(1)
                end = labelled.end()
            } else if (withUnit.find()) {
                amount = withUnit.group(1)
                end = withUnit.end(1)
            }
        }
        if (deposit == null) {
            deposit = when {
                DEPOSIT_WORDS.any { text.contains(it) } -> true
                WITHDRAWAL_WORDS.any { text.contains(it) } -> false
                else -> null
            }
        }
        if (amount == null) return Pair(null, deposit)
        val unit = UNIT_AFTER.matcher(text.substring(end)).let { if (it.find()) it.group(1) else "ریال" }
        return Pair("$amount $unit", deposit)
    }
}
