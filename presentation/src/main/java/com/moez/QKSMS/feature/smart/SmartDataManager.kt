package com.moez.QKSMS.feature.smart

import android.content.Context
import com.moez.QKSMS.feature.smart.model.OtpItem
import com.moez.QKSMS.feature.smart.model.PromoItem
import com.moez.QKSMS.model.Conversation
import java.util.concurrent.CopyOnWriteArrayList

object SmartDataManager {

    private val promoList = CopyOnWriteArrayList<PromoItem>()
    private val otpList = CopyOnWriteArrayList<OtpItem>()

    fun getPromos(): List<PromoItem> = promoList.filter { !it.isUsed }
    fun getOtps(): List<OtpItem> = otpList

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
