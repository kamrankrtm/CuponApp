package com.cuponapp.smsdiscounts

/**
 * دسته‌بندی محلی پیامک — نسخه کاتلین.
 *
 * وقتی پیامک تازه‌ای می‌رسد ممکن است اپ اصلاً باز نباشد، یعنی WebView و
 * موتور فیلتر جاوااسکریپتی در دسترس نیستند. برای همین همان منطق اینجا
 * دوباره پیاده شده است. هر تغییری در src/lib/smsFilter.ts باید اینجا هم
 * اعمال شود، وگرنه رفتار اسکن دستی و دریافت خودکار از هم جدا می‌افتد.
 */
object SmsClassifier {

    enum class Kind { PROMOTIONAL, PERSONAL, BANKING }

    data class Result(
        val kind: Kind,
        val safeToSend: Boolean,
        val score: Int,
        val reason: String
    )

    /** رمز یک‌بارمصرف و کد تایید — حساس‌ترین دسته، هرگز ارسال نمی‌شوند */
    private val OTP = listOf(
        Regex("کد\\s*(?:تایید|تأیید|ورود|فعال\\s*سازی|احراز|امنیتی|یکبار|یک\\s*بار)"),
        Regex("رمز\\s*(?:عبور|ورود|موقت|یکبار|یک\\s*بار)"),
        Regex("verification\\s*code", RegexOption.IGNORE_CASE),
        Regex("one[-\\s]?time\\s*(?:password|code)", RegexOption.IGNORE_CASE),
        Regex("\\bOTP\\b", RegexOption.IGNORE_CASE)
    )

    /** پیامک بانکی و تراکنش مالی */
    private val BANKING = listOf(
        Regex("رمز\\s*(?:پویا|دوم)"),
        Regex("مانده[:\\s]"),
        Regex("موجودی"),
        Regex("واریز\\s*(?:به|:)"),
        Regex("برداشت\\s*(?:از|:)"),
        Regex("انتقال\\s*وجه"),
        Regex("شماره\\s*(?:شبا|حساب|کارت)"),
        Regex("\\bشبا\\b"),
        Regex("\\bIR\\d{20,}\\b"),
        Regex("صورتحساب"),
        Regex("چک\\s*(?:برگشتی|صیادی)"),
        Regex("تسهیلات|اقساط|وام"),
        Regex("بانک\\s*(?:ملت|ملی|صادرات|تجارت|سپه|پاسارگاد|سامان|پارسیان|رفاه|کشاورزی|مسکن|شهر|دی|سینا|اقتصاد|آینده|قوامین)"),
        Regex("(?:بدهکار|بستانکار)"),
        Regex("سامانه\\s*پیامکی\\s*بانک")
    )

    private val PERSONAL_SENDER = Regex("^(?:\\+?98|0098|0)?9\\d{9}$")

    /** نشانه‌های تخفیف با وزن؛ آینه‌ی دقیق SIGNALS در سمت جاوااسکریپت */
    private val SIGNALS: List<Pair<Regex, Int>> = listOf(
        Regex("تخفیف") to 3,
        Regex("کوپن") to 3,
        Regex("حراج") to 3,
        Regex("کش\\s*بک|cashback", RegexOption.IGNORE_CASE) to 3,
        Regex("بن\\s*(?:خرید|تخفیف)") to 3,
        Regex("\\bآف\\b|\\boff\\b", RegexOption.IGNORE_CASE) to 3,
        Regex("کد\\s*(?:تخفیف|هدیه|معرف|ترویجی)") to 3,

        Regex("جشنواره") to 2,
        Regex("پیشنهاد\\s*(?:ویژه|شگفت|لحظه)") to 2,
        Regex("شگفت\\s*انگیز") to 2,
        Regex("فروش\\s*ویژه") to 2,
        Regex("هدیه") to 2,
        Regex("رایگان") to 2,
        Regex("اعتبار\\s*(?:هدیه|رایگان)") to 2,
        Regex("\\d+\\s*(?:٪|درصد|%)") to 2,
        Regex("نیم\\s*بها|نصف\\s*قیمت") to 2,

        Regex("\\d{1,3}(?:[,،]\\d{3})+\\s*(?:تومان|ریال)") to 1,
        Regex("\\d+\\s*هزار\\s*تومان") to 1,
        Regex("(?:^|\\s)کد[:\\s]+[A-Za-z0-9]{3,15}(?:\\s|$|\\.)") to 1,
        Regex("\\b[A-Z]{3,}[0-9]{1,4}\\b") to 1,
        Regex("https?://|\\b\\w+\\.(?:ir|com|co)\\b", RegexOption.IGNORE_CASE) to 1,
        Regex("خرید\\s*(?:کنید|کن)|سفارش\\s*(?:دهید|بده)") to 1,
        Regex("مهلت|تا\\s*پایان|فقط\\s*امروز|فقط\\s*تا") to 1,
        Regex("لغو\\s*(?:11|۱۱)") to 1,
        Regex(
            "اسنپ|تپسی|دیجی\\s*کالا|فیلیمو|نماوا|باسلام|اکالا|okala|digikala|snapp|tapsi|filimo|torob|ترب|بانی\\s*مد|زرین|علی\\s*بابا|مقصد|شیپور|دیوار",
            RegexOption.IGNORE_CASE
        ) to 1
    )

    /** آستانه امتیاز به ازای هر سطح سخت‌گیری */
    fun thresholdFor(strictness: String?): Int = when (strictness) {
        "relaxed" -> 2
        "strict" -> 5
        else -> 3
    }

    private const val MIN_BODY_LENGTH = 25

    /** تبدیل ارقام فارسی و عربی به لاتین تا الگوها روی هر دو کار کنند */
    fun normalizeDigits(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            sb.append(
                when (ch) {
                    in '۰'..'۹' -> ('0' + (ch - '۰'))
                    in '٠'..'٩' -> ('0' + (ch - '٠'))
                    else -> ch
                }
            )
        }
        return sb.toString()
    }

    private fun matchesAny(patterns: List<Regex>, text: String) =
        patterns.any { it.containsMatchIn(text) }

    private fun isPersonalSender(sender: String): Boolean {
        val cleaned = normalizeDigits(sender).replace(Regex("[\\s\\-()]"), "")
        return PERSONAL_SENDER.matches(cleaned)
    }

    fun score(text: String): Int = SIGNALS.sumOf { (re, w) -> if (re.containsMatchIn(text)) w else 0 }

    /**
     * دسته‌بندی یک پیامک. ترتیب بررسی عمدی است: ابتدا موارد حساس کنار
     * گذاشته می‌شوند، سپس پیام شخصی، و در آخر امتیاز تخفیف.
     */
    fun classify(sender: String, body: String, strictness: String?): Result {
        val text = normalizeDigits(body)

        if (matchesAny(OTP, text)) {
            return Result(Kind.BANKING, false, 0, "رمز یک‌بارمصرف یا کد تایید")
        }
        if (matchesAny(BANKING, text)) {
            return Result(Kind.BANKING, false, 0, "پیامک بانکی")
        }

        val s = score(text)
        val threshold = thresholdFor(strictness)

        if (isPersonalSender(sender)) {
            return if (s >= maxOf(threshold, 3)) {
                Result(Kind.PROMOTIONAL, true, s, "شماره شخصی با نشانه قوی تخفیف")
            } else {
                Result(Kind.PERSONAL, false, s, "پیام شخصی")
            }
        }

        if (text.trim().length < MIN_BODY_LENGTH) {
            return Result(Kind.PROMOTIONAL, false, s, "متن کوتاه‌تر از حد لازم")
        }

        return if (s >= threshold) {
            Result(Kind.PROMOTIONAL, true, s, "نشانه تخفیف، امتیاز $s")
        } else {
            Result(Kind.PROMOTIONAL, false, s, "امتیاز $s کمتر از آستانه $threshold")
        }
    }
}
