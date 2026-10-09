package com.moez.QKSMS.feature.smart.analysis

import java.util.regex.Pattern

enum class MoneyUnit { RIAL, TOMAN, UNKNOWN }

enum class Direction { CREDIT, DEBIT, UNKNOWN }

/** One amount of money as the message wrote it. */
data class Money(
    val value: Long,
    /** Only what the message itself establishes; never guessed. */
    val unit: MoneyUnit,
    /** +1 or -1 when the amount carries a sign, 0 otherwise. */
    val sign: Int,
    /** Position in the [SmsText]-normalised body. */
    val start: Int,
    val end: Int
) {
    /** "500,000 ریال", or "500,000" when the message does not say which unit. */
    fun label(): String {
        val grouped = group(value)
        return when (unit) {
            MoneyUnit.RIAL -> "$grouped ریال"
            MoneyUnit.TOMAN -> "$grouped تومان"
            MoneyUnit.UNKNOWN -> grouped
        }
    }

    companion object {
        fun group(value: Long): String {
            val raw = value.toString()
            val sb = StringBuilder()
            for ((index, ch) in raw.withIndex()) {
                if (index > 0 && (raw.length - index) % 3 == 0) sb.append(',')
                sb.append(ch)
            }
            return sb.toString()
        }
    }
}

/** What a bank or wallet message says, field by field. */
data class BankingDetails(
    /** Receipt evidence minus advertising evidence; a ranking weight, not a probability. */
    val score: Int,
    /** Receipt evidence alone, before advertising wording is weighed against it. */
    val strength: Int,
    val isReceipt: Boolean,
    val evidence: Set<Evidence>,
    val issuer: String?,
    val issuerSource: BankDirectory.Source?,
    val destinationBank: String?,
    val transaction: Money?,
    val balance: Money?,
    val fee: Money?,
    val direction: Direction
) {
    /** The amount to headline: the transaction, or the fee when that is all that moved. */
    val headline: Money? get() = transaction ?: fee

    val isDeposit: Boolean?
        get() = when (direction) {
            Direction.CREDIT -> true
            Direction.DEBIT -> false
            Direction.UNKNOWN -> null
        }
}

/**
 * Reads bank and wallet messages.
 *
 * The old reader took the first signed number as the transaction, so "مانده:+9,000,000؛
 * برداشت:500,000" became a 9,000,000 deposit; it assumed rials whenever no unit was written;
 * and any advertising word ("فروشگاه", "هدیه", "تخفیف") vetoed a real receipt. Here:
 * - the message is cut into segments (lines, "؛", "،", sentences) and every amount takes the
 *   role of the label nearest to it in its own segment: transaction, balance or fee;
 * - the direction comes from the selected amount's own sign or segment, and only falls back to
 *   the rest of the message when that segment says nothing;
 * - a unit is reported only when the message establishes it;
 * - advertising wording is evidence against a receipt, weighed against the receipt's own
 *   evidence, not a veto; lending words ("وام", "قسط") and a bank's name are never enough alone.
 */
object BankingParser {

    /** Receipt evidence at or above this outweighs any amount of advertising wording. */
    const val STRONG_RECEIPT = 75

    /** Net evidence at or above this makes a message a receipt. */
    const val RECEIPT = 50

    private enum class Role { TRANSACTION, BALANCE, FEE }

    private class Token(
        val value: Long,
        val start: Int,
        val end: Int,
        val sign: Int,
        val unit: MoneyUnit,
        val grouped: Boolean,
        val prose: Boolean,
        val length: Int
    )

    private class Label(val start: Int, val end: Int, val role: Role, val movement: Boolean)

    private class Placed(val token: Token, val role: Role?, val movement: Boolean, val segment: IntRange)

    private val NUMBER = Pattern.compile("\\d{1,3}(?:,\\d{3})+|\\d+")

    private val UNIT = Pattern.compile("^\\s?(ریال|تومان|تومن|rials?|rls|irr|toman|irt)", Pattern.CASE_INSENSITIVE)

    private val SCALE = Pattern.compile("^\\s?(هزار|میلیون|میلیارد)(?![\\p{L}])")

    /** Numbers followed by these are durations, shares or counts. */
    private val NOT_MONEY_AFTER = Pattern.compile("^\\s?(?:درصد|%|٪|روز|ساعت|دقیقه|ثانیه|ماه|سال|نفر|عدد|بار|قسط)")

    /** Words right before a number that make it an identifier rather than money. */
    private val IDENTIFIER_WORDS = setOf(
        "حساب", "کارت", "شماره", "بشماره", "شبا", "کد", "پیگیری", "رهگیری", "مرجع", "سریال",
        "ترمینال", "پایانه", "پذیرنده", "سند", "تاریخ", "ساعت", "شناسه", "قبض", "ق", "ش", "تلفن", "ir"
    )

    private val MASKED_CARD = Pattern.compile("\\d{4}[-\\s]?(?:\\*{2,}|x{2,})|\\*{2,}\\d{2,4}", Pattern.CASE_INSENSITIVE)
    private val ACCOUNT_WORD = Pattern.compile("(?:حساب|کارت|account|card)\\s*[:：]?\\s*\\d")
    /** A bare account number: 10+ digits that are not a phone number. */
    private val LONG_NUMBER = Pattern.compile("(?<![\\d,])\\d{10,}(?![\\d,])")

    private val DATE_STAMP = Pattern.compile("\\d{1,2}:\\d{2}|\\d{2,4}/\\d{1,2}/\\d{1,2}")
    private val OPT_OUT = Pattern.compile("لغو\\s*\\d")

    private val WALLET_MOVES = listOf("شارژ شد", "واریز", "برداشت", "پرداخت", "کسر شد", "افزایش")

    fun parse(sender: String, text: String, personalSender: Boolean = false): BankingDetails {
        val lower = SmsText.lower(text)
        val segments = segments(text)
        val labels = labels(lower)
        val tokens = tokens(text, lower)
        val placed = tokens.mapNotNull { place(it, labels, segments, lower) }

        val transaction = pickTransaction(placed)
        val balance = placed.firstOrNull { it.role == Role.BALANCE }
        val fee = placed.firstOrNull { it.role == Role.FEE }

        val evidence = HashSet<Evidence>()
        var positive = 0
        if (balance != null) {
            evidence.add(Evidence.BALANCE_STATED)
            positive += 40
        }
        if (transaction != null) {
            when {
                transaction.role == Role.TRANSACTION && transaction.movement && !transaction.token.prose -> {
                    evidence.add(Evidence.TRANSACTION_LABELLED)
                    positive += 35
                }
                transaction.role == Role.TRANSACTION && !transaction.token.prose -> {
                    evidence.add(Evidence.AMOUNT_LABELLED)
                    positive += 20
                }
                transaction.role == Role.TRANSACTION -> positive += 10
                else -> Unit
            }
            if (transaction.token.sign != 0) {
                evidence.add(Evidence.SIGNED_AMOUNT)
                positive += if (transaction.role == null) 25 else 10
            }
        }
        if (fee != null) {
            evidence.add(Evidence.FEE_STATED)
            positive += 20
        }
        val account = ACCOUNT_WORD.matcher(lower).find() || MASKED_CARD.matcher(text).find() ||
            hasAccountNumber(text) || identifierNumbers(text, lower)
        if (account) {
            evidence.add(Evidence.ACCOUNT_REFERENCE)
            positive += 15
        }
        if (SmsSignals.containsAny(lower, SmsSignals.TRANSACTION_VERBS)) {
            evidence.add(Evidence.TRANSACTION_VERB)
            positive += 15
        }
        val bank = BankDirectory.resolve(sender, text)
        if (bank.bankSender) {
            evidence.add(Evidence.BANK_SENDER)
            positive += 20
        }
        if (bank.strongMention) {
            evidence.add(Evidence.BANK_SIGNATURE)
            positive += 10
        }
        if (lower.contains("کیف پول") && WALLET_MOVES.any { lower.contains(it) }) {
            evidence.add(Evidence.WALLET_MOVEMENT)
            positive += 40
        }
        if (SmsSignals.containsAny(lower, SmsSignals.GATEWAY_RECEIPTS)) {
            evidence.add(Evidence.GATEWAY_RECEIPT)
            positive += 40
        }
        if (DATE_STAMP.matcher(text).find()) {
            evidence.add(Evidence.DATE_STAMP)
            positive += 5
        }

        var penalty = 0
        if (OPT_OUT.matcher(text).find()) {
            evidence.add(Evidence.OPT_OUT_LINE)
            penalty += 40
        }
        val adWords = SmsSignals.AD_WORDS.count { SmsSignals.containsPhrase(lower, it) }
        if (adWords > 0) {
            evidence.add(Evidence.PROMOTIONAL_WORDS)
            penalty += minOf(30, adWords * 15)
        }
        if (SmsSignals.containsAny(lower, SmsSignals.LENDING_WORDS)) {
            evidence.add(Evidence.LENDING_WORDS)
            if (balance == null && !account && (transaction?.token?.sign ?: 0) == 0) penalty += 15
        }

        val hasMoney = transaction != null || balance != null || fee != null
        val net = positive - penalty
        val receipt = hasMoney && when {
            // Someone texting about money is not a bank, unless the message is unmistakably one
            personalSender -> positive >= STRONG_RECEIPT && (balance != null || account)
            else -> positive >= STRONG_RECEIPT || net >= RECEIPT
        }

        val selected = transaction?.takeIf { it.role != Role.BALANCE && it.role != Role.FEE }
        val direction = when {
            selected != null -> directionOf(selected, placed, lower, segments)
            fee != null -> Direction.DEBIT
            else -> Direction.UNKNOWN
        }

        return BankingDetails(
            score = net,
            strength = positive,
            isReceipt = receipt,
            evidence = evidence,
            issuer = bank.issuer,
            issuerSource = bank.source,
            destinationBank = bank.destination,
            transaction = selected?.let { money(it.token, text, it.segment) },
            balance = balance?.let { money(it.token, text, it.segment) },
            fee = fee?.let { money(it.token, text, it.segment) },
            direction = direction
        )
    }

    /** The amount that moved: labelled first, then signed, then one with a unit. */
    private fun pickTransaction(placed: List<Placed>): Placed? {
        val candidates = placed.filter { it.role == Role.TRANSACTION || (it.role == null && (it.token.sign != 0 || it.token.unit != MoneyUnit.UNKNOWN)) }
        return candidates.firstOrNull { it.role == Role.TRANSACTION && it.movement && !it.token.prose }
            ?: candidates.firstOrNull { it.role == Role.TRANSACTION && !it.token.prose }
            ?: candidates.firstOrNull { it.role == null && it.token.sign != 0 }
            ?: candidates.firstOrNull { it.role == Role.TRANSACTION }
            ?: candidates.firstOrNull { it.role == null && !it.token.prose }
    }

    private fun directionOf(selected: Placed, placed: List<Placed>, lower: String, segments: List<IntRange>): Direction {
        when (selected.token.sign) {
            1 -> return Direction.CREDIT
            -1 -> return Direction.DEBIT
        }
        nearestDirection(lower, selected.segment, selected.token.start)?.let { return it }

        // The segment says nothing ("مبلغ: 2,319"); the rest of the message may ("واریز سود"),
        // as long as it is not the balance's or the fee's own line
        val excluded = placed.filter { it.role == Role.BALANCE || it.role == Role.FEE }.map { it.segment }.toSet()
        var best: Pair<Int, Direction>? = null
        for (segment in segments) {
            if (segment in excluded) continue
            for ((words, dir) in listOf(SmsSignals.CREDIT_WORDS to Direction.CREDIT, SmsSignals.DEBIT_WORDS to Direction.DEBIT)) {
                for (word in words) {
                    val idx = SmsSignals.indexOfPhrase(lower, word, segment.first)
                    if (idx < 0 || idx > segment.last) continue
                    if (best == null || idx < best.first) best = idx to dir
                }
            }
        }
        return best?.second ?: Direction.UNKNOWN
    }

    /** The direction word closest to [anchor] inside [segment], if any. */
    private fun nearestDirection(lower: String, segment: IntRange, anchor: Int): Direction? {
        var best: Pair<Int, Direction>? = null
        for ((words, dir) in listOf(SmsSignals.CREDIT_WORDS to Direction.CREDIT, SmsSignals.DEBIT_WORDS to Direction.DEBIT)) {
            for (word in words) {
                var idx = SmsSignals.indexOfPhrase(lower, word, segment.first)
                while (idx in segment) {
                    val distance = Math.abs(idx - anchor)
                    if (best == null || distance < best.first) best = distance to dir
                    idx = SmsSignals.indexOfPhrase(lower, word, idx + 1)
                }
            }
        }
        return best?.second
    }

    private fun money(token: Token, text: String, segment: IntRange): Money {
        var unit = token.unit
        if (unit == MoneyUnit.UNKNOWN) unit = unitIn(text.substring(segment.first, segment.last + 1))
        if (unit == MoneyUnit.UNKNOWN) unit = unitIn(text)
        return Money(token.value, unit, token.sign, token.start, token.end)
    }

    /** The one unit [text] uses, or UNKNOWN when it names none or both. */
    private fun unitIn(text: String): MoneyUnit {
        val lower = SmsText.lower(text)
        val rial = lower.contains("ریال") || Regex("(?<![a-z])(rials?|rls|irr)(?![a-z])").containsMatchIn(lower)
        val toman = lower.contains("تومان") || lower.contains("تومن") || Regex("(?<![a-z])(toman|irt)(?![a-z])").containsMatchIn(lower)
        return when {
            rial && !toman -> MoneyUnit.RIAL
            toman && !rial -> MoneyUnit.TOMAN
            else -> MoneyUnit.UNKNOWN
        }
    }

    /** Lines, "؛", "،", ";", "|" and sentence ends cut the message into segments. */
    private fun segments(text: String): List<IntRange> {
        val result = ArrayList<IntRange>()
        var start = 0
        for (i in text.indices) {
            val c = text[i]
            val cut = c == '\n' || c == '؛' || c == ';' || c == '|' || c == '،' ||
                (c == '.' && (i + 1 >= text.length || text[i + 1] == ' ' || text[i + 1] == '\n'))
            if (cut) {
                if (i > start) result.add(start until i)
                start = i + 1
            }
        }
        if (start < text.length) result.add(start until text.length)
        return result
    }

    private fun labels(lower: String): List<Label> {
        val found = ArrayList<Label>()
        fun add(phrases: List<String>, role: Role, movement: Boolean) {
            for (phrase in phrases) {
                var idx = SmsSignals.indexOfPhrase(lower, phrase, 0)
                while (idx >= 0) {
                    found.add(Label(idx, idx + phrase.length, role, movement))
                    idx = SmsSignals.indexOfPhrase(lower, phrase, idx + 1)
                }
            }
        }
        add(SmsSignals.BALANCE_LABELS, Role.BALANCE, false)
        add(SmsSignals.FEE_LABELS, Role.FEE, false)
        add(SmsSignals.MOVEMENT_LABELS, Role.TRANSACTION, true)
        add(SmsSignals.AMOUNT_LABELS, Role.TRANSACTION, false)
        return found
    }

    /** Every number in the message that could be money. */
    private fun tokens(text: String, lower: String): List<Token> {
        val result = ArrayList<Token>()
        val m = NUMBER.matcher(text)
        while (m.find()) {
            val start = m.start()
            val end = m.end()
            val b1 = text.getOrElse(start - 1) { ' ' }
            val a1 = text.getOrElse(end) { ' ' }
            val a2 = text.getOrElse(end + 1) { ' ' }
            // Pieces of dates, times, decimals, masked cards and alphanumeric references
            val b2 = text.getOrElse(start - 2) { ' ' }
            // "برداشت:500,000" is a label and its amount; "10:31" and "1405/07" are not amounts
            if (b1 in "/:._,*" && (SmsText.isDigit(b2) || b2 == '*')) continue
            if (b1 == '*' || b1 == '_' || SmsText.isLatinLetter(b1) || SmsText.isLatinLetter(a1)) continue
            if (a1 in "/:_*" || ((a1 == ',' || a1 == '.') && SmsText.isDigit(a2))) continue
            if (a1 == '-' && SmsText.isDigit(a2)) continue
            if (b1 == '-' && SmsText.isDigit(b2)) continue

            val raw = m.group()
            val grouped = raw.contains(',')
            val digits = raw.replace(",", "")
            if (digits.length > 15) continue
            val rest = text.substring(end)
            if (NOT_MONEY_AFTER.matcher(rest).lookingAt()) continue

            var value = digits.toLongOrNull() ?: continue
            var tail = end
            var prose = false
            val scale = SCALE.matcher(rest)
            if (scale.lookingAt()) {
                value *= when (scale.group(1)) {
                    "هزار" -> 1_000L
                    "میلیون" -> 1_000_000L
                    else -> 1_000_000_000L
                }
                tail = end + scale.end()
                prose = true
            }
            val unitMatch = UNIT.matcher(text.substring(tail))
            val unit = if (unitMatch.lookingAt()) {
                val word = unitMatch.group(1).toLowerCase()
                if (word.startsWith("ت") || word.startsWith("toman") || word == "irt") MoneyUnit.TOMAN else MoneyUnit.RIAL
            } else MoneyUnit.UNKNOWN

            // Identifiers: long bare numbers and numbers right after "حساب", "کارت", "ش.ق"…
            if (!grouped && unit == MoneyUnit.UNKNOWN && !prose && digits.length >= 10) continue
            if (!grouped && unit == MoneyUnit.UNKNOWN && !prose && digits.length <= 3) continue
            if (precededByIdentifier(lower, start)) continue

            result.add(Token(value, start, end, signOf(text, start, end), unit, grouped, prose, digits.length))
        }
        return result
    }

    /** "+7,000,000", "- 400,000" or "400,000-": a sign that is not part of a range or a date. */
    private fun signOf(text: String, start: Int, end: Int): Int {
        var i = start - 1
        if (i >= 0 && text[i] == ' ') i--
        if (i >= 0 && (text[i] == '+' || text[i] == '-')) {
            val before = text.getOrElse(i - 1) { ' ' }
            if (!before.isLetterOrDigit()) return if (text[i] == '+') 1 else -1
        }
        var j = end
        if (j < text.length && text[j] == ' ') j++
        if (j < text.length && (text[j] == '+' || text[j] == '-')) {
            val after = text.getOrElse(j + 1) { ' ' }
            if (!after.isLetterOrDigit()) return if (text[j] == '+') 1 else -1
        }
        return 0
    }

    private fun precededByIdentifier(lower: String, start: Int): Boolean {
        val before = lower.substring(maxOf(0, start - 16), start)
        if (before.isNotEmpty() && SmsText.isDigit(before.last())) return false
        val words = before.split(Regex("[^\\p{L}]+")).filter { it.isNotEmpty() }
        val tail = before.substring(before.lastIndexOf(words.lastOrNull() ?: return false) + words.last().length)
        // Only the word right before the number, with nothing but punctuation in between
        return words.last() in IDENTIFIER_WORDS && tail.trim().all { it == ':' || it == '：' || it == '.' || it == '#' || it == '-' }
    }

    private fun hasAccountNumber(text: String): Boolean {
        val m = LONG_NUMBER.matcher(text)
        while (m.find()) {
            val digits = m.group()
            if (digits.length >= 12 || !digits.startsWith("0")) return true
        }
        return false
    }

    /** "حساب:56007", "ش.ق 1404…": numbers that identify, which only bank texts carry. */
    private fun identifierNumbers(text: String, lower: String): Boolean {
        val m = Pattern.compile("\\d{4,}").matcher(text)
        while (m.find()) {
            val start = m.start()
            val b1 = text.getOrElse(start - 1) { ' ' }
            if (SmsText.isDigit(b1)) continue
            val before = lower.substring(maxOf(0, start - 12), start)
            if (Regex("(?:حساب|کارت|بشماره|شماره حساب)\\s*[:：]?\\s*$").containsMatchIn(before)) return true
        }
        return false
    }

    private fun place(token: Token, labels: List<Label>, segments: List<IntRange>, lower: String): Placed? {
        val segment = segments.firstOrNull { token.start in it } ?: return null
        // The closest label before the number in its own segment, with no other number between
        val previousDigit = (token.start - 1 downTo segment.first).firstOrNull { SmsText.isDigit(lower[it]) } ?: (segment.first - 1)
        val before = labels.filter { it.start >= segment.first && it.start > previousDigit && it.end <= token.start && token.start - it.end <= 30 }
            .sortedWith(compareByDescending<Label> { it.end }.thenByDescending { it.end - it.start })
            .firstOrNull()
        if (before != null) return Placed(token, before.role, before.movement, segment)

        // "۱۰,۰۰۰,۰۰۰ تومان شارژ شد": the label follows
        val nextDigit = (token.end until segment.last + 1).firstOrNull { SmsText.isDigit(lower[it]) } ?: (segment.last + 1)
        val after = labels.filter { it.start >= token.end && it.start < nextDigit && it.start - token.end <= 15 }
            .minBy { it.start }
        if (after != null) return Placed(token, after.role, after.movement, segment)
        return Placed(token, null, false, segment)
    }
}
