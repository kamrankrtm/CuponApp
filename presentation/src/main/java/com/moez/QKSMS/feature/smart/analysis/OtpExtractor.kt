package com.moez.QKSMS.feature.smart.analysis

import com.moez.QKSMS.feature.smart.promo.PromoCodeExtractor
import com.moez.QKSMS.feature.smart.promo.PromoValueParser
import java.util.regex.Pattern

/** One complete number in a message that could be its verification code. */
data class OtpCandidate(
    val code: String,
    /** Position in the [SmsText]-normalised body, [start] inclusive, [end] exclusive. */
    val start: Int,
    val end: Int,
    /** A ranking score out of 100, not a measured probability. */
    val score: Int,
    /** Whether a verification label sits right next to the number. */
    val labelled: Boolean
)

/** Everything the OTP reader found in one message. */
data class OtpResult(
    /** Whether the message is about a verification code at all. */
    val verification: Boolean,
    /** Every eligible candidate, one per distinct code, best first. */
    val candidates: List<OtpCandidate>,
    /** How long the message says the code is valid, or null when it does not say. */
    val validityMillis: Long?
) {
    val best: OtpCandidate? get() = candidates.firstOrNull()

    /** How far the best candidate leads the next one; the whole score when it is alone. */
    val margin: Int get() = (best?.score ?: 0) - (candidates.getOrNull(1)?.score ?: 0)

    /** The message's code, when it is a verification message and one candidate stands out enough. */
    val selected: OtpCandidate?
        get() = best?.takeIf { verification && it.score >= OtpExtractor.MIN_SELECT_SCORE }

    /** Whether the local reading alone is sure enough to put the code on the clipboard. */
    val autoCopyEligible: Boolean
        get() {
            val chosen = selected ?: return false
            return chosen.score >= OtpExtractor.AUTO_COPY_MIN_SCORE && margin >= OtpExtractor.AUTO_COPY_MIN_MARGIN
        }

    /** Whether [code] is exactly one of the numbers this message could have meant. */
    fun isEligible(code: String): Boolean = candidates.any { it.code == code }

    companion object {
        val NONE = OtpResult(false, emptyList(), null)
    }
}

/**
 * Reads verification codes.
 *
 * The old reader took the first 4–8 digits it met, so a card number in front of the code won,
 * cut "1234567890" down to "12345678", and refused any code starting with 09 as if it were a
 * phone number. This one lists every *complete* number of 4–8 digits, drops the ones that are
 * plainly something else (part of a card, account, phone number, amount, date, time, link or
 * tracking reference) and ranks the rest by the labels around them.
 *
 * Scores are ranking weights calibrated on the regression suite, not probabilities.
 */
object OtpExtractor {

    /** The least score at which a verification message's best number is taken as its code. */
    const val MIN_SELECT_SCORE = 60

    /** Local auto-copy needs at least this score… */
    const val AUTO_COPY_MIN_SCORE = 90

    /** …and at least this lead over the next candidate. */
    const val AUTO_COPY_MIN_MARGIN = 20

    private const val BASE = 40
    private const val MESSAGE_VERIFICATION = 10
    private const val LABEL_ADJACENT = 50
    private const val LABEL_NEAR = 30
    private const val LABEL_AFTER_ADJACENT = 45
    private const val LABEL_AFTER_NEAR = 35
    private const val GENERIC_ADJACENT = 25
    private const val GENERIC_ADJACENT_LABELLED_MESSAGE = 40
    private const val ENTRY_NEAR = 15
    private const val TYPICAL_LENGTH = 5
    private const val YEAR_LIKE = -15
    private const val NEGATIVE_NEAR = -30

    private const val MIN_LENGTH = 4
    private const val MAX_LENGTH = 8

    /** Words that name a code without saying what kind: "کد 4829", "code: 4829". */
    private val GENERIC_WORDS = setOf("کد", "رمز", "پین", "code", "pin", "password", "passcode")

    /** Words that may sit between a label and its code: "کد تایید شما: 482913". */
    private val FILLERS = setOf(
        "شما", "جدید", "عبارت", "است", "عبارتست", "برابر", "با", "به", "از", "این", "را", "هست",
        "میباشد", "می", "باشد", "is", "your", "the", "you", "here", "new", "code", "کد"
    )

    /** What a code is for, which may sit between its label and it: "کد فعالسازی حساب شما: 4829". */
    private val CONTEXT_NOUNS = setOf(
        "حساب", "کاربری", "سامانه", "سایت", "اپلیکیشن", "برنامه", "ورود", "account", "app", "for", "login"
    )

    /**
     * Words right before a number that make it something other than a code. A verification
     * label after the word ("کارت 1234؛ کد تایید: 4829") still applies to its own number.
     */
    private val NEGATIVE_BEFORE = setOf(
        "کارت", "حساب", "شماره", "تلفن", "تماس", "موبایل", "همراه", "مبلغ", "بمبلغ", "مانده",
        "موجودی", "کارمزد", "رهگیری", "پیگیری", "سفارش", "مرجع", "فاکتور", "شناسه", "قبض",
        "سریال", "ترمینال", "پایانه", "پذیرنده", "شبا", "ملی", "پستی", "کدپستی", "تاریخ", "ساعت",
        "سال", "واحد", "پلاک", "مرسوله", "بسته", "بلیط", "صندلی", "پرواز", "اشتراک", "سند",
        "card", "account", "acc", "tel", "phone", "call", "order", "tracking", "ref", "reference",
        "invoice", "amount", "balance", "id", "no", "number"
    )

    /** Units after a number make it an amount, a share or a duration. */
    private val UNIT_AFTER = Pattern.compile(
        "^\\s*(?:ریال|تومان|تومن|ت(?![\\p{L}])|درصد|%|٪|هزار|میلیون|میلیارد|دقیقه|ثانیه|ساعت|روز|ماه|سال|" +
            "rial|irr|irt|toman|min(?![a-z])|mins|minutes?|sec(?![a-z])|secs|seconds?|hours?|days?)",
        Pattern.CASE_INSENSITIVE
    )

    /** Web addresses and e-mail; a number inside one is never a code. */
    private val URL = Pattern.compile(
        "(?:https?://|www\\.)\\S+|[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+|" +
            "(?<![A-Za-z0-9])[A-Za-z0-9\\-]{2,}(?:\\.[A-Za-z0-9\\-]+)*\\.[A-Za-z]{2,6}(?![A-Za-z0-9])(?:/\\S*)?",
        Pattern.CASE_INSENSITIVE
    )

    /** Instructions that only make sense for a code, but which an offer may borrow. */
    private val WEAK_SIGNALS = listOf(
        Pattern.compile("کد\\s*[0-9]{4,8}\\s*(?:را|رو)(?![\\p{L}])"),
        Pattern.compile("(?:کد|رمز)\\s*یک\\s*بار\\s*مصرف"),
        Pattern.compile("کد شما|your code|code is", Pattern.CASE_INSENSITIVE)
    )

    private const val DURATION_UNIT = "(دقیقه|ثانیه|ساعت|minutes?|mins?(?![a-z])|seconds?|secs?(?![a-z])|hours?|hrs?)"
    private val VALIDITY_BEFORE = Pattern.compile(
        "(?:اعتبار|معتبر|مهلت|منقضی|انقضا|فرصت|ظرف|طی|valid|expire|expires|expiry|within)[^\\d\\n]{0,25}?(\\d{1,4})\\s*$DURATION_UNIT",
        Pattern.CASE_INSENSITIVE
    )
    private val VALIDITY_AFTER = Pattern.compile(
        "(\\d{1,4})\\s*$DURATION_UNIT[^\\d\\n]{0,20}?(?:اعتبار|معتبر|مهلت|فرصت|منقضی|valid)",
        Pattern.CASE_INSENSITIVE
    )

    private const val MIN_VALIDITY_MS = 10_000L
    private const val MAX_VALIDITY_MS = 24L * 60 * 60 * 1000

    private data class Run(val start: Int, val end: Int)

    private data class Label(val start: Int, val end: Int)

    /** Reads [body] as written; it is normalised here. */
    fun extract(body: String): OtpResult = extractNormalized(SmsText.text(body))

    /** Reads a body already in [SmsText] form. */
    fun extractNormalized(text: String): OtpResult {
        val lower = SmsText.lower(text)
        val verification = isVerification(text, lower)
        val strongLabel = SmsSignals.hasVerificationLabel(lower)
        val labels = findAll(lower, SmsSignals.VERIFICATION_LABELS)
        val entries = findAll(lower, SmsSignals.VERIFICATION_ENTRY)
        val urls = spans(URL, text)

        val runs = digitRuns(text)
        val grouped = groupedRuns(text, runs)
        val scored = HashMap<String, OtpCandidate>()
        for ((index, run) in runs.withIndex()) {
            val length = run.end - run.start
            // Never cut a longer number down to a code-sized piece
            if (length < MIN_LENGTH || length > MAX_LENGTH) continue
            if (index in grouped) continue
            if (!standsAlone(text, run)) continue
            if (urls.any { run.start >= it.first && run.end <= it.second }) continue
            if (UNIT_AFTER.matcher(text.substring(run.end)).lookingAt()) continue

            val candidate = score(text, lower, run, verification, strongLabel, labels, entries) ?: continue
            val previous = scored[candidate.code]
            if (previous == null || previous.score < candidate.score) scored[candidate.code] = candidate
        }

        val ranked = scored.values.sortedWith(compareByDescending<OtpCandidate> { it.score }.thenBy { it.start })
        return OtpResult(verification, ranked, validityOf(lower))
    }

    /**
     * Whether the message is about a verification code. A label ("کد تایید", "رمز پویا") is
     * enough on its own; looser wording ("جهت ورود", "کد 4829 را …", "کد شما") only counts
     * when the message is not an offer whose coupon is that number.
     */
    fun isVerification(text: String, lower: String = SmsText.lower(text)): Boolean {
        if (SmsSignals.hasVerificationLabel(lower)) return true
        val weak = SmsSignals.hasVerificationEntry(lower) || WEAK_SIGNALS.any { it.matcher(text).find() }
        if (!weak) return false
        val couponOffer = PromoCodeExtractor.looksPromotional(text) &&
            PromoCodeExtractor.extractAll(text).any { it.confidence >= 80 }
        return !couponOffer
    }

    private fun score(
        text: String,
        lower: String,
        run: Run,
        verification: Boolean,
        strongLabel: Boolean,
        labels: List<Label>,
        entries: List<Label>
    ): OtpCandidate? {
        var score = BASE
        if (verification) score += MESSAGE_VERIFICATION
        if (run.end - run.start in TYPICAL_LENGTH..6) score += 5

        // What sits between the previous number (or a label) and this one
        val windowStart = lastDigitBefore(text, run.start) + 1
        val before = lower.substring(maxOf(windowStart, run.start - 40), run.start)
        val beforeWords = words(before)

        val labelBefore = labels.filter { it.end <= run.start && it.start >= windowStart }.maxBy { it.end }
        val negativeIndex = beforeWords.indexOfLast { it in NEGATIVE_BEFORE }
        var labelled = false
        if (labelBefore != null) {
            val gap = lower.substring(labelBefore.end, run.start)
            val gapWords = words(gap)
            val lastWord = gapWords.lastOrNull()
            // "کد تایید خرید با کارت 1234": the card word labels this number, not the code label
            if (lastWord != null && lastWord in NEGATIVE_BEFORE &&
                gap.substring(gap.lastIndexOf(lastWord) + lastWord.length).none { it.isLetterOrDigit() }) return null
            when {
                // Across a line break only when the label line ends in a colon: "کد ورود شما:\n4829"
                gap.length <= 24 && gapWords.all { it in FILLERS || it in CONTEXT_NOUNS } &&
                    (!gap.contains('\n') || gap.substringBefore('\n').trimEnd().endsWith(":")) -> {
                    score += LABEL_ADJACENT
                    labelled = true
                }
                gap.length <= 45 && gap.count { it == '\n' } <= 1 -> score += LABEL_NEAR
            }
        } else if (negativeIndex >= 0) {
            // "کارت 12345678", "شماره پیگیری: 998877" — the word is the number's own label
            if (negativeIndex >= beforeWords.size - 3) return null
            score += NEGATIVE_NEAR
        } else if (verification && beforeWords.isNotEmpty()) {
            // "Your code is 4829", "رمز: 48291375": a generic word, past any fillers
            val generic = beforeWords.lastOrNull { it !in FILLERS || it in GENERIC_WORDS }
            if (generic != null && generic in GENERIC_WORDS) {
                val tail = before.substring(before.lastIndexOf(generic) + generic.length)
                if (tail.length <= 12) {
                    // "رمز: 48291375" under a "رمز پویا" heading is the code the heading names
                    score += if (strongLabel) GENERIC_ADJACENT_LABELLED_MESSAGE else GENERIC_ADJACENT
                }
            }
        }

        if (!labelled) {
            val nextDigit = nextDigitFrom(text, run.end)
            val labelAfter = labels.filter { it.start >= run.end && it.start < nextDigit }.minBy { it.start }
            if (labelAfter != null) {
                val gap = lower.substring(run.end, labelAfter.start)
                val gapWords = words(gap)
                if (gap.length <= 15 && gapWords.all { it in FILLERS }) {
                    score += LABEL_AFTER_ADJACENT
                    labelled = true
                } else if (gap.length <= 35 && !gap.contains('\n')) {
                    score += LABEL_AFTER_NEAR
                }
            }
        }

        if (entries.any { (it.start >= run.end && it.start - run.end <= 25) || (it.end <= run.start && run.start - it.end <= 25) }) {
            score += ENTRY_NEAR
        }

        val code = text.substring(run.start, run.end)
        if (code.length == 4 && code.toInt().let { it in 1300..1499 || it in 1950..2099 }) score += YEAR_LIKE

        if (score <= 0) return null
        return OtpCandidate(code, run.start, run.end, minOf(100, score), labelled)
    }

    /**
     * Whether the run is a whole number on its own, rather than one piece of a longer one:
     * grouped amounts (1,234), dates (1405/07/20), times (10:31), masked or dashed cards
     * (****1234, 6037-9918), signed amounts (+5000, 4000-) and coupons (FOOD1234).
     */
    private fun standsAlone(text: String, run: Run): Boolean {
        val b1 = text.getOrElse(run.start - 1) { ' ' }
        val b2 = text.getOrElse(run.start - 2) { ' ' }
        val a1 = text.getOrElse(run.end) { ' ' }
        val a2 = text.getOrElse(run.end + 1) { ' ' }
        if (SmsText.isLatinLetter(b1) || SmsText.isLatinLetter(a1)) return false
        if (b1 == '*' || a1 == '*') return false
        if (b1 in ",./:" && (SmsText.isDigit(b2) || b2 == '*')) return false
        if (a1 in ",./:" && (SmsText.isDigit(a2) || a2 == '*')) return false
        if (b1 == '-' && !b2.isLetter()) return false
        if (a1 == '-' && (SmsText.isDigit(a2) || a2 == '*' || !a2.isLetterOrDigit())) return false
        if (b1 == '+' || a1 == '+') return false
        if (b1 == '_' || a1 == '_') return false
        return true
    }

    /** Runs that are one of several space-separated groups: "6037 9918 1234 5678", "021 8877 6655". */
    private fun groupedRuns(text: String, runs: List<Run>): Set<Int> {
        val result = HashSet<Int>()
        var i = 0
        while (i < runs.size) {
            var j = i
            while (j + 1 < runs.size && runs[j + 1].start == runs[j].end + 1 && text[runs[j].end] == ' ') j++
            val size = j - i + 1
            val allFour = (i..j).all { runs[it].end - runs[it].start == 4 }
            if (size >= 3 || (size == 2 && allFour)) (i..j).forEach { result.add(it) }
            i = j + 1
        }
        return result
    }

    private fun digitRuns(text: String): List<Run> {
        val runs = ArrayList<Run>()
        var i = 0
        while (i < text.length) {
            if (!SmsText.isDigit(text[i])) {
                i++
                continue
            }
            val start = i
            while (i < text.length && SmsText.isDigit(text[i])) i++
            runs.add(Run(start, i))
        }
        return runs
    }

    private fun lastDigitBefore(text: String, index: Int): Int {
        var i = index - 1
        while (i >= 0 && !SmsText.isDigit(text[i])) i--
        return i
    }

    private fun nextDigitFrom(text: String, index: Int): Int {
        var i = index
        while (i < text.length && !SmsText.isDigit(text[i])) i++
        return i
    }

    private fun words(text: String): List<String> =
        text.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }

    private fun findAll(lower: String, phrases: List<String>): List<Label> {
        val found = ArrayList<Label>()
        for (phrase in phrases) {
            var idx = SmsSignals.indexOfPhrase(lower, phrase, 0)
            while (idx >= 0) {
                found.add(Label(idx, idx + phrase.length))
                idx = SmsSignals.indexOfPhrase(lower, phrase, idx + 1)
            }
        }
        return found
    }

    private fun spans(pattern: Pattern, text: String): List<Pair<Int, Int>> {
        val m = pattern.matcher(text)
        val list = ArrayList<Pair<Int, Int>>()
        while (m.find()) list.add(m.start() to m.end())
        return list
    }

    /** The validity the message states ("۲ دقیقه معتبر", "valid for 10 minutes"), or null. */
    fun validityOf(lowerText: String): Long? {
        val text = PromoValueParser.digitizeNumberWords(lowerText)
        for (pattern in listOf(VALIDITY_BEFORE, VALIDITY_AFTER)) {
            val m = pattern.matcher(text)
            while (m.find()) {
                val amount = m.group(1)?.toLongOrNull() ?: continue
                val unit = m.group(2)?.toLowerCase() ?: continue
                val millis = amount * when {
                    unit.startsWith("ثانیه") || unit.startsWith("sec") -> 1_000L
                    unit.startsWith("دقیقه") || unit.startsWith("min") -> 60_000L
                    else -> 3_600_000L
                }
                if (millis in MIN_VALIDITY_MS..MAX_VALIDITY_MS) return millis
            }
        }
        return null
    }
}
