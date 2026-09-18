package com.cuponapp.smsdiscounts

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat

/** کپی کد تخفیف از روی خود اعلان، بدون باز کردن اپ */
class CopyCodeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != SmsNotifier.ACTION_COPY) return

        val code = intent.getStringExtra(SmsNotifier.EXTRA_CODE) ?: return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("کد تخفیف", code))

        Toast.makeText(context, "کد $code کپی شد", Toast.LENGTH_SHORT).show()

        val id = intent.getIntExtra(SmsNotifier.EXTRA_NOTIFICATION_ID, -1)
        if (id != -1) NotificationManagerCompat.from(context).cancel(id)
    }
}
