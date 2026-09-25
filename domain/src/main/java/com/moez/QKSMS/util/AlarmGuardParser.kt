/*
 * Copyright (C) 2026 CuponApp / Moez Bhatti
 *
 * This file is part of QKSMS / CuponApp.
 *
 * QKSMS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.moez.QKSMS.util

object AlarmGuardParser {

    enum class Status {
        ARMED,
        DISARMED,
        PENDING_ARM,
        PENDING_DISARM,
        UNKNOWN
    }

    data class ParsedInfo(
        val status: Status,
        val detail: String? = null,
        val warning: String? = null,
        val credit: String? = null
    )

    fun normalizePersianText(text: String): String {
        return text
            .replace("\u200c", " ")
            .replace("\u200f", "")
            .replace("\u200e", "")
            .replace("ي", "ی")
            .replace("ك", "ک")
            .replace("ة", "ه")
            .replace("٠", "0").replace("١", "1").replace("٢", "2").replace("٣", "3").replace("٤", "4")
            .replace("٥", "5").replace("٦", "6").replace("٧", "7").replace("٨", "8").replace("٩", "9")
            .replace("۰", "0").replace("۱", "1").replace("۲", "2").replace("۳", "3").replace("۴", "4")
            .replace("۵", "5").replace("۶", "6").replace("۷", "7").replace("۸", "8").replace("۹", "9")
    }

    fun normalizePhoneNumber(number: String?): String {
        if (number.isNullOrBlank()) return ""
        val digits = number.filter { it.isDigit() }
        return if (digits.length >= 10) digits.takeLast(10) else digits
    }

    fun isMatchingPhoneNumber(address: String?, configuredNumber: String?): Boolean {
        if (address.isNullOrBlank() || configuredNumber.isNullOrBlank()) return false
        val cleanAddr = normalizePhoneNumber(address)
        val cleanConfig = normalizePhoneNumber(configuredNumber)
        return cleanAddr.isNotEmpty() && cleanConfig.isNotEmpty() && cleanAddr == cleanConfig
    }

    /**
     * Parse message text to determine alarm status, trigger source, warnings, and credit.
     */
    fun parseMessage(
        body: String,
        armKeywordsStr: String = "فعال شد,فعال گردید,روشن شد",
        disarmKeywordsStr: String = "غیر فعال شد,غیرفعال شد,غیر فعال گردید,غیرفعال گردید,خاموش شد"
    ): ParsedInfo {
        val norm = normalizePersianText(body)

        val disarmKeywords = disarmKeywordsStr.split(",")
            .map { normalizePersianText(it.trim()) }
            .filter { it.isNotEmpty() }
            .ifEmpty { listOf("غیر فعال", "غیرفعال", "خاموش") }

        val armKeywords = armKeywordsStr.split(",")
            .map { normalizePersianText(it.trim()) }
            .filter { it.isNotEmpty() }
            .ifEmpty { listOf("فعال شد", "فعال گردید", "روشن شد") }

        // 1. Check Disarm first (since disarm text often contains "فعال")
        var detectedStatus = Status.UNKNOWN
        for (kw in disarmKeywords) {
            if (norm.contains(kw, ignoreCase = true)) {
                detectedStatus = Status.DISARMED
                break
            }
        }

        // 2. If not disarmed, check for Arm keywords
        if (detectedStatus == Status.UNKNOWN) {
            for (kw in armKeywords) {
                if (norm.contains(kw, ignoreCase = true)) {
                    // Double check it doesn't have "غیر" immediately before
                    detectedStatus = Status.ARMED
                    break
                }
            }
        }

        // Fallback checks for standard Iranian alarm responses
        if (detectedStatus == Status.UNKNOWN) {
            if (norm.contains("غیر فعال") || norm.contains("غیرفعال")) {
                detectedStatus = Status.DISARMED
            } else if (norm.contains("فعال شد") || norm.contains("فعال گردید")) {
                detectedStatus = Status.ARMED
            }
        }

        // 3. Extract trigger method: e.g. "از طریق (ریموت۱)" or "از طریق (پیامک:رئیس)"
        var detail: String? = null
        val triggerRegex = Regex("""از\s+طریق\s*\(?([^)\n.]+)\)?""")
        val triggerMatch = triggerRegex.find(norm)
        if (triggerMatch != null) {
            detail = triggerMatch.groupValues[1].trim()
        }

        // 4. Extract sensor/battery warnings
        var warning: String? = null
        if (norm.contains("باتری سنسور") || norm.contains("بررسی شود") || norm.contains("هشدار")) {
            val lines = body.lines()
            val warnLine = lines.firstOrNull { l ->
                val nl = normalizePersianText(l)
                nl.contains("باتری سنسور") || nl.contains("بررسی شود") || nl.contains("هشدار")
            }
            warning = warnLine?.trim() ?: body.trim()
        }

        // 5. Extract SIM credit / Etebar
        var credit: String? = null
        val creditRegex = Regex("""(?:Etebar|اعتبار)\s*:\s*([0-9,]+)""", RegexOption.IGNORE_CASE)
        val creditMatch = creditRegex.find(body)
        if (creditMatch != null) {
            credit = creditMatch.groupValues[1].trim() + " ریال"
        }

        return ParsedInfo(
            status = detectedStatus,
            detail = detail,
            warning = warning,
            credit = credit
        )
    }

    /**
     * Process an incoming or existing SMS message and update preferences if applicable.
     * Returns true if the message changed the alarm state or updated info.
     */
    fun processMessage(
        prefs: Preferences,
        address: String?,
        body: String,
        timestamp: Long = System.currentTimeMillis()
    ): Boolean {
        if (!prefs.alarmGuardEnabled.get()) return false

        val configuredNumber = prefs.alarmPhoneNumber.get()
        val isNumberMatch = isMatchingPhoneNumber(address, configuredNumber)

        // Also check if text itself strongly indicates it is from an alarm system
        val norm = normalizePersianText(body)
        val hasAlarmSignature = norm.contains("کل بخش ها") ||
                norm.contains("باتری سنسور") ||
                (norm.contains("از طریق") && (norm.contains("فعال") || norm.contains("ریموت")))

        if (!isNumberMatch && !hasAlarmSignature) {
            return false
        }

        val parsed = parseMessage(
            body = body,
            armKeywordsStr = prefs.alarmArmKeywords.get(),
            disarmKeywordsStr = prefs.alarmDisarmKeywords.get()
        )

        var updated = false

        if (parsed.status != Status.UNKNOWN) {
            prefs.alarmLastStatus.set(parsed.status.name)
            prefs.alarmLastStatusTime.set(timestamp.toString())
            updated = true
        }

        if (!parsed.detail.isNullOrBlank()) {
            prefs.alarmLastStatusDetail.set(parsed.detail)
            updated = true
        }

        if (!parsed.warning.isNullOrBlank()) {
            prefs.alarmLastWarning.set(parsed.warning)
            updated = true
        }

        if (!parsed.credit.isNullOrBlank()) {
            prefs.alarmLastCredit.set(parsed.credit)
            updated = true
        }

        return updated
    }
}
