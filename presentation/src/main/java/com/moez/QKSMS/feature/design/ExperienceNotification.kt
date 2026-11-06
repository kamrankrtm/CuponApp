package com.moez.QKSMS.feature.design

import android.content.Context
import android.graphics.Color
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.moez.QKSMS.R

enum class ExperienceNotice { MESSAGE, PROMO, OTP, BANKING, SPAM, ALARM }

/**
 * Notification design respects SystemUI's Android 12+ templates and accessibility,
 * while retaining messaging shortcuts, sensitive OTP lock-screen policy and status color.
 */
object ExperienceNotification {
    fun decorate(context: Context, builder: NotificationCompat.Builder, kind: ExperienceNotice) {
        val experience = ExperienceStore.get(context)
        val section = when (kind) {
            ExperienceNotice.MESSAGE -> "پیام"
            ExperienceNotice.PROMO -> "تخفیف"
            ExperienceNotice.OTP -> "کد ورود"
            ExperienceNotice.BANKING -> "بانک"
            ExperienceNotice.SPAM -> "تبلیغات"
            ExperienceNotice.ALARM -> "امنیت"
        }
        val label = when (experience) {
            Experience.MICROSOFT -> "WORKSPACE  •  " + section
            Experience.APPLE -> "smsPRO  ·  " + section
            Experience.AVATR -> "SYSTEM / " + section
            Experience.PINTEREST -> "BOARD  ·  " + section
            Experience.TELEGRAM -> "smsPRO  •  " + section
            Experience.META -> "SOCIAL FLOW  ·  " + section
        }
        builder.setSubText(label)
        // Alarm green/amber is a safety signal, never override it with an arbitrary brand color.
        if (kind != ExperienceNotice.ALARM && kind != ExperienceNotice.SPAM) {
            builder.setColor(experience.accent)
        }
    }

    /**
     * Dedicated native layouts for financial, coupon and alarm notifications.
     * Generic SMS retains MessagingStyle, inline reply and conversation grouping.
     * A public lock-screen variant never reveals the bank amount, coupon or alarm details.
     */
    fun special(
        context: Context,
        builder: NotificationCompat.Builder,
        kind: ExperienceNotice,
        title: String,
        summary: String,
        expandedText: String
    ) {
        val small = RemoteViews(context.packageName, specialLayout(context, false))
        val big = RemoteViews(context.packageName, specialLayout(context, true))
        val icon = when (kind) {
            ExperienceNotice.PROMO -> R.drawable.ic_lc_ticket
            ExperienceNotice.BANKING -> R.drawable.ic_lc_landmark
            ExperienceNotice.ALARM -> R.drawable.ic_lc_shield
            else -> R.drawable.ic_lc_inbox
        }
        listOf(small, big).forEach { remote ->
            remote.setImageViewResource(R.id.noticeIcon, icon)
            remote.setTextViewText(R.id.noticeTitle, title)
            remote.setTextViewText(R.id.noticeBody, summary)
            remote.setTextViewText(R.id.noticeMeta, if (remote === big) expandedText else kindLabel(kind))
        }
        builder.setStyle(NotificationCompat.DecoratedCustomViewStyle())
        builder.setCustomContentView(small)
        builder.setCustomBigContentView(big)
        builder.setCustomHeadsUpContentView(small)
        builder.setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        if (kind != ExperienceNotice.ALARM) {
            val public = NotificationCompat.Builder(context,
                if (kind == ExperienceNotice.PROMO)
                    com.moez.QKSMS.common.util.NotificationManagerImpl.DISCOUNT_CHANNEL_ID
                else com.moez.QKSMS.common.util.NotificationManagerImpl.DEFAULT_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("اعلان " + kindLabel(kind))
                .setContentText("برای دیدن جزئیات قفل گوشی را باز کنید")
                .build()
            builder.setPublicVersion(public)
        }
    }

    private fun kindLabel(kind: ExperienceNotice): String = when (kind) {
        ExperienceNotice.PROMO -> "تخفیف"
        ExperienceNotice.BANKING -> "بانکی"
        ExperienceNotice.ALARM -> "امنیتی"
        ExperienceNotice.OTP -> "کد ورود"
        ExperienceNotice.SPAM -> "اسپم"
        ExperienceNotice.MESSAGE -> "پیام"
    }

    private fun specialLayout(context: Context, expanded: Boolean): Int {
        return when (ExperienceStore.get(context)) {
            Experience.MICROSOFT -> if (expanded) R.layout.notification_smart_microsoft_expanded else R.layout.notification_smart_microsoft
            Experience.APPLE -> if (expanded) R.layout.notification_smart_apple_expanded else R.layout.notification_smart_apple
            Experience.AVATR -> if (expanded) R.layout.notification_smart_avatr_expanded else R.layout.notification_smart_avatr
            Experience.PINTEREST -> if (expanded) R.layout.notification_smart_pinterest_expanded else R.layout.notification_smart_pinterest
            Experience.TELEGRAM -> if (expanded) R.layout.notification_smart_telegram_expanded else R.layout.notification_smart_telegram
            Experience.META -> if (expanded) R.layout.notification_smart_meta_expanded else R.layout.notification_smart_meta
        }
    }

    fun otpLayout(context: Context, expanded: Boolean): Int {
        return when (ExperienceStore.get(context)) {
            Experience.MICROSOFT -> if (expanded) R.layout.notification_otp_microsoft_expanded else R.layout.notification_otp_microsoft
            Experience.APPLE -> if (expanded) R.layout.notification_otp_apple_expanded else R.layout.notification_otp_apple
            Experience.AVATR -> if (expanded) R.layout.notification_otp_avatr_expanded else R.layout.notification_otp_avatr
            Experience.PINTEREST -> if (expanded) R.layout.notification_otp_pinterest_expanded else R.layout.notification_otp_pinterest
            Experience.TELEGRAM -> if (expanded) R.layout.notification_otp_telegram_expanded else R.layout.notification_otp_telegram
            Experience.META -> if (expanded) R.layout.notification_otp_meta_expanded else R.layout.notification_otp_meta
        }
    }

    fun tintOtp(context: Context, small: RemoteViews, big: RemoteViews) {
        val style = ExperienceStore.get(context)
        val highlight = if (style.darkHeader) Color.rgb(220, 209, 255) else style.accent
        val meta = if (style.darkHeader) Color.rgb(199, 185, 233) else Color.rgb(88, 99, 118)
        small.setTextColor(R.id.otpCode, highlight)
        small.setTextColor(R.id.otpService, meta)
        big.setTextColor(R.id.otpCode, highlight)
        big.setTextColor(R.id.otpService, meta)
        big.setTextColor(R.id.otpNote, meta)
    }
}
