/*
 * Copyright (C) 2026 CuponApp / Moez Bhatti
 * Distributed under GPLv3 or later.
 */
package com.moez.QKSMS.util

/**
 * Converts only configured alarm-device responses into security events.
 * Generic alarm-like text from other senders must keep the ordinary SMS notification.
 */
object AlarmGuardNotification {

    data class Event(
        val status: AlarmGuardParser.Status,
        val body: String,
        val detail: String?,
        val warning: String?,
        val credit: String?
    )

    fun fromSms(
        enabled: Boolean,
        sender: String?,
        configuredNumber: String?,
        body: String,
        armKeywords: String,
        disarmKeywords: String
    ): Event? {
        if (!enabled || body.isBlank()) return null

        // Only a valid configured phone number can be trusted; do not accept arbitrary
        // short codes or merely matching message contents as a dis/arm confirmation.
        val expected = AlarmGuardParser.normalizePhoneNumber(
            AlarmGuardParser.normalizePersianText(configuredNumber.orEmpty())
        )
        val actual = AlarmGuardParser.normalizePhoneNumber(
            AlarmGuardParser.normalizePersianText(sender.orEmpty())
        )
        if (expected.length != 10 || actual != expected) return null

        val info = AlarmGuardParser.parseMessage(body, armKeywords, disarmKeywords)
        if (info.status != AlarmGuardParser.Status.ARMED &&
            info.status != AlarmGuardParser.Status.DISARMED) return null

        return Event(
            status = info.status,
            body = body,
            detail = info.detail,
            warning = info.warning,
            credit = info.credit
        )
    }
}
