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
        "واریز", "برداشت", "مانده حساب", "موجودی:", "مانده:", "انتقال وجه",
        "خرید از:", "شاپرک", "پایا", "ساتنا", "حساب:"
    )

    // Discount keywords
    private val DISCOUNT_KEYWORDS = listOf(
        "کد تخفیف", "تخفیف", "جشنواره", "ارسال رایگان", "درصد تخفیف", "هدیه خرید",
        "off", "discount", "promo"
    )

    /**
     * Main classification method
     */
    fun classify(sender: String, body: String): SmsCategory {
        val cleanSender = sender.trim()
        val cleanBody = body.trim()

        // 1. Check for OTP / Verification Code first
        if (isOtpMessage(cleanBody)) {
            val code = extractOtpCode(cleanBody)
            if (code != null) {
                val service = extractServiceName(cleanSender, cleanBody)
                val otp = OtpItem(
                    id = "otp-${System.currentTimeMillis()}-${code.hashCode()}",
                    code = code,
                    serviceName = service,
                    sender = cleanSender,
                    body = cleanBody
                )
                return SmsCategory.Otp(otp)
            }
        }

        // 2. Check for Discount Code / Promotion
        if (hasDiscountCode(cleanBody)) {
            val promo = extractPromo(cleanSender, cleanBody)
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
        val chars = input.toCharArray()
        for (i in chars.indices) {
            val c = chars[i]
            when (c) {
                in '۰'..'۹' -> chars[i] = ('0'.toInt() + (c - '۰')).toChar()
                in '٠'..'٩' -> chars[i] = ('0'.toInt() + (c - '٠')).toChar()
            }
        }
        return String(chars)
    }

    fun isPersonalNumber(sender: String): Boolean {
        val normalized = normalizeDigits(sender).replace("\\s+".toRegex(), "").replace("-", "")
        return PERSONAL_NUMBER_REGEX.matcher(normalized).matches()
    }

    private fun isOtpMessage(body: String): Boolean {
        val lower = body.toLowerCase()
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

    private fun extractServiceName(sender: String, body: String): String {
        val brands = listOf(
            "بانک ملت" to "بانک ملت", "بانک ملی" to "بانک ملی", "بانک صادرات" to "بانک صادرات",
            "بانک تجارت" to "بانک تجارت", "بانک سپه" to "بانک سپه", "بانک پاسارگاد" to "پاسارگاد",
            "بانک سامان" to "سامان", "بانک آینده" to "آینده", "بانک شهر" to "بانک شهر",
            "بلوبانک" to "بلوبانک", "رسالت" to "بانک رسالت", "مهر ایران" to "بانک مهر ایران",
            "اسنپ" to "اسنپ", "تپسی" to "تپسی", "دیجی‌کالا" to "دیجی‌کالا", "دیجیکالا" to "دیجی‌کالا",
            "روبیکا" to "روبیکا", "ایتا" to "ایتا", "بله" to "بله", "سروش" to "سروش",
            "نشان" to "نشان", "بلد" to "بلد", "دیوار" to "دیوار", "شیپور" to "شیپور",
            "آپ" to "آپ (آسان پرداخت)", "تاپ" to "تاپ", "همراه کارت" to "همراه کارت"
        )
        for ((keyword, name) in brands) {
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

    fun extractPromo(sender: String, body: String): PromoItem? {
        // Brand identification
        var brand = "سایر فروشگاه‌ها"
        var brandEn = "Store"
        var category = "فروشگاه آنلاین"
        var categorySlug = "ecommerce"

        when {
            body.contains("اسنپ فود") || body.contains("اسنپ‌فود") -> {
                brand = "اسنپ‌فود"
                brandEn = "SnappFood"
                category = "غذا و رستوران"
                categorySlug = "food"
            }
            body.contains("تپسی فود") || body.contains("تپسی‌فود") -> {
                brand = "تپسی‌فود"
                brandEn = "Tapsi Food"
                category = "غذا و رستوران"
                categorySlug = "food"
            }
            body.contains("دیجی کالا") || body.contains("دیجی‌کالا") || body.contains("دیجیکالا") -> {
                brand = "دیجی‌کالا"
                brandEn = "Digikala"
                category = "فروشگاه آنلاین"
                categorySlug = "ecommerce"
            }
            body.contains("فیلیمو") -> {
                brand = "فیلیمو"
                brandEn = "Filimo"
                category = "فیلم و سریال"
                categorySlug = "entertainment"
            }
            body.contains("سینماتیکت") || body.contains("سینما تیکت") -> {
                brand = "سینماتیکت"
                brandEn = "CinemaTicket"
                category = "تفریح و سینما"
                categorySlug = "entertainment"
            }
            body.contains("اکالا") || body.contains("افق کوروش") -> {
                brand = "اکالا"
                brandEn = "Okala"
                category = "سوپرمارکت"
                categorySlug = "supermarket"
            }
            body.contains("باسلام") -> {
                brand = "باسلام"
                brandEn = "Basalam"
                category = "فروشگاه آنلاین"
                categorySlug = "ecommerce"
            }
            body.contains("اسنپ مارکت") || body.contains("اسنپ‌مارکت") -> {
                brand = "اسنپ‌مارکت"
                brandEn = "SnappMarket"
                category = "سوپرمارکت"
                categorySlug = "supermarket"
            }
            body.contains("تپسی") -> {
                brand = "تپسی"
                brandEn = "Tapsi"
                category = "تاکسی اینترنتی"
                categorySlug = "transport"
            }
            body.contains("اسنپ") -> {
                brand = "اسنپ"
                brandEn = "Snapp"
                category = "تاکسی اینترنتی"
                categorySlug = "transport"
            }
            body.contains("علی بابا") || body.contains("علی‌بابا") -> {
                brand = "علی‌بابا"
                brandEn = "Alibaba"
                category = "گردشگری و سفر"
                categorySlug = "transport"
            }
        }

        // Code extraction
        val codePattern1 = Pattern.compile("(?:کد(?:\\s*تخفیف)?[:\\s]+)([a-zA-Z0-9_\\-]+)", Pattern.CASE_INSENSITIVE)
        val codeMatcher1 = codePattern1.matcher(body)
        var code = ""
        if (codeMatcher1.find()) {
            code = codeMatcher1.group(1)?.trim() ?: ""
        } else {
            // Standalone uppercase or mixed code
            val codePattern2 = Pattern.compile("\\b([A-Z0-9]{4,12})\\b")
            val codeMatcher2 = codePattern2.matcher(body)
            while (codeMatcher2.find()) {
                val candidate = codeMatcher2.group(1) ?: ""
                if (!candidate.all { it.isDigit() } && !candidate.startsWith("09")) {
                    code = candidate
                    break
                }
            }
        }

        if (code.isBlank()) {
            return null // Not a valid promo code SMS
        }

        // Discount amount extraction
        val normBody = normalizeDigits(body)
        val amountPattern = Pattern.compile("(\\d+[\\s‌]*(?:هزار تومان|درصد|٪|تومان))", Pattern.CASE_INSENSITIVE)
        val amountMatcher = amountPattern.matcher(normBody)
        val discountAmount = if (amountMatcher.find()) amountMatcher.group(1)?.trim() ?: "تخفیف ویژه" else "تخفیف ویژه"

        // Minimum order extraction
        val minOrderPattern = Pattern.compile("(?:بالای|حداقل خرید)\\s*([0-9,]+(?:\\s*هزار)?\\s*تومان)", Pattern.CASE_INSENSITIVE)
        val minOrderMatcher = minOrderPattern.matcher(normBody)
        val minOrder = if (minOrderMatcher.find()) "حداقل خرید " + minOrderMatcher.group(1)?.trim() else null

        // Expiry extraction
        val expiryPattern = Pattern.compile("(?:اعتبار تا|مهلت تا|انقضا:?|تا پایان)\\s*([^\\.\\n]+)", Pattern.CASE_INSENSITIVE)
        val expiryMatcher = expiryPattern.matcher(body)
        val expiryDateText = if (expiryMatcher.find()) expiryMatcher.group(1)?.trim() ?: "معتبر تا اطلاع ثانوی" else "معتبر تا اطلاع ثانوی"

        val description = "تخفیف $discountAmount ویژه $brand"
        val instructions = "وارد اپلیکیشن یا سایت $brand شوید، سفارش خود را تکمیل کرده و در صفحه پرداخت کد $code را وارد کنید."

        return PromoItem(
            id = "promo-${System.currentTimeMillis()}-${code.hashCode()}",
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
            body = body
        )
    }

    private fun isBankingMessage(sender: String, body: String): Boolean {
        if (sender.contains("bank", ignoreCase = true) || sender.contains("بانک")) return true
        val count = BANKING_KEYWORDS.count { body.contains(it, ignoreCase = true) }
        return count >= 1
    }

    private fun extractBankName(sender: String, body: String): String {
        val banks = listOf(
            "بانک ملی", "بانک ملت", "بانک صادرات", "بانک تجارت", "بانک سپه",
            "بانک پاسارگاد", "بانک سامان", "بانک آینده", "بانک پارسیان", "بانک مسکن",
            "بانک کشاورزی", "بانک رفاه", "بلوبانک", "بانک رسالت", "بانک شهر"
        )
        for (bank in banks) {
            if (body.contains(bank) || sender.contains(bank)) return bank
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
