package com.moez.QKSMS.feature.smart.analysis

import com.moez.QKSMS.feature.smart.SmartSmsClassifier
import com.moez.QKSMS.feature.smart.model.OtpItem
import com.moez.QKSMS.feature.smart.model.PromoItem
import com.moez.QKSMS.feature.smart.promo.PromoCodeExtractor
import com.moez.QKSMS.feature.smart.promo.PromoParser
import com.moez.QKSMS.feature.smart.promo.PromoStore
import java.security.MessageDigest
import java.util.regex.Pattern

/**
 * Reads one message into an [SmsAnalysis]. Pure: no Android, no I/O, no sender overrides —
 * the user's "Move to …" choices decide where a conversation is listed, never what a message
 * is, so they can never widen what may be uploaded either.
 *
 * Order of trust:
 * 1. a verification code with a clear winner among its numbers;
 * 2. a bank receipt strong enough to outweigh any advertising wording in it;
 * 3. a coupon offer;
 * 4. a weaker receipt the advertising wording does not cancel;
 * 5. a person;
 * 6. an advertisement: promotional wording or the legal opt-out line;
 * 7. otherwise UNKNOWN, which stays in All and notifies normally.
 */
object SmsAnalyzer {

    /** Raise when a rule changes what a message reads as; cached readings are then redone. */
    const val RULES_VERSION = 1

    /** Rules and coupon engine together: a reading from either older engine is stale. */
    const val ENGINE_VERSION = "r$RULES_VERSION.p${PromoStore.ENGINE_VERSION}"

    private val OPT_OUT = Pattern.compile("لغو\\s*\\d")

    private val HEX = "0123456789abcdef".toCharArray()

    fun analyze(
        sender: String,
        body: String,
        date: Long,
        threadId: Long = 0L,
        sourceKey: String? = null
    ): SmsAnalysis {
        val cleanSender = sender.trim()
        val cleanBody = body.trim()
        val text = SmsText.text(cleanBody)
        val lower = SmsText.lower(text)
        val fingerprint = fingerprint(cleanSender, date, text)
        val key = sourceKey?.takeIf { it.isNotEmpty() } ?: "fp:$fingerprint"
        val personal = SmartSmsClassifier.isPersonalNumber(cleanSender)

        val evidence = HashSet<Evidence>()
        val otp = OtpExtractor.extractNormalized(text)
        if (otp.verification) {
            evidence.add(if (SmsSignals.hasVerificationLabel(lower)) Evidence.VERIFICATION_LABEL else Evidence.VERIFICATION_WORDING)
        }
        if (personal) evidence.add(Evidence.PERSONAL_SENDER) else evidence.add(Evidence.COMMERCIAL_SENDER)

        val banking = BankingParser.parse(cleanSender, text, personal)
        evidence.addAll(banking.evidence)

        val selected = otp.selected
        if (selected != null) {
            evidence.add(Evidence.OTP_CANDIDATE)
            evidence.add(if (otp.autoCopyEligible) Evidence.OTP_AUTO_COPY_ELIGIBLE else Evidence.OTP_AMBIGUOUS)
            if (otp.validityMillis != null) evidence.add(Evidence.OTP_VALIDITY_STATED)
            val item = OtpItem(
                id = "otp-$key-${selected.code}",
                code = selected.code,
                serviceName = SmartSmsClassifier.serviceName(cleanSender, text, banking.issuer),
                sender = cleanSender,
                body = cleanBody,
                receivedAt = date,
                sourceKey = key,
                confidence = selected.score,
                expiresAt = otp.validityMillis?.let { date + it }
            )
            return SmsAnalysis(key, fingerprint, ENGINE_VERSION, cleanSender, cleanBody, date, SmsKind.OTP,
                selected.score, evidence, otp, item, banking.takeIf { it.isReceipt }, emptyList(), emptyList())
        }

        // Not gated on promotional wording: the parser also applies what the AI already answered
        val promoAnalyses = PromoParser.analyze(cleanSender, cleanBody, date, threadId)
        val promos: List<PromoItem> = promoAnalyses.map { it.promo }
        if (promos.isNotEmpty()) evidence.add(Evidence.COUPON_CODE)

        val optOut = OPT_OUT.matcher(text).find()
        val advertising = optOut || PromoCodeExtractor.looksPromotional(text) ||
            SmsSignals.AD_WORDS.any { SmsSignals.containsPhrase(lower, it) }
        if (advertising) evidence.add(Evidence.PROMOTIONAL_WORDS)

        val (kind, confidence) = when {
            banking.isReceipt && (promos.isEmpty() || banking.strength >= BankingParser.STRONG_RECEIPT) ->
                SmsKind.BANKING to minOf(100, banking.strength)
            promos.isNotEmpty() -> SmsKind.PROMO to (promos.maxBy { it.confidence }?.confidence ?: 50)
            personal -> SmsKind.PERSONAL to 90
            advertising -> SmsKind.SPAM to if (optOut) 85 else 65
            else -> {
                evidence.add(Evidence.NO_CLEAR_SIGNAL)
                SmsKind.UNKNOWN to 30
            }
        }
        return SmsAnalysis(key, fingerprint, ENGINE_VERSION, cleanSender, cleanBody, date, kind, confidence,
            evidence, otp, null, banking.takeIf { it.isReceipt }, promos, promoAnalyses)
    }

    /** SHA-256 over the normalised sender, timestamp and body, hex-encoded. */
    fun fingerprint(sender: String, date: Long, normalizedBody: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(SmsText.text(sender).trim().toByteArray(Charsets.UTF_8))
        digest.update(0)
        digest.update(date.toString().toByteArray(Charsets.UTF_8))
        digest.update(0)
        digest.update(normalizedBody.toByteArray(Charsets.UTF_8))
        val bytes = digest.digest()
        val chars = CharArray(bytes.size * 2)
        for ((i, b) in bytes.withIndex()) {
            val v = b.toInt() and 0xff
            chars[i * 2] = HEX[v ushr 4]
            chars[i * 2 + 1] = HEX[v and 0x0f]
        }
        return String(chars)
    }

    /** "sms:<provider id>": the provider's own id survives a full re-sync, Realm's does not. */
    fun sourceKeyOf(type: String, contentId: Long): String? =
        if (contentId > 0L && type.isNotEmpty()) "$type:$contentId" else null
}
