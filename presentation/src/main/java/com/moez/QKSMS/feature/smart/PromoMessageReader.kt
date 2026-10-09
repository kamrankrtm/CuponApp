package com.moez.QKSMS.feature.smart

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.Telephony
import com.moez.QKSMS.feature.smart.promo.PromoParser
import com.moez.QKSMS.feature.smart.promo.PromoReadPolicy
import com.moez.QKSMS.model.Message
import io.realm.Realm
import timber.log.Timber

/** Updates only confirmed coupon SMS rows; other unread messages in the thread stay unread. */
object PromoMessageReader {
    fun confirmed(sender: String, body: String, date: Long, threadId: Long): Boolean =
        PromoReadPolicy.shouldMarkRead(PromoParser.analyze(sender, body, date, threadId))

    fun markRead(context: Context, messageIds: Collection<Long>) {
        if (messageIds.isEmpty() || Telephony.Sms.getDefaultSmsPackage(context) != context.packageName) return
        val values = ContentValues().apply {
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.SEEN, 1)
        }
        Realm.getDefaultInstance().use { realm ->
            val updated = messageIds.distinct().filter { id ->
                val message = realm.where(Message::class.java).equalTo("id", id)
                    .equalTo("type", "sms")
                    .equalTo("boxId", Telephony.Sms.MESSAGE_TYPE_INBOX).findFirst()
                    ?: return@filter false
                if (message.read && message.seen) return@filter false
                try {
                    context.contentResolver.update(
                        ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, message.contentId), values,
                        "${Telephony.Sms.TYPE} = ?",
                        arrayOf(Telephony.Sms.MESSAGE_TYPE_INBOX.toString())
                    ) > 0
                } catch (e: Exception) {
                    Timber.w(e, "Could not mark coupon SMS read")
                    false
                }
            }
            if (updated.isEmpty()) return
            realm.executeTransaction {
                updated.forEach { id ->
                    realm.where(Message::class.java).equalTo("id", id)
                        .equalTo("type", "sms").findFirst()?.let { message ->
                            message.read = true
                            message.seen = true
                        }
                }
            }
        }
    }
}
