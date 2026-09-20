package com.moez.QKSMS.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.moez.QKSMS.feature.smart.ClipboardHelper

class CopyClipReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val textToCopy = intent.getStringExtra(EXTRA_TEXT) ?: return
        val label = intent.getStringExtra(EXTRA_LABEL) ?: "CuponApp"
        ClipboardHelper.copyToClipboard(context, textToCopy, label, showToast = true)
    }

    companion object {
        const val EXTRA_TEXT = "extra_text_to_copy"
        const val EXTRA_LABEL = "extra_label_to_copy"
    }
}
