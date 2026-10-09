package com.moez.QKSMS.feature.smart.promo

/** Uncertain candidates remain unread until a local label or a validated AI answer confirms them. */
object PromoReadPolicy {
    fun shouldMarkRead(analyses: List<PromoParser.Analysis>, now: Long = System.currentTimeMillis()): Boolean =
        analyses.any { it.codeConfidence >= 80 && !it.promo.isExpired(now) }
}
