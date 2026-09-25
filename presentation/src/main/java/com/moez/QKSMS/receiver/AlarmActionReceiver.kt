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
package com.moez.QKSMS.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.moez.QKSMS.feature.alarmguard.AlarmGuardManager
import com.moez.QKSMS.feature.alarmguard.AlarmGuardWidgetProvider
import com.moez.QKSMS.util.Preferences
import dagger.android.AndroidInjection
import timber.log.Timber
import javax.inject.Inject

class AlarmActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_ARM = "com.moez.QKSMS.alarmguard.ACTION_ARM"
        const val ACTION_DISARM = "com.moez.QKSMS.alarmguard.ACTION_DISARM"
        const val ACTION_REFRESH = "com.moez.QKSMS.alarmguard.ACTION_REFRESH"
    }

    @Inject lateinit var prefs: Preferences

    override fun onReceive(context: Context, intent: Intent) {
        try {
            AndroidInjection.inject(this, context)
        } catch (t: Throwable) {
            Timber.e(t, "AlarmActionReceiver injection failed")
        }

        when (intent.action) {
            ACTION_ARM -> {
                if (::prefs.isInitialized) {
                    AlarmGuardManager.sendArm(context, prefs)
                }
            }
            ACTION_DISARM -> {
                if (::prefs.isInitialized) {
                    AlarmGuardManager.sendDisarm(context, prefs)
                }
            }
            ACTION_REFRESH -> {
                AlarmGuardWidgetProvider.updateAllWidgets(context)
            }
        }
    }
}
