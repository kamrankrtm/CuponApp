package com.moez.QKSMS.feature.reminders

import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.appcompat.app.AlertDialog
import androidx.core.app.NotificationCompat
import com.moez.QKSMS.R
import com.moez.QKSMS.feature.compose.ComposeActivity
import org.json.JSONObject
import java.util.Calendar

/**
 * A reminder is an independent notification for a received SMS, NOT an outgoing scheduled SMS.
 * Persist the original preview and thread ID, and restore alarms after device restart/update.
 */
object MessageReminderManager {
    private const val STORE = "sms_reminders_v1"
    private const val CHANNEL = "message_reminders"
    private const val ACTION_REMIND = "com.moez.QKSMS.MESSAGE_REMINDER"

    private fun store(context: Context) = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)

    fun showPicker(context: Context, threadId: Long, sender: String, preview: String) {
        if (threadId <= 0L) return
        ensureChannel(context)
        val now = Calendar.getInstance()
        val options = arrayOf("یک ساعت دیگر", "امشب، ساعت ۲۰", "فردا صبح، ساعت ۹", "فردا عصر، ساعت ۱۸", "انتخاب تاریخ و ساعت")
        val old = read(context, threadId)
        val actions = if (old != null) options + arrayOf("حذف یادآوری قبلی") else options
        AlertDialog.Builder(context)
            .setTitle("یادآوری پیام از $sender")
            .setItems(actions) { _, position ->
                if (position == 5) {
                    cancel(context, threadId)
                    android.widget.Toast.makeText(context, "یادآوری حذف شد", android.widget.Toast.LENGTH_SHORT).show()
                    return@setItems
                }
                if (position == 4) {
                    val date = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
                    DatePickerDialog(context, { _, year, month, day ->
                        TimePickerDialog(context, { _, hour, minute ->
                            val whenAt = Calendar.getInstance().apply {
                                set(year, month, day, hour, minute)
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                            }.timeInMillis
                            saveOrWarn(context, threadId, sender, preview, whenAt)
                        }, 9, 0, true).show()
                    }, date.get(Calendar.YEAR), date.get(Calendar.MONTH), date.get(Calendar.DAY_OF_MONTH)).show()
                } else {
                    val at = when (position) {
                        0 -> now.apply { add(Calendar.HOUR_OF_DAY, 1) }
                        1 -> now.apply {
                            set(Calendar.HOUR_OF_DAY, 20); set(Calendar.MINUTE, 0)
                            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
                        }
                        2 -> now.apply {
                            add(Calendar.DAY_OF_YEAR, 1)
                            set(Calendar.HOUR_OF_DAY, 9); set(Calendar.MINUTE, 0)
                        }
                        else -> now.apply {
                            add(Calendar.DAY_OF_YEAR, 1)
                            set(Calendar.HOUR_OF_DAY, 18); set(Calendar.MINUTE, 0)
                        }
                    }
                    saveOrWarn(context, threadId, sender, preview, at.timeInMillis)
                }
            }.setNegativeButton("انصراف", null).show()
    }

    private fun ensureChannel(context: Context) {
        // Android 13 (targetSdk <= 32) prompts for notification permission only after
        // a channel is created while an Activity is in the foreground.
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(NotificationChannel(
                CHANNEL, "یادآوری پیامک‌ها", NotificationManager.IMPORTANCE_HIGH))
        }
    }

    private fun saveOrWarn(context: Context, threadId: Long, sender: String, preview: String, whenAt: Long) {
        if (whenAt <= System.currentTimeMillis()) {
            android.widget.Toast.makeText(context, "لطفاً زمانی در آینده انتخاب کنید", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        cancel(context, threadId)
        val record = JSONObject()
            .put("thread", threadId)
            .put("sender", sender)
            .put("text", preview.take(300))
            .put("at", whenAt)
        store(context).edit().putString(threadId.toString(), record.toString()).apply()
        schedule(context, threadId, whenAt)
        android.widget.Toast.makeText(context, "یادآوری ثبت شد", android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun read(context: Context, threadId: Long): JSONObject? =
        try { store(context).getString(threadId.toString(), null)?.let(::JSONObject) } catch (_: Exception) { null }

    private fun pending(context: Context, threadId: Long): PendingIntent {
        val intent = Intent(context, MessageReminderReceiver::class.java).apply {
            action = ACTION_REMIND
            data = android.net.Uri.parse("cuponapp://reminder/$threadId")
            putExtra("thread", threadId)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        return PendingIntent.getBroadcast(context, threadId.hashCode(), intent, flags)
    }

    private fun schedule(context: Context, threadId: Long, at: Long) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pending(context, threadId)
        try {
            if (Build.VERSION.SDK_INT >= 23) {
                alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                alarm.setExact(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (_: SecurityException) {
            // Exact alarms can be restricted on Android 12+, but reminders must still fire.
            if (Build.VERSION.SDK_INT >= 23) alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else alarm.set(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun cancel(context: Context, threadId: Long) {
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending(context, threadId))
        store(context).edit().remove(threadId.toString()).apply()
    }

    fun restore(context: Context) {
        for ((key, _) in store(context).all) {
            val thread = key.toLongOrNull() ?: continue
            val record = read(context, thread) ?: continue
            val at = record.optLong("at")
            if (at > System.currentTimeMillis()) schedule(context, thread, at)
            else store(context).edit().remove(key).apply()
        }
    }

    fun deliver(context: Context, threadId: Long) {
        val record = read(context, threadId) ?: return
        store(context).edit().remove(threadId.toString()).apply()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(context)
        val sender = record.optString("sender", "پیامک")
        val preview = record.optString("text", "پیام موردنظر")
        val open = Intent(context, ComposeActivity::class.java).apply {
            putExtra("threadId", threadId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        val pi = PendingIntent.getActivity(context, threadId.hashCode(), open, flags)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("یادآوری پیامک: $sender")
            .setContentText(preview)
            .setStyle(NotificationCompat.BigTextStyle().bigText(preview))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
        try { manager.notify(600000 + (threadId.hashCode() and 0xffff), notification) }
        catch (_: SecurityException) { /* Notifications may be disabled by Android/user. */ }
    }
}

class MessageReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            MessageReminderManager.restore(context)
        } else if (intent.action == "com.moez.QKSMS.MESSAGE_REMINDER") {
            MessageReminderManager.deliver(context, intent.getLongExtra("thread", -1L))
        }
    }
}
