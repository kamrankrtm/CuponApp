/*
 * Copyright (C) 2017 Moez Bhatti <moez.bhatti@gmail.com>
 *
 * This file is part of QKSMS.
 *
 * QKSMS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * QKSMS is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with QKSMS.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.moez.QKSMS.receiver

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.moez.QKSMS.interactor.MarkFailed
import com.moez.QKSMS.interactor.MarkSent
import dagger.android.AndroidInjection
import javax.inject.Inject

class SmsSentReceiver : BroadcastReceiver() {

    @Inject lateinit var markSent: MarkSent
    @Inject lateinit var markFailed: MarkFailed

    override fun onReceive(context: Context, intent: Intent) {
        try {
            AndroidInjection.inject(this, context)
        } catch (t: Throwable) {
            android.util.Log.e("SmsSentReceiver", "Injection failed, using direct Realm fallback", t)
        }

        val id = intent.getLongExtra("id", 0L)
        if (id == 0L) return

        when (resultCode) {
            Activity.RESULT_OK -> {
                val pendingResult = goAsync()
                try {
                    markSent.execute(id) { pendingResult.finish() }
                } catch (t: Throwable) {
                    io.realm.Realm.getDefaultInstance()?.use { realm ->
                        val msg = realm.where(com.moez.QKSMS.model.Message::class.java).equalTo("id", id).findFirst()
                        msg?.let {
                            realm.executeTransaction { msg.boxId = android.provider.Telephony.Sms.MESSAGE_TYPE_SENT }
                        }
                    }
                    pendingResult.finish()
                }
            }

            else -> {
                val pendingResult = goAsync()
                try {
                    markFailed.execute(MarkFailed.Params(id, resultCode)) { pendingResult.finish() }
                } catch (t: Throwable) {
                    pendingResult.finish()
                }
            }
        }
    }

}
