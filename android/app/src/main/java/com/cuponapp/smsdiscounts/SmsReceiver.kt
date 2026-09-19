package com.cuponapp.smsdiscounts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import java.util.Date
import java.util.concurrent.Executors

/**
 * دریافت لحظه‌ای پیامک.
 *
 * اندروید این گیرنده را حتی وقتی اپ بسته است بیدار می‌کند. مسیر کار همان
 * مسیر اسکن دستی است و ترتیبش هم عوض نمی‌شود: ابتدا فیلتر محلی، و تنها
 * چیزی که از آن عبور کند به شبکه می‌رود. پیام شخصی، بانکی و رمز یک‌بارمصرف
 * همین‌جا روی گوشی کنار گذاشته می‌شوند.
 */
class SmsReceiver : BroadcastReceiver() {

    companion object {
        // استخر کوچک، چون پیامک‌ها معمولاً تک‌تک می‌رسند
        private val executor = Executors.newFixedThreadPool(2)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        // نخستین نشانه حیات: اگر این خط ثبت نشود، گیرنده اصلاً صدا زده نشده
        BackgroundLog.record(context, "received", "پیامک دریافت شد")

        val messages = try {
            Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        } catch (e: Exception) {
            return
        }
        if (messages.isEmpty()) return

        // پیامک چندبخشی به صورت چند شیء می‌رسد و باید به هم چسبانده شود
        val sender = messages[0].originatingAddress ?: return
        val body = messages.joinToString("") { it.messageBody ?: "" }
        val timestamp = messages[0].timestampMillis.takeIf { it > 0 } ?: System.currentTimeMillis()

        if (body.isBlank()) {
            BackgroundLog.record(context, "skipped", "متن پیامک خالی بود")
            return
        }

        val settings = AppSettings.read(context)
        val verdict = SmsClassifier.classify(sender, body, settings.strictness)

        // متوقف شدن اینجا یعنی پیامک هرگز از گوشی خارج نمی‌شود
        if (!verdict.safeToSend) {
            BackgroundLog.record(context, "filtered", "${verdict.kind} — ${verdict.reason}")
            return
        }
        if (!settings.isUsable) {
            BackgroundLog.record(context, "no-key", "کلید هوش مصنوعی تنظیم نشده است")
            return
        }

        val fingerprint = fingerprintOf(sender, body)
        if (PendingPromoStore.isAlreadySeen(context, fingerprint)) {
            BackgroundLog.record(context, "duplicate", "این پیامک قبلاً بررسی شده بود")
            return
        }

        BackgroundLog.record(context, "sending", "امتیاز ${verdict.score} — ارسال به هوش مصنوعی")

        // کار شبکه‌ای نباید روی رشته اصلی انجام شود؛ goAsync مهلت می‌دهد
        val pending = goAsync()
        val appContext = context.applicationContext

        executor.execute {
            try {
                PendingPromoStore.markSeen(appContext, fingerprint)

                val promo = AvalAiClient.analyze(settings, sender, body, Date(timestamp))
                if (promo != null) {
                    PendingPromoStore.add(appContext, promo, sender, body, timestamp, "SIM")
                    SmsNotifier.notifyPromo(appContext, promo)
                    BackgroundLog.record(appContext, "found", "${promo.brand} — کد ${promo.code}")
                } else {
                    BackgroundLog.record(appContext, "no-promo", "هوش مصنوعی کد تخفیفی پیدا نکرد یا پاسخ نداد")
                }
            } catch (e: Exception) {
                // خطای شبکه نباید گیرنده را بشکند؛ پیامک در اسکن بعدی دیده می‌شود
                BackgroundLog.record(appContext, "error", e.message ?: e.javaClass.simpleName)
            } finally {
                pending.finish()
            }
        }
    }

    /**
     * اثر انگشت پیامک، هم‌راستا با نسخه جاوااسکریپتی: ارقام به # تبدیل
     * می‌شوند تا پیامک‌های یکسانی که فقط عددشان فرق دارد دوباره تحلیل نشوند.
     */
    private fun fingerprintOf(sender: String, body: String): String {
        val normalized = SmsClassifier.normalizeDigits(body)
            .replace(Regex("[\\u200c\\s]+"), " ")
            .replace(Regex("[.,،؛:!؟?]"), "")
            .trim()
            .lowercase()
            .replace(Regex("\\d+"), "#")
        return "$sender|$normalized".hashCode().toString(36)
    }
}
