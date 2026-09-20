package com.moez.QKSMS.feature.smart

import android.content.Context
import com.moez.QKSMS.feature.smart.model.OtpItem
import com.moez.QKSMS.feature.smart.model.PromoItem
import com.moez.QKSMS.model.Conversation
import java.util.concurrent.CopyOnWriteArrayList

object SmartDataManager {

    private val promoList = CopyOnWriteArrayList<PromoItem>()
    private val otpList = CopyOnWriteArrayList<OtpItem>()

    fun isPromoExpired(promo: PromoItem): Boolean {
        if (promo.isUsed || promo.isInvalid || promo.isExpired) return true
        val bodyLower = promo.body.toLowerCase()
        val expiryText = promo.expiryDateText.toLowerCase()
        val now = System.currentTimeMillis()
        val daysOld = (now - promo.receivedAt) / (1000 * 60 * 60 * 24)

        if (daysOld >= 7) {
            promo.isExpired = true
            return true
        }
        if (daysOld >= 1 && (expiryText.contains("امشب") || expiryText.contains("امروز") || bodyLower.contains("تا امشب") || bodyLower.contains("فقط امروز") || bodyLower.contains("تا پایان امروز"))) {
            promo.isExpired = true
            return true
        }
        if (daysOld >= 2 && (expiryText.contains("فردا") || bodyLower.contains("تا فردا") || bodyLower.contains("۲۴ ساعت") || bodyLower.contains("24 ساعت"))) {
            promo.isExpired = true
            return true
        }
        return false
    }

    fun getPromos(): List<PromoItem> = promoList.filter { !it.isUsed && !isPromoExpired(it) }
    fun getArchivedPromos(): List<PromoItem> = promoList.filter { it.isUsed || isPromoExpired(it) }
    fun getOtps(): List<OtpItem> = otpList

    fun clearExpiredPromos() {
        promoList.removeAll { it.isUsed || isPromoExpired(it) }
    }

    fun addPromo(promo: PromoItem) {
        if (promoList.none { it.code.equals(promo.code, ignoreCase = true) }) {
            promoList.add(0, promo)
        }
    }

    fun addOtp(otp: OtpItem) {
        if (otpList.none { it.code == otp.code && it.sender == otp.sender }) {
            otpList.add(0, otp)
        }
    }

    fun scanConversations(conversations: List<Conversation>) {
        for (conv in conversations) {
            if (!conv.isValid) continue
            val lastMsg = conv.lastMessage ?: continue
            val sender = conv.recipients.firstOrNull()?.address ?: ""
            val body = lastMsg.body

            when (val result = SmartSmsClassifier.classify(sender, body)) {
                is com.moez.QKSMS.feature.smart.model.SmsCategory.Promo -> addPromo(result.promo)
                is com.moez.QKSMS.feature.smart.model.SmsCategory.Otp -> addOtp(result.otp)
                else -> Unit
            }
        }
    }
}
