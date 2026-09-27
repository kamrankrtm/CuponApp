package com.moez.QKSMS.feature.smart.promo

import org.json.JSONArray
import org.json.JSONObject

/**
 * One code the AI read out of one message, in the words of the message.
 *
 * Everything but [code] is optional: the local parser re-reads amounts and dates from the
 * text itself, so these only fill the gaps it could not.
 */
data class AiFinding(
    val code: String,
    val merchant: String? = null,
    val category: String? = null,
    val issuer: String? = null,
    val payWith: String? = null,
    val value: String? = null,
    val minOrder: String? = null,
    val cap: String? = null,
    val expiry: String? = null
)

/**
 * What the app has learned from the AI, so it never pays twice for the same answer.
 *
 * Three layers, each cheaper to hit than the last:
 * - **per message**: the AI's reading of one exact SMS. A re-scan (after an update, or when
 *   the inbox is rebuilt) re-uses it instead of re-sending the message.
 * - **per template**: brands send the same text again and again with only the code, amount or
 *   date changed. A new message with the same shape from the same sender gets the brand — and
 *   the position of the code — from the earlier answer, with no request at all.
 * - **per sender**: a small shop whose every message the AI attributed to the same brand is
 *   recognised by its sender from then on.
 *
 * Pure Kotlin plus org.json, so it can be unit-tested; [PromoStore] loads it and saves it
 * after each AI batch.
 */
object PromoMemory {

    private const val MAX_MESSAGES = 800
    private const val MAX_TEMPLATES = 400
    private const val MAX_SENDERS = 300

    /** Skeletons shorter than this are too generic to recognise a campaign by. */
    private const val MIN_SKELETON = 24

    /** A campaign's shape: who it is for, and which latin token in it is the code. */
    data class Template(
        val merchant: String?,
        val category: String?,
        val issuer: String?,
        val codeTokenIndex: Int
    )

    /** A brand learned for a sender, and whether its messages still agree on it. */
    private data class SenderStat(val merchant: String, val category: String?, val count: Int, val conflicting: Boolean)

    private fun <V> lru(max: Int): LinkedHashMap<String, V> = object : LinkedHashMap<String, V>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, V>?): Boolean = size > max
    }

    private val messages = lru<List<AiFinding>>(MAX_MESSAGES)
    private val templates = lru<Template>(MAX_TEMPLATES)
    private val senders = lru<SenderStat>(MAX_SENDERS)

    private val LATIN_TOKEN = Regex("[A-Za-z0-9](?:[A-Za-z0-9_\\-]*[A-Za-z0-9])?")

    // ---------------------------------------------------------------- keys

    /** A sender as one comparable string: "+98 3000 445" and "3000445" are the same line. */
    fun senderKey(sender: String): String {
        val compact = PromoValueParser.normalize(sender).toLowerCase().filter { it.isLetterOrDigit() }
        if (compact.isEmpty() || !compact.all { it.isDigit() }) return compact
        return when {
            compact.startsWith("0098") -> compact.substring(4)
            compact.startsWith("98") && compact.length > 4 -> compact.substring(2)
            compact.startsWith("0") -> compact.substring(1)
            else -> compact
        }
    }

    /** One exact message: its sender, when it arrived and what it said. */
    fun messageKey(sender: String, date: Long, body: String): String =
        "${senderKey(sender)}|$date|${Integer.toHexString(PromoValueParser.normalize(body).trim().hashCode())}"

    /**
     * The shape of a campaign: the Persian wording with every code, number and link blanked,
     * so "کد FOOD30 با ۳۰٪" and "کد FOOD45 با ۴۵٪" from one sender are the same template.
     */
    fun templateKey(sender: String, body: String): String? {
        val skeleton = skeleton(body)
        if (skeleton.length < MIN_SKELETON) return null
        return "${senderKey(sender)}|${Integer.toHexString(skeleton.hashCode())}|${skeleton.length}"
    }

    private fun skeleton(body: String): String {
        val text = PromoValueParser.normalize(body).toLowerCase()
        val sb = StringBuilder(text.length)
        var inToken = false
        for (c in text) {
            val latinOrDigit = c in 'a'..'z' || c in '0'..'9'
            when {
                latinOrDigit -> {
                    if (!inToken) sb.append('#')
                    inToken = true
                }
                c.isLetter() -> {
                    sb.append(c)
                    inToken = false
                }
                else -> inToken = false
            }
        }
        return sb.toString()
    }

    /** The latin tokens of a message, in order, with where each one sits. */
    fun latinTokens(normalizedBody: String): List<Triple<String, Int, Int>> =
        LATIN_TOKEN.findAll(normalizedBody).map { Triple(it.value, it.range.first, it.range.last + 1) }.toList()

    private fun squash(text: String): String = text.filter { it.isLetterOrDigit() }.toLowerCase()

    // ---------------------------------------------------------------- reading

    @Synchronized
    fun findingsFor(messageKey: String): List<AiFinding>? = messages[messageKey]

    @Synchronized
    fun templateFor(sender: String, body: String): Template? {
        val key = templateKey(sender, body) ?: return null
        return templates[key]
    }

    /** The brand every earlier AI answer gave this sender, once there are two that agree. */
    @Synchronized
    fun learnedForSender(sender: String): Template? {
        val stat = senders[senderKey(sender)] ?: return null
        if (stat.conflicting || stat.count < 2) return null
        return Template(stat.merchant, stat.category, null, -1)
    }

    // ---------------------------------------------------------------- learning

    /**
     * Records the AI's answer for one message. An empty [findings] is an answer too — "no code
     * here" — and stops the message being sent again.
     */
    @Synchronized
    fun remember(sender: String, date: Long, body: String, findings: List<AiFinding>) {
        messages[messageKey(sender, date, body)] = findings

        if (findings.isNotEmpty()) {
            val merchants = findings.mapNotNull { it.merchant?.trim()?.takeIf { m -> m.isNotEmpty() } }.distinct()
            val merchant = merchants.singleOrNull()
            val first = findings.first()

            templateKey(sender, body)?.let { key ->
                val tokenIndex = if (findings.size == 1) {
                    val wanted = squash(first.code)
                    latinTokens(PromoValueParser.normalize(body)).indexOfFirst { squash(it.first) == wanted }
                } else {
                    -1
                }
                templates[key] = Template(merchant, first.category, first.issuer, tokenIndex)
            }

            if (merchant != null) {
                val key = senderKey(sender)
                if (key.isNotEmpty()) {
                    val stat = senders[key]
                    senders[key] = when {
                        stat == null -> SenderStat(merchant, first.category, 1, false)
                        stat.merchant == merchant -> stat.copy(count = stat.count + 1)
                        else -> stat.copy(conflicting = true)
                    }
                }
            }
        }
    }

    @Synchronized
    fun clear() {
        messages.clear()
        templates.clear()
        senders.clear()
    }

    // ---------------------------------------------------------------- persistence

    @Synchronized
    fun export(): String {
        val root = JSONObject()
        root.put("v", 1)
        val m = JSONObject()
        for ((key, findings) in messages) {
            val array = JSONArray()
            findings.forEach { array.put(findingToJson(it)) }
            m.put(key, array)
        }
        root.put("m", m)

        val t = JSONObject()
        for ((key, template) in templates) {
            t.put(key, JSONObject().apply {
                putOpt("m", template.merchant)
                putOpt("k", template.category)
                putOpt("by", template.issuer)
                put("i", template.codeTokenIndex)
            })
        }
        root.put("t", t)

        val s = JSONObject()
        for ((key, stat) in senders) {
            s.put(key, JSONObject().apply {
                put("m", stat.merchant)
                putOpt("k", stat.category)
                put("n", stat.count)
                put("x", stat.conflicting)
            })
        }
        root.put("s", s)
        return root.toString()
    }

    /** Restores what [export] wrote; anything unreadable is dropped, never guessed at. */
    @Synchronized
    fun import(raw: String?) {
        clear()
        if (raw == null || raw.isBlank()) return
        try {
            val root = JSONObject(raw)
            if (root.optInt("v", 0) != 1) return
            root.optJSONObject("m")?.let { m ->
                for (key in m.keys()) {
                    val array = m.optJSONArray(key) ?: continue
                    val findings = ArrayList<AiFinding>()
                    for (i in 0 until array.length()) {
                        array.optJSONObject(i)?.let { findingFromJson(it) }?.let { findings.add(it) }
                    }
                    messages[key] = findings
                }
            }
            root.optJSONObject("t")?.let { t ->
                for (key in t.keys()) {
                    val o = t.optJSONObject(key) ?: continue
                    templates[key] = Template(o.optNullable("m"), o.optNullable("k"), o.optNullable("by"), o.optInt("i", -1))
                }
            }
            root.optJSONObject("s")?.let { s ->
                for (key in s.keys()) {
                    val o = s.optJSONObject(key) ?: continue
                    val merchant = o.optNullable("m") ?: continue
                    senders[key] = SenderStat(merchant, o.optNullable("k"), o.optInt("n", 1), o.optBoolean("x", false))
                }
            }
        } catch (e: Exception) {
            clear()
        }
    }

    fun findingToJson(finding: AiFinding): JSONObject = JSONObject().apply {
        put("c", finding.code)
        putOpt("m", finding.merchant)
        putOpt("k", finding.category)
        putOpt("by", finding.issuer)
        putOpt("pay", finding.payWith)
        putOpt("v", finding.value)
        putOpt("min", finding.minOrder)
        putOpt("cap", finding.cap)
        putOpt("exp", finding.expiry)
    }

    fun findingFromJson(o: JSONObject): AiFinding? {
        val code = o.optNullable("c") ?: o.optNullable("code") ?: return null
        return AiFinding(
            code = code,
            merchant = o.optNullable("m") ?: o.optNullable("merchant") ?: o.optNullable("brand"),
            category = o.optNullable("k") ?: o.optNullable("category"),
            issuer = o.optNullable("by") ?: o.optNullable("issuer"),
            payWith = o.optNullable("pay") ?: o.optNullable("payWith"),
            value = o.optNullable("v") ?: o.optNullable("discountAmount") ?: o.optNullable("value"),
            minOrder = o.optNullable("min") ?: o.optNullable("minOrder"),
            cap = o.optNullable("cap") ?: o.optNullable("maxDiscount"),
            expiry = o.optNullable("exp") ?: o.optNullable("expiryDateText") ?: o.optNullable("expiry")
        )
    }

    private fun JSONObject.optNullable(key: String): String? {
        if (!has(key) || isNull(key)) return null
        val value = optString(key).trim()
        return if (value.isEmpty() || value.equals("null", ignoreCase = true)) null else value
    }
}
