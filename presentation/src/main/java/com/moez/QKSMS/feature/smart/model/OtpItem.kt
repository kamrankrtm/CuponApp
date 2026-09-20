package com.moez.QKSMS.feature.smart.model

data class OtpItem(
    val id: String,
    val code: String,
    val serviceName: String,
    val sender: String,
    val body: String,
    val receivedAt: Long = System.currentTimeMillis()
)
