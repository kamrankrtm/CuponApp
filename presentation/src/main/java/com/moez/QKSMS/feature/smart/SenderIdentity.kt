package com.moez.QKSMS.feature.smart

import com.moez.QKSMS.feature.smart.promo.Brand
import com.moez.QKSMS.feature.smart.promo.BrandRegistry
import com.moez.QKSMS.feature.smart.promo.BrandRole
import com.moez.QKSMS.feature.smart.promo.PromoValueParser

/**
 * Who a sender is, for display: a person, or a business we may recognise by its sender id.
 *
 * Lookups run for every row the inbox binds, so answers are cached per address.
 */
object SenderIdentity {

    private const val MAX_CACHED = 512
    private val NONE = Any()
    private val cache = object : LinkedHashMap<String, Any>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Any>?): Boolean = size > MAX_CACHED
    }

    /** A saved contact or a mobile number is a person; anything else is a business sender. */
    fun isPerson(address: String?, hasContact: Boolean): Boolean =
            hasContact || (address != null && SmartSmsClassifier.isPersonalNumber(address))

    /** The brand behind a business sender id, if the registry knows it. Matches on the sender only. */
    @Synchronized
    fun brandFor(address: String?): Brand? {
        if (address.isNullOrBlank()) return null
        val hit = cache[address]
        if (hit != null) return hit as? Brand

        // Banks are in the registry as sponsors of card offers; their conversations keep the
        // sender id they have always shown
        val brand = if (SmartSmsClassifier.isPersonalNumber(address)) {
            null
        } else {
            BrandRegistry.matchSender(PromoValueParser.normalize(address).toLowerCase())
                ?.takeIf { it.role != BrandRole.BANK }
        }
        cache[address] = brand ?: NONE
        return brand
    }
}
