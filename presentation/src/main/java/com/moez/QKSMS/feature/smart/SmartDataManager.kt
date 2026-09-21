package com.moez.QKSMS.feature.smart

import com.moez.QKSMS.feature.smart.model.OtpItem
import com.moez.QKSMS.feature.smart.model.PromoItem
import java.util.Collections
import java.util.concurrent.CopyOnWriteArrayList

object SmartDataManager {
    private val promoList = CopyOnWriteArrayList<PromoItem>()
    private val otpList = CopyOnWriteArrayList<OtpItem>()
    private val promoCodesSet = Collections.synchronizedSet(HashSet<String>())
    private val otpCodesSet = Collections.synchronizedSet(HashSet<String>())

    fun getPromos(): List<PromoItem> = promoList.filter { !it.isUsed && !it.isExpired() }

    fun getOtps(): List<OtpItem> = otpList

    fun addPromo(promo: PromoItem) {
        if (promo.isExpired()) return
        val key = promo.code.trim().toUpperCase()
        if (promoCodesSet.add(key)) {
            promoList.add(0, promo)
        }
    }

    fun addOtp(otp: OtpItem) {
        val key = "${otp.sender}_${otp.code}"
        if (otpCodesSet.add(key)) {
            otpList.add(0, otp)
        }
    }

    fun setPromosAndOtps(promos: List<PromoItem>, otps: List<OtpItem>) {
        promoList.clear()
        promoCodesSet.clear()
        for (p in promos) {
            if (p.isExpired()) continue
            val key = p.code.trim().toUpperCase()
            if (promoCodesSet.add(key)) {
                promoList.add(p)
            }
        }

        otpList.clear()
        otpCodesSet.clear()
        for (o in otps) {
            val key = "${o.sender}_${o.code}"
            if (otpCodesSet.add(key)) {
                otpList.add(o)
            }
        }
    }
}
