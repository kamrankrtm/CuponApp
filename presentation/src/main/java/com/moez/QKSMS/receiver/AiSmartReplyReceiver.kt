package com.moez.QKSMS.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.SmsManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.moez.QKSMS.R
import com.moez.QKSMS.feature.qkreply.QkReplyActivity
import com.moez.QKSMS.feature.smart.ai.AiChatAssistant

class AiSmartReplyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val threadId = intent.getLongExtra("threadId", -1L)
        if (threadId == -1L) return

        val sp = android.preference.PreferenceManager.getDefaultSharedPreferences(context)
        val notifManager = NotificationManagerCompat.from(context)

        val channelId = "ai_smart_reply_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "AI Smart Reply",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "AI generated replies for SMS and Smartwatches"
                enableVibration(true)
            }
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }

        val draftNotifId = (threadId + 200000).toInt()
        val draftingNotif = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_message_black_24dp)
            .setContentTitle("🤖 AI Smart Reply")
            .setContentText("Analyzing context and drafting reply...")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .build()
        notifManager.notify(draftNotifId, draftingNotif)

        val pendingResult = goAsync()

        AiChatAssistant.generateReply(context, threadId) { success, replyText, tId, address, contactName ->
            try {
                notifManager.cancel(draftNotifId)

                if (!success) {
                    Toast.makeText(context, "AI Reply Error: $replyText", Toast.LENGTH_LONG).show()
                    val errorNotif = NotificationCompat.Builder(context, channelId)
                        .setSmallIcon(R.drawable.ic_message_black_24dp)
                        .setContentTitle("AI Reply Failed")
                        .setContentText(replyText)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true)
                        .build()
                    notifManager.notify(draftNotifId, errorNotif)
                    return@generateReply
                }

                val autoSend = sp.getBoolean("aiAutoSendReply", false)
                if (autoSend && address.isNotBlank()) {
                    // Auto-send directly
                    try {
                        val smsManager = SmsManager.getDefault()
                        val parts = smsManager.divideMessage(replyText)
                        if (parts.size > 1) {
                            smsManager.sendMultipartTextMessage(address, null, parts, null, null)
                        } else {
                            smsManager.sendTextMessage(address, null, replyText, null, null)
                        }
                        Toast.makeText(context, "AI Reply Sent: $replyText", Toast.LENGTH_LONG).show()

                        val sentNotif = NotificationCompat.Builder(context, channelId)
                            .setSmallIcon(R.drawable.ic_message_black_24dp)
                            .setContentTitle("AI Sent to $contactName")
                            .setContentText(replyText)
                            .setStyle(NotificationCompat.BigTextStyle().bigText(replyText))
                            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                            .setAutoCancel(true)
                            .build()
                        notifManager.notify(draftNotifId, sentNotif)
                    } catch (t: Throwable) {
                        Toast.makeText(context, "Failed to send: ${t.message}", Toast.LENGTH_LONG).show()
                    }
                } else {
                    // Send Now Intent (1 tap from Watch or Phone!)
                    val sendIntent = Intent(context, AiSendReplyReceiver::class.java).apply {
                        putExtra("threadId", tId)
                        putExtra("address", address)
                        putExtra("body", replyText)
                        putExtra("notifId", draftNotifId)
                    }
                    val sendPI = PendingIntent.getBroadcast(
                        context,
                        (tId + 300000).toInt(),
                        sendIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
                    )

                    // Edit Intent
                    val editIntent = Intent(context, QkReplyActivity::class.java).apply {
                        putExtra("threadId", tId)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    val editPI = PendingIntent.getActivity(
                        context,
                        (tId + 400000).toInt(),
                        editIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
                    )

                    val readyNotif = NotificationCompat.Builder(context, channelId)
                        .setSmallIcon(R.drawable.ic_message_black_24dp)
                        .setContentTitle("🤖 AI: $contactName")
                        .setContentText(replyText)
                        .setStyle(NotificationCompat.BigTextStyle().bigText(replyText))
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setDefaults(NotificationCompat.DEFAULT_ALL)
                        .setAutoCancel(true)
                        .addAction(R.drawable.ic_send_black_24dp, "🚀 Send Now", sendPI)
                        .addAction(R.drawable.ic_reply_white_24dp, "✏️ Edit", editPI)
                        .build()

                    notifManager.notify(draftNotifId, readyNotif)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
