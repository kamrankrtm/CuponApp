package com.moez.QKSMS.feature.smart.ai

import android.app.AlertDialog
import android.content.Context
import com.moez.QKSMS.feature.smart.promo.PromoStore

/**
 * Asks, separately and explicitly, before the full text of a banking, verification-code or
 * unclassifiable message may go to the configured AI provider.
 *
 * Distinct from [AiConsentDialog]: agreeing to send advertisements never covers this. Off by
 * default; revoking it cancels every queued request at once.
 */
object SensitiveAiConsentDialog {

    fun ask(context: Context, onGranted: () -> Unit, onDenied: () -> Unit = {}) {
        AlertDialog.Builder(context)
            .setTitle("ارسال پیامک‌های بانکی و کد تایید به هوش مصنوعی")
            .setMessage(
                "با این اجازه، وقتی موتور داخلی نتواند پیامکی را با اطمینان دسته‌بندی کند، " +
                    "متن کامل همان پیامک به سرویس هوش مصنوعی‌ای که در تنظیمات وارد کرده‌اید فرستاده می‌شود. " +
                    "این شامل این موارد است:\n" +
                    "• پیامک‌های بانکی و تراکنش‌ها، با مبلغ، مانده و شماره حساب یا کارتی که در متن آمده\n" +
                    "• پیامک‌های کد تایید و رمز یکبار مصرف، با خود کد\n" +
                    "• پیامک‌های سرویس‌ها که دسته‌ی آن‌ها معلوم نیست\n\n" +
                    "فقط همان یک پیامک و نام فرستنده‌اش ارسال می‌شود؛ هرگز گفتگوهای دیگر، مخاطبین یا " +
                    "پیامک شماره‌های شخصی. هر پاسخ پیش از استفاده با متن اصلی پیامک بررسی می‌شود و " +
                    "کد تایید فقط وقتی خودکار کپی می‌شود که دقیقاً در پیامک آمده باشد.\n\n" +
                    "سرویس هوش مصنوعی شخص ثالث است و ممکن است این متن را طبق سیاست خودش نگه دارد. " +
                    "این اجازه جدا از اجازه‌ی ارسال پیامک‌های تبلیغاتی است و هر زمان از تنظیمات قابل لغو است."
            )
            .setPositiveButton("اجازه می‌دهم") { _, _ ->
                PromoStore.setSensitiveAiConsent(true)
                onGranted()
            }
            .setNegativeButton("انصراف") { _, _ -> onDenied() }
            .setOnCancelListener { onDenied() }
            .show()
    }

    /** Withdraws consent and cancels everything queued under it. */
    fun revoke(context: Context) {
        PromoStore.setSensitiveAiConsent(false)
        SmsAiFallback.cancelAll(context.applicationContext)
    }
}
