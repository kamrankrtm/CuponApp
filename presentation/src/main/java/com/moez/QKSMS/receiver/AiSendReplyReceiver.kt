package com.moez.QKSMS.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat

class AiSendReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val threadId = intent.getLongExtra("threadId", -1L)
        val address = intent.getStringExtra("address") ?: ""
        val body = intent.getStringExtra("body") ?: ""
        val notifId = intent.getIntExtra("notifId", 0)

        if (notifId != 0) {
            NotificationManagerCompat.from(context).cancel(notifId)
        }

        if (address.isNotBlank() && body.isNotBlank()) {
            try {
                val smsManager = SmsManager.getDefault()
                val parts = smsManager.divideMessage(body)
                if (parts.size > 1) {
                    smsManager.sendMultipartTextMessage(address, null, parts, null, null)
                } else {
                    smsManager.sendTextMessage(address, null, body, null, null)
                }
                Toast.makeText(context, "AI reply sent to $address", Toast.LENGTH_SHORT).show()
            } catch (t: Throwable) {
                android.util.Log.e("AiSendReplyReceiver", "Failed to send SMS", t)
                Toast.makeText(context, "Failed to send AI reply: ${t.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
