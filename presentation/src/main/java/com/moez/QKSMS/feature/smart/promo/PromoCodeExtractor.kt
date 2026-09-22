package com.moez.QKSMS.feature.smart.promo

import java.util.regex.Pattern

/** A coupon code candidate together with how much the extractor trusts it. */
data class CodeCandidate(val code: String, val confidence: Int)

/**
 * Pulls the coupon code itself out of a promotional SMS.
 *
 * Separated from brand and amount parsing because this is the one part that decides whether a
 * message is a promo at all: no code, no card.
 */
object PromoCodeExtractor {

    /**
     * Labelled patterns, strongest first. A code introduced by "کد تخفیف:" is far more
     * trustworthy than a bare token sitting on its own line.
     */
    private val LABELLED_PATTERNS: List<Pair<Pattern, Int>> = listOf(
        Pattern.compile(
            "(?:کد\\s*(?:تخفیف|هدیه|معرف|اشتراک|شگفت[\\s‌]*انگیز)|کوپن|voucher|coupon)" +
                "[^a-zA-Z0-9\\n]{0,15}([a-zA-Z][a-zA-Z0-9_\\-]{2,23})",
            Pattern.CASE_INSENSITIVE
        ) to 95,
        Pattern.compile(
            "(?:با\\s*کد|کد\\s*را|کد)[^a-zA-Z0-9\\n]{0,12}([a-zA-Z][a-zA-Z0-9_\\-]{2,23})",
            Pattern.CASE_INSENSITIVE
        ) to 85,
        Pattern.compile(
            "(?:promo\\s*code|discount\\s*code|code|promo)[:\\s]+([a-zA-Z][a-zA-Z0-9_\\-]{2,23})",
            Pattern.CASE_INSENSITIVE
        ) to 80
    )

    /** A token alone on its own line, the usual shape of "SNAPPFOOD\nVDTTESZQ4D8HXWDFWV". */
    private val STANDALONE_CONFIDENCE = 55

    /**
     * Words that look like codes but never are: URL parts, carrier jargon, and the brand
     * slugs that appear in every one of these messages.
     */
    private val STOP_WORDS = setOf(
        "http", "https", "www", "link", "ir", "com", "net", "org", "co", "app",
        "sms", "volte", "mci", "mtn", "irancell", "hamrah", "shatel", "tci",
        "tapsi", "snapp", "snappfood", "digikala", "dgkl", "snpf", "dgpay", "okala",
        "off", "code", "promo", "coupon", "discount", "free", "gift", "new", "the",
        "android", "ios", "bazaar", "myket", "telegram", "instagram", "whatsapp",
        "lghv", "cancel", "stop", "help", "info", "test", "null", "none"
    )

    /**
     * Finds the coupon code, or null when the message does not actually carry one.
     *
     * @param normalizedBody body with digits and characters already normalized
     */
    fun extract(normalizedBody: String): CodeCandidate? {
        for ((pattern, confidence) in LABELLED_PATTERNS) {
            val matcher = pattern.matcher(normalizedBody)
            while (matcher.find()) {
                val candidate = matcher.group(1)?.trim() ?: continue
                if (isPlausible(candidate)) {
                    return CodeCandidate(candidate, adjustConfidence(candidate, confidence))
                }
            }
        }

        for (line in normalizedBody.lines()) {
            val trimmed = line.trim()
            if (trimmed.contains(" ") || trimmed.contains(".") || trimmed.contains("/")) continue
            if (!isPlausible(trimmed)) continue
            return CodeCandidate(trimmed, adjustConfidence(trimmed, STANDALONE_CONFIDENCE))
        }

        return null
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

    private fun squash(text: String): String {
        val sb = StringBuilder(text.length)
        for (c in text) {
            if (!c.isWhitespace() && c != '\u200c' && c != '-' && c != '_') {
                sb.append(c.toLowerCase())
            }
        }
        return sb.toString()
    }

    /**
     * Rejects candidates that cannot be coupon codes.
     *
     * A code has to contain a letter (pure digits are phone numbers, prices and dates), must
     * not be a phone number or URL fragment, and must not be one of the words that shows up in
     * every promotional text.
     */
    private fun isPlausible(candidate: String): Boolean {
        if (candidate.length < 3 || candidate.length > 24) return false
        if (candidate.startsWith("09") || candidate.startsWith("+98") || candidate.startsWith("98")) return false
        if (!candidate.any { it in 'a'..'z' || it in 'A'..'Z' }) return false
        if (candidate.all { it in 'a'..'z' || it in 'A'..'Z' } && candidate.length < 4) return false
        if (STOP_WORDS.contains(candidate.toLowerCase())) return false
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
