package com.moez.QKSMS.feature.smart.promo

import com.moez.QKSMS.common.util.JalaliCalendar
import java.util.regex.Pattern

/** What kind of saving a promo code gives, once the free text has been understood. */
enum class DiscountType {
    PERCENT,
    AMOUNT,
    FREE_SHIPPING,
    UNKNOWN
}

/**
 * A discount amount parsed into something the app can compare and sort on, alongside the
 * Persian text shown on the card.
 *
 * [value] is a percentage for [DiscountType.PERCENT] and Tomans for [DiscountType.AMOUNT].
 */
data class ParsedDiscount(
    val type: DiscountType,
    val value: Long,
    val display: String
)

/** A minimum-basket requirement, in Tomans, with its Persian label. */
data class ParsedMinOrder(
    val value: Long,
    val display: String
)

/**
 * An expiry understood as an absolute instant where possible.
 *
 * [atMillis] is null when the message states no deadline at all; [display] is always safe to
 * show. [isExplicit] distinguishes a deadline the sender actually wrote from a default the app
 * assumed, which the ranker treats very differently.
 */
data class ParsedExpiry(
    val atMillis: Long?,
    val display: String,
    val isExplicit: Boolean
)

/**
 * Turns the free-form Persian text of a promotional SMS into numbers.
 *
 * Everything here is pure Kotlin so it can be unit-tested without an Android device.
 */
object PromoValueParser {

    private const val DAY_MS = 24L * 60 * 60 * 1000L

    val PERSIAN_MONTHS = listOf(
        "فروردین", "اردیبهشت", "خرداد",
        "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر",
        "دی", "بهمن", "اسفند"
    )

    /** Converts Persian/Arabic-Indic digits to ASCII and unifies Arabic letter variants. */
    fun normalize(input: String): String {
        val chars = input
            .replace('ي', 'ی')
            .replace('ك', 'ک')
            .replace('ة', 'ه')
            .replace('ۀ', 'ه')
            .replace('‌', ' ') // ZWNJ -> space, so "اسنپ‌فود" and "اسنپ فود" match alike
            .toCharArray()
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
            if (c in '0'..'9') chars[i] = ('۰' + (c.toInt() - '0'.toInt()))
        }
        return String(chars)
    }

    /** Groups an integer with thousands separators before it is shown to the user. */
    private fun groupDigits(value: Long): String {
        val raw = value.toString()
        val sb = StringBuilder()
        for ((index, ch) in raw.withIndex()) {
            if (index > 0 && (raw.length - index) % 3 == 0) sb.append(',')
            sb.append(ch)
        }
        return sb.toString()
    }

    // ---------------------------------------------------------------- minimum order

    private val MIN_ORDER_PATTERN = Pattern.compile(
        "(?:کف\\s*خرید|کف\\s*سبد|حداقل\\s*خرید|حداقل\\s*سبد|حداقل\\s*سفارش|حداقل\\s*مبلغ|" +
            "سبد\\s*بالای|خرید\\s*بالای|سفارش\\s*بالای|بالای)\\s*:?\\s*" +
            "([0-9]{1,3}(?:,[0-9]{3})+|[0-9]+(?:[./][0-9]+)?)\\s*(میلیون|هزار|تومان|ت|ریال)?",
        Pattern.CASE_INSENSITIVE
    )

    /**
     * Finds a minimum-basket requirement. Returns the parse plus the span it occupied, so the
     * caller can blank it out before looking for the discount amount — otherwise
     * "۳۰٪ تخفیف با حداقل خرید ۲۰۰ هزار تومان" reads the 200,000 as the discount.
     */
    fun parseMinOrder(normalizedBody: String): Pair<ParsedMinOrder, IntRange>? {
        val matcher = MIN_ORDER_PATTERN.matcher(normalizedBody)
        if (!matcher.find()) return null

        val rawNumber = matcher.group(1) ?: return null
        val unit = matcher.group(2) ?: ""
        val value = toTomans(rawNumber, unit) ?: return null

        val display = "حداقل خرید ${toPersianDigits(formatTomans(value))}"
        return ParsedMinOrder(value, display) to IntRange(matcher.start(), matcher.end() - 1)
    }

    /** Renders a Toman figure the way Iranians read it: "۲۰۰ هزار تومان", "۲ میلیون تومان". */
    fun formatTomans(value: Long): String = when {
        value >= 1_000_000L && value % 1_000_000L == 0L ->
            "${value / 1_000_000L} میلیون تومان"
        // 1,500,000 reads as "۱.۵ میلیون", never as "۱۵۰۰ هزار"
        value >= 1_000_000L && value % 100_000L == 0L ->
            "${value / 1_000_000L}.${(value % 1_000_000L) / 100_000L} میلیون تومان"
        value >= 1_000L && value < 1_000_000L && value % 1_000L == 0L ->
            "${value / 1_000L} هزار تومان"
        else -> "${groupDigits(value)} تومان"
    }

    /** Converts a number plus an optional Persian unit into Tomans. */
    private fun toTomans(rawNumber: String, unit: String): Long? {
        val cleaned = rawNumber.replace(",", "").replace("/", ".")
        val base = cleaned.toDoubleOrNull() ?: return null
        val tomans = when {
            unit.contains("میلیون") -> base * 1_000_000
            unit.contains("هزار") -> base * 1_000
            unit.contains("ریال") -> base / 10
            else -> base
        }
        if (tomans <= 0) return null
        return tomans.toLong()
    }

    // ---------------------------------------------------------------- number words

    /**
     * Persian number words that routinely stand in for digits in ad copy
     * ("بیست درصد تخفیف", "صد هزار تومان"). Longest first so "بیست و پنج" beats "بیست".
     */
    private val NUMBER_WORDS: List<Pair<String, Long>> = listOf(
        "بیست و پنج" to 25L, "بیست وپنج" to 25L, "بیست‌وپنج" to 25L,
        "سی و پنج" to 35L, "چهل و پنج" to 45L, "پنجاه و پنج" to 55L,
        "هفتاد و پنج" to 75L, "شصت و پنج" to 65L, "هشتاد و پنج" to 85L,
        "دویست" to 200L, "سیصد" to 300L, "چهارصد" to 400L, "پانصد" to 500L,
        "ششصد" to 600L, "هفتصد" to 700L, "هشتصد" to 800L, "نهصد" to 900L,
        "پانزده" to 15L, "شانزده" to 16L, "هفده" to 17L, "هجده" to 18L, "نوزده" to 19L,
        "یازده" to 11L, "دوازده" to 12L, "سیزده" to 13L, "چهارده" to 14L,
        "چهل" to 40L, "پنجاه" to 50L, "شصت" to 60L, "هفتاد" to 70L,
        "هشتاد" to 80L, "نود" to 90L, "بیست" to 20L, "سی" to 30L,
        "صد" to 100L, "ده" to 10L, "پنج" to 5L
    ).sortedByDescending { it.first.length }

    /**
     * Rewrites spelled-out numbers as digits so the existing patterns can read them.
     *
     * Runs longest-match-first over a consumed-character map for the same reason the brand
     * matcher does: without it, "بیست" inside "بیست و پنج" would rewrite to 20 و 5.
     */
    fun digitizeNumberWords(text: String): String {
        val consumed = BooleanArray(text.length)
        val replacements = ArrayList<Triple<Int, Int, String>>()

        for ((word, value) in NUMBER_WORDS) {
            var idx = text.indexOf(word)
            while (idx >= 0) {
                var overlaps = false
                for (i in idx until idx + word.length) {
                    if (consumed[i]) {
                        overlaps = true
                        break
                    }
                }
                // Only treat it as a number when it stands alone, so "سیصد" is not chopped
                // up and "دهکده" does not turn into "10کده".
                val beforeOk = idx == 0 || !text[idx - 1].isLetter()
                val afterIdx = idx + word.length
                val afterOk = afterIdx >= text.length || !text[afterIdx].isLetter()
                if (!overlaps && beforeOk && afterOk) {
                    for (i in idx until afterIdx) {
                        consumed[i] = true
                    }
                    replacements.add(Triple(idx, afterIdx, value.toString()))
                }
                idx = text.indexOf(word, idx + 1)
            }
        }

        if (replacements.isEmpty()) return text

        replacements.sortBy { it.first }
        val sb = StringBuilder()
        var cursor = 0
        for ((start, end, digits) in replacements) {
            if (start < cursor) continue
            sb.append(text, cursor, start)
            sb.append(digits)
            cursor = end
        }
        sb.append(text, cursor, text.length)
        return sb.toString()
    }

    // ---------------------------------------------------------------- discount amount

    private val PERCENT_PATTERNS = listOf(
        Pattern.compile("([0-9]{1,3})\\s*(?:درصد|٪|%)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?:٪|%)\\s*([0-9]{1,3})", Pattern.CASE_INSENSITIVE)
    )

    private val AMOUNT_PATTERNS = listOf(
        // "۳م تخفیف" — the colloquial "م" for million, only valid next to "تخفیف"
        Pattern.compile("([0-9]+)\\s*م\\s*تخفیف") to "میلیون",
        Pattern.compile("([0-9]+(?:[./][0-9]+)?)\\s*میلیون") to "میلیون",
        Pattern.compile("([0-9]+(?:[./][0-9]+)?)\\s*هزار\\s*تومان") to "هزار",
        Pattern.compile("([0-9]+(?:[./][0-9]+)?)\\s*هزار\\s*توم[ا]?ن?") to "هزار",
        Pattern.compile("([0-9]+(?:[./][0-9]+)?)\\s*(?:هزار|هزارتومان|هزارتومن)") to "هزار",
        // "+70ت تخفیف" / "70ت تخفیف" — "ت" here abbreviates thousand Tomans, not Toman
        Pattern.compile("\\+?([0-9]{2,4})\\s*ت\\s*تخفیف") to "هزار",
        Pattern.compile("([0-9]{1,3}(?:,[0-9]{3})+)\\s*ریال") to "ریال",
        Pattern.compile("([0-9]{1,3}(?:,[0-9]{3})+)\\s*(?:تومان|تومن|ت\\b)") to "تومان",
        Pattern.compile("([0-9]{1,3}(?:,[0-9]{3})+)") to "تومان"
    )

    /**
     * Reads the headline saving out of the message.
     *
     * [minOrderSpan] marks a minimum-basket figure already found, which is excluded so it is
     * not mistaken for the discount itself.
     */
    fun parseDiscount(normalizedBody: String, minOrderSpan: IntRange? = null): ParsedDiscount {
        val masked = if (minOrderSpan == null) {
            normalizedBody
        } else {
            val prefix = normalizedBody.substring(0, minOrderSpan.first)
            val suffix = normalizedBody.substring(minOrderSpan.last + 1)
            "$prefix $suffix"
        }
        val text = digitizeNumberWords(masked)

        for (pattern in PERCENT_PATTERNS) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                val percent = matcher.group(1)?.toLongOrNull()
                // 0% and >100% are almost always a code fragment or a phone number, not a discount
                if (percent != null && percent in 1..100) {
                    return ParsedDiscount(DiscountType.PERCENT, percent, "${toPersianDigits(percent.toString())}٪")
                }
            }
        }

        for ((pattern, unit) in AMOUNT_PATTERNS) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                val value = toTomans(matcher.group(1) ?: continue, unit) ?: continue
                // Below 1000 Tomans is noise (a code fragment, a year, an item count)
                if (value < 1_000L) continue
                return ParsedDiscount(DiscountType.AMOUNT, value, toPersianDigits(formatTomans(value)))
            }
        }

        if (text.contains("ارسال رایگان")) {
            return ParsedDiscount(DiscountType.FREE_SHIPPING, 0L, "ارسال رایگان")
        }

        return ParsedDiscount(DiscountType.UNKNOWN, 0L, "تخفیف ویژه")
    }

    // ---------------------------------------------------------------- expiry

    private val EXPLICIT_DATE_PATTERN =
        Pattern.compile("(?:13|14)?(\\d{2})\\s*[/-]\\s*(\\d{1,2})\\s*[/-]\\s*(\\d{1,2})")

    private val MONTH_NAME_PATTERN = Pattern.compile(
        "(\\d{1,2})\\s*(فروردین|اردیبهشت|خرداد|تیر|مرداد|شهریور|مهر|آبان|آذر|دی|بهمن|اسفند)"
    )

    private val DURATION_PATTERN =
        Pattern.compile("(?:تا\\s*)?([0-9]{1,3})\\s*(ساعت|روز|هفته)")

    /**
     * Works out when the offer stops being usable.
     *
     * Unlike the previous implementation this searches the *whole* message, not just a
     * pre-extracted expiry phrase, so a deadline written only in the body is still honoured.
     * When nothing is stated it falls back to [defaultValidityDays] and marks the result
     * non-explicit, so the UI can say "assumed" rather than inventing a hard date.
     */
    fun parseExpiry(
        normalizedBody: String,
        receivedAt: Long,
        defaultValidityDays: Int = DEFAULT_VALIDITY_DAYS
    ): ParsedExpiry {
        val received = JalaliCalendar.fromMillis(receivedAt)

        // 1. An explicit Jalali date anywhere in the message: 1404/07/05, 04-07-05
        val dateMatcher = EXPLICIT_DATE_PATTERN.matcher(normalizedBody)
        while (dateMatcher.find()) {
            val rawYear = dateMatcher.group(1)?.toIntOrNull() ?: continue
            val month = dateMatcher.group(2)?.toIntOrNull() ?: continue
            val day = dateMatcher.group(3)?.toIntOrNull() ?: continue
            if (month !in 1..12 || day !in 1..31) continue
            val year = if (rawYear < 100) 1400 + rawYear else rawYear
            // Guard against matching an unrelated number run far from the received year
            if (year < received.year || year > received.year + 2) continue
            val millis = JalaliCalendar.toMillis(year, month, day, endOfDay = true)
            val display = toPersianDigits("$year/${pad(month)}/${pad(day)}")
            return ParsedExpiry(millis, display, isExplicit = true)
        }

        // 2. A Persian month name: "تا ۵ مهر"
        val monthMatcher = MONTH_NAME_PATTERN.matcher(normalizedBody)
        if (monthMatcher.find()) {
            val day = monthMatcher.group(1)?.toIntOrNull() ?: 0
            val monthIdx = PERSIAN_MONTHS.indexOf(monthMatcher.group(2) ?: "")
            if (day in 1..31 && monthIdx != -1) {
                val month = monthIdx + 1
                // A month earlier than the one we received in must mean next year
                val year = if (month >= received.month) received.year else received.year + 1
                val millis = JalaliCalendar.toMillis(year, month, day, endOfDay = true)
                val display = "$day ${PERSIAN_MONTHS[monthIdx]}"
                return ParsedExpiry(millis, toPersianDigits(display), isExplicit = true)
            }
        }

        // 3. End-of-day wording: "تا پایان امشب", "فقط امروز", "تا ساعت ۲۴"
        if (normalizedBody.contains("امشب") || normalizedBody.contains("امروز") ||
            normalizedBody.contains("ساعت 24") || normalizedBody.contains("پایان روز")
        ) {
            val millis = JalaliCalendar.toMillis(received.year, received.month, received.day, endOfDay = true)
            return ParsedExpiry(millis, "تا پایان امشب", isExplicit = true)
        }

        // 4. "فردا" — end of the day after the message arrived
        if (normalizedBody.contains("فردا")) {
            val millis = JalaliCalendar.toMillis(received.year, received.month, received.day, endOfDay = true) + DAY_MS
            return ParsedExpiry(millis, "تا فردا شب", isExplicit = true)
        }

        // 5. A relative window: "۴۸ ساعت", "۳ روز", "۱ هفته"
        val durationMatcher = DURATION_PATTERN.matcher(normalizedBody)
        if (durationMatcher.find()) {
            val count = durationMatcher.group(1)?.toLongOrNull() ?: 0L
            val unit = durationMatcher.group(2) ?: ""
            if (count in 1..365) {
                val millis = when (unit) {
                    "ساعت" -> receivedAt + count * 60 * 60 * 1000L
                    "روز" -> receivedAt + count * DAY_MS
                    "هفته" -> receivedAt + count * 7 * DAY_MS
                    else -> null
                }
                if (millis != null) {
                    val display = "تا ${toPersianDigits(count.toString())} $unit"
                    return ParsedExpiry(millis, display, isExplicit = true)
                }
            }
        }
        if (normalizedBody.contains("یک هفته")) {
            return ParsedExpiry(receivedAt + 7 * DAY_MS, "تا ۱ هفته", isExplicit = true)
        }

        // 6. Nothing stated — assume a default window and say so.
        return ParsedExpiry(
            receivedAt + defaultValidityDays * DAY_MS,
            "مهلت اعلام نشده",
            isExplicit = false
        )
    }

    private fun pad(value: Int): String = if (value < 10) "0$value" else value.toString()

    /**
     * How long an undated code is assumed to stay usable. A single constant so the extractor,
     * the expiry check and the card all agree — previously they used 7, 30 and 30 days.
     *
     * A week, not a month: Iranian promotional codes are typically short-lived, so assuming a
     * month keeps dead codes on screen far longer than they are worth.
     */
    const val DEFAULT_VALIDITY_DAYS = 7
}
