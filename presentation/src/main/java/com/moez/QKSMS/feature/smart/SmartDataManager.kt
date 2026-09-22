package com.moez.QKSMS.feature.smart

import com.moez.QKSMS.feature.smart.model.OtpItem
import com.moez.QKSMS.feature.smart.model.PromoItem
import java.util.concurrent.CopyOnWriteArrayList

object SmartDataManager {
    private val promoList = CopyOnWriteArrayList<PromoItem>()
    private val otpList = CopyOnWriteArrayList<OtpItem>()

    private fun getYesterdayCutoff(): Long {
        val cal = java.util.Calendar.getInstance().apply {
            add(java.util.Calendar.DAY_OF_YEAR, -1)
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    fun getPromos(): List<PromoItem> = promoList.filter { !it.isUsed && !it.isExpired() }

    fun getOtps(): List<OtpItem> {
        val cutoff = getYesterdayCutoff()
        return otpList.filter { it.receivedAt >= cutoff }
    }

    fun addPromo(promo: PromoItem) {
        if (promo.isExpired()) return
        val key = "${promo.brand.toLowerCase().trim()}_${promo.code.trim().toUpperCase()}"
        // Remove existing duplicate so the newest message is placed at index 0
        promoList.removeAll { "${it.brand.toLowerCase().trim()}_${it.code.trim().toUpperCase()}" == key }
        promoList.add(0, promo)
    }

    fun addOtp(otp: OtpItem) {
        if (otp.receivedAt < getYesterdayCutoff()) return
        val key = "${otp.sender.trim()}_${otp.code.trim()}"
        otpList.removeAll { "${it.sender.trim()}_${it.code.trim()}" == key }
        otpList.add(0, otp)
    }

    fun setPromosAndOtps(promos: List<PromoItem>, otps: List<OtpItem>) {
        promoList.clear()
        val seenPromoKeys = HashSet<String>()
        // Promos are sorted by receivedAt DESCENDING, so first occurrence is the freshest
        for (p in promos) {
            if (p.isExpired()) continue
            val key = "${p.brand.toLowerCase().trim()}_${p.code.trim().toUpperCase()}"
            if (seenPromoKeys.add(key)) {
                promoList.add(p)
            }
        }

        otpList.clear()
        val cutoff = getYesterdayCutoff()
        val seenOtpKeys = HashSet<String>()
        for (o in otps) {
            if (o.receivedAt < cutoff) continue
            val key = "${o.sender.trim()}_${o.code.trim()}"
            if (seenOtpKeys.add(key)) {
                otpList.add(o)
            }
        }
    }
}
