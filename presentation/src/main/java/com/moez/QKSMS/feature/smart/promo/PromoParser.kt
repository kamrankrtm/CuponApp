package com.moez.QKSMS.feature.smart.promo

import com.moez.QKSMS.feature.smart.model.PromoItem

/**
 * Turns one SMS into discount cards: the codes, who they are for, and what they are worth.
 *
 * The order of trust is fixed:
 * 1. an AI answer remembered for this exact message ([PromoMemory]) — already paid for, and
 *    already checked against the text;
 * 2. the rules engine on the device, with the brand filled in from a learned template or
 *    sender when the text alone leaves it unclear;
 * 3. nothing: no code, no card.
 *
 * Each result carries separate confidences for the code and the brand, which is what
 * [AiEscalation] reads to decide whether the message is worth a request.
 */
object PromoParser {

    enum class Source(val tag: String) {
        LOCAL(PromoItem.SOURCE_LOCAL),
        LEARNED(PromoItem.SOURCE_LEARNED),
        AI(PromoItem.SOURCE_AI)
    }

    /** One card plus what the parser thinks of it. */
    data class Analysis(
        val promo: PromoItem,
        val codeConfidence: Int,
        val brandConfidence: Int,
        val valueFound: Boolean,
        val source: Source
    )

    /** Codes below this are not shown at all. */
    private const val MIN_CONFIDENCE = 40

    /** A second code in one message must be labelled to count as its own offer. */
    private const val EXTRA_CODE_CONFIDENCE = 80

    /** Brand confidence given to a brand learned from an earlier AI answer. */
    private const val LEARNED_BRAND_CONFIDENCE = 88

    /** Confidence of an AI-read code that was found verbatim in the message. */
    private const val AI_CONFIDENCE = 88

    fun parse(sender: String, body: String, date: Long, threadId: Long = 0L): List<PromoItem> =
        analyze(sender, body, date, threadId).map { it.promo }

    fun analyze(sender: String, body: String, date: Long, threadId: Long = 0L): List<Analysis> {
        val normalizedBody = PromoValueParser.normalize(body)

        // Both gates live here rather than in the callers, so no entry point can skip them: a
        // bank's login SMS once reached the discounts tab, its SMS Retriever hash offered as a code.
        if (PromoCodeExtractor.isVerificationMessage(normalizedBody)) return emptyList()
        if (PromoCodeExtractor.isSpentNotice(normalizedBody)) return emptyList()

        val key = PromoMemory.messageKey(sender, date, body)
        val remembered = PromoMemory.findingsFor(key)
        if (remembered != null) {
            // Where each code sits, so a message with several codes gives each its own figures
            val placed = remembered.mapNotNull { finding ->
                val start = PromoCodeExtractor.indexOf(finding.code.trim(), normalizedBody)
                if (start < 0) null else CodeCandidate(finding.code.trim(), AI_CONFIDENCE, start, start + finding.code.trim().length)
            }.sortedBy { it.start }
            val fromAi = remembered.mapNotNull { fromFinding(it, sender, body, normalizedBody, date, threadId, key, placed) }
                .distinctBy { it.promo.code.toUpperCase() }
            if (fromAi.isNotEmpty()) return fromAi
            // The AI read this message and found no code: only a labelled local code survives that
            return local(sender, body, normalizedBody, date, threadId, key)
                .filter { it.codeConfidence >= EXTRA_CODE_CONFIDENCE }
        }

        if (!PromoCodeExtractor.looksPromotional(normalizedBody)) return emptyList()
        return local(sender, body, normalizedBody, date, threadId, key)
    }

    // ---------------------------------------------------------------- the rules engine

    private fun local(
        sender: String,
        body: String,
        normalizedBody: String,
        date: Long,
        threadId: Long,
        key: String
    ): List<Analysis> {
        val candidates = PromoCodeExtractor.extractAll(normalizedBody).toMutableList()
        val template = PromoMemory.templateFor(sender, body)

        // Same campaign as a message the AI already read: its code sits in the same place
        if ((candidates.isEmpty() || candidates.first().confidence < 60) && template != null && template.codeTokenIndex >= 0) {
            PromoMemory.latinTokens(normalizedBody).getOrNull(template.codeTokenIndex)?.let { (token, start, end) ->
                if (token.length >= 3 && token.any { it.isLetter() } && !BrandRegistry.isBrandWord(token)) {
                    candidates.removeAll { it.code.equals(token, ignoreCase = true) }
                    candidates.add(0, CodeCandidate(token, 85, start, end))
                }
            }
        }

        val best = candidates.firstOrNull() ?: return emptyList()
        if (best.confidence < MIN_CONFIDENCE) return emptyList()
        val kept = listOf(best) + candidates.drop(1).filter { it.confidence >= EXTRA_CODE_CONFIDENCE }
        val ordered = kept.sortedBy { it.start }

        val lowerBody = normalizedBody.toLowerCase()
        val lowerSender = PromoValueParser.normalize(sender).toLowerCase()
        val codeSpans = ordered.filter { it.start >= 0 }.map { it.start until it.end }
        val learned = template?.takeIf { it.merchant != null } ?: PromoMemory.learnedForSender(sender)

        return kept.map { candidate ->
            val scope = scopeFor(normalizedBody, candidate, ordered)
            val resolution = BrandResolver.resolve(
                lowerSender, lowerBody, candidate.start, candidate.end, codeSpans, scope.text.toLowerCase()
            )
            var merchant = resolution.merchant
            var issuer = resolution.issuer
            var brandConfidence = resolution.confidence
            var source = Source.LOCAL

            // A learned brand fills in for a vague reading, never over a clear one
            if (learned != null && brandConfidence < 80) {
                val learnedBrand = learned.merchant?.let { brandFromName(it, learned.category) }
                if (learnedBrand != null) {
                    merchant = learnedBrand
                    brandConfidence = LEARNED_BRAND_CONFIDENCE
                    source = Source.LEARNED
                    learned.issuer?.let { name -> BrandRegistry.byName(name) }
                        ?.takeIf { !BrandRegistry.sameFamily(it, learnedBrand) }
                        ?.let { issuer = it }
                }
            }

            // "فروشگاه لباس رز: ..." from a bare number: the shop signs its own message
            if (merchant == null) {
                headerBrand(normalizedBody)?.let {
                    merchant = it
                    brandConfidence = HEADER_BRAND_CONFIDENCE
                }
            }

            build(
                code = candidate.code,
                codeConfidence = candidate.confidence,
                brand = merchant ?: unknownBrandFor(sender),
                brandConfidence = if (merchant == null) 30 else brandConfidence,
                issuer = issuer,
                payWith = resolution.payWith,
                usableAt = if (merchant == resolution.merchant) resolution.usableAt else emptyList(),
                scope = scope,
                normalizedBody = normalizedBody,
                sender = sender,
                body = body,
                date = date,
                threadId = threadId,
                key = key,
                source = source,
                aiFinding = null
            )
        }
    }

    // ---------------------------------------------------------------- AI answers

    private fun fromFinding(
        finding: AiFinding,
        sender: String,
        body: String,
        normalizedBody: String,
        date: Long,
        threadId: Long,
        key: String,
        placed: List<CodeCandidate>
    ): Analysis? {
        val code = finding.code.trim()
        // The message is the ground truth: a code that is not in it was invented or misplaced
        if (!PromoCodeExtractor.appearsIn(code, normalizedBody)) return null

        val lowerBody = normalizedBody.toLowerCase()
        val lowerSender = PromoValueParser.normalize(sender).toLowerCase()
        val start = PromoCodeExtractor.indexOf(code, normalizedBody)
        val end = if (start >= 0) start + code.length else -1
        val spans = if (start >= 0) listOf(start until end) else emptyList()
        val local = BrandResolver.resolve(lowerSender, lowerBody, start, end, spans)

        // The AI's brand is taken only when the message backs it up: the brand, or another
        // service of its family, is named in the text or is the sender. A brand carries an app
        // to open and a colour, so a wrong one is worse than none.
        val claimed = finding.merchant?.let { BrandRegistry.byName(it) }
        val merchant: Brand? = when {
            claimed != null && isBackedByMessage(claimed, lowerBody, lowerSender, local) -> claimed
            claimed == null && finding.merchant != null && namedIn(finding.merchant, lowerBody, lowerSender) ->
                brandFromName(finding.merchant, finding.category)
            else -> local.merchant
        }
        val issuer = finding.issuer?.let { BrandRegistry.byName(it) }
            ?.takeIf { isBackedByMessage(it, lowerBody, lowerSender, local) && !BrandRegistry.sameFamily(it, merchant) }
            ?: local.issuer?.takeIf { !BrandRegistry.sameFamily(it, merchant) }
            // The rules took the wallet or operator for the shop; the AI named the real shop
            ?: local.merchant?.takeIf {
                merchant != null && it.role != BrandRole.MERCHANT && !BrandRegistry.sameFamily(it, merchant)
            }
        val payWith = finding.payWith?.let { BrandRegistry.byName(it) }
            ?.takeIf { isBackedByMessage(it, lowerBody, lowerSender, local) }
            ?: local.payWith
            ?: issuer?.takeIf { it.role == BrandRole.PAYMENT }

        val candidate = CodeCandidate(code, AI_CONFIDENCE, start, end)
        return build(
            code = code,
            codeConfidence = AI_CONFIDENCE,
            brand = merchant ?: unknownBrandFor(sender),
            brandConfidence = if (merchant == null) 30 else AI_CONFIDENCE,
            issuer = issuer,
            payWith = payWith?.takeIf { it != merchant },
            usableAt = if (merchant == local.merchant) local.usableAt else emptyList(),
            scope = scopeFor(normalizedBody, candidate, if (placed.any { it.start == start }) placed else listOf(candidate)),
            normalizedBody = normalizedBody,
            sender = sender,
            body = body,
            date = date,
            threadId = threadId,
            key = key,
            source = Source.AI,
            aiFinding = finding
        )
    }

    private fun isBackedByMessage(brand: Brand, lowerBody: String, lowerSender: String, local: BrandResolution): Boolean {
        if (namedIn(brand.fa, lowerBody, lowerSender) || namedIn(brand.en, lowerBody, lowerSender)) return true
        if ((brand.keywords + brand.weakKeywords).any { namedIn(it, lowerBody, lowerSender) }) return true
        if (local.usableAt.contains(brand)) return true
        if (BrandRegistry.sameFamily(brand, local.merchant) || BrandRegistry.sameFamily(brand, local.issuer)) return true
        return BrandRegistry.sameFamily(brand, BrandRegistry.matchSender(lowerSender))
    }

    private fun namedIn(name: String, lowerBody: String, lowerSender: String): Boolean {
        val wanted = PromoValueParser.normalize(name).toLowerCase().trim()
        if (wanted.length < 2) return false
        val squashed = wanted.replace(" ", "")
        return lowerBody.contains(wanted) || lowerSender.contains(wanted) ||
            lowerBody.replace(" ", "").contains(squashed) || lowerSender.replace(" ", "").contains(squashed)
    }

    // ---------------------------------------------------------------- building a card

    /** The part of the message that talks about [candidate], when it carries several codes. */
    private data class Scope(val text: String, val anchor: Int)

    private fun scopeFor(normalizedBody: String, candidate: CodeCandidate, ordered: List<CodeCandidate>): Scope {
        if (ordered.size <= 1 || candidate.start < 0) return Scope(normalizedBody, candidate.start)
        val index = ordered.indexOfFirst { it.start == candidate.start }
        val from = if (index > 0) splitPoint(normalizedBody, ordered[index - 1].end, candidate.start) else 0
        val to = if (index in 0 until ordered.size - 1) {
            splitPoint(normalizedBody, candidate.end, ordered[index + 1].start)
        } else {
            normalizedBody.length
        }
        if (from >= to) return Scope(normalizedBody, candidate.start)
        return Scope(normalizedBody.substring(from, to), candidate.start - from)
    }

    /** Where the text between two codes changes subject: a line break, a comma, an "و". */
    private fun splitPoint(text: String, from: Int, to: Int): Int {
        if (from >= to) return from
        val gap = text.substring(from, to)
        val breaks = listOf("\n", "،", "؛", ". ", " و ", ",")
        val last = breaks.map { mark -> gap.lastIndexOf(mark).let { if (it < 0) -1 else it + mark.length } }.max() ?: -1
        return from + if (last > 0) last else gap.length / 2
    }

    private fun build(
        code: String,
        codeConfidence: Int,
        brand: Brand,
        brandConfidence: Int,
        issuer: Brand?,
        payWith: Brand?,
        usableAt: List<Brand>,
        scope: Scope,
        normalizedBody: String,
        sender: String,
        body: String,
        date: Long,
        threadId: Long,
        key: String,
        source: Source,
        aiFinding: AiFinding?
    ): Analysis {
        val scoped = PromoValueParser.parseValues(scope.text, scope.anchor)
        val whole = if (scope.text.length == normalizedBody.length) scoped else PromoValueParser.parseValues(normalizedBody)

        var discount = scoped.discount
        if (discount.type == DiscountType.UNKNOWN && aiFinding?.value != null) {
            val fromAi = PromoValueParser.parseDiscount(PromoValueParser.normalize(aiFinding.value))
            discount = if (fromAi.type != DiscountType.UNKNOWN) fromAi else discount
        }
        val minOrder = scoped.minOrder ?: whole.minOrder
            ?: aiFinding?.minOrder?.let { PromoValueParser.parseMinOrder("حداقل خرید " + PromoValueParser.normalize(it))?.first }
        val cap = if (discount.type != DiscountType.PERCENT) {
            null
        } else {
            scoped.cap ?: aiFinding?.cap?.let { PromoValueParser.parseCap("تا سقف " + PromoValueParser.normalize(it))?.first }
        }
        val expiry = PromoValueParser.findExpiry(scope.text, date)
            ?: PromoValueParser.findExpiry(normalizedBody, date)
            ?: aiFinding?.expiry?.let { PromoValueParser.findExpiry(PromoValueParser.normalize(it), date) }
            ?: PromoValueParser.parseExpiry("", date)

        // A card with neither a recognisable brand nor a stated saving is probably not an
        // offer at all, so it is flagged rather than presented as a sure thing.
        var confidence = codeConfidence
        if (brandConfidence < 60) confidence -= 15
        if (discount.type == DiscountType.UNKNOWN) confidence -= 10
        confidence = confidence.coerceIn(10, 100)

        val description = when (discount.type) {
            DiscountType.FREE_SHIPPING -> "ارسال رایگان از ${brand.fa}"
            DiscountType.UNKNOWN -> "کد تخفیف ${brand.fa}"
            else -> {
                val kind = if (discount.isCashback) "کش‌بک" else "تخفیف"
                listOfNotNull("${discount.display} $kind ${brand.fa}", cap?.display).joinToString(" ")
            }
        }
        val instructions = buildString {
            append("در صفحه پرداخت ${brand.fa} کد $code را وارد کنید.")
            payWith?.let { append(" پرداخت باید با ${it.fa} انجام شود.") }
        }

        val promo = PromoItem(
            id = "promo-$date-${code.hashCode()}",
            brand = brand.fa,
            brandEn = brand.en,
            category = brand.category,
            categorySlug = brand.categorySlug,
            code = code,
            discountAmount = discount.display,
            description = description,
            minOrder = minOrder?.display,
            instructions = instructions,
            expiryDateText = expiry.display,
            sender = sender,
            body = body,
            receivedAt = date,
            threadId = threadId,
            discountType = discount.type,
            discountValue = discount.value,
            minOrderValue = minOrder?.value ?: 0L,
            expiresAt = expiry.atMillis,
            expiryIsExplicit = expiry.isExplicit,
            confidence = confidence,
            brandColor = brand.color,
            appPackage = brand.appPackage,
            website = brand.website,
            issuer = issuer?.fa,
            payWith = payWith?.fa,
            usableAt = usableAt.map { it.fa },
            maxDiscountValue = cap?.value ?: 0L,
            maxDiscount = cap?.display,
            isCashback = discount.isCashback,
            source = source.tag,
            sourceKey = key
        )
        return Analysis(promo, codeConfidence, brandConfidence, discount.type != DiscountType.UNKNOWN, source)
    }

    /** A registry brand for [name], or one made up on the spot so the card still reads right. */
    private fun brandFromName(name: String, category: String?): Brand {
        BrandRegistry.byName(name)?.let { return it }
        val slug = slugFor(category)
        return BrandRegistry.UNKNOWN.copy(
            fa = name.trim(),
            en = name.trim(),
            category = BrandRegistry.categoryLabel(slug),
            categorySlug = slug,
            color = BrandRegistry.fallbackColor(name.trim())
        )
    }

    /** Confidence in a shop name read from the message's own heading. */
    private const val HEADER_BRAND_CONFIDENCE = 78

    /** Headings that are a greeting or a slogan, not a shop's name. */
    private val GENERIC_HEADER_WORDS = setOf(
        "تخفیف", "حراج", "جشنواره", "ویژه", "شگفت", "سلام", "گرامی", "عزیز", "کاربر", "مشتری",
        "هدیه", "کد", "فروش", "پیشنهاد", "فوری", "توجه", "خبر", "اطلاعیه", "مهم", "جدید", "همراه",
        "مشترک", "دوست", "با", "و", "برای", "رایگان", "یلدا", "نوروز", "عید", "black", "friday"
    )

    /** Words in a shop's name that say what kind of shop it is. */
    private val HEADER_CATEGORIES: List<Pair<List<String>, String>> = listOf(
        listOf("رستوران", "کافه", "فست فود", "پیتزا", "کبابی", "شیرینی", "قنادی") to BrandRegistry.SLUG_FOOD,
        listOf("سوپرمارکت", "سوپر مارکت", "هایپر", "مارکت", "میوه") to BrandRegistry.SLUG_SUPERMARKET,
        listOf("فروشگاه", "پوشاک", "لباس", "کفش", "مزون", "بوتیک", "گالری", "چرم", "موبایل", "لوازم") to BrandRegistry.SLUG_ECOMMERCE,
        listOf("هتل", "آژانس", "تور", "مسافرتی") to BrandRegistry.SLUG_TRANSPORT,
        listOf("آموزشگاه", "آکادمی", "کلینیک", "آرایشگاه", "سالن", "باشگاه", "خشکشویی") to BrandRegistry.SLUG_SERVICES
    )

    /**
     * The name a message is signed with in its first line — "فروشگاه لباس رز: حراج …" — when
     * it reads like a name rather than a greeting or a slogan.
     */
    fun headerBrand(normalizedBody: String): Brand? {
        val firstLine = normalizedBody.trim().substringBefore('\n')
        val cut = firstLine.indexOfFirst { it == ':' || it == '|' || it == '：' }
        if (cut !in 2..32) return null
        val name = firstLine.substring(0, cut).trim().trim('*', '«', '»', '"', '-', '–')
        if (name.length < 2 || name.any { it.isDigit() } || !name.any { it.isLetter() }) return null
        val words = name.toLowerCase().split(' ').filter { it.isNotBlank() }
        if (words.size > 4 || words.any { it in GENERIC_HEADER_WORDS }) return null
        val slug = HEADER_CATEGORIES.firstOrNull { (cues, _) -> cues.any { name.contains(it) } }?.second
            ?: BrandRegistry.SLUG_OTHER
        return BrandRegistry.UNKNOWN.copy(
            fa = name,
            en = name,
            category = BrandRegistry.categoryLabel(slug),
            categorySlug = slug,
            color = BrandRegistry.fallbackColor(name)
        )
    }

    fun slugFor(category: String?): String = when (category?.trim()?.toLowerCase()) {
        "food" -> BrandRegistry.SLUG_FOOD
        "supermarket", "grocery" -> BrandRegistry.SLUG_SUPERMARKET
        "ecommerce", "shopping", "shop" -> BrandRegistry.SLUG_ECOMMERCE
        "transport", "travel", "taxi" -> BrandRegistry.SLUG_TRANSPORT
        "entertainment", "books", "streaming" -> BrandRegistry.SLUG_ENTERTAINMENT
        "fintech", "payment", "insurance" -> BrandRegistry.SLUG_FINTECH
        "telecom" -> BrandRegistry.SLUG_TELECOM
        "services", "education", "health" -> BrandRegistry.SLUG_SERVICES
        else -> BrandRegistry.SLUG_OTHER
    }

    /**
     * Falls back to the sender as a brand name when nothing is recognised, so a card from an
     * unknown shop still shows something better than "Store".
     */
    fun unknownBrandFor(sender: String): Brand {
        val trimmed = sender.trim()
        val usableAsName = trimmed.isNotBlank() &&
            !trimmed.startsWith("09") &&
            !trimmed.startsWith("+98") &&
            !trimmed.all { it.isDigit() }

        return if (usableAsName) {
            BrandRegistry.UNKNOWN.copy(
                fa = trimmed,
                en = trimmed,
                color = BrandRegistry.fallbackColor(trimmed)
            )
        } else {
            BrandRegistry.UNKNOWN
        }
    }
}
