package com.moez.QKSMS.feature.smart.promo

import java.util.regex.Pattern

/**
 * A coupon code candidate together with how much the extractor trusts it and where it sits in
 * the normalized body ([start] inclusive, [end] exclusive; -1 when unknown). [note] says what
 * the label restricted it to: "کد تخفیف قسطی" gives "خرید قسطی".
 */
data class CodeCandidate(
    val code: String,
    val confidence: Int,
    val start: Int = -1,
    val end: Int = -1,
    val note: String? = null
)

/**
 * Pulls the coupon codes themselves out of a promotional SMS.
 *
 * Separated from brand and amount parsing because this is the one part that decides whether a
 * message is a promo at all: no code, no card.
 */
object PromoCodeExtractor {

    /**
     * Labels that introduce a code, with the trust they lend the token after them. Matched
     * longest first, so "کد تخفیف" is never also read as a bare "کد".
     */
    private val LABELS: List<Pair<String, Int>> = listOf(
        "کد تخفیف اختصاصی" to 95, "کدهای تخفیف" to 95, "کد تخفیف" to 95, "کد هدیه" to 95,
        "کد کوپن" to 95, "کد شگفت انگیز" to 95, "کد اشتراک هدیه" to 95, "کد تبلیغاتی" to 92,
        "کوپن" to 92, "کد اختصاصی" to 90, "کد ویژه" to 90, "کد جایزه" to 90, "کد پاداش" to 90,
        "کد اشتراک" to 85, "کد خرید" to 85,
        "promo code" to 95, "promocode" to 95, "discount code" to 95, "gift code" to 92,
        "voucher" to 92, "coupon" to 92,
        "کد معرف" to 80, "کد دعوت" to 80, "referral code" to 80, "invite code" to 80,
        "با کد" to 85, "کد" to 82, "code" to 80, "promo" to 75
    ).flatMap { (label, trust) ->
        val joined = label.replace(" ", "")
        if (joined != label) listOf(label to trust, joined to trust) else listOf(label to trust)
    }.sortedByDescending { it.first.length }

    /** A label at or above this lends enough trust to accept a purely numeric code. */
    private const val NUMERIC_LABEL_TRUST = 90

    /** Words that may sit between a label and its code: "کد تخفیف ویژه شما: FOOD30". */
    private val FILLERS = setOf(
        "شما", "خود", "خودت", "اختصاصی", "ویژه", "زیر", "را", "رو", "با", "این", "تخفیف", "هدیه",
        "عبارت", "کلمه", "جدید", "مخصوص", "همین", "حالا", "هم", "اکنون", "یعنی", "برای", "اولین",
        "اول", "دوم", "درصدی", "درصد", "تومانی", "تومان", "هزار", "هزارتومانی", "میلیونی",
        "میلیون", "ریالی", "شگفت", "انگیز", "انگیزت", "عزیز", "جان", "است", "هست", "یک", "دیگر",
        "مشترک", "گرامی", "ما", "تو", "بعدی", "وارد", "کن", "کنید", "از", "و", "به", "در"
    )

    /**
     * Words between a label and its code that say what the code is for: one message often
     * carries a "کد تخفیف نقدی" and a "کد تخفیف قسطی", and the cards must tell them apart.
     */
    private val QUALIFIERS = mapOf(
        "نقدی" to "خرید نقدی",
        "قسطی" to "خرید قسطی",
        "اقساطی" to "خرید قسطی"
    )

    /** "کد رهگیری AB123" is a receipt, not a coupon: these end the search for a code. */
    private val BLOCKERS = setOf(
        "رهگیری", "پیگیری", "سفارش", "پستی", "ملی", "اقتصادی", "مرسوله", "محصول", "کالا",
        "مشتری", "عضویت", "پرسنلی", "کاربری", "تایید", "ورود", "امنیتی", "فعالسازی", "فعال",
        "شارژ", "پین", "رمز", "پذیرنده", "ترمینال", "شعبه", "دستوری", "بیمه", "ملک", "رهیابی",
        "مرجع", "پرونده", "قبض", "شناسه", "پیامک"
    )

    /**
     * Words that look like codes but never are: URL parts, carrier jargon and generic English
     * that ads sprinkle around. Brand names are rejected separately.
     */
    private val STOP_WORDS = setOf(
        "http", "https", "www", "link", "ir", "com", "net", "org", "co", "app", "html", "php",
        "sms", "volte", "lte", "wifi", "off", "code", "promo", "coupon", "discount", "free", "gift",
        "new", "the", "and", "for", "you", "your", "with", "now", "sale", "vip", "plus", "pro",
        "android", "ios", "iphone", "bazaar", "myket", "telegram", "instagram", "whatsapp", "rubika",
        "eitaa", "bale", "lghv", "cancel", "stop", "help", "info", "test", "null", "none", "click",
        "online", "shop", "store", "club", "black", "friday", "cyber", "monday", "hot", "best",
        "top", "super", "mega", "special", "only", "today", "offer", "deal", "deals", "bonus"
    )

    /**
     * Words that mark a message as an actual offer.
     *
     * Without this gate any SMS carrying a stray alphanumeric token became a "discount": a
     * bank's login message was filed under discounts and its SMS Retriever hash was offered to
     * the user as a coupon code.
     */
    private val PROMOTIONAL_SIGNALS = listOf(
        "تخفیف", "کد تخفیف", "درصد تخفیف", "جشنواره", "ارسال رایگان", "هدیه", "کوپن",
        "حراج", "فروش ویژه", "پیشنهاد ویژه", "شگفت انگیز", "اعتبار هدیه", "بن خرید",
        "کمپین", "کش بک", "کشبک", "off", "discount", "promo", "coupon", "sale", "cashback",
        "voucher", "ارزان تر", "ارزانتر", "ارزون تر", "ارزونتر", "نصف قیمت", "نیم بها", "رایگان",
        "درصد", "٪", "%"
    )

    /**
     * Phrases that mark a message as a login or verification code.
     *
     * These veto promo extraction outright. A bank that also runs promotions is fine; a
     * message telling the user to type a code to sign in is not an offer, whatever else it
     * happens to contain.
     */
    private val VERIFICATION_SIGNALS = listOf(
        "جهت ورود", "برای ورود", "رمز ورود", "کد ورود", "کد تایید", "کد فعالسازی",
        "کد فعال سازی", "رمز یکبار مصرف", "رمز یکبارمصرف", "رمز پویا", "کد احراز",
        "کد امنیتی", "کد عبور", "رمز دوم", "verification code", "login code",
        "security code", "one time password", "otp"
    )

    /**
     * Receipts about a code already spent or dead. "کد FOOD30 روی سفارش شما اعمال شد" names a
     * code, but offering it again as a discount would be wrong.
     */
    private val SPENT_SIGNALS = listOf(
        "اعمال شد", "اعمال گردید", "استفاده شد", "استفاده گردید", "منقضی شد", "منقضی گردید",
        "باطل شد", "لغو شد", "مصرف شد"
    )

    /** "کد 36330 را ..." — a numeric code the user is told to type, i.e. a one-time password. */
    private val NUMERIC_CODE_INSTRUCTION =
        Pattern.compile("کد\\s*[0-9]{4,8}\\s*(?:را|رو)(?![\\p{L}])")

    /** Latin or digit tokens, which is what codes are made of, and Persian words between them. */
    private val TOKEN = Pattern.compile("([A-Za-z0-9](?:[A-Za-z0-9_\\-]*[A-Za-z0-9])?)|(\\p{L}+)")

    /** Web addresses and e-mail; a token inside one is never a code. */
    private val URL = Pattern.compile(
        "(?:https?://|www\\.)\\S+|[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+|" +
            "(?<![A-Za-z0-9])[A-Za-z0-9\\-]{2,}(?:\\.[A-Za-z0-9\\-]+)*\\.[A-Za-z]{2,6}(?![A-Za-z0-9])(?:/\\S*)?",
        Pattern.CASE_INSENSITIVE
    )

    /** Units after a number that make it a figure, not a code: "۳۰ درصدی", "۵۰ هزار". */
    private val UNIT_AFTER = Pattern.compile("^\\s*(?:درصد|٪|%|هزار|میلیون|تومان|تومن|ریال|ت(?![\\p{L}])|ساعت|روز|نفر)")

    /** Whether the message is advertising something. */
    fun looksPromotional(normalizedBody: String): Boolean {
        val lower = normalizedBody.toLowerCase()
        return PROMOTIONAL_SIGNALS.any { lower.contains(it) }
    }

    /** Whether the message is a login / verification code rather than an offer. */
    fun isVerificationMessage(normalizedBody: String): Boolean {
        val lower = normalizedBody.toLowerCase()
        if (VERIFICATION_SIGNALS.any { lower.contains(it) }) return true
        return NUMERIC_CODE_INSTRUCTION.matcher(normalizedBody).find()
    }

    /** Whether the message reports a code as used, applied or expired. */
    fun isSpentNotice(normalizedBody: String): Boolean = SPENT_SIGNALS.any { normalizedBody.contains(it) }

    /**
     * The 11-character signature Android's SMS Retriever API appends to verification texts.
     *
     * It looks exactly like a coupon code — mixed case, letters and digits, alone on the last
     * line — which is how one ended up on a card as "کپی کد (QVCOXY7HHZR)".
     */
    fun looksLikeSmsRetrieverHash(token: String): Boolean {
        if (token.length != 11) return false
        if (!token.any { it in 'a'..'z' }) return false
        if (!token.any { it in 'A'..'Z' }) return false
        return token.all { it.isLetterOrDigit() || it == '+' || it == '/' }
    }

    /** Finds the most trustworthy coupon code, or null when the message carries none. */
    fun extract(normalizedBody: String): CodeCandidate? = extractAll(normalizedBody).firstOrNull()

    /**
     * Every code in the message, most trustworthy first.
     *
     * Labelled codes ("کد تخفیف: FOOD30") are the strong case; a token alone on its own line,
     * or wrapped in quotes, is weaker; a bare mixed token like "FOOD30" in running text is the
     * weakest and is only kept so the AI tier can confirm it.
     *
     * @param normalizedBody body with digits and characters already normalized
     */
    fun extractAll(normalizedBody: String): List<CodeCandidate> {
        val text = normalizedBody
        val lower = text.toLowerCase()
        val urls = urlSpans(text)
        val found = LinkedHashMap<String, CodeCandidate>()

        fun offer(candidate: CodeCandidate) {
            val key = candidate.code.toUpperCase()
            val existing = found[key]
            if (existing == null || existing.confidence < candidate.confidence) found[key] = candidate
        }

        // 1. Codes introduced by a label
        val claimed = BooleanArray(lower.length)
        for ((label, trust) in LABELS) {
            var idx = lower.indexOf(label)
            while (idx >= 0) {
                val end = idx + label.length
                var free = true
                for (i in idx until end) if (claimed[i]) free = false
                if (free && isLabelWord(lower, idx, end)) {
                    for (i in idx until end) claimed[i] = true
                    codeAfterLabel(text, end, trust, urls)?.let { offer(it) }
                }
                idx = lower.indexOf(label, idx + 1)
            }
        }

        // 2. Unlabelled tokens: alone on a line, in quotes, or code-shaped in running text
        val matcher = TOKEN.matcher(text)
        tokens@ while (matcher.find()) {
            val token = matcher.group(1) ?: continue
            val start = matcher.start(1)
            val end = matcher.end(1)
            if (found.containsKey(token.toUpperCase())) continue
            if (inside(urls, start, end) || !isPlausible(token) || looksLikeSmsRetrieverHash(token)) continue
            if (isReceiptNumber(text, start)) continue

            val confidence = when {
                isAloneOnLine(text, start, end) -> STANDALONE_CONFIDENCE
                isQuoted(text, start, end) -> QUOTED_CONFIDENCE
                isCodeShaped(token) -> BARE_CONFIDENCE
                else -> continue@tokens
            }
            offer(CodeCandidate(token, adjustConfidence(token, confidence), start, end))
        }

        return found.values.sortedWith(compareBy<CodeCandidate>({ -it.confidence }, { it.start }))
    }

    /** A token alone on its own line, the usual shape of "SNAPPFOOD\nVDTTESZQ4D8HXWDFWV". */
    private const val STANDALONE_CONFIDENCE = 55

    /** «FOOD30», "FOOD30", *FOOD30*. */
    private const val QUOTED_CONFIDENCE = 65

    /** FOOD30 somewhere in a sentence, with no label: worth keeping, not worth trusting. */
    private const val BARE_CONFIDENCE = 40

    /**
     * Walks forward from a label to the code it introduces, across filler words and at most
     * one line break. Gives up at a word that makes it a different kind of code ("رهگیری") or
     * after too many unexplained words.
     */
    private fun codeAfterLabel(text: String, labelEnd: Int, trust: Int, urls: List<IntRange>): CodeCandidate? {
        var limit = minOf(text.length, labelEnd + 48)
        val firstBreak = text.indexOf('\n', labelEnd)
        if (firstBreak in labelEnd until limit) {
            val secondBreak = text.indexOf('\n', firstBreak + 1)
            if (secondBreak in (firstBreak + 1) until limit) limit = secondBreak
        }

        var penalty = 0
        var unknownWords = 0
        var note: String? = null
        val matcher = TOKEN.matcher(text)
        matcher.region(labelEnd, limit)
        while (matcher.find()) {
            val persian = matcher.group(2)
            if (persian != null) {
                if (persian in BLOCKERS) return null
                val qualifier = QUALIFIERS[persian]
                if (qualifier != null) {
                    note = qualifier
                    continue
                }
                if (persian in FILLERS) continue
                unknownWords++
                penalty += 6
                if (unknownWords > 2) return null
                continue
            }

            val token = matcher.group(1) ?: continue
            val start = matcher.start(1)
            val end = matcher.end(1)
            if (inside(urls, start, end)) continue

            if (token.all { it.isDigit() }) {
                if (UNIT_AFTER.matcher(text.substring(end)).find()) {
                    penalty += 4
                    continue
                }
                if (trust >= NUMERIC_LABEL_TRUST && penalty == 0 && token.length in 4..12 &&
                    !token.startsWith("09") && !token.startsWith("98")
                ) {
                    return CodeCandidate(token, 70, start, end, note)
                }
                penalty += 4
                continue
            }

            if (!isPlausible(token) || looksLikeSmsRetrieverHash(token)) {
                penalty += 3
                continue
            }
            return CodeCandidate(token, adjustConfidence(token, trust - penalty), start, end, note)
        }
        return null
    }

    /** A Persian label must be its own word: "کد" in "کدام" or "کدهای" is not a label. */
    private fun isLabelWord(text: String, start: Int, end: Int): Boolean {
        if (start > 0 && text[start - 1].isLetter()) return false
        return end >= text.length || !text[end].isLetter()
    }

    private fun isAloneOnLine(text: String, start: Int, end: Int): Boolean {
        val lineStart = text.lastIndexOf('\n', start - 1) + 1
        val lineEnd = text.indexOf('\n', end).let { if (it < 0) text.length else it }
        val rest = text.substring(lineStart, start) + text.substring(end, lineEnd)
        return rest.all { !it.isLetterOrDigit() }
    }

    /** "کد رهگیری AB12345", "شماره سفارش: X12": a token a blocker word points at. */
    private fun isReceiptNumber(text: String, start: Int): Boolean {
        val lineStart = text.lastIndexOf('\n', start - 1) + 1
        val before = text.substring(maxOf(lineStart, start - 28), start)
        return (before.contains("کد") || before.contains("شماره")) && BLOCKERS.any { before.contains(it) }
    }

    private fun isQuoted(text: String, start: Int, end: Int): Boolean {
        if (start == 0 || end >= text.length) return false
        val before = text[start - 1]
        val after = text[end]
        return (before == '«' && after == '»') || (before == '"' && after == '"') ||
            (before == '\'' && after == '\'') || (before == '*' && after == '*') ||
            (before == '“' && after == '”') || (before == '[' && after == ']')
    }

    /** Letters and digits together, in capitals: the classic coupon shape. */
    private fun isCodeShaped(token: String): Boolean =
        token.length in 5..16 &&
            token.any { it in 'A'..'Z' } && token.any { it in '0'..'9' } &&
            token.none { it in 'a'..'z' }

    private fun urlSpans(text: String): List<IntRange> {
        val spans = ArrayList<IntRange>()
        val m = URL.matcher(text)
        while (m.find()) spans.add(m.start() until m.end())
        return spans
    }

    private fun inside(spans: List<IntRange>, start: Int, end: Int): Boolean =
        spans.any { start >= it.first && end <= it.last + 1 }

    /**
     * Whether the message has anything a code could be: a latin token that is not a link, a
     * brand name or filler English. The AI can only report a code that is in the text, so a
     * message without one is never worth sending.
     */
    fun hasCodeShapedToken(normalizedBody: String): Boolean {
        val urls = urlSpans(normalizedBody)
        val matcher = TOKEN.matcher(normalizedBody)
        while (matcher.find()) {
            val token = matcher.group(1) ?: continue
            if (token.length < 4 || token.all { it.isDigit() }) continue
            if (inside(urls, matcher.start(1), matcher.end(1))) continue
            if (isPlausible(token) && !looksLikeSmsRetrieverHash(token)) return true
        }
        return false
    }

    /**
     * Whether [code] really occurs in [normalizedBody].
     *
     * A coupon code is something the user types from the message, so it must be present in the
     * message. This is the check that keeps an external extractor honest: a code that is not in
     * the text either came from another message or was invented, and either way it is useless.
     *
     * Spaces and zero-width joiners are ignored on both sides, because senders break codes up
     * ("PAY CNN48") and an extractor will report them joined.
     */
    fun appearsIn(code: String, normalizedBody: String): Boolean {
        val needle = squash(code)
        if (needle.length < 3) return false
        return squash(normalizedBody).contains(needle)
    }

    /** Where [code] sits in [normalizedBody], ignoring case; -1 when it is split up or absent. */
    fun indexOf(code: String, normalizedBody: String): Int =
        normalizedBody.toLowerCase().indexOf(code.toLowerCase())

    private fun squash(text: String): String {
        val sb = StringBuilder(text.length)
        for (c in text) {
            if (!c.isWhitespace() && c != '‌' && c != '-' && c != '_') {
                sb.append(c.toLowerCase())
            }
        }
        return sb.toString()
    }

    /**
     * Rejects candidates that cannot be coupon codes.
     *
     * A code has to contain a letter (pure digits are phone numbers, prices and dates), must
     * not be a phone number or URL fragment, and must not be a brand name or one of the words
     * that shows up in every promotional text.
     */
    private fun isPlausible(candidate: String): Boolean {
        if (candidate.length < 3 || candidate.length > 24) return false
        if (candidate.startsWith("09") || candidate.startsWith("+98") || candidate.startsWith("98")) return false
        if (!candidate.any { it in 'a'..'z' || it in 'A'..'Z' }) return false
        if (candidate.all { it in 'a'..'z' || it in 'A'..'Z' } && candidate.length < 4) return false
        val lower = candidate.toLowerCase()
        if (STOP_WORDS.contains(lower)) return false
        if (BrandRegistry.isBrandWord(lower)) return false
        return true
    }

    /**
     * Nudges confidence by how code-like the token looks.
     *
     * Mixed letters and digits ("FOOD70") are the classic Iranian coupon shape; an all-letters
     * lowercase word is more likely a stray English word the pattern happened to catch.
     */
    private fun adjustConfidence(candidate: String, base: Int): Int {
        var confidence = base
        val hasDigit = candidate.any { it in '0'..'9' }
        val hasUpper = candidate.any { it in 'A'..'Z' }

        if (hasDigit && hasUpper) confidence += 5
        if (!hasDigit && !hasUpper) confidence -= 20
        if (candidate.length in 5..12) confidence += 3

        return confidence.coerceIn(10, 100)
    }
}
