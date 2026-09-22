package com.moez.QKSMS.feature.smart

import com.moez.QKSMS.feature.smart.model.OtpItem
import com.moez.QKSMS.feature.smart.model.PromoItem
import com.moez.QKSMS.feature.smart.model.SmsCategory
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

    fun extractPromo(sender: String, body: String, date: Long = System.currentTimeMillis()): PromoItem? {
        val norm = normalizeText(body)
        val combined = "$sender $norm"

        // Brand identification
        var brand = "سایر فروشگاه‌ها"
        var brandEn = "Store"
        var category = "فروشگاه آنلاین"
        var categorySlug = "ecommerce"

        when {
            combined.contains("اسنپ فود", true) || combined.contains("اسنپ‌فود", true) || sender.contains("SNAPPFOOD", true) -> {
                brand = "اسنپ‌فود"
                brandEn = "SnappFood"
                category = "غذا و رستوران"
                categorySlug = "food"
            }
            combined.contains("تپسی فود", true) || combined.contains("تپسی‌فود", true) -> {
                brand = "تپسی‌فود"
                brandEn = "Tapsi Food"
                category = "غذا و رستوران"
                categorySlug = "food"
            }
            combined.contains("دیجی کالا", true) || combined.contains("دیجی‌کالا", true) || combined.contains("دیجیکالا", true) || sender.contains("DIGIKALA", true) -> {
                brand = "دیجی‌کالا"
                brandEn = "Digikala"
                category = "فروشگاه آنلاین"
                categorySlug = "ecommerce"
            }
            combined.contains("دیجی‌استایل", true) || combined.contains("دیجی استایل", true) -> {
                brand = "دیجی‌استایل"
                brandEn = "DigiStyle"
                category = "مد و پوشاک"
                categorySlug = "ecommerce"
            }
            combined.contains("الوپیک", true) || combined.contains("الو پیک", true) -> {
                brand = "الوپیک"
                brandEn = "Alopeyk"
                category = "ارسال بسته و پیک"
                categorySlug = "transport"
            }
            combined.contains("تپسی مارکت", true) || combined.contains("تپسی‌مارکت", true) || combined.contains("tpmk", true) -> {
                brand = "تپسی‌مارکت"
                brandEn = "Tapsi Market"
                category = "سوپرمارکت"
                categorySlug = "supermarket"
            }
            combined.contains("اسنپ پی", true) || combined.contains("اسنپ‌پی", true) || combined.contains("snapppay", true) -> {
                brand = "اسنپ‌پی"
                brandEn = "SnappPay"
                category = "پرداخت اقساطی"
                categorySlug = "fintech"
            }
            combined.contains("دیجی پی", true) || combined.contains("دیجی‌پی", true) || combined.contains("dgpay", true) -> {
                brand = "دیجی‌پی"
                brandEn = "Digipay"
                category = "پرداخت اقساطی"
                categorySlug = "fintech"
            }
            combined.contains("دیجی‌واش", true) || combined.contains("دیجی واش", true) || combined.contains("irdgw", true) -> {
                brand = "دیجی‌واش"
                brandEn = "DigiWash"
                category = "خشکشویی آنلاین"
                categorySlug = "services"
            }
            combined.contains("اکتیوکلینرز", true) || combined.contains("اکتیو کلینرز", true) -> {
                brand = "اکتیو کلینرز"
                brandEn = "Active Cleaners"
                category = "خشکشویی آنلاین"
                categorySlug = "services"
            }
            combined.contains("گوشی‌شاپ", true) || combined.contains("گوشی شاپ", true) -> {
                brand = "گوشی‌شاپ"
                brandEn = "Gooshishop"
                category = "کالای دیجیتال"
                categorySlug = "ecommerce"
            }
            combined.contains("تخفیفان", true) -> {
                brand = "تخفیفان"
                brandEn = "Takhfifan"
                category = "کوپن و تخفیف"
                categorySlug = "ecommerce"
            }
            combined.contains("اوزون", true) || sender.contains("OZONE", true) -> {
                brand = "درگاه اوزون"
                brandEn = "Ozon"
                category = "پرداخت و تخفیف"
                categorySlug = "fintech"
            }
            combined.contains("زی‌تل", true) || combined.contains("زیتل", true) -> {
                brand = "زی‌تل"
                brandEn = "Zitel"
                category = "اینترنت ثابت"
                categorySlug = "telecom"
            }
            combined.contains("همراه اول", true) || sender.contains("HAMRAH", true) || sender.contains("MCI", true) -> {
                brand = "همراه اول"
                brandEn = "MCI"
                category = "اپراتور تلفن همراه"
                categorySlug = "telecom"
            }
            combined.contains("ایرانسل", true) || sender.contains("MTN", true) || sender.contains("IRANCELL", true) -> {
                brand = "ایرانسل"
                brandEn = "Irancell"
                category = "اپراتور تلفن همراه"
                categorySlug = "telecom"
            }
            combined.contains("رایتل", true) || sender.contains("RIGHTEL", true) -> {
                brand = "رایتل"
                brandEn = "Rightel"
                category = "اپراتور تلفن همراه"
                categorySlug = "telecom"
            }
            combined.contains("فیلیمو", true) -> {
                brand = "فیلیمو"
                brandEn = "Filimo"
                category = "فیلم و سریال"
                categorySlug = "entertainment"
            }
            combined.contains("نماوا", true) -> {
                brand = "نماوا"
                brandEn = "Namava"
                category = "فیلم و سریال"
                categorySlug = "entertainment"
            }
            combined.contains("فیلم نت", true) || combined.contains("فیلم‌نت", true) -> {
                brand = "فیلم‌نت"
                brandEn = "Filmnet"
                category = "فیلم و سریال"
                categorySlug = "entertainment"
            }
            combined.contains("سینماتیکت", true) || combined.contains("سینما تیکت", true) -> {
                brand = "سینماتیکت"
                brandEn = "CinemaTicket"
                category = "تفریح و سینما"
                categorySlug = "entertainment"
            }
            combined.contains("اکالا", true) || combined.contains("افق کوروش", true) || sender.contains("okala", true) -> {
                brand = "اکالا"
                brandEn = "Okala"
                category = "سوپرمارکت"
                categorySlug = "supermarket"
            }
            combined.contains("باسلام", true) -> {
                brand = "باسلام"
                brandEn = "Basalam"
                category = "فروشگاه آنلاین"
                categorySlug = "ecommerce"
            }
            combined.contains("اسنپ مارکت", true) || combined.contains("اسنپ‌مارکت", true) -> {
                brand = "اسنپ‌مارکت"
                brandEn = "SnappMarket"
                category = "سوپرمارکت"
                categorySlug = "supermarket"
            }
            combined.contains("چرم منط", true) || combined.contains("چرم مَنط", true) -> {
                brand = "چرم مَنط"
                brandEn = "Mant Leather"
                category = "پوشاک و چرم"
                categorySlug = "ecommerce"
            }
            combined.contains("نوین‌چرم", true) || combined.contains("نوین چرم", true) -> {
                brand = "نوین‌چرم"
                brandEn = "Novin Leather"
                category = "پوشاک و چرم"
                categorySlug = "ecommerce"
            }
            combined.contains("بیمه‌دات‌کام", true) || combined.contains("بیمه دات کام", true) || combined.contains("bmeh.me", true) -> {
                brand = "بیمه دات‌کام"
                brandEn = "Bimeh.com"
                category = "بیمه آنلاین"
                categorySlug = "services"
            }
            combined.contains("مسترکالا", true) || combined.contains("masterkala", true) -> {
                brand = "مسترکالا"
                brandEn = "Masterkala"
                category = "کالای دیجیتال"
                categorySlug = "ecommerce"
            }
            combined.contains("کروم", true) -> {
                brand = "فروشگاه کروم"
                brandEn = "Crom"
                category = "پوشاک"
                categorySlug = "ecommerce"
            }
            combined.contains("تپسی", true) -> {
                brand = "تپسی"
                brandEn = "Tapsi"
                category = "تاکسی اینترنتی"
                categorySlug = "transport"
            }
            combined.contains("اسنپ", true) -> {
                brand = "اسنپ"
                brandEn = "Snapp"
                category = "تاکسی اینترنتی"
                categorySlug = "transport"
            }
            combined.contains("علی بابا", true) || combined.contains("علی‌بابا", true) -> {
                brand = "علی‌بابا"
                brandEn = "Alibaba"
                category = "گردشگری و سفر"
                categorySlug = "transport"
            }
            else -> {
                if (sender.isNotBlank() && !sender.startsWith("09") && !sender.startsWith("+98") && !sender.all { it.isDigit() }) {
                    brand = sender
                    brandEn = sender
                }
            }
        }

        // Code extraction:
        // 1. Explicit code patterns (with optional emoji, colon, space, or brand name)
        val codePatterns = listOf(
            Pattern.compile("(?:کد(?:\\s*تخفیف|\\s*هدیه|\\s*معرف|\\s*درگاه[^:\\n]{0,10})?|با\\s*کد)[^a-zA-Z0-9\\n]{0,12}[:\\s]+([a-zA-Z0-9_\\-]{3,24})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("کد[^:\\n]{0,10}:([a-zA-Z0-9_\\-]{3,24})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?:دیجی‌پی|دیجی پی|اسنپ‌پی|اسنپ پی|تپسی)[^:\\n]{0,8}[:\\s]+([A-Za-z0-9_\\-]{4,20})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?:code|promo)[:\\s]+([a-zA-Z0-9_\\-]{3,24})", Pattern.CASE_INSENSITIVE)
        )

        var code = ""
        val invalidCodeWords = setOf("http", "https", "link", "ir", "com", "net", "org", "volte", "sms", "tapsi", "snapp", "dgkl", "snpf", "dgpay", "mci", "shatel")

        for (pat in codePatterns) {
            val matcher = pat.matcher(norm)
            if (matcher.find()) {
                val candidate = matcher.group(1)?.trim() ?: ""
                val candLower = candidate.toLowerCase()
                if (candidate.length in 3..24 && !candidate.startsWith("09") && !candidate.startsWith("+98") && !candidate.all { it.isDigit() }) {
                    if (!invalidCodeWords.contains(candLower) && candidate.any { it in 'a'..'z' || it in 'A'..'Z' }) {
                        code = candidate
                        break
                    }
                }
            }
        }

        // 2. Standalone code line (e.g. SNAPPFOOD: VDTTESZQ4D8HXWDFWV)
        if (code.isBlank()) {
            val lines = norm.lines()
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.length in 4..24 && !trimmed.contains(" ") && !trimmed.startsWith("http") && !trimmed.startsWith("09") && !trimmed.startsWith("+98") && !trimmed.contains(".") && !trimmed.contains("/")) {
                    val candLower = trimmed.toLowerCase()
                    if (!invalidCodeWords.contains(candLower) && trimmed.any { it in 'a'..'z' || it in 'A'..'Z' }) {
                        code = trimmed
                        break
                    }
                }
            }
        }

        if (code.isBlank()) {
            return null // Not a valid promo code SMS
        }

        // Minimum order extraction
        var minOrder: String? = null
        val minOrderPattern = Pattern.compile(
            "(?:کف\\s*خرید|کف\\s*سبد|حداقل\\s*خرید|حداقل\\s*سبد|حداقل\\s*سفارش|سبد\\s*بالای|بالای)\\s*[:\\s]*([0-9,]+(?:\\s*(?:میلیون|هزار|تومان|ت))*)",
            Pattern.CASE_INSENSITIVE
        )
        val minOrderMatcher = minOrderPattern.matcher(norm)
        var bodyForAmount = norm
        if (minOrderMatcher.find()) {
            var rawVal = minOrderMatcher.group(1)?.trim() ?: ""
            if (!rawVal.endsWith("تومان") && !rawVal.endsWith("ت")) {
                rawVal += " تومان"
            }
            minOrder = "حداقل خرید ${toPersianDigits(rawVal)}"
            bodyForAmount = norm.replace(minOrderMatcher.group(0), " ")
        }

        // Discount amount extraction (with Persian digits)
        var discountAmount = "تخفیف ویژه"

        // 1. Percentage (e.g. 50%, 50٪, 10 درصد)
        val pctPattern1 = Pattern.compile("(?:کد\\s*)?([0-9]{1,3}\\s*(?:درصد|٪|%))(?:\\s*تخفیف)?", Pattern.CASE_INSENSITIVE)
        val pctMatcher1 = pctPattern1.matcher(bodyForAmount)
        if (pctMatcher1.find()) {
            val numStr = pctMatcher1.group(1)!!.replace("%", "٪").replace("درصد", "٪").replace(" ", "").trim()
            discountAmount = toPersianDigits(numStr)
        } else {
            val pctPattern2 = Pattern.compile("([%٪]\\s*[0-9]{1,3})", Pattern.CASE_INSENSITIVE)
            val pctMatcher2 = pctPattern2.matcher(bodyForAmount)
            if (pctMatcher2.find()) {
                val numDigits = pctMatcher2.group(1)!!.replace(Regex("[^0-9]"), "")
                discountAmount = "${toPersianDigits(numDigits)}٪"
            } else {
                // 2. Millions (e.g. 3م تخفیف, 2 میلیون تومان)
                val mPattern1 = Pattern.compile("([0-9]+)\\s*م\\s*تخفیف", Pattern.CASE_INSENSITIVE)
                val mMatcher1 = mPattern1.matcher(bodyForAmount)
                if (mMatcher1.find()) {
                    discountAmount = "${toPersianDigits(mMatcher1.group(1)!!)} میلیون تومان"
                } else {
                    val mPattern2 = Pattern.compile("([0-9]+(?:[\\.,][0-9]+)?)\\s*میلیون(?:\\s*تومان|\\s*تومانی|\\s*ت)?", Pattern.CASE_INSENSITIVE)
                    val mMatcher2 = mPattern2.matcher(bodyForAmount)
                    if (mMatcher2.find()) {
                        discountAmount = "${toPersianDigits(mMatcher2.group(1)!!)} میلیون تومان"
                    } else {
                        // 3. Thousands (e.g. 400هزار تومان, 700 هزار ت)
                        val kPattern1 = Pattern.compile("([0-9]+(?:[\\.,][0-9]+)?)\\s*(?:هزار|هزارتومن|هزارتومان)(?:\\s*تومان|\\s*تومانی|\\s*ت)?", Pattern.CASE_INSENSITIVE)
                        val kMatcher1 = kPattern1.matcher(bodyForAmount)
                        if (kMatcher1.find()) {
                            discountAmount = "${toPersianDigits(kMatcher1.group(1)!!)} هزار تومان"
                        } else {
                            val kPattern2 = Pattern.compile("\\+?([0-9]{2,4})ت\\s*تخفیف", Pattern.CASE_INSENSITIVE)
                            val kMatcher2 = kPattern2.matcher(bodyForAmount)
                            if (kMatcher2.find()) {
                                discountAmount = "${toPersianDigits(kMatcher2.group(1)!!)} هزار تومان"
                            } else {
                                // 4. Formatted currency (e.g. 3,000,000ت)
                                val curPattern = Pattern.compile("([0-9]{1,3}(?:,[0-9]{3})+)\\s*(?:تومان|ت|ریال)", Pattern.CASE_INSENSITIVE)
                                val curMatcher = curPattern.matcher(bodyForAmount)
                                if (curMatcher.find()) {
                                    val formattedNum = toPersianDigits(curMatcher.group(1)!!)
                                    discountAmount = if (curMatcher.group(0)!!.contains("ریال")) "$formattedNum ریال" else "$formattedNum تومان"
                                } else if (bodyForAmount.contains("ارسال رایگان")) {
                                    discountAmount = "ارسال رایگان"
                                }
                            }
                        }
                    }
                }
            }
        }

        // Expiry extraction
        val expiryPattern = Pattern.compile(
            "(?:اعتبار تا|مهلت تا|انقضا:?|تا پایان|فقط تا|مهلت استفاده تا|معتبر تا|تا تاریخ|اعتبار فقط تا|تا\\s*[۰-۹0-9]+\\s*روز|تا ساعت\\s*[۰-۹0-9]+)\\s*([^\\.\\n,،!]+)",
            Pattern.CASE_INSENSITIVE
        )
        val expiryMatcher = expiryPattern.matcher(body)
        var rawExpiry = if (expiryMatcher.find()) expiryMatcher.group(0)?.trim() ?: "" else ""
        if (rawExpiry.isBlank()) {
            if (body.contains("امشب")) rawExpiry = "تا پایان امشب"
            else if (body.contains("تا فردا") || body.contains("فردا")) rawExpiry = "تا فردا"
            else if (body.contains("۷ روز") || body.contains("7 روز")) rawExpiry = "تا ۷ روز"
            else if (body.contains("۳ روز") || body.contains("3 روز")) rawExpiry = "تا ۳ روز"
        }
        val expiryDateText = if (rawExpiry.isNotBlank() && !rawExpiry.contains("اطلاع ثانوی")) {
            toPersianDigits(rawExpiry)
        } else {
            val jExp = com.moez.QKSMS.common.util.JalaliCalendar.fromMillis(date + (7L * 24 * 60 * 60 * 1000L))
            "${toPersianDigits(jExp.year.toString())}/${toPersianDigits(String.format("%02d", jExp.month))}/${toPersianDigits(String.format("%02d", jExp.day))} (۱ هفته)"
        }

        val description = "تخفیف $discountAmount ویژه $brand"
        val instructions = "وارد اپلیکیشن یا سایت $brand شوید، سفارش خود را تکمیل کرده و در صفحه پرداخت کد $code را وارد کنید."

        return PromoItem(
            id = "promo-$date-${code.hashCode()}",
            brand = brand,
            brandEn = brandEn,
            category = category,
            categorySlug = categorySlug,
            code = code,
            discountAmount = discountAmount,
            description = description,
            minOrder = minOrder,
            instructions = instructions,
            expiryDateText = expiryDateText,
            sender = sender,
            body = body,
            receivedAt = date
        )
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
