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
package com.moez.QKSMS.feature.alarmguard

import android.content.Context
import android.telephony.SmsManager
import android.widget.Toast
import com.moez.QKSMS.R
import com.moez.QKSMS.model.Conversation
import com.moez.QKSMS.model.Message
import com.moez.QKSMS.util.AlarmGuardParser
import com.moez.QKSMS.util.Preferences
import timber.log.Timber

object AlarmGuardManager {

    /**
     * Determines whether the given conversation belongs to the configured alarm system.
     */
    fun isAlarmConversation(conversation: Conversation?, prefs: Preferences): Boolean {
        if (conversation == null) return false
        val targetPhone = prefs.alarmPhoneNumber.get()

        // 1. Check title/name
        val title = conversation.getTitle()
        if (title.contains("دزدگیر", ignoreCase = true) || title.contains("alarm", ignoreCase = true)) {
            return true
        }

        // 2. Check recipient addresses
        try {
            for (recipient in conversation.recipients) {
                if (AlarmGuardParser.isMatchingPhoneNumber(recipient.address, targetPhone)) {
                    return true
                }
                val rName = recipient.getDisplayName()
                if (rName.contains("دزدگیر", ignoreCase = true) || rName.contains("alarm", ignoreCase = true)) {
                    return true
                }
            }
        } catch (t: Throwable) {
            Timber.e(t, "Error checking recipients in isAlarmConversation")
        }

        return false
    }

    /**
     * Send Arm command (روشن / فعال کردن دزدگیر).
     */
    fun sendArm(context: Context, prefs: Preferences) {
        val phone = prefs.alarmPhoneNumber.get().trim()
        val code = prefs.alarmArmCode.get().trim()
        if (phone.isBlank() || code.isBlank()) {
            Toast.makeText(context, context.getString(R.string.alarm_guard_not_configured), Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val smsManager = SmsManager.getDefault()
            val parts = smsManager.divideMessage(code)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(phone, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(phone, null, code, null, null)
            }

            prefs.alarmLastStatus.set(AlarmGuardParser.Status.PENDING_ARM.name)
            prefs.alarmLastStatusTime.set(System.currentTimeMillis().toString())
            AlarmGuardWidgetProvider.updateAllWidgets(context)

            val msg = context.getString(R.string.alarm_guard_arm_sent, code)
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        } catch (t: Throwable) {
            Timber.e(t, "Failed to send Arm SMS")
            Toast.makeText(context, context.getString(R.string.alarm_guard_send_failed, t.message ?: ""), Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Send Disarm command (خاموش / غیرفعال کردن دزدگیر).
     */
    fun sendDisarm(context: Context, prefs: Preferences) {
        val phone = prefs.alarmPhoneNumber.get().trim()
        val code = prefs.alarmDisarmCode.get().trim()
        if (phone.isBlank() || code.isBlank()) {
            Toast.makeText(context, context.getString(R.string.alarm_guard_not_configured), Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val smsManager = SmsManager.getDefault()
            val parts = smsManager.divideMessage(code)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(phone, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(phone, null, code, null, null)
            }

            prefs.alarmLastStatus.set(AlarmGuardParser.Status.PENDING_DISARM.name)
            prefs.alarmLastStatusTime.set(System.currentTimeMillis().toString())
            AlarmGuardWidgetProvider.updateAllWidgets(context)

            val msg = context.getString(R.string.alarm_guard_disarm_sent, code)
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        } catch (t: Throwable) {
            Timber.e(t, "Failed to send Disarm SMS")
            Toast.makeText(context, context.getString(R.string.alarm_guard_send_failed, t.message ?: ""), Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Scan existing conversation messages to detect the most recent alarm state.
     */
    fun detectStatusFromMessages(prefs: Preferences, messages: List<Message>?): Boolean {
        if (messages.isNullOrEmpty()) return false
        val armKw = prefs.alarmArmKeywords.get()
        val disarmKw = prefs.alarmDisarmKeywords.get()

        // Scan backwards (from most recent to oldest)
        for (i in (messages.size - 1) downTo 0) {
            val msg = messages[i]
            val body = msg.body
            if (body.isBlank()) continue

            val parsed = AlarmGuardParser.parseMessage(body, armKw, disarmKw)
            if (parsed.status != AlarmGuardParser.Status.UNKNOWN) {
                prefs.alarmLastStatus.set(parsed.status.name)
                prefs.alarmLastStatusTime.set(msg.date.toString())
                parsed.detail?.let { prefs.alarmLastStatusDetail.set(it) }
                parsed.warning?.let { prefs.alarmLastWarning.set(it) }
                parsed.credit?.let { prefs.alarmLastCredit.set(it) }
                return true
            }
        }
        return false
    }
}
