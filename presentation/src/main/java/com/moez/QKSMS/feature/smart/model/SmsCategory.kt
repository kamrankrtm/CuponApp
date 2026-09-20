package com.moez.QKSMS.feature.smart.model

sealed class SmsCategory {
    object Personal : SmsCategory()
    data class Promo(val promo: PromoItem) : SmsCategory()
    data class Otp(val otp: OtpItem) : SmsCategory()
    data class Banking(val bankName: String, val amount: String?, val isDeposit: Boolean?) : SmsCategory()
    object Spam : SmsCategory()
    data class Custom(val categoryName: String) : SmsCategory()
}
