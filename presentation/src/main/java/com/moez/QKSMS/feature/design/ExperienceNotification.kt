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
