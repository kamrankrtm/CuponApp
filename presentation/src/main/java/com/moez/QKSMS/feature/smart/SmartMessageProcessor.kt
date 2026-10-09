package com.moez.QKSMS.feature.smart

import android.content.Context
import android.provider.Telephony
import com.moez.QKSMS.feature.smart.ai.AiPromoExtractor
import com.moez.QKSMS.feature.smart.ai.SmsAiFallback
import com.moez.QKSMS.feature.smart.analysis.OtpCopyPolicy
import com.moez.QKSMS.feature.smart.analysis.SmsAnalysis
import com.moez.QKSMS.feature.smart.analysis.SmsAnalyzer
import com.moez.QKSMS.feature.smart.analysis.SmsKind
import com.moez.QKSMS.manager.MessageAnalysisProcessor
import com.moez.QKSMS.model.Message
import com.moez.QKSMS.repository.SyncRepository
import com.moez.QKSMS.util.Preferences
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one place an incoming SMS is read. Runs on the receive pipeline's background thread,
 * after the message is stored and before its notification is built:
 * - the reading goes to [SmartAnalysisCache], where the notification, the tabs and the inbox
 *   scan pick it up instead of reading the message again;
 * - a verification code is listed and, if every condition of [OtpCopyPolicy] holds, copied
 *   once — never again on a notification refresh;
 * - coupon cards are saved in one write;
 * - the AI tiers are scheduled, never awaited.
 *
 * It also watches full syncs: they rebuild every local record, so every reading is redone.
 */
@Singleton
class SmartMessageProcessor @Inject constructor(
    private val context: Context,
    private val prefs: Preferences,
    syncRepo: SyncRepository
) : MessageAnalysisProcessor {

    @Volatile
    private var syncRunning = false

    init {
        syncRepo.syncProgress.subscribe({ progress ->
            val running = progress is SyncRepository.SyncProgress.Running
            if (syncRunning && !running) SmartAnalysisCache.invalidateAll()
            syncRunning = running
        }, { Timber.w(it) })
    }

    override fun onMessageStored(message: Message) {
        if (!message.isSms() || message.boxId != Telephony.Sms.MESSAGE_TYPE_INBOX) return
        val analysis = SmartAnalysisCache.analyze(message.address, message.body, message.date, message.threadId,
            SmsAnalyzer.sourceKeyOf(message.type, message.contentId))
        apply(context, prefs, analysis)
    }

    companion object {

        /** Files one reading into the smart lists and acts on it; shared with the AI fallback. */
        fun apply(context: Context, prefs: Preferences, analysis: SmsAnalysis, aiSelected: Boolean = false) {
            analysis.otpItem?.let { otp ->
                // A late reading never displaces a newer code; it is still listed under it
                SmartDataManager.addOtp(otp)
                maybeAutoCopy(context, prefs, analysis, aiSelected)
            }
            if (analysis.promos.isNotEmpty()) SmartDataManager.addPromos(analysis.promos)

            if (analysis.kind == SmsKind.PROMO || analysis.kind == SmsKind.SPAM) {
                AiPromoExtractor.scheduleAutoRefine(context, prefs)
            }
            SmsAiFallback.maybeSchedule(context, prefs, analysis)
            SmartEvents.changed()
        }

        private fun maybeAutoCopy(context: Context, prefs: Preferences, analysis: SmsAnalysis, aiSelected: Boolean) {
            val otp = analysis.otpItem ?: return
            val decision = OtpCopyPolicy.decide(
                otp = analysis.otp,
                code = otp.code,
                receivedAt = otp.receivedAt,
                expiresAt = otp.expiresAt,
                now = System.currentTimeMillis(),
                autoCopyEnabled = prefs.autoCopyOtp.get(),
                supersededByNewer = SmartDataManager.hasNewerOtp(otp.receivedAt, otp.sourceKey),
                alreadyCopied = OtpCopyTracker.wasCopied(otp.sourceKey),
                aiSelected = aiSelected
            )
            if (!decision.copy) return
            if (ClipboardHelper.copyToClipboard(context, otp.code, "OTP", showToast = true, sensitive = true)) {
                OtpCopyTracker.markCopied(otp.sourceKey)
            }
        }
    }
}

/** Which codes were put on the clipboard automatically, so a refresh never copies one again. Memory only. */
object OtpCopyTracker {
    private const val MAX = 64
    private val copied = LinkedHashSet<String>()

    @Synchronized
    fun wasCopied(sourceKey: String): Boolean = sourceKey.isNotEmpty() && sourceKey in copied

    @Synchronized
    fun markCopied(sourceKey: String) {
        if (sourceKey.isEmpty()) return
        copied.add(sourceKey)
        while (copied.size > MAX) copied.remove(copied.first())
    }
}

/** Tells the open screen that the smart lists or readings changed, on the main thread. */
object SmartEvents {
    @Volatile
    var listener: (() -> Unit)? = null

    private val main by lazy { android.os.Handler(android.os.Looper.getMainLooper()) }

    fun changed() {
        val l = listener ?: return
        main.post { l() }
    }
}
