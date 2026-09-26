package com.moez.QKSMS.feature.smart.promo

import org.json.JSONArray
import org.json.JSONObject
import java.util.regex.Pattern

/**
 * What goes to the model and how its answer is read, kept free of Android and networking so
 * both halves are unit-tested.
 *
 * The request is built to be cheap: one short fixed instruction shared by a whole batch,
 * each message trimmed to what can matter (no links beyond their domain, no opt-out line, no
 * emoji, no greeting name) and an answer format with one- or two-letter keys, because answer
 * tokens cost several times what prompt tokens do.
 */
object AiPromoProtocol {

    val SYSTEM_PROMPT = """
        You extract discount codes from Iranian promotional SMS. Input: one message per line, "#<n> <sender>: <text>".
        Reply with JSON only: {"r":[{"i":n,"c":"CODE","m":"merchant","k":"category","by":"sponsor","pay":"payment","v":"value","min":"min order","cap":"max discount","exp":"deadline"}]}
        c: the exact code the user types, copied from the text. Skip messages without a code. Never invent one.
        m: the specific service where the code is used, in Persian (e.g. اسنپ‌فود, اسنپ‌شاپ, اسنپ‌تریپ, دیجی‌کالا جت, فیلیمو). Snapp, Tapsi and Digikala run many services: name the one the text is about (food→اسنپ‌فود, hotel/flight→اسنپ‌تریپ, ride→اسنپ), not the parent.
        by: the wallet, bank or operator that sent or sponsors the code, only if it is not the merchant.
        pay: the payment method the offer requires, if stated.
        k: food, supermarket, ecommerce, transport, entertainment, fintech, telecom, services or other.
        v, min, cap, exp: copy the words from the text; omit when absent.
        One object per code. Omit empty fields.
    """.trimIndent()

    /** One message of a batch; [index] is the number the model refers back to. */
    data class Item(val index: Int, val sender: String, val body: String)

    /** One answer tied back to its message. */
    data class IndexedFinding(val index: Int, val finding: AiFinding)

    /** Tokens used by one request, as the provider reported them. */
    data class Usage(val promptTokens: Int, val completionTokens: Int)

    private const val MAX_BODY_CHARS = 320
    private const val MAX_SENDER_CHARS = 20

    private val OPT_OUT = Pattern.compile("لغو\\s*\\d+|ارسال\\s*عدد\\s*\\d+\\s*برای\\s*لغو|stop\\s*\\d*", Pattern.CASE_INSENSITIVE)

    private val URL = Pattern.compile("(?:https?://)?(?:www\\.)?([A-Za-z0-9\\-]+(?:\\.[A-Za-z0-9\\-]+)*\\.[A-Za-z]{2,6})(?:/\\S*)?", Pattern.CASE_INSENSITIVE)

    /** "کامران عزیز" → "کاربر عزیز": a name adds nothing to the reading and should not leave the phone. */
    private val GREETING_NAME = Pattern.compile("(?<![\\p{L}])\\p{L}+(\\s+(?:عزیز|گرامی|جان))(?![\\p{L}])")

    private const val KEPT_PUNCTUATION = ".,:;!?%٪،؛()-+/«»\"'*#@_|"

    /** A message reduced to what can matter for reading its offer. */
    fun compact(body: String): String {
        var text = PromoValueParser.normalize(body)
        text = OPT_OUT.matcher(text).replaceAll(" ")
        text = URL.matcher(text).replaceAll("$1")
        text = GREETING_NAME.matcher(text).replaceAll("کاربر$1")

        val sb = StringBuilder(text.length)
        for (c in text) {
            when {
                c == '\n' -> sb.append(" | ")
                c.isLetterOrDigit() || c == ' ' || KEPT_PUNCTUATION.indexOf(c) >= 0 -> sb.append(c)
                else -> sb.append(' ')
            }
        }
        val collapsed = sb.toString()
            .replace(Regex("\\s+"), " ")
            .replace(Regex("(\\s*\\|\\s*)+"), " | ")
            .trim(' ', '|')
        return if (collapsed.length <= MAX_BODY_CHARS) collapsed else collapsed.take(MAX_BODY_CHARS).trimEnd() + "…"
    }

    fun compactSender(sender: String): String {
        val text = PromoValueParser.normalize(sender).replace("\n", " ").trim()
        return if (text.length <= MAX_SENDER_CHARS) text else text.take(MAX_SENDER_CHARS)
    }

    fun buildUserContent(items: List<Item>): String {
        val sb = StringBuilder()
        for (item in items) {
            sb.append('#').append(item.index).append(' ')
                .append(compactSender(item.sender)).append(": ")
                .append(compact(item.body)).append('\n')
        }
        return sb.toString().trimEnd()
    }

    /** Room for one answer object per message and some slack; an answer never needs more. */
    fun maxTokensFor(messageCount: Int): Int = 80 + messageCount * 90

    /** The chat-completions request body. */
    fun buildRequest(model: String, items: List<Item>): JSONObject = JSONObject().apply {
        put("model", model)
        put("temperature", 0)
        put("max_tokens", maxTokensFor(items.size))
        put("messages", JSONArray().apply {
            put(JSONObject().apply {
                put("role", "system")
                put("content", SYSTEM_PROMPT)
            })
            put(JSONObject().apply {
                put("role", "user")
                put("content", buildUserContent(items))
            })
        })
    }

    /** The assistant text of a chat-completions response, or "" when there is none. */
    fun contentOf(response: String): String = try {
        JSONObject(response)
            .optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content")
            ?: ""
    } catch (e: Exception) {
        ""
    }

    fun usageOf(response: String): Usage = try {
        val usage = JSONObject(response).optJSONObject("usage")
        Usage(usage?.optInt("prompt_tokens", 0) ?: 0, usage?.optInt("completion_tokens", 0) ?: 0)
    } catch (e: Exception) {
        Usage(0, 0)
    }

    private val FLAT_OBJECT = Pattern.compile("\\{[^{}]*\\}")

    /**
     * Reads the model's answer, forgivingly: code fences, a bare array, prose around the JSON
     * and an answer cut off by the token limit all still yield every complete finding.
     */
    fun parse(content: String): List<IndexedFinding> {
        val clean = content
            .replace(Regex("^```(?:json)?", RegexOption.MULTILINE), "")
            .replace(Regex("```$", RegexOption.MULTILINE), "")
            .trim()

        val objects = ArrayList<JSONObject>()
        val array: JSONArray? = try {
            val root = JSONObject(clean)
            root.optJSONArray("r") ?: root.optJSONArray("results") ?: root.optJSONArray("data")
        } catch (e: Exception) {
            try {
                JSONArray(clean)
            } catch (e2: Exception) {
                null
            }
        }

        if (array != null) {
            for (i in 0 until array.length()) array.optJSONObject(i)?.let { objects.add(it) }
        } else {
            val m = FLAT_OBJECT.matcher(clean)
            while (m.find()) {
                try {
                    objects.add(JSONObject(m.group()))
                } catch (e: Exception) {
                    // A fragment cut off mid-object; the rest are still good
                }
            }
        }

        val findings = ArrayList<IndexedFinding>()
        for (o in objects) {
            val index = when {
                o.has("i") -> o.optInt("i", -1)
                o.has("index") -> o.optInt("index", -1)
                else -> -1
            }
            val finding = PromoMemory.findingFromJson(o) ?: continue
            if (index < 0) continue
            findings.add(IndexedFinding(index, finding))
        }
        return findings
    }
}
