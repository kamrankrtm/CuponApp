package com.moez.QKSMS.feature.smart.model

data class OtpItem(
    val id: String,
    val code: String,
    val serviceName: String,
    val sender: String,
    val body: String,
    val receivedAt: Long = System.currentTimeMillis(),
    /** The message the code came from ("sms:<provider id>"), so a re-read replaces, not duplicates. */
    val sourceKey: String = "",
    /** The extractor's ranking score for the code, 0..100; not a probability. */
    val confidence: Int = 0,
    /** When the message says the code stops working, or null when it does not say. */
    val expiresAt: Long? = null
) {
    /** Whether the code is past a validity the message stated; unknown validity never expires here. */
    fun isExpired(now: Long = System.currentTimeMillis()): Boolean = expiresAt != null && now >= expiresAt
}
