package com.moez.QKSMS.feature.smart.model

data class PromoItem(
    val id: String,
    val brand: String,
    val brandEn: String,
    val category: String,
    val categorySlug: String,
    val code: String,
    val discountAmount: String,
    val description: String,
    val minOrder: String? = null,
    val instructions: String = "",
    val expiryDateText: String = "معتبر تا اطلاع ثانوی",
    val sender: String = "",
    val body: String = "",
    val receivedAt: Long = System.currentTimeMillis(),
    var isUsed: Boolean = false,
    var isInvalid: Boolean = false,
    var isExpired: Boolean = false
)
