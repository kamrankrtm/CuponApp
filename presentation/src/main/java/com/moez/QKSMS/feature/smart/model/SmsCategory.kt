package com.moez.QKSMS.feature.smart.model

import com.moez.QKSMS.feature.smart.analysis.BankingDetails

sealed class SmsCategory {
    object Personal : SmsCategory()
    data class Promo(val promo: PromoItem) : SmsCategory()
    data class Otp(val otp: OtpItem) : SmsCategory()
    data class Banking(
        val bankName: String,
        val amount: String?,
        val isDeposit: Boolean?,
        /** The full reading: balance, fee, destination bank and evidence. */
        val details: BankingDetails? = null
    ) : SmsCategory()
    object Spam : SmsCategory()
    /**
     * Nothing marks the message as any of the above. It has no tab of its own: it stays in All
     * and notifies normally, because uncertainty alone is no reason to silence a message.
     */
    object Unknown : SmsCategory()
    data class Custom(val categoryName: String) : SmsCategory()
}
