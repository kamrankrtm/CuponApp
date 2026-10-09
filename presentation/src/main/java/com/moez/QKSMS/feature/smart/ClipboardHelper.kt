package com.moez.QKSMS.feature.smart

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.widget.Toast
import timber.log.Timber

object ClipboardHelper {

    /**
     * The flag Android 13 reads to keep a clip out of the copy preview and clipboard history
     * (ClipDescription.EXTRA_IS_SENSITIVE). Written as a string so it compiles against older
     * SDKs; earlier releases ignore it.
     */
    private const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"

    /**
     * Puts [text] on the clipboard.
     *
     * @param sensitive marks the clip sensitive where the platform supports it (OTPs)
     * @return whether the clipboard accepted it; the toast only claims success when it did
     */
    fun copyToClipboard(
        context: Context,
        text: String,
        label: String = "OTP",
        showToast: Boolean = true,
        sensitive: Boolean = label == "OTP"
    ): Boolean {
        val copied = try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText(label, text)
            if (sensitive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                clip.description.extras = PersistableBundle().apply { putBoolean(EXTRA_IS_SENSITIVE, true) }
            }
            clipboard.setPrimaryClip(clip)
            // Reading the clip back is not possible from the background on Android 10+, so a
            // write that did not throw is the most the app can know
            true
        } catch (e: Exception) {
            // Never log the text: it is a credential
            Timber.w("Clipboard write failed: ${e.javaClass.simpleName}")
            false
        }

        if (showToast) {
            val message = when {
                !copied -> "کپی کد ممکن نشد؛ آن را دستی کپی کنید"
                label == "OTP" -> "کد تایید $text کپی شد"
                else -> "کد $text کپی شد"
            }
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT).show()
            }
        }
        return copied
    }
}
