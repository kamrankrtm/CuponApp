package com.moez.QKSMS.feature.smart.analysis

/**
 * The one text normaliser every reader of an SMS shares, so classification, OTP and banking
 * extraction, coupon parsing and the outbound privacy checks all see the same characters.
 *
 * - Persian (۰–۹), Arabic-Indic (٠–٩) and full-width digits become ASCII.
 * - Arabic letter forms become the Persian ones: ي ى → ی, ك ڪ → ک, ة ۀ ھ ہ → ه, أ إ ٱ → ا, ؤ → و.
 * - Tatweel, harakat and the invisible marks keyboards and multipart SMS leave behind (zero-width
 *   space and joiner, direction marks and isolates, BOM, soft hyphen) are dropped.
 * - The zero-width non-joiner and the odd spaces (no-break, thin, ideographic, tab) become one
 *   space, and runs of spaces collapse. Newlines are kept: banks put one field per line.
 * - The Arabic thousands separator ٬ becomes "," and the decimal separator ٫ becomes "."; the
 *   minus sign and non-breaking hyphens become "-".
 *
 * [normalize] also maps every kept character back to its index in the original, so a code or
 * an amount found in the normalised text can be checked against what the sender wrote.
 *
 * Pure Kotlin: it is unit-tested on the JVM with the rest of the engine.
 */
object SmsText {

    /** A normalised message and the way back to the original. */
    class Normalized internal constructor(
        val original: String,
        val text: String,
        private val offsets: IntArray
    ) {
        /** Index in [original] of the character at [index] in [text]; the end maps to the end. */
        fun originalIndex(index: Int): Int = when {
            index < 0 -> 0
            index >= offsets.size -> original.length
            else -> offsets[index]
        }

        /** The original characters [start, end) of [text] came from, invisible marks included. */
        fun originalSpan(start: Int, end: Int): String {
            if (end <= start) return ""
            val from = originalIndex(start)
            val to = originalIndex(end - 1) + 1
            return original.substring(from, to.coerceAtMost(original.length))
        }
    }

    /** Normalises [input] and keeps the offsets back into it. */
    fun normalize(input: String): Normalized {
        val offsets = IntArray(input.length)
        val text = run(input, offsets)
        return Normalized(input, text, offsets.copyOf(text.length))
    }

    /** Only the normalised text, for callers that never need to map back. */
    fun text(input: String): String = run(input, null)

    /** Lower-cases ASCII and other cased letters one for one, so offsets stay valid. */
    fun lower(text: String): String {
        val chars = text.toCharArray()
        for (i in chars.indices) chars[i] = Character.toLowerCase(chars[i])
        return String(chars)
    }

    private fun run(input: String, offsets: IntArray?): String {
        val sb = StringBuilder(input.length)
        for ((index, raw) in input.withIndex()) {
            val c = map(raw) ?: continue
            if (c == ' ' && (sb.isEmpty() || sb[sb.length - 1] == ' ')) continue
            offsets?.set(sb.length, index)
            sb.append(c)
        }
        return sb.toString()
    }

    /** The normalised form of one character, or null to drop it. */
    private fun map(raw: Char): Char? = when (raw) {
        'ي', 'ى' -> 'ی'
        'ك', 'ڪ' -> 'ک'
        'ة', 'ۀ', 'ھ', 'ہ' -> 'ه'
        'أ', 'إ', 'ٱ' -> 'ا'
        'ؤ' -> 'و'
        '٬' -> ','
        '٫' -> '.'
        '−', '‐', '‑' -> '-'
        '‌', ' ', ' ', ' ', ' ', ' ', ' ', ' ',
        '　', '\t' -> ' '
        'ـ', '\r', '​', '‍', '‎', '‏', '‪', '‫', '‬',
        '‭', '‮', '⁦', '⁧', '⁨', '⁩', '﻿', '­',
        '؜', '⁠', '᠎' -> null
        in 'ً'..'ْ', 'ٰ' -> null
        in '۰'..'۹' -> '0' + (raw - '۰')
        in '٠'..'٩' -> '0' + (raw - '٠')
        in '０'..'９' -> '0' + (raw - '０')
        else -> raw
    }

    /** Whether [c] is an ASCII digit; after [normalize] every digit in a message is one. */
    fun isDigit(c: Char): Boolean = c in '0'..'9'

    fun isLatinLetter(c: Char): Boolean = c in 'a'..'z' || c in 'A'..'Z'
}
