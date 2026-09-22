package com.moez.QKSMS.feature.smart.promo

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.moez.QKSMS.R
import com.moez.QKSMS.common.util.NotificationManagerImpl
import com.moez.QKSMS.feature.main.MainActivity
import com.moez.QKSMS.feature.smart.model.PromoItem
import com.moez.QKSMS.receiver.CopyClipReceiver

/**
 * Warns the user before a worthwhile code expires.
 *
 * Without this the app only ever told the user about a code the moment it arrived, which is
 * precisely when they are least likely to act on it. The reminder fires while there is still
 * time to spend it.
 */
object PromoExpiryNotifier {

    private const val NOTIFICATION_ID_BASE = 920000

    /** Never warn about the same code twice in this window. */
    private const val REMINDER_COOLDOWN_MS = 6L * 60 * 60 * 1000

    private val lastNotified = HashMap<String, Long>()

    /**
     * Notifies about codes that are nearly out of time and actually worth something.
     *
     * @return how many reminders were posted
     */
    fun notifyExpiring(context: Context, promos: List<PromoItem>, enabled: Boolean): Int {
        if (!enabled) return 0

        val now = System.currentTimeMillis()
        val candidates = PromoRanker.expiringSoon(promos, now)
        if (candidates.isEmpty()) return 0

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        var posted = 0

        // One reminder at a time; a burst of notifications about coupons is spam.
        for (promo in candidates.take(1)) {
            val key = promo.dedupeKey
            val previous = lastNotified[key] ?: 0L
            if (now - previous < REMINDER_COOLDOWN_MS) continue
            lastNotified[key] = now

            val notificationId = NOTIFICATION_ID_BASE + (key.hashCode() and 0xFFFF)

            val copyIntent = Intent(context, CopyClipReceiver::class.java).apply {
                putExtra(CopyClipReceiver.EXTRA_TEXT, promo.code)
                putExtra(CopyClipReceiver.EXTRA_LABEL, "PROMO")
            }
            val copyPendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId,
                copyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT
            )

            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val openPendingIntent = PendingIntent.getActivity(
                context,
                notificationId + 1,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT
            )

            val title = "⏰ کد ${promo.brand} رو به اتمام است"
            val body = "${promo.discountAmount} • ${promo.remainingLabel(now)}"

            val notification = NotificationCompat.Builder(
                context,
                NotificationManagerImpl.DISCOUNT_CHANNEL_ID
            )
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .setBigContentTitle(title)
                        .bigText(
                            "🎁 کد: ${promo.code}\n" +
                                "💰 تخفیف: ${promo.discountAmount}\n" +
                                "⏳ ${promo.remainingLabel(now)}" +
                                (promo.minOrder?.let { "\n🛒 $it" } ?: "")
                        )
                )
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(openPendingIntent)
                .addAction(R.drawable.ic_content_copy_black_24dp, "📋 کپی کد", copyPendingIntent)
                .build()

            manager.notify(notificationId, notification)
            posted++
        }

        return posted
    }
}
