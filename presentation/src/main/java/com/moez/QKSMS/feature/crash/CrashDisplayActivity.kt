package com.moez.QKSMS.feature.crash

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.moez.QKSMS.R
import java.io.File

/**
 * Standalone crash UI — no Dagger, so it can open even when DI/Realm failed.
 */
class CrashDisplayActivity : Activity() {

    companion object {
        const val EXTRA_CRASH_TEXT = "crash_text"
        const val EXTRA_CRASH_FILE = "crash_file"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.crash_display_activity)

        val crashTextView = findViewById<TextView>(R.id.crashText)
        val copyButton = findViewById<Button>(R.id.copyButton)
        val closeButton = findViewById<Button>(R.id.closeButton)

        val report = intent.getStringExtra(EXTRA_CRASH_TEXT)
                ?: intent.getStringExtra(EXTRA_CRASH_FILE)?.let { path ->
                    try {
                        File(path).takeIf { it.exists() }?.readText()
                    } catch (e: Exception) {
                        null
                    }
                }
                ?: "Crash report missing"

        crashTextView.text = report

        copyButton.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("smsPRO crash", report))
            Toast.makeText(this, "کپی شد — می‌توانید برای پشتیبانی بفرستید", Toast.LENGTH_LONG).show()
        }

        closeButton.setOnClickListener {
            finishAffinity()
        }
    }
}
