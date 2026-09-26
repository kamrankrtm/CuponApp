package com.moez.QKSMS.feature.smart.promo

/**
 * Who an offer is really from and where it is used.
 *
 * @property merchant the shop or service where the code is typed in; null when nothing in the
 *   message or its sender is recognisable
 * @property issuer who sent or sponsors the code when that is not the merchant — the wallet
 *   ("دیجی‌پی"), bank or operator behind a code for somebody else's shop
 * @property payWith the payment method the offer requires, when it names one
 * @property usableAt the shops a sponsor lists for one code; [merchant] is then the sponsor
 * @property confidence 0..100, how sure the resolver is about [merchant]
 * @property basis a short trace of how the answer was reached, for tests and debugging
 */
data class BrandResolution(
    val merchant: Brand?,
    val issuer: Brand?,
    val payWith: Brand?,
    val usableAt: List<Brand>,
    val confidence: Int,
    val basis: String
)

/**
 * Works out the merchant, the sponsor and the payment method of an offer.
 *
 * Picking "the longest brand name in the text" — what the engine used to do — fails on the
 * messages people care about most. A Digipay text about a Filimo code named Digipay because
 * its name was longer; every Snapp service collapsed into the taxi app because the text said
 * only "اسنپ". This reads the roles instead:
 * - a shop named near the code is where it is used;
 * - a wallet, bank or operator is a sponsor, and becomes the merchant only when no shop is named;
 * - a bare family name ("اسنپ", "تپسی", "دیجی‌کالا") is narrowed down by the sender or by
 *   the words around it ("غذا", "هتل", "اقساط").
 */
object BrandResolver {

    /** Words shortly before a wallet or bank that make it the way to pay: "پرداخت از طریق همراه". */
    private val PAYMENT_WORDS = listOf("پرداخت", "از طریق", "درگاه", "کیف پول", "کارت")

    /** Words right before one: "با دیجی‌پی", "توسط اسنپ‌پی". */
    private val PAYMENT_LEADS = listOf("با", "توسط", "همراه")

    private val SENTENCE_BREAKS = charArrayOf('\n', '!', '؟', '?', '؛')

    /**
     * @param sender normalized, lower-cased sender
     * @param body normalized, lower-cased body
     * @param codeStart where the code sits in [body], or -1
     * @param codeSpans every code in the message, which never count as brand mentions
     * @param cueText the words that describe this code's offer, when a message carries several
     */
    fun resolve(
        sender: String,
        body: String,
        codeStart: Int = -1,
        codeEnd: Int = -1,
        codeSpans: List<IntRange> = emptyList(),
        cueText: String = body
    ): BrandResolution {
        val senderBrand = BrandRegistry.matchSender(sender)
        val mentions = BrandRegistry.findMentions(body, codeSpans, senderBrand)
        val merchants = mentions.filter { it.brand.role == BrandRole.MERCHANT }
        val sponsors = mentions.filter { it.brand.role != BrandRole.MERCHANT }
        val segment = segmentAround(body, codeStart, codeEnd)

        var merchant: Brand? = null
        var confidence = 30
        var basis = "unknown"
        var usableAt: List<Brand> = emptyList()

        if (merchants.isNotEmpty()) {
            val inSegment = merchants.filter { it.start >= segment.first && it.end <= segment.last + 1 }
            val pool = collapseFamilies(if (inSegment.isNotEmpty()) inSegment else merchants)
            val distinct = pool.map { it.brand }.distinct()
            val families = distinct.map { it.family ?: it.en }.distinct()
            val sponsor = sponsors.firstOrNull()?.brand ?: senderBrand?.takeIf { it.role != BrandRole.MERCHANT }

            if (families.size >= 2 && sponsor != null) {
                // "با دیجی‌پی در فیلیمو، نماوا و اسنپ‌فود": one code, several shops
                merchant = sponsor
                usableAt = distinct
                confidence = 80
                basis = "sponsor-many"
            } else {
                merchant = nearest(pool, codeStart, codeEnd).brand
                confidence = when {
                    families.size == 1 && inSegment.isNotEmpty() -> 90
                    families.size == 1 -> 85
                    else -> 70
                }
                basis = "body"
            }
        } else if (sponsors.isNotEmpty()) {
            // A wallet or operator advertising its own service
            merchant = nearest(sponsors, codeStart, codeEnd).brand
            confidence = 82
            basis = "sponsor-own"
        } else if (senderBrand != null) {
            // A brand's own sender id ("FILIMO", "DIGIKALA") is a fair witness on its own;
            // a family name like "SNAPP" is capped by its rootConfidence below
            merchant = senderBrand
            confidence = 80
            basis = "sender"
        }

        val named = merchant
        if (named != null && named.isFamilyRoot && usableAt.isEmpty()) {
            val fromSender = senderBrand?.takeIf {
                it != named && !it.isFamilyRoot && BrandRegistry.sameFamily(it, named)
            }
            val cue = BrandRegistry.bestFamilyMember(named.family ?: "", cueText)
            when {
                fromSender != null -> {
                    merchant = fromSender
                    confidence = maxOf(confidence, 85)
                    basis += "+sender-member"
                }
                cue != null && cue.first != named -> {
                    merchant = cue.first
                    confidence = if (cue.second >= 2) 88 else 80
                    basis += "+cue"
                }
                cue != null -> {
                    confidence = maxOf(80, minOf(confidence, 88))
                    basis += "+cue-root"
                }
                else -> {
                    confidence = minOf(confidence, named.rootConfidence)
                    basis += "+bare-root"
                }
            }
        }

        val finalMerchant = merchant
        val issuer = when {
            finalMerchant == null || usableAt.isNotEmpty() -> null
            senderBrand != null && !BrandRegistry.sameFamily(senderBrand, finalMerchant) ->
                // Snapp's own sender id says less than the Snapp service named in the text
                sponsors.firstOrNull { BrandRegistry.sameFamily(it.brand, senderBrand) }?.brand ?: senderBrand
            senderBrand == null && finalMerchant.role == BrandRole.MERCHANT ->
                sponsors.firstOrNull { !BrandRegistry.sameFamily(it.brand, finalMerchant) }?.brand
            else -> null
        }

        // A wallet or instalment service named in an offer for a shop is how to pay there; a
        // bank only when the text says so, since banks also sign messages about other things
        var payWith: Brand? = sponsors.firstOrNull { it.brand.role == BrandRole.PAYMENT && it.brand != finalMerchant }?.brand
            ?: sponsors.firstOrNull { it.brand.role == BrandRole.BANK && isPaymentPhrase(body, it) }?.brand
        if (payWith == null && issuer?.role == BrandRole.PAYMENT) payWith = issuer
        if (payWith == null && senderBrand?.role == BrandRole.PAYMENT && finalMerchant?.role == BrandRole.MERCHANT) {
            payWith = senderBrand
        }
        if (payWith == null && usableAt.isNotEmpty() && finalMerchant?.role == BrandRole.PAYMENT) payWith = finalMerchant
        // "پرداخت با دیجی‌پی" on Digipay's own offer adds nothing
        if (payWith == finalMerchant && usableAt.isEmpty()) payWith = null

        return BrandResolution(finalMerchant, issuer, payWith, usableAt, confidence.coerceIn(0, 100), basis)
    }

    /** "پرداخت با دیجی‌پی", "از طریق کیف پول اسنپ‌پی", "با دیجی‌پی پرداخت کنید". */
    private fun isPaymentPhrase(body: String, mention: BrandMention): Boolean {
        val before = body.substring(maxOf(0, mention.start - 24), mention.start).trimEnd()
        if (PAYMENT_WORDS.any { before.contains(it) }) return true
        if (PAYMENT_LEADS.any { before == it || before.endsWith(" $it") }) return true
        val after = body.substring(mention.end, minOf(body.length, mention.end + 14)).trimStart()
        return after.startsWith("پرداخت")
    }

    /** A specific service beats the bare name of its own family: "اسنپ" goes when "اسنپ‌فود" is there. */
    private fun collapseFamilies(mentions: List<BrandMention>): List<BrandMention> {
        val specificFamilies = mentions.filter { !it.brand.isFamilyRoot && it.brand.family != null }
            .map { it.brand.family }
            .toSet()
        return mentions.filterNot { it.brand.isFamilyRoot && it.brand.family in specificFamilies }
    }

    private fun nearest(mentions: List<BrandMention>, codeStart: Int, codeEnd: Int): BrandMention {
        if (codeStart < 0) return mentions.first()
        return mentions.minBy { mention ->
            when {
                mention.end <= codeStart -> codeStart - mention.end
                mention.start >= codeEnd -> mention.start - codeEnd
                else -> 0
            }
        } ?: mentions.first()
    }

    /** The sentence or line the code sits in; the whole message when there is no code. */
    private fun segmentAround(body: String, codeStart: Int, codeEnd: Int): IntRange {
        if (codeStart < 0 || codeStart >= body.length) return 0 until body.length
        var start = codeStart
        while (start > 0 && body[start - 1] !in SENTENCE_BREAKS && !isFullStop(body, start - 1)) start--
        var end = maxOf(codeEnd, codeStart)
        while (end < body.length && body[end] !in SENTENCE_BREAKS && !isFullStop(body, end)) end++
        return start until end
    }

    /** A dot that ends a sentence, not one inside "snappfood.ir" or "1.5". */
    private fun isFullStop(body: String, index: Int): Boolean =
        body[index] == '.' && (index + 1 >= body.length || body[index + 1] == ' ' || body[index + 1] == '\n')
}
