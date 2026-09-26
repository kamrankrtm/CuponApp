package com.moez.QKSMS.feature.smart.ai

import android.app.AlertDialog
import android.content.Context
import com.moez.QKSMS.feature.smart.promo.PromoStore

/**
 * Asks, once, before any message text is sent to a third-party AI service.
 *
 * Uploading someone's SMS to an external server is not something to do silently, so the
 * answer is stored and every scan checks it.
 */
object AiConsentDialog {

    /**
     * Runs [onGranted] immediately if consent was already given, otherwise asks first.
     *
     * @param onDenied invoked when the user declines or dismisses the dialog
     */
    fun ensureConsent(context: Context, onGranted: () -> Unit, onDenied: () -> Unit = {}) {
        if (PromoStore.hasAiConsent()) {
            onGranted()
            return
        }

        AlertDialog.Builder(context)
            .setTitle("ارسال پیامک‌ها به هوش مصنوعی")
            .setMessage(
                "برای پیدا کردن کدهایی که موتور داخلی نتوانسته بخواند، متن پیامک‌های تبلیغاتی " +
                    "به سرویس هوش مصنوعی شما ارسال می‌شود.\n\n" +
                    "این موارد هرگز ارسال نمی‌شوند:\n" +
                    "• پیامک‌های بانکی و تراکنش‌ها\n" +
                    "• کدهای ورود و رمزهای یکبارمصرف\n" +
                    "• پیامک شماره‌های شخصی\n" +
                    "• پیامک‌های حاوی شماره کارت، شبا یا کد ملی\n\n" +
                    "فقط پیامک‌های تبلیغاتی دارای کد فرستاده می‌شوند، هر پیامک یک بار؛ لینک‌ها، نام شما " +
                    "و خط لغو پیش از ارسال حذف می‌شوند. به‌طور پیش‌فرض همه‌ی کدها برای دقت بیشتر بررسی " +
                    "می‌شوند و در تنظیمات می‌توانید آن را به «فقط موارد نامطمئن» محدود کنید. این بررسی " +
                    "خودکار و در پس‌زمینه انجام می‌شود، تا سقف روزانه‌ای که در تنظیمات تعیین می‌کنید. " +
                    "این اجازه را بعداً می‌توانید از تنظیمات لغو کنید."
            )
            .setPositiveButton("اجازه می‌دهم") { _, _ ->
                PromoStore.setAiConsent(true)
                onGranted()
            }
            .setNegativeButton("انصراف") { _, _ -> onDenied() }
            .setOnCancelListener { onDenied() }
            .show()
    }

    /** Withdraws consent; the next scan will ask again. */
    fun revoke() {
        PromoStore.setAiConsent(false)
    }
}
