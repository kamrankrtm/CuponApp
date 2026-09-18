package com.cuponapp.smsdiscounts

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/** اعلان کد تخفیف تازه‌ای که در پس‌زمینه پیدا شده است */
object SmsNotifier {

    private const val CHANNEL_ID = "promo_found"
    const val ACTION_COPY = "com.cuponapp.smsdiscounts.COPY_CODE"
    const val EXTRA_CODE = "code"
    const val EXTRA_NOTIFICATION_ID = "notificationId"

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "کدهای تخفیف جدید",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "وقتی از یک پیامک تبلیغاتی کد تخفیف استخراج شود"
        }
        manager.createNotificationChannel(channel)
    }

    fun notifyPromo(context: Context, promo: AvalAiClient.Promo) {
        ensureChannel(context)

        val notificationId = (promo.brand + promo.code).hashCode()
        val hasCode = promo.code.isNotBlank() && promo.code != "بدون کد"

        val openIntent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP }

        val contentPending = PendingIntent.getActivity(
            context,
            notificationId,
            openIntent ?: Intent(),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val expiry = promo.expiryDateText.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""
        val title = "${promo.brand}: ${promo.discountAmount}"
        val text = if (hasCode) "کد ${promo.code}$expiry" else "بدون کد$expiry"

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_myplaces)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    listOfNotNull(
                        text,
                        promo.minOrder?.takeIf { it.isNotBlank() },
                        promo.instructions.takeIf { it.isNotBlank() }
                    ).joinToString("\n")
                )
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentPending)

        if (hasCode) {
            val copyIntent = Intent(context, CopyCodeReceiver::class.java).apply {
                action = ACTION_COPY
                putExtra(EXTRA_CODE, promo.code)
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            }
            val copyPending = PendingIntent.getBroadcast(
                context,
                notificationId,
                copyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(android.R.drawable.ic_menu_edit, "کپی کد", copyPending)
        }

        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (e: SecurityException) {
            // کاربر مجوز اعلان نداده است؛ کد در صف می‌ماند تا در اپ دیده شود
        }
    }
}
