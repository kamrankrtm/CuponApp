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

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import com.moez.QKSMS.R
import com.moez.QKSMS.feature.compose.ComposeActivity
import com.moez.QKSMS.manager.WidgetManager
import com.moez.QKSMS.receiver.AlarmActionReceiver
import com.moez.QKSMS.util.AlarmGuardParser
import com.moez.QKSMS.util.Preferences
import dagger.android.AndroidInjection
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class AlarmGuardWidgetProvider : AppWidgetProvider() {

    companion object {
        fun updateAllWidgets(context: Context) {
            try {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, AlarmGuardWidgetProvider::class.java))
                if (ids != null && ids.isNotEmpty()) {
                    val intent = Intent(context, AlarmGuardWidgetProvider::class.java).apply {
                        action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                    }
                    context.sendBroadcast(intent)
                }
            } catch (t: Throwable) {
                Timber.e(t, "Failed to broadcast updateAllWidgets")
            }
        }

        private fun pendingIntentFlags(): Int {
            return PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        }
    }

    @Inject lateinit var prefs: Preferences

    override fun onReceive(context: Context, intent: Intent) {
        try {
            AndroidInjection.inject(this, context)
        } catch (t: Throwable) {
            Timber.e(t, "AlarmGuardWidgetProvider injection failed")
        }

        when (intent.action) {
            WidgetManager.ACTION_UPDATE_ALARM_WIDGET,
            AppWidgetManager.ACTION_APPWIDGET_UPDATE -> {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, AlarmGuardWidgetProvider::class.java))
                if (ids != null) {
                    onUpdate(context, appWidgetManager, ids)
                }
            }
            else -> super.onReceive(context, intent)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        for (id in appWidgetIds) {
            updateWidget(context, appWidgetManager, id)
        }
    }

    private fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_alarm_guard)

        val statusStr = if (::prefs.isInitialized) prefs.alarmLastStatus.get() else "UNKNOWN"
        val status = try {
            AlarmGuardParser.Status.valueOf(statusStr)
        } catch (_: Throwable) {
            AlarmGuardParser.Status.UNKNOWN
        }

        val detail = if (::prefs.isInitialized) prefs.alarmLastStatusDetail.get() else ""
        val warning = if (::prefs.isInitialized) prefs.alarmLastWarning.get() else ""
        val credit = if (::prefs.isInitialized) prefs.alarmLastCredit.get() else ""
        val phone = if (::prefs.isInitialized) prefs.alarmPhoneNumber.get() else ""
        val timeStr = if (::prefs.isInitialized) prefs.alarmLastStatusTime.get() else "0"

        // Format time
        val formattedTime = try {
            val ts = timeStr.toLongOrNull() ?: 0L
            if (ts > 0) {
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))
            } else {
                ""
            }
        } catch (_: Throwable) {
            ""
        }
        if (formattedTime.isNotBlank()) {
            views.setTextViewText(R.id.widgetAlarmTime, formattedTime)
            views.setViewVisibility(R.id.widgetAlarmTime, View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.widgetAlarmTime, View.GONE)
        }

        // Status badge and text
        when (status) {
            AlarmGuardParser.Status.ARMED -> {
                views.setTextViewText(R.id.widgetAlarmStatusText, "روشن (فعال)")
                views.setTextColor(R.id.widgetAlarmStatusText, 0xFF22C55E.toInt())
                views.setInt(R.id.widgetAlarmStatusBox, "setBackgroundResource", R.drawable.bg_alarm_badge_armed)
                views.setInt(R.id.widgetAlarmShieldIcon, "setColorFilter", 0xFF22C55E.toInt())
            }
            AlarmGuardParser.Status.DISARMED -> {
                views.setTextViewText(R.id.widgetAlarmStatusText, "خاموش (غیرفعال)")
                views.setTextColor(R.id.widgetAlarmStatusText, 0xFFEF4444.toInt())
                views.setInt(R.id.widgetAlarmStatusBox, "setBackgroundResource", R.drawable.bg_alarm_badge_disarmed)
                views.setInt(R.id.widgetAlarmShieldIcon, "setColorFilter", 0xFF94A3B8.toInt())
            }
            AlarmGuardParser.Status.PENDING_ARM -> {
                views.setTextViewText(R.id.widgetAlarmStatusText, "در حال فعال‌سازی...")
                views.setTextColor(R.id.widgetAlarmStatusText, 0xFFF59E0B.toInt())
                views.setInt(R.id.widgetAlarmStatusBox, "setBackgroundResource", R.drawable.bg_alarm_badge_pending)
                views.setInt(R.id.widgetAlarmShieldIcon, "setColorFilter", 0xFFF59E0B.toInt())
            }
            AlarmGuardParser.Status.PENDING_DISARM -> {
                views.setTextViewText(R.id.widgetAlarmStatusText, "در حال غیرفعال‌سازی...")
                views.setTextColor(R.id.widgetAlarmStatusText, 0xFFF59E0B.toInt())
                views.setInt(R.id.widgetAlarmStatusBox, "setBackgroundResource", R.drawable.bg_alarm_badge_pending)
                views.setInt(R.id.widgetAlarmShieldIcon, "setColorFilter", 0xFFF59E0B.toInt())
            }
            AlarmGuardParser.Status.UNKNOWN -> {
                views.setTextViewText(R.id.widgetAlarmStatusText, "دزدگیر اماکن (آماده)")
                views.setTextColor(R.id.widgetAlarmStatusText, 0xFF94A3B8.toInt())
                views.setInt(R.id.widgetAlarmStatusBox, "setBackgroundResource", R.drawable.bg_alarm_badge_disarmed)
                views.setInt(R.id.widgetAlarmShieldIcon, "setColorFilter", 0xFF94A3B8.toInt())
            }
        }

        // Detail
        val detailText = when {
            detail.isNotBlank() -> "از طریق $detail"
            phone.isNotBlank() -> phone
            else -> "سیستم دزدگیر هوشمند"
        }
        views.setTextViewText(R.id.widgetAlarmDetail, detailText)

        // Credit info
        if (credit.isNotBlank()) {
            views.setViewVisibility(R.id.widgetAlarmCredit, View.VISIBLE)
            views.setTextViewText(R.id.widgetAlarmCredit, "اعتبار: $credit")
        } else {
            views.setViewVisibility(R.id.widgetAlarmCredit, View.GONE)
        }

        // Warning banner
        if (warning.isNotBlank()) {
            views.setViewVisibility(R.id.widgetAlarmWarning, View.VISIBLE)
            views.setTextViewText(R.id.widgetAlarmWarning, "⚠️ $warning")
        } else {
            views.setViewVisibility(R.id.widgetAlarmWarning, View.GONE)
        }

        // PendingIntent for Arm button
        val armIntent = Intent(context, AlarmActionReceiver::class.java).apply {
            action = AlarmActionReceiver.ACTION_ARM
        }
        val armPendingIntent = PendingIntent.getBroadcast(
            context,
            101,
            armIntent,
            pendingIntentFlags()
        )
        views.setOnClickPendingIntent(R.id.widgetBtnArm, armPendingIntent)

        // PendingIntent for Disarm button
        val disarmIntent = Intent(context, AlarmActionReceiver::class.java).apply {
            action = AlarmActionReceiver.ACTION_DISARM
        }
        val disarmPendingIntent = PendingIntent.getBroadcast(
            context,
            102,
            disarmIntent,
            pendingIntentFlags()
        )
        views.setOnClickPendingIntent(R.id.widgetBtnDisarm, disarmPendingIntent)

        // PendingIntent for clicking widget container to open conversation
        val openIntent = Intent(context, ComposeActivity::class.java).apply {
            action = Intent.ACTION_SENDTO
            data = Uri.parse("smsto:$phone")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            103,
            openIntent,
            pendingIntentFlags()
        )
        views.setOnClickPendingIntent(R.id.widgetAlarmContainer, openPendingIntent)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}
