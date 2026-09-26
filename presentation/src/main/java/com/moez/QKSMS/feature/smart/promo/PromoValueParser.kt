package com.moez.QKSMS.feature.smart.promo

import com.moez.QKSMS.common.util.JalaliCalendar
import java.util.Calendar
import java.util.regex.Matcher
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
 * [isCashback] marks money paid back after the purchase rather than taken off the price.
 */
data class ParsedDiscount(
    val type: DiscountType,
    val value: Long,
    val display: String,
    val isCashback: Boolean = false
)

/** A minimum-basket requirement, in Tomans, with its Persian label. */
data class ParsedMinOrder(
    val value: Long,
    val display: String
)

/** The most a percentage code takes off ("تا سقف ۵۰ هزار تومان"), in Tomans. */
data class ParsedCap(
    val value: Long,
    val display: String
)

/** Everything a message says about what a code is worth, read in one pass. */
data class ParsedValues(
    val discount: ParsedDiscount,
    val minOrder: ParsedMinOrder?,
    val cap: ParsedCap?
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

    private const val HOUR_MS = 60L * 60 * 1000L
    private const val DAY_MS = 24L * HOUR_MS

    val PERSIAN_MONTHS = listOf(
        "فروردین", "اردیبهشت", "خرداد",
        "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر",
        "دی", "بهمن", "اسفند"
    )

    /**
     * Brings the many ways Persian text is typed down to one spelling.
     *
     * Arabic letter forms become Persian, every digit becomes ASCII, the zero-width joiner
     * becomes a space and the invisible formatting marks, kashida ("تخفیـــف") and runs of
     * spaces disappear. Ads use all of these, and each one used to break a keyword match:
     * "اسنپ‌ فود" with a joiner and a space never matched "اسنپ فود".
     */
    fun normalize(input: String): String {
        val sb = StringBuilder(input.length)
        for (raw in input) {
            val c: Char? = when (raw) {
                'ي', 'ى' -> 'ی'
                'ك' -> 'ک'
                'ة', 'ۀ' -> 'ه'
                'أ', 'إ', 'ٱ' -> 'ا'
                '٬' -> ','
                '٫' -> '.'
                '\u200c', '\u00a0', '\u2002', '\u2003', '\u2007', '\u2009', '\u200a', '\u202f',
                '\u3000', '\t' -> ' '
                'ـ', '\r', '\u200b', '\u200d', '\u200e', '\u200f', '\u202a', '\u202b', '\u202c',
                '\u202d', '\u202e', '\u2066', '\u2067', '\u2068', '\u2069', '\ufeff', '\u00ad' -> null
                in '۰'..'۹' -> '0' + (raw - '۰')
                in '٠'..'٩' -> '0' + (raw - '٠')
                else -> raw
            }
            if (c == null) continue
            if (c == ' ' && (sb.isEmpty() || sb[sb.length - 1] == ' ')) continue
            sb.append(c)
        }
        return sb.toString()
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

    // ---------------------------------------------------------------- number words

    private val NUMBER_WORDS: Map<String, Double> = mapOf(
        "یک" to 1.0, "دو" to 2.0, "سه" to 3.0, "چهار" to 4.0, "پنج" to 5.0,
        "شش" to 6.0, "شیش" to 6.0, "هفت" to 7.0, "هشت" to 8.0, "نه" to 9.0,
        "ده" to 10.0, "یازده" to 11.0, "دوازده" to 12.0, "سیزده" to 13.0, "چهارده" to 14.0,
        "پانزده" to 15.0, "پونزده" to 15.0, "شانزده" to 16.0, "هفده" to 17.0, "هیفده" to 17.0,
        "هجده" to 18.0, "هیجده" to 18.0, "نوزده" to 19.0,
        "بیست" to 20.0, "سی" to 30.0, "چهل" to 40.0, "پنجاه" to 50.0, "شصت" to 60.0,
        "هفتاد" to 70.0, "هشتاد" to 80.0, "نود" to 90.0,
        "صد" to 100.0, "یکصد" to 100.0, "دویست" to 200.0, "سیصد" to 300.0, "چهارصد" to 400.0,
        "پانصد" to 500.0, "پونصد" to 500.0, "ششصد" to 600.0, "هفتصد" to 700.0,
        "هشتصد" to 800.0, "نهصد" to 900.0,
        "نیم" to 0.5
    )

    private val WORD = Pattern.compile("\\p{L}+")

    /** The next part of a spelled-out number may only fill a smaller place: "صد و بیست و پنج". */
    private fun canExtend(total: Double, next: Double): Boolean = when {
        next == 0.5 -> total >= 1.0 && total < 1000.0 && total % 1.0 == 0.0
        total % 100.0 == 0.0 -> next < 100.0
        total % 10.0 == 0.0 -> next < 10.0
        else -> false
    }

    private fun numberWord(word: String): Double? = NUMBER_WORDS[word]

    /**
     * Rewrites spelled-out numbers as digits so the value patterns can read them:
     * "بیست و پنج درصد" → "25 درصد", "صد و پنجاه هزار" → "150 هزار", "یک و نیم میلیون" →
     * "1.5 میلیون". The scale words (هزار, میلیون) are kept, because the amount patterns
     * already understand them.
     *
     * A run is joined only while each part fills a smaller place, so "دو و سه" stays two
     * numbers, and whole words only, so "دهکده" never turns into "10کده".
     */
    fun digitizeNumberWords(text: String): String {
        val words = ArrayList<Triple<Int, Int, String>>()
        val matcher = WORD.matcher(text)
        while (matcher.find()) words.add(Triple(matcher.start(), matcher.end(), matcher.group()))
        if (words.isEmpty()) return text

        val sb = StringBuilder()
        var cursor = 0
        var i = 0
        while (i < words.size) {
            val (start, end, word) = words[i]
            val first = numberWord(word)
            // A bare "نیم" (half) only counts in front of a scale: "نیم میلیون"
            if (first == null || (first == 0.5 && !followedByScale(text, end))) {
                i++
                continue
            }

            var total = first
            var runEnd = end
            var j = i + 1
            while (j < words.size) {
                val (wStart, wEnd, w) = words[j]
                if (!onlySpaces(text, runEnd, wStart)) break
                // "و پنج" as two words, or "وپنج" glued together
                val next: Double?
                val nextEnd: Int
                if (w == "و" && j + 1 < words.size && onlySpaces(text, wEnd, words[j + 1].first)) {
                    next = numberWord(words[j + 1].third)
                    nextEnd = words[j + 1].second
                    if (next == null || !canExtend(total, next)) break
                    j += 2
                } else if (w.length > 1 && w[0] == 'و' && numberWord(w.substring(1)) != null) {
                    next = numberWord(w.substring(1))
                    nextEnd = wEnd
                    if (next == null || !canExtend(total, next)) break
                    j += 1
                } else {
                    break
                }
                total += next
                runEnd = nextEnd
            }

            sb.append(text, cursor, start)
            sb.append(if (total % 1.0 == 0.0) total.toLong().toString() else total.toString())
            cursor = runEnd
            i = j
        }
        sb.append(text, cursor, text.length)
        return sb.toString()
    }

    private fun onlySpaces(text: String, from: Int, to: Int): Boolean {
        if (from > to) return false
        for (k in from until to) if (text[k] != ' ') return false
        return true
    }

    private fun followedByScale(text: String, from: Int): Boolean {
        val rest = text.substring(from).trimStart()
        return rest.startsWith("میلیون") || rest.startsWith("هزار")
    }

    // ---------------------------------------------------------------- shared number reading

    private const val NUMBER = "(?<![A-Za-z0-9.,/])(\\d{1,3}(?:,\\d{3})+|\\d+(?:[./]\\d+)?)"
    private const val UNIT = "(میلیون\\s*(?:تومان|تومن|ریال)?|هزار\\s*(?:تومان|تومن|ریال)?|تومان|تومن|ت(?![\\p{L}])|ریال)?"

    /** Converts a number plus an optional Persian unit into Tomans. */
    private fun toTomans(rawNumber: String, unit: String): Long? {
        val cleaned = rawNumber.replace(",", "").replace("/", ".")
        val base = cleaned.toDoubleOrNull() ?: return null
        val tomans = when {
            unit.contains("میلیون") && unit.contains("ریال") -> base * 100_000
            unit.contains("میلیون") -> base * 1_000_000
            unit.contains("هزار") && unit.contains("ریال") -> base * 100
            unit.contains("هزار") -> base * 1_000
            unit.contains("ریال") -> base / 10
            // In ad copy a bare "ت" after a small number means thousand Tomans: "۵۰ت تخفیف"
            unit == "ت" && base < 10_000 -> base * 1_000
            // "حداقل خرید ۲۰۰" with no unit is two hundred thousand, never two hundred Tomans
            unit.isEmpty() && base < 1_000 -> base * 1_000
            else -> base
        }
        if (tomans <= 0) return null
        return Math.round(tomans)
    }

    private fun followedByPercent(text: String, from: Int): Boolean {
        val rest = text.substring(from, minOf(text.length, from + 8)).trimStart()
        return rest.startsWith("درصد") || rest.startsWith("٪") || rest.startsWith("%")
    }

    // ---------------------------------------------------------------- minimum order

    private val MIN_ORDER_PATTERN = Pattern.compile(
        "(?:" +
            "(?:کف|حداقل|حد\\s*اقل)\\s*(?:مبلغ\\s*)?(?:خرید|سبد|سفارش|فاکتور|مبلغ)(?:\\s*(?:های|ها|شما|تان))?" +
            "|(?:خرید|سفارش|سبد|فاکتور)(?:\\s*(?:های|ها))?\\s*(?:با\\s*مبلغ\\s*)?(?:بالای|بیش\\s*از|بیشتر\\s*از|بالاتر\\s*از)" +
            "|(?<![\\p{L}])(?:بالای)" +
            ")\\s*:?[^\\d\\n]{0,15}?" + NUMBER + "\\s*" + UNIT
    )

    /**
     * Finds a minimum-basket requirement. Returns the parse plus the span it occupied, so the
     * caller can blank it out before looking for the discount amount — otherwise
     * "۳۰٪ تخفیف با حداقل خرید ۲۰۰ هزار تومان" reads the 200,000 as the discount.
     */
    fun parseMinOrder(normalizedBody: String): Pair<ParsedMinOrder, IntRange>? {
        val text = digitizeNumberWords(normalizedBody)
        val matcher = MIN_ORDER_PATTERN.matcher(text)
        while (matcher.find()) {
            val rawNumber = matcher.group(1) ?: continue
            val unit = (matcher.group(2) ?: "").replace(" ", "")
            if (followedByPercent(text, matcher.end(1))) continue
            // A bare "بالای" is also "over 10,000 users"; only a stated price makes it a basket
            val bareTrigger = matcher.group().trimStart().startsWith("بالای")
            if (bareTrigger && !(unit.contains("تومان") || unit.contains("تومن") || unit == "ت" || unit.contains("ریال"))) {
                continue
            }
            val value = toTomans(rawNumber, unit) ?: continue
            if (value < 1_000L) continue
            val display = "حداقل خرید ${toPersianDigits(formatTomans(value))}"
            return ParsedMinOrder(value, display) to IntRange(matcher.start(), matcher.end() - 1)
        }
        return null
    }

    // ---------------------------------------------------------------- cap

    private val CAP_PATTERN = Pattern.compile(
        "(?:تا\\s*سقف|سقف(?:\\s*تخفیف)?|حداکثر(?:\\s*(?:مبلغ\\s*)?تخفیف)?(?:\\s*تا)?|تا\\s*حداکثر)" +
            "\\s*:?\\s*" + NUMBER + "\\s*" + UNIT
    )

    /** "۳۰٪ تخفیف تا سقف ۵۰ هزار تومان": the cap, and the span to keep out of the discount search. */
    fun parseCap(normalizedBody: String): Pair<ParsedCap, IntRange>? {
        val text = digitizeNumberWords(normalizedBody)
        val matcher = CAP_PATTERN.matcher(text)
        while (matcher.find()) {
            val rawNumber = matcher.group(1) ?: continue
            val unit = (matcher.group(2) ?: "").replace(" ", "")
            if (followedByPercent(text, matcher.end(1))) continue
            val value = toTomans(rawNumber, unit) ?: continue
            if (value < 1_000L) continue
            val display = "تا سقف ${toPersianDigits(formatTomans(value))}"
            return ParsedCap(value, display) to IntRange(matcher.start(), matcher.end() - 1)
        }
        return null
    }

    // ---------------------------------------------------------------- discount amount

    private val PERCENT_PATTERNS = listOf(
        Pattern.compile("(?<![A-Za-z0-9.])(\\d{1,3})(?:\\.\\d+)?\\s*(?:درصد|٪|%)"),
        Pattern.compile("(?:٪|%)\\s*(\\d{1,3})(?![\\d])")
    )

    private enum class AmountKind { MILLION_THOUSAND, SCALED, COLLOQUIAL_T, RIAL, TOMAN, BARE }

    private val AMOUNT_PATTERNS: List<Pair<Pattern, AmountKind>> = listOf(
        Pattern.compile("(?<![A-Za-z0-9])(\\d+(?:\\.\\d+)?)\\s*میلیون\\s*و\\s*(\\d+)\\s*هزار(\\s*ریال)?") to AmountKind.MILLION_THOUSAND,
        // "۳م تخفیف" — the colloquial "م" for million, only valid next to "تخفیف"
        Pattern.compile("(?<![A-Za-z0-9])(\\d+)\\s*(م)\\s*تخفیف") to AmountKind.SCALED,
        Pattern.compile("(?<![A-Za-z0-9.,/])(\\d+(?:[./]\\d+)?)\\s*(میلیون(?:\\s*ریال)?)") to AmountKind.SCALED,
        Pattern.compile("(?<![A-Za-z0-9.,/])(\\d+(?:[./]\\d+)?)\\s*(هزار(?:\\s*ریال)?)") to AmountKind.SCALED,
        // "+70ت تخفیف" / "70ت" — "ت" here abbreviates thousand Tomans, not Toman
        Pattern.compile("(?<![A-Za-z0-9.,/])\\+?(\\d{1,4})\\s*ت(?![\\p{L}])") to AmountKind.COLLOQUIAL_T,
        Pattern.compile("(?<![A-Za-z0-9.,/])(\\d{1,3}(?:,\\d{3})+|\\d{4,})\\s*ریال") to AmountKind.RIAL,
        Pattern.compile("(?<![A-Za-z0-9.,/])(\\d{1,3}(?:,\\d{3})+|\\d{4,})\\s*(?:تومان|تومن)") to AmountKind.TOMAN,
        Pattern.compile("(?<![A-Za-z0-9.,/])(\\d{1,3}(?:,\\d{3})+)(?![\\d,])") to AmountKind.BARE
    )

    /** Words a discount figure sits next to; a number far from all of them is probably a price. */
    private val DISCOUNT_WORDS = listOf(
        "تخفیف", "off", "هدیه", "اعتبار", "بن خرید", "کش بک", "کشبک", "cashback", "بازگشت وجه",
        "discount", "رایگان", "جایزه"
    )

    private val CASHBACK_WORDS = listOf("کش بک", "کشبک", "cashback", "بازگشت وجه", "برگشت پول")

    private val FREE_SHIPPING_WORDS = listOf(
        "ارسال رایگان", "ارسال مجانی", "پیک رایگان", "هزینه ارسال رایگان", "ارسال رایگان",
        "free delivery", "free shipping"
    )

    private data class ValueCandidate(
        val type: DiscountType,
        val value: Long,
        val start: Int,
        val end: Int,
        val base: Int
    )

    /**
     * Reads the headline saving out of the message.
     *
     * [minOrderSpan] marks a minimum-basket figure already found, which is excluded so it is
     * not mistaken for the discount itself.
     */
    fun parseDiscount(normalizedBody: String, minOrderSpan: IntRange? = null): ParsedDiscount {
        val excluded = if (minOrderSpan == null) emptyList() else listOf(minOrderSpan)
        return pickDiscount(digitizeNumberWords(normalizedBody), excluded, -1)
    }

    /**
     * Reads discount, minimum order and cap together, so each figure is claimed once.
     *
     * @param anchor position of the code in [normalizedBody], or -1; figures near the code
     *   win when a message quotes several
     */
    fun parseValues(normalizedBody: String, anchor: Int = -1): ParsedValues {
        val text = digitizeNumberWords(normalizedBody)
        val minOrder = parseMinOrder(text)
        val cap = parseCap(text)
        val excluded = listOfNotNull(minOrder?.second, cap?.second)
        // The anchor was measured before number words were rewritten; close enough to rank by
        val discount = pickDiscount(text, excluded, anchor)
        // A cap only means something next to a percentage
        val usefulCap = if (discount.type == DiscountType.PERCENT) cap?.first else null
        return ParsedValues(discount, minOrder?.first, usefulCap)
    }

    private fun pickDiscount(text: String, excluded: List<IntRange>, anchor: Int): ParsedDiscount {
        val candidates = ArrayList<ValueCandidate>()
        fun overlapsTaken(start: Int, end: Int): Boolean =
            excluded.any { start <= it.last && end - 1 >= it.first } ||
                candidates.any { start < it.end && end > it.start }

        for (pattern in PERCENT_PATTERNS) {
            val m = pattern.matcher(text)
            while (m.find()) {
                val percent = m.group(1)?.toLongOrNull() ?: continue
                // 0% and >100% are almost always a code fragment or a phone number
                if (percent !in 1..100) continue
                if (overlapsTaken(m.start(), m.end())) continue
                candidates.add(ValueCandidate(DiscountType.PERCENT, percent, m.start(), m.end(), 50))
            }
        }

        for ((pattern, kind) in AMOUNT_PATTERNS) {
            val m = pattern.matcher(text)
            while (m.find()) {
                if (overlapsTaken(m.start(), m.end())) continue
                if (followedByPercent(text, m.end())) continue
                val value = amountOf(m, kind) ?: continue
                // Below 1000 Tomans is noise (a code fragment, a year, an item count)
                if (value < 1_000L) continue
                val base = when (kind) {
                    AmountKind.BARE -> 20
                    AmountKind.COLLOQUIAL_T -> 40
                    else -> 45
                }
                candidates.add(ValueCandidate(DiscountType.AMOUNT, value, m.start(), m.end(), base))
            }
        }

        val keywordSpans = spansOf(text.toLowerCase(), DISCOUNT_WORDS)
        var best: ValueCandidate? = null
        var bestScore = Int.MIN_VALUE
        var bestKeyword: String? = null
        for (candidate in candidates) {
            var score = candidate.base
            var nearest = Int.MAX_VALUE
            var nearestWord: String? = null
            for ((span, word) in keywordSpans) {
                val distance = distance(candidate.start, candidate.end, span.first, span.last + 1)
                if (distance < nearest) {
                    nearest = distance
                    nearestWord = word
                }
            }
            score += when {
                nearest <= 25 -> 40
                nearest <= 60 -> 20
                nearest <= 120 -> 5
                else -> 0
            }
            if (anchor >= 0 && distance(candidate.start, candidate.end, anchor, anchor + 1) <= 60) score += 10

            // "قیمت ۱۲ میلیون", "از ۹۹ هزار تومان": a price, not a saving
            val before = text.substring(maxOf(0, candidate.start - 14), candidate.start)
            if (before.contains("قیمت")) score -= 35
            if (before.trimEnd().endsWith(" از") || before.trimEnd() == "از") score -= 15
            if (before.contains("فقط")) score -= 10

            if (score > bestScore) {
                bestScore = score
                best = candidate
                bestKeyword = nearestWord
            }
        }

        val chosen = best
        if (chosen != null && bestScore >= 45) {
            val cashback = bestKeyword != null && CASHBACK_WORDS.contains(bestKeyword)
            return if (chosen.type == DiscountType.PERCENT) {
                ParsedDiscount(DiscountType.PERCENT, chosen.value, "${toPersianDigits(chosen.value.toString())}٪", cashback)
            } else {
                ParsedDiscount(DiscountType.AMOUNT, chosen.value, toPersianDigits(formatTomans(chosen.value)), cashback)
            }
        }

        val lower = text.toLowerCase()
        if (FREE_SHIPPING_WORDS.any { lower.contains(it) }) {
            return ParsedDiscount(DiscountType.FREE_SHIPPING, 0L, "ارسال رایگان")
        }

        return ParsedDiscount(DiscountType.UNKNOWN, 0L, "تخفیف ویژه")
    }

    private fun amountOf(m: Matcher, kind: AmountKind): Long? {
        val number = m.group(1) ?: return null
        return when (kind) {
            AmountKind.MILLION_THOUSAND -> {
                val millions = number.toDoubleOrNull() ?: return null
                val thousands = m.group(2)?.toLongOrNull() ?: return null
                val tomans = millions * 1_000_000 + thousands * 1_000
                val rial = m.group(3) != null
                Math.round(if (rial) tomans / 10 else tomans)
            }
            AmountKind.SCALED -> {
                val unit = m.group(2) ?: ""
                if (unit == "م") toTomans(number, "میلیون") else toTomans(number, unit.replace(" ", ""))
            }
            AmountKind.COLLOQUIAL_T -> toTomans(number, "ت")
            AmountKind.RIAL -> toTomans(number, "ریال")
            AmountKind.TOMAN, AmountKind.BARE -> toTomans(number, "تومان")
        }
    }

    private fun spansOf(text: String, words: List<String>): List<Pair<IntRange, String>> {
        val spans = ArrayList<Pair<IntRange, String>>()
        for (word in words) {
            var idx = text.indexOf(word)
            while (idx >= 0) {
                spans.add(IntRange(idx, idx + word.length - 1) to word)
                idx = text.indexOf(word, idx + 1)
            }
        }
        return spans
    }

    private fun distance(aStart: Int, aEnd: Int, bStart: Int, bEnd: Int): Int = when {
        aEnd <= bStart -> bStart - aEnd
        bEnd <= aStart -> aStart - bEnd
        else -> 0
    }

    // ---------------------------------------------------------------- expiry

    private val EXPLICIT_DATE_PATTERN =
        Pattern.compile("(?<![\\d/])(?:13|14)?(\\d{2})\\s*[/-]\\s*(\\d{1,2})\\s*[/-]\\s*(\\d{1,2})(?![\\d/])")

    private const val MONTHS = "(فروردین|اردیبهشت|خرداد|تیر|مرداد|شهریور|مهر|آبان|آذر|دی|بهمن|اسفند)"

    /** "۵ مهر", "۱۵ام آبان", "۱۰ آذرماه" — the month must stand alone, so "۲۰ دیجی‌کالا" is not Dey. */
    private val MONTH_NAME_PATTERN = Pattern.compile(
        "(?<![\\d])(\\d{1,2})\\s*(?:ام|م)?\\s*$MONTHS(?:\\s*ماه)?(?![\\p{L}])"
    )

    /** "تا پایان مهر", "تا آخر مهرماه": the last day of that month. */
    private val END_OF_MONTH_NAME_PATTERN = Pattern.compile(
        "(?:تا|تاریخ)\\s*(?:پایان|آخر|اخر|انتهای)\\s*$MONTHS(?:\\s*ماه)?(?![\\p{L}])"
    )

    private val END_OF_THIS_MONTH_PATTERN = Pattern.compile("(?:تا|فقط\\s*تا)\\s*(?:پایان|آخر|اخر)\\s*(?:این\\s*)?ماه(?![\\p{L}])")

    private val END_OF_WEEK_PATTERN = Pattern.compile("(?:تا|فقط\\s*تا)\\s*(?:پایان|آخر|اخر)\\s*(?:این\\s*)?هفته(?![\\p{L}])")

    /** "تا پنجشنبه", "فقط تا جمعه", "تا پایان روز سه شنبه". */
    private val WEEKDAY_PATTERN = Pattern.compile(
        "تا\\s*(?:پایان\\s*|آخر\\s*)?(?:روز\\s*)?(?:(یک|دو|سه|چهار|پنج)\\s*)?(شنبه|جمعه)(?![\\p{L}])"
    )

    private val TODAY_PHRASES = listOf(
        "امشب", "فقط امروز", "تنها امروز", "تا پایان امروز", "تا آخر امروز", "تا اخر امروز",
        "امروز آخرین", "امروز اخرین", "ساعت 24", "ساعت 23:59", "پایان روز", "تا پایان وقت امروز",
        "فقط تا امروز"
    )

    /** "Last chance" usually means today, but a stated window beats it. */
    private val LAST_CHANCE_PHRASES = listOf("آخرین فرصت", "اخرین فرصت", "آخرین مهلت", "اخرین مهلت")

    /** Delivery promises that read like a window: "ارسال تا ۲ ساعت", "تحویل طی ۱ روز". */
    private val DELIVERY_WORDS = listOf("ارسال", "تحویل", "پیک", "رسیدن", "می رسد", "میرسد")

    private val TOMORROW_PATTERN = Pattern.compile(
        "(?:تا|فقط\\s*تا)\\s*(?:پایان\\s*|آخر\\s*|اخر\\s*)?(?:روز\\s*)?فردا|فردا\\s*(?:آخرین|اخرین|تمام|منقضی|به\\s*پایان)"
    )

    /** A window introduced by a word that makes it a deadline: "تا ۴۸ ساعت", "مهلت ۳ روزه". */
    private val DURATION_WITH_LEAD = Pattern.compile(
        "(?:تا|فقط|تنها|به\\s*مدت|مهلت(?:\\s*استفاده)?|اعتبار|معتبر(?:\\s*به\\s*مدت)?|طی)\\s*:?\\s*(?:فقط\\s*)?" +
            "(\\d{1,3})\\s*(ساعت|روز|هفته|ماه)"
    )

    /** A window followed by a word that makes it a deadline: "۴۸ ساعت فرصت", "۳ روز دیگر". */
    private val DURATION_WITH_TAIL = Pattern.compile(
        "(?<![\\d])(\\d{1,3})\\s*(ساعت|روز|هفته|ماه)\\s*(?:آینده|دیگر|فرصت|مهلت|اعتبار|باقی|وقت)"
    )

    /**
     * Works out when the offer stops being usable.
     *
     * The whole message is searched, most precise wording first: a written date, a month name,
     * the end of a month or week, a weekday, "tonight", "tomorrow", then a relative window.
     * A window counts only next to a word that makes it a deadline, because "ارسال ۲ ساعته"
     * (two-hour delivery) is not one. When nothing is stated it falls back to
     * [defaultValidityDays] and marks the result non-explicit.
     */
    fun parseExpiry(
        normalizedBody: String,
        receivedAt: Long,
        defaultValidityDays: Int = DEFAULT_VALIDITY_DAYS
    ): ParsedExpiry = findExpiry(normalizedBody, receivedAt) ?: ParsedExpiry(
        receivedAt + defaultValidityDays * DAY_MS,
        "مهلت اعلام نشده",
        isExplicit = false
    )

    /** [parseExpiry] without the default: null when the text states no deadline. */
    fun findExpiry(normalizedBody: String, receivedAt: Long): ParsedExpiry? {
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

        // 2. A Persian month name with a day: "تا ۵ مهر"
        val monthMatcher = MONTH_NAME_PATTERN.matcher(normalizedBody)
        while (monthMatcher.find()) {
            val day = monthMatcher.group(1)?.toIntOrNull() ?: continue
            val monthIdx = PERSIAN_MONTHS.indexOf(monthMatcher.group(2) ?: "")
            if (day !in 1..31 || monthIdx == -1) continue
            val month = monthIdx + 1
            val year = yearFor(month, received.year, received.month)
            val millis = JalaliCalendar.toMillis(year, month, day, endOfDay = true)
            val display = "$day ${PERSIAN_MONTHS[monthIdx]}"
            return ParsedExpiry(millis, toPersianDigits(display), isExplicit = true)
        }

        // 3. The end of a named month: "تا پایان مهر"
        val endOfMonth = END_OF_MONTH_NAME_PATTERN.matcher(normalizedBody)
        if (endOfMonth.find()) {
            val monthIdx = PERSIAN_MONTHS.indexOf(endOfMonth.group(1) ?: "")
            if (monthIdx != -1) {
                val month = monthIdx + 1
                val year = yearFor(month, received.year, received.month)
                return ParsedExpiry(endOfJalaliMonth(year, month), "تا پایان ${PERSIAN_MONTHS[monthIdx]}", isExplicit = true)
            }
        }

        // 4. The end of this month or week
        if (END_OF_THIS_MONTH_PATTERN.matcher(normalizedBody).find()) {
            return ParsedExpiry(endOfJalaliMonth(received.year, received.month), "تا پایان ماه", isExplicit = true)
        }
        if (END_OF_WEEK_PATTERN.matcher(normalizedBody).find()) {
            return ParsedExpiry(endOfWeekday(receivedAt, Calendar.FRIDAY), "تا پایان هفته", isExplicit = true)
        }

        // 5. A weekday: "تا پنجشنبه"
        val weekday = WEEKDAY_PATTERN.matcher(normalizedBody)
        if (weekday.find()) {
            val prefix = weekday.group(1)
            val target = when {
                weekday.group(2) == "جمعه" -> Calendar.FRIDAY
                prefix == null -> Calendar.SATURDAY
                prefix == "یک" -> Calendar.SUNDAY
                prefix == "دو" -> Calendar.MONDAY
                prefix == "سه" -> Calendar.TUESDAY
                prefix == "چهار" -> Calendar.WEDNESDAY
                else -> Calendar.THURSDAY
            }
            val name = if (prefix == null) weekday.group(2) else "${prefix}\u200c${weekday.group(2)}"
            return ParsedExpiry(endOfWeekday(receivedAt, target), "تا $name", isExplicit = true)
        }

        // 6. Tonight, and the other ways of saying "only today"
        if (TODAY_PHRASES.any { normalizedBody.contains(it) }) {
            val millis = JalaliCalendar.toMillis(received.year, received.month, received.day, endOfDay = true)
            return ParsedExpiry(millis, "تا پایان امشب", isExplicit = true)
        }

        // 7. Tomorrow — but "از فردا" is when an offer starts, not when it ends
        if (TOMORROW_PATTERN.matcher(normalizedBody).find()) {
            val millis = JalaliCalendar.toMillis(received.year, received.month, received.day, endOfDay = true) + DAY_MS
            return ParsedExpiry(millis, "تا فردا شب", isExplicit = true)
        }

        // 8. A relative window: "تا ۴۸ ساعت", "یک هفته فرصت"
        val digitized = digitizeNumberWords(normalizedBody)
        for (pattern in listOf(DURATION_WITH_LEAD, DURATION_WITH_TAIL)) {
            val m = pattern.matcher(digitized)
            while (m.find()) {
                val count = m.group(1)?.toLongOrNull() ?: continue
                val unit = m.group(2) ?: continue
                if (count !in 1..365) continue
                val before = digitized.substring(maxOf(0, m.start() - 14), m.start())
                if (DELIVERY_WORDS.any { before.contains(it) }) continue
                val millis = when (unit) {
                    "ساعت" -> receivedAt + count * HOUR_MS
                    "روز" -> receivedAt + count * DAY_MS
                    "هفته" -> receivedAt + count * 7 * DAY_MS
                    else -> receivedAt + count * 30 * DAY_MS
                }
                val display = "تا ${toPersianDigits(count.toString())} $unit"
                return ParsedExpiry(millis, display, isExplicit = true)
            }
        }

        // 9. "آخرین فرصت" with nothing more precise: today
        if (LAST_CHANCE_PHRASES.any { normalizedBody.contains(it) }) {
            val millis = JalaliCalendar.toMillis(received.year, received.month, received.day, endOfDay = true)
            return ParsedExpiry(millis, "تا پایان امشب", isExplicit = true)
        }

        return null
    }

    /** A month earlier than the one we received in must mean next year. */
    private fun yearFor(month: Int, receivedYear: Int, receivedMonth: Int): Int =
        if (month >= receivedMonth) receivedYear else receivedYear + 1

    private fun endOfJalaliMonth(year: Int, month: Int): Long {
        val nextYear = if (month == 12) year + 1 else year
        val nextMonth = if (month == 12) 1 else month + 1
        return JalaliCalendar.toMillis(nextYear, nextMonth, 1, endOfDay = false) - 1
    }

    /** The last millisecond of the next [dayOfWeek] on or after [fromMillis]. */
    private fun endOfWeekday(fromMillis: Long, dayOfWeek: Int): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = fromMillis
        val ahead = (dayOfWeek - cal.get(Calendar.DAY_OF_WEEK) + 7) % 7
        cal.add(Calendar.DAY_OF_YEAR, ahead)
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        return cal.timeInMillis
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
