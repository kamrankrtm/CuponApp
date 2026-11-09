/*
 * Copyright (C) 2026 CuponApp. GPLv3 or later.
 */
package com.moez.QKSMS.util

import android.content.Context
import android.content.Intent
import timber.log.Timber

/**
 * Android launcher shortcuts are not directly callable by a background SMS app.
 * An opt-in app such as MacroDroid can receive these events and trigger the user's
 * Xiaomi Home scenes through a shortcut plugin. Dispatch != camera confirmation.
 */
object AlarmCameraAutomation {
    enum class Scene { WAKE, SLEEP }
    const val ACTION_WAKE = "com.moez.QKSMS.alarmcamera.WAKE"
    const val ACTION_SLEEP = "com.moez.QKSMS.alarmcamera.SLEEP"

    private val packageRegex = Regex("""[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z_][A-Za-z0-9_]*)+""")

    fun isValidPackage(packageName: String): Boolean =
        packageName.length in 3..180 && packageRegex.matches(packageName)

    fun sceneForSms(
        enabled: Boolean,
        sender: String?,
        configuredNumber: String?,
        body: String,
        armKeywords: String,
        disarmKeywords: String,
        smsTimestamp: Long,
        lastHandledTimestamp: Long
    ): Scene? {
        if (!enabled || smsTimestamp <= 0L || smsTimestamp <= lastHandledTimestamp) return null
        val event = AlarmGuardNotification.fromSms(
            true, sender, configuredNumber, body, armKeywords, disarmKeywords
        ) ?: return null
        return when (event.status) {
            AlarmGuardParser.Status.ARMED -> Scene.WAKE
            AlarmGuardParser.Status.DISARMED -> Scene.SLEEP
            else -> null
        }
    }

    fun onReceivedSms(
        context: Context,
        prefs: Preferences,
        sender: String?,
        body: String,
        timestamp: Long
    ): Boolean {
        val scene = sceneForSms(
            enabled = prefs.alarmGuardEnabled.get() && prefs.alarmCameraAutomationEnabled.get(),
            sender = sender,
            configuredNumber = prefs.alarmPhoneNumber.get(),
            body = body,
            armKeywords = prefs.alarmArmKeywords.get(),
            disarmKeywords = prefs.alarmDisarmKeywords.get(),
            smsTimestamp = timestamp,
            lastHandledTimestamp = prefs.alarmCameraLastSmsTimestamp.get().toLongOrNull() ?: 0L
        ) ?: return false

        val sent = sendScene(context, prefs.alarmCameraAutomationPackage.get(), scene)
        if (sent) prefs.alarmCameraLastSmsTimestamp.set(timestamp.toString())
        return sent
    }

    /** Manual test bypasses SMS conditions but does not change the alarm itself. */
    fun sendTest(context: Context, prefs: Preferences, scene: Scene): Boolean =
        sendScene(context, prefs.alarmCameraAutomationPackage.get(), scene)

    private fun sendScene(context: Context, receiverPackage: String, scene: Scene): Boolean {
        val name = receiverPackage.trim()
        if (!isValidPackage(name)) return false
        return try {
            val broadcast = Intent(if (scene == Scene.WAKE) ACTION_WAKE else ACTION_SLEEP).apply {
                setPackage(name)
                putExtra("source", "cuponapp_alarm_sms")
            }
            context.sendBroadcast(broadcast)
            Timber.i("Alarm camera request sent: %s", scene)
            true
        } catch (error: Exception) {
            Timber.e(error, "Alarm-camera broadcast failed")
            false
        }
    }
}
