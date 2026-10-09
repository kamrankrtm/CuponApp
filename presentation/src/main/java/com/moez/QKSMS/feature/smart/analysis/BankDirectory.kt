package com.moez.QKSMS.feature.smart.analysis

import java.util.regex.Pattern

/**
 * Iranian banks, credit institutions and wallets, and how to tell which one wrote a message.
 *
 * The issuer is the institution that sent the SMS. A Bank Mellat receipt for a transfer to a
 * Bank Melli account mentions both, and the old reader picked whichever name came first in its
 * list. [resolve] trusts the sender id first, then the signature the bank opens or closes the
 * message with, and treats a name after "به", "مقصد" or "نزد" as the destination.
 */
object BankDirectory {

    /** An alias as written in a message, and the name shown for it. */
    private class Alias(val text: String, val name: String, val strong: Boolean)

    /** Persian names, in [SmsText] form. Bare words ("کوثر", "گردشگری") are weak aliases. */
    private val BANK_ALIASES: List<Alias> = listOf(
        "بلوبانک" to "بلوبانک",
        "بلو بانک" to "بلوبانک",
        "ویپاد" to "ویپاد (ترابانک پاسارگاد)",
        "بانک ملی ایران" to "بانک ملی ایران",
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
        "بانک اقتصاد نوین" to "بانک اقتصاد نوین",
        "اقتصاد نوین" to "بانک اقتصاد نوین",
        "بانک کارآفرین" to "بانک کارآفرین",
        "کارآفرین" to "بانک کارآفرین",
        "بانک گردشگری" to "بانک گردشگری",
        "گردشگری" to "بانک گردشگری",
        "بانک ایران زمین" to "بانک ایران زمین",
        "ایران زمین" to "بانک ایران زمین",
        "بانک سرمایه" to "بانک سرمایه",
        "پست بانک" to "پست بانک ایران",
        "بانک رسالت" to "بانک قرض‌الحسنه رسالت",
        "رسالت" to "بانک قرض‌الحسنه رسالت",
        "بانک مهر ایران" to "بانک قرض‌الحسنه مهر ایران",
        "مهر ایران" to "بانک قرض‌الحسنه مهر ایران",
        "بانک خاورمیانه" to "بانک خاورمیانه",
        "خاورمیانه" to "بانک خاورمیانه",
        "بانک توسعه تعاون" to "بانک توسعه تعاون",
        "توسعه تعاون" to "بانک توسعه تعاون",
        "بانک صنعت و معدن" to "بانک صنعت و معدن",
        "صنعت و معدن" to "بانک صنعت و معدن",
        "بانک توسعه صادرات" to "بانک توسعه صادرات",
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
    ).map { (text, name) -> Alias(text, name, text.contains("بانک") || text in UNIQUE) }
        .sortedByDescending { it.text.length }

    /** Names that only ever mean the bank, though they lack the word "بانک". */
    private val UNIQUE: Set<String>
        get() = setOf("ویپاد", "آبانک", "باجت", "زیپاد", "توبانک", "ملی ایران", "صادرات ایران", "رفاه کارگران")

    /** Wallets and gateways, named when a transaction comes from one rather than a bank. */
    private val WALLET_ALIASES: List<Alias> = listOf(
        "زرین پال" to "زرین‌پال",
        "زرینپال" to "زرین‌پال",
        "بازارپی" to "بازارپی",
        "بازار پی" to "بازارپی",
        "دیجی پی" to "دیجی‌پی",
        "دیجیپی" to "دیجی‌پی",
        "اسنپ پی" to "اسنپ‌پی",
        "اسنپپی" to "اسنپ‌پی",
        "کیف پول" to "کیف پول"
    ).map { (text, name) -> Alias(text, name, true) }

    /** Latin sender ids banks send from ("B.QMEHRIRAN", "BLUBANK"), longest first. */
    private val SENDER_IDS: List<Pair<String, String>> = listOf(
        "mehriran" to "بانک قرض‌الحسنه مهر ایران",
        "resalat" to "بانک قرض‌الحسنه رسالت",
        "blubank" to "بلوبانک",
        "wepod" to "ویپاد (ترابانک پاسارگاد)",
        "melli" to "بانک ملی ایران",
        "bmi" to "بانک ملی ایران",
        "mellat" to "بانک ملت",
        "saderat" to "بانک صادرات ایران",
        "tejarat" to "بانک تجارت",
        "sepah" to "بانک سپه",
        "pasargad" to "بانک پاسارگاد",
        "saman" to "بانک سامان",
        "parsian" to "بانک پارسیان",
        "ayandeh" to "بانک آینده",
        "keshavarzi" to "بانک کشاورزی",
        "maskan" to "بانک مسکن",
        "eghtesad" to "بانک اقتصاد نوین",
        "karafarin" to "بانک کارآفرین",
        "gardeshgari" to "بانک گردشگری",
        "iranzamin" to "بانک ایران زمین",
        "sarmayeh" to "بانک سرمایه",
        "postbank" to "پست بانک ایران",
        "refah" to "بانک رفاه کارگران",
        "sina" to "بانک سینا",
        "blu" to "بلوبانک"
    ).sortedByDescending { it.first.length }

    /** "بلو" opens every Blu Bank message but also sits inside ordinary words, so it must stand alone. */
    private val BLU_WORD = Pattern.compile("(?<!\\p{L})بلو(?!\\p{L})")

    /** Words right before a bank's name that make it where the money went, not who wrote. */
    private val DESTINATION_WORDS = setOf("به", "مقصد", "نزد", "بانک مقصد", "to", "در")

    enum class Source { SENDER, SIGNATURE, BODY }

    /** Who wrote the message and, if named, the other bank the money went to or came from. */
    data class Resolution(
        val issuer: String?,
        val source: Source?,
        val destination: String?,
        /** Whether the sender id itself is a bank's. */
        val bankSender: Boolean,
        /** Whether the body names a bank by a name that only a bank goes by. */
        val strongMention: Boolean
    )

    private class Mention(val start: Int, val end: Int, val name: String, val strong: Boolean)

    /** Resolves the issuer and destination; [text] is the [SmsText]-normalised body. */
    fun resolve(sender: String, text: String): Resolution {
        val senderLower = SmsText.lower(SmsText.text(sender)).trim()
        val fromSender = senderBank(senderLower)
        val mentions = mentions(text)

        var issuer = fromSender
        var source = if (fromSender != null) Source.SENDER else null
        var destination: String? = null

        val firstLineEnd = text.indexOf('\n').let { if (it < 0) text.length else it }
        val lastLineStart = text.lastIndexOf('\n').let { if (it < 0) 0 else it + 1 }
        for (mention in mentions) {
            val toward = isDestination(text, mention.start)
            when {
                issuer == null && !toward && (mention.start < firstLineEnd || mention.start >= lastLineStart) -> {
                    issuer = mention.name
                    source = Source.SIGNATURE
                }
                toward && mention.name != issuer && destination == null -> destination = mention.name
            }
        }
        if (issuer == null) {
            mentions.firstOrNull { !isDestination(text, it.start) }?.let {
                issuer = it.name
                source = Source.BODY
            }
        }
        if (destination == null) {
            destination = mentions.firstOrNull { it.name != issuer && isDestination(text, it.start) }?.name
        }
        if (issuer == null && BLU_WORD.matcher(text).find()) {
            issuer = "بلوبانک"
            source = Source.SIGNATURE
        }
        if (issuer == null) {
            val lower = SmsText.lower(text)
            WALLET_ALIASES.firstOrNull { indexOfWord(lower, it.text) >= 0 }?.let {
                issuer = it.name
                source = Source.BODY
            }
        }

        return Resolution(
            issuer = issuer,
            source = source,
            destination = destination,
            bankSender = fromSender != null || senderLower.contains("bank") || senderLower.contains("بانک"),
            strongMention = mentions.any { it.strong } || BLU_WORD.matcher(text).find()
        )
    }

    /** The bank a sender id belongs to, or null. */
    fun senderBank(senderLower: String): String? {
        SENDER_IDS.firstOrNull { (id, _) -> senderLower.contains(id) }?.let { return it.second }
        return BANK_ALIASES.firstOrNull { indexOfWord(senderLower, it.text) >= 0 }?.name
    }

    /** Every bank named in [text], in order, one per position. */
    private fun mentions(text: String): List<Mention> {
        val found = ArrayList<Mention>()
        for (alias in BANK_ALIASES) {
            var idx = indexOfWord(text, alias.text, 0)
            while (idx >= 0) {
                val end = idx + alias.text.length
                if (found.none { idx < it.end && end > it.start }) found.add(Mention(idx, end, alias.name, alias.strong))
                idx = indexOfWord(text, alias.text, idx + 1)
            }
        }
        return found.sortedBy { it.start }
    }

    private fun isDestination(text: String, start: Int): Boolean {
        val before = text.substring(maxOf(0, start - 20), start)
        val words = before.split(Regex("[^\\p{L}]+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return false
        val last = words.last()
        val lastTwo = if (words.size >= 2) words[words.size - 2] + " " + last else last
        return last in DESTINATION_WORDS || lastTwo in DESTINATION_WORDS ||
            (words.size >= 2 && words[words.size - 2] == "به" && last == "حساب")
    }

    /** [word] in [text] with no letter glued to either side, or -1. */
    fun indexOfWord(text: String, word: String, from: Int = 0): Int {
        var idx = text.indexOf(word, from)
        while (idx >= 0) {
            val end = idx + word.length
            val startOk = idx == 0 || !text[idx - 1].isLetter()
            val endOk = end >= text.length || !text[end].isLetter()
            if (startOk && endOk) return idx
            idx = text.indexOf(word, idx + 1)
        }
        return -1
    }

    /** The bank or wallet named anywhere in [sender] or [text], for display. */
    fun anyName(sender: String, text: String): String? = resolve(sender, text).issuer
}
