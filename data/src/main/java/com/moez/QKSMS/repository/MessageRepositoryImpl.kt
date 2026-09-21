/*
 * Copyright (C) 2017 Moez Bhatti <moez.bhatti@gmail.com>
 *
 * This file is part of QKSMS.
 *
 * QKSMS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * QKSMS is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with QKSMS.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.moez.QKSMS.repository

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.provider.Telephony.Mms
import android.provider.Telephony.Sms
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.webkit.MimeTypeMap
import androidx.core.content.contentValuesOf
import com.google.android.mms.ContentType
import com.google.android.mms.MMSPart
import com.google.android.mms.pdu_alt.MultimediaMessagePdu
import com.google.android.mms.pdu_alt.PduPersister
import com.klinker.android.send_message.SmsManagerFactory
import com.klinker.android.send_message.StripAccents
import com.klinker.android.send_message.Transaction
import com.moez.QKSMS.common.util.extensions.now
import com.moez.QKSMS.compat.TelephonyCompat
import com.moez.QKSMS.extensions.anyOf
import com.moez.QKSMS.manager.ActiveConversationManager
import com.moez.QKSMS.manager.KeyManager
import com.moez.QKSMS.model.Attachment
import com.moez.QKSMS.model.Conversation
import com.moez.QKSMS.model.Message
import com.moez.QKSMS.model.MmsPart
import com.moez.QKSMS.receiver.SendSmsReceiver
import com.moez.QKSMS.receiver.SmsDeliveredReceiver
import com.moez.QKSMS.receiver.SmsSentReceiver
import com.moez.QKSMS.util.ImageUtils
import com.moez.QKSMS.util.PhoneNumberUtils
import com.moez.QKSMS.util.Preferences
import com.moez.QKSMS.util.tryOrNull
import io.realm.Case
import io.realm.Realm
import io.realm.RealmResults
import io.realm.Sort
import timber.log.Timber
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.collections.ArrayList
import kotlin.math.sqrt

@Singleton
class MessageRepositoryImpl @Inject constructor(
    private val activeConversationManager: ActiveConversationManager,
    private val context: Context,
    private val messageIds: KeyManager,
    private val phoneNumberUtils: PhoneNumberUtils,
    private val prefs: Preferences,
    private val syncRepository: SyncRepository
) : MessageRepository {

    override fun getMessages(threadId: Long, query: String): RealmResults<Message> {
        return Realm.getDefaultInstance()
                .where(Message::class.java)
                .equalTo("threadId", threadId)
                .let {
                    when (query.isEmpty()) {
                        true -> it
                        false -> it
                                .beginGroup()
                                .contains("body", query, Case.INSENSITIVE)
                                .or()
                                .contains("parts.text", query, Case.INSENSITIVE)
                                .endGroup()
                    }
                }
                .sort("date")
                .findAllAsync()
    }

    override fun getMessage(id: Long): Message? {
        return Realm.getDefaultInstance()
                .also { realm -> realm.refresh() }
                .where(Message::class.java)
                .equalTo("id", id)
                .findFirst()
    }

    override fun getMessageForPart(id: Long): Message? {
        return Realm.getDefaultInstance()
                .where(Message::class.java)
                .equalTo("parts.id", id)
                .findFirst()
    }

    override fun getLastIncomingMessage(threadId: Long): RealmResults<Message> {
        return Realm.getDefaultInstance()
                .where(Message::class.java)
                .equalTo("threadId", threadId)
                .beginGroup()
                .beginGroup()
                .equalTo("type", "sms")
                .`in`("boxId", arrayOf(Sms.MESSAGE_TYPE_INBOX, Sms.MESSAGE_TYPE_ALL))
                .endGroup()
                .or()
                .beginGroup()
                .equalTo("type", "mms")
                .`in`("boxId", arrayOf(Mms.MESSAGE_BOX_INBOX, Mms.MESSAGE_BOX_ALL))
                .endGroup()
                .endGroup()
                .sort("date", Sort.DESCENDING)
                .findAll()
    }

    override fun getUnreadCount(): Long {
        return Realm.getDefaultInstance().use { realm ->
            realm.refresh()
            realm.where(Conversation::class.java)
                    .equalTo("archived", false)
                    .equalTo("blocked", false)
                    .equalTo("lastMessage.read", false)
                    .count()
        }
    }

    override fun getPart(id: Long): MmsPart? {
        return Realm.getDefaultInstance()
                .where(MmsPart::class.java)
                .equalTo("id", id)
                .findFirst()
    }

    override fun getPartsForConversation(threadId: Long): RealmResults<MmsPart> {
        return Realm.getDefaultInstance()
                .where(MmsPart::class.java)
                .equalTo("messages.threadId", threadId)
                .beginGroup()
                .contains("type", "image/")
                .or()
                .contains("type", "video/")
                .endGroup()
                .sort("id", Sort.DESCENDING)
                .findAllAsync()
    }

    override fun savePart(id: Long): File? {
        val part = getPart(id) ?: return null

        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(part.type) ?: return null
        val date = part.messages?.first()?.date
        val dir = File(Environment.getExternalStorageDirectory(), "QKSMS/Media").apply { mkdirs() }
        val fileName = part.name?.takeIf { name -> name.endsWith(extension) }
                ?: "${part.type.split("/").last()}_$date.$extension"
        var file: File
        var index = 0
        do {
            file = File(dir, if (index == 0) fileName else fileName.replace(".$extension", " ($index).$extension"))
            index++
        } while (file.exists())

        try {
            FileOutputStream(file).use { outputStream ->
                context.contentResolver.openInputStream(part.getUri())?.use { inputStream ->
                    inputStream.copyTo(outputStream, 1024)
                }
            }
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }

        MediaScannerConnection.scanFile(context, arrayOf(file.path), null, null)

        return file.takeIf { it.exists() }
    }

    /**
     * Retrieves the list of messages which should be shown in the notification
     * for a given conversation
     */
    override fun getUnreadUnseenMessages(threadId: Long): RealmResults<Message> {
        return Realm.getDefaultInstance()
                .also { it.refresh() }
                .where(Message::class.java)
                .equalTo("seen", false)
                .equalTo("read", false)
                .equalTo("threadId", threadId)
                .sort("date")
                .findAll()
    }

    override fun getUnreadMessages(threadId: Long): RealmResults<Message> {
        return Realm.getDefaultInstance()
                .where(Message::class.java)
                .equalTo("read", false)
                .equalTo("threadId", threadId)
                .sort("date")
                .findAll()
    }

    override fun markAllSeen() {
        val realm = Realm.getDefaultInstance()
        val messages = realm.where(Message::class.java).equalTo("seen", false).findAll()
        realm.executeTransaction { messages.forEach { message -> message.seen = true } }
        realm.close()
    }

    override fun markSeen(threadId: Long) {
        val realm = Realm.getDefaultInstance()
        val messages = realm.where(Message::class.java)
                .equalTo("threadId", threadId)
                .equalTo("seen", false)
                .findAll()

        realm.executeTransaction {
            messages.forEach { message ->
                message.seen = true
            }
        }
        realm.close()
    }

    override fun markRead(vararg threadIds: Long) {
        Realm.getDefaultInstance()?.use { realm ->
            val query = realm.where(Message::class.java)
                .beginGroup()
                .equalTo("read", false)
                .or()
                .equalTo("seen", false)
                .endGroup()

            if (threadIds.isNotEmpty()) {
                query.beginGroup().anyOf("threadId", threadIds).endGroup()
            }

            val messages = query.findAll()

            realm.executeTransaction {
                messages.forEach { message ->
                    message.seen = true
                    message.read = true
                }
            }
        }

        val values = ContentValues()
        values.put(Sms.SEEN, true)
        values.put(Sms.READ, true)

        try {
            if (threadIds.isEmpty()) {
                context.contentResolver.update(Telephony.Sms.CONTENT_URI, values, "${Sms.READ} = 0 OR ${Sms.SEEN} = 0", null)
            } else {
                threadIds.forEach { threadId ->
                    try {
                        val uri = ContentUris.withAppendedId(Telephony.MmsSms.CONTENT_CONVERSATIONS_URI, threadId)
                        context.contentResolver.update(uri, values, "${Sms.READ} = 0", null)
                    } catch (exception: Exception) {
                        Timber.w(exception)
                    }
                }
            }
        } catch (t: Throwable) {
            Timber.w(t)
        }
    }

    override fun markUnread(vararg threadIds: Long) {
        Realm.getDefaultInstance()?.use { realm ->
            val conversations = realm.where(Conversation::class.java)
                    .anyOf("id", threadIds)
                    .equalTo("lastMessage.read", true)
                    .findAll()

            realm.executeTransaction {
                conversations.forEach { conversation ->
                    conversation.lastMessage?.read = false
                }
            }
        }
    }

    override fun sendMessage(
        subId: Int,
        threadId: Long,
        addresses: List<String>,
        body: String,
        attachments: List<Attachment>,
        delay: Int
    ) {
        val signedBody = when {
            prefs.signature.get().isEmpty() -> body
            body.isNotEmpty() -> body + '\n' + prefs.signature.get()
            else -> prefs.signature.get()
        }

        val cleanAddresses = addresses.map { phoneNumberUtils.cleanDestinationAddress(it) }

        // Resolve active subscription for Dual SIM devices
        val subManager = tryOrNull { context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager }
        val defaultSubId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            SubscriptionManager.getDefaultSmsSubscriptionId()
        } else {
            -1
        }
        val resolvedSubId = when {
            subId != -1 && subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID -> subId
            defaultSubId != -1 && defaultSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID -> defaultSubId
            else -> tryOrNull { subManager?.activeSubscriptionInfoList?.firstOrNull()?.subscriptionId } ?: -1
        }

        val smsManager: SmsManager = when {
            resolvedSubId != -1 && resolvedSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID && Build.VERSION.SDK_INT >= 22 -> {
                @Suppress("DEPRECATION")
                tryOrNull { SmsManager.getSmsManagerForSubscriptionId(resolvedSubId) } ?: SmsManager.getDefault()
            }
            else -> SmsManager.getDefault()
        }

        // We only care about stripping SMS
        val strippedBody = when (prefs.unicode.get()) {
            true -> StripAccents.stripAccents(signedBody)
            false -> signedBody
        }

        val parts = smsManager.divideMessage(strippedBody).orEmpty()
        val forceMms = prefs.longAsMms.get() && parts.size > 1

        if (cleanAddresses.size == 1 && attachments.isEmpty() && !forceMms) { // SMS
            if (delay > 0) { // With delay
                val sendTime = System.currentTimeMillis() + delay
                val message = insertSentSms(resolvedSubId, threadId, cleanAddresses.first(), strippedBody, sendTime)

                val intent = getIntentForDelayedSms(message.id)

                try {
                    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, sendTime, intent)
                    } else {
                        alarmManager.setExact(AlarmManager.RTC_WAKEUP, sendTime, intent)
                    }
                } catch (t: Throwable) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        sendSms(message)
                    }, delay.toLong())
                }
            } else { // No delay
                val message = insertSentSms(resolvedSubId, threadId, cleanAddresses.first(), strippedBody, now())
                sendSms(message)
            }
        } else { // MMS
            val parts = arrayListOf<MMSPart>()

            val maxWidth = smsManager.carrierConfigValues.getInt(SmsManager.MMS_CONFIG_MAX_IMAGE_WIDTH)
                    .takeIf { prefs.mmsSize.get() == -1 } ?: Int.MAX_VALUE

            val maxHeight = smsManager.carrierConfigValues.getInt(SmsManager.MMS_CONFIG_MAX_IMAGE_HEIGHT)
                    .takeIf { prefs.mmsSize.get() == -1 } ?: Int.MAX_VALUE

            var remainingBytes = when (prefs.mmsSize.get()) {
                -1 -> smsManager.carrierConfigValues.getInt(SmsManager.MMS_CONFIG_MAX_MESSAGE_SIZE)
                0 -> Int.MAX_VALUE
                else -> prefs.mmsSize.get() * 1024
            } * 0.9 // Ugly, but buys us a bit of wiggle room

            signedBody.takeIf { it.isNotEmpty() }?.toByteArray()?.let { bytes ->
                remainingBytes -= bytes.size
                parts += MMSPart("text", ContentType.TEXT_PLAIN, bytes)
            }

            // Attach contacts
            parts += attachments
                    .mapNotNull { attachment -> attachment as? Attachment.Contact }
                    .map { attachment -> attachment.vCard.toByteArray() }
                    .map { vCard ->
                        remainingBytes -= vCard.size
                        MMSPart("contact", ContentType.TEXT_VCARD, vCard)
                    }

            val imageBytesByAttachment = attachments
                    .mapNotNull { attachment -> attachment as? Attachment.Image }
                    .associateWith { attachment ->
                        val uri = attachment.getUri() ?: return@associateWith byteArrayOf()
                        when (attachment.isGif(context)) {
                            true -> ImageUtils.getScaledGif(context, uri, maxWidth, maxHeight)
                            false -> ImageUtils.getScaledImage(context, uri, maxWidth, maxHeight)
                        }
                    }
                    .toMutableMap()

            val imageByteCount = imageBytesByAttachment.values.sumBy { byteArray -> byteArray.size }
            if (imageByteCount > remainingBytes) {
                imageBytesByAttachment.forEach { (attachment, originalBytes) ->
                    val uri = attachment.getUri() ?: return@forEach
                    val maxBytes = originalBytes.size / imageByteCount.toFloat() * remainingBytes

                    // Get the image dimensions
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeStream(context.contentResolver.openInputStream(uri), null, options)
                    val width = options.outWidth
                    val height = options.outHeight
                    val aspectRatio = width.toFloat() / height.toFloat()

                    var attempts = 0
                    var scaledBytes = originalBytes

                    while (scaledBytes.size > maxBytes) {
                        // Estimate how much we need to scale the image down by. If it's still too big, we'll need to
                        // try smaller and smaller values
                        val scale = maxBytes / originalBytes.size * (0.9 - attempts * 0.2)
                        if (scale <= 0) {
                            Timber.w("Failed to compress ${originalBytes.size / 1024}Kb to ${maxBytes.toInt() / 1024}Kb")
                            return@forEach
                        }

                        val newArea = scale * width * height
                        val newWidth = sqrt(newArea * aspectRatio).toInt()
                        val newHeight = (newWidth / aspectRatio).toInt()

                        attempts++
                        scaledBytes = when (attachment.isGif(context)) {
                            true -> ImageUtils.getScaledGif(context, uri, newWidth, newHeight, 80)
                            false -> ImageUtils.getScaledImage(context, uri, newWidth, newHeight, 80)
                        }

                        Timber.d("Compression attempt $attempts: ${scaledBytes.size / 1024}/${maxBytes.toInt() / 1024}Kb ($width*$height -> $newWidth*$newHeight)")
                    }

                    Timber.v("Compressed ${originalBytes.size / 1024}Kb to ${scaledBytes.size / 1024}Kb with a target size of ${maxBytes.toInt() / 1024}Kb in $attempts attempts")
                    imageBytesByAttachment[attachment] = scaledBytes
                }
            }

            imageBytesByAttachment.forEach { (attachment, bytes) ->
                parts += when (attachment.isGif(context)) {
                    true -> MMSPart("image", ContentType.IMAGE_GIF, bytes)
                    false -> MMSPart("image", ContentType.IMAGE_JPEG, bytes)
                }
            }

            // We need to strip the separators from outgoing MMS, or else they'll appear to have sent and not go through
            val transaction = Transaction(context)
            val recipients = addresses.map(phoneNumberUtils::normalizeNumber)
            transaction.sendNewMessage(subId, threadId, recipients, parts, null, null)
        }
    }

    override fun sendSms(message: Message) {
        val destAddress = phoneNumberUtils.cleanDestinationAddress(message.address).takeIf { it.isNotEmpty() } ?: message.address

        // Resolve active subscription for Dual SIM devices
        val subManager = tryOrNull { context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager }
        val defaultSubId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            SubscriptionManager.getDefaultSmsSubscriptionId()
        } else {
            -1
        }
        val effectiveSubId = when {
            message.subId != -1 && message.subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID -> message.subId
            defaultSubId != -1 && defaultSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID -> defaultSubId
            else -> tryOrNull { subManager?.activeSubscriptionInfoList?.firstOrNull()?.subscriptionId } ?: -1
        }

        val smsManager: SmsManager = when {
            effectiveSubId != -1 && effectiveSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID && Build.VERSION.SDK_INT >= 22 -> {
                @Suppress("DEPRECATION")
                tryOrNull { SmsManager.getSmsManagerForSubscriptionId(effectiveSubId) } ?: SmsManager.getDefault()
            }
            else -> SmsManager.getDefault()
        }

        val strippedBody = if (prefs.unicode.get()) StripAccents.stripAccents(message.body) else message.body
        val parts = smsManager.divideMessage(strippedBody)?.takeIf { it.isNotEmpty() } ?: arrayListOf(strippedBody)

        val flagMutable = 0x02000000 // PendingIntent.FLAG_MUTABLE for Android 12+ (API 31+)
        val piFlags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) flagMutable else 0)

        val sentIntents = parts.mapIndexed { index, _ ->
            val intent = Intent(context, SmsSentReceiver::class.java).apply {
                setPackage(context.packageName)
                putExtra("id", message.id)
                putExtra("partIndex", index)
                action = "com.moez.QKSMS.SMS_SENT_${message.id}_$index"
            }
            val requestCode = ((message.id.hashCode() * 31) + index) and 0x7FFFFFFF
            PendingIntent.getBroadcast(context, requestCode, intent, piFlags)
        }

        val deliveryEnabled = prefs.delivery.get()
        val deliveredIntents = if (deliveryEnabled) {
            parts.mapIndexed { index, _ ->
                val intent = Intent(context, SmsDeliveredReceiver::class.java).apply {
                    setPackage(context.packageName)
                    putExtra("id", message.id)
                    putExtra("partIndex", index)
                    action = "com.moez.QKSMS.SMS_DELIVERED_${message.id}_$index"
                }
                val requestCode = ((message.id.hashCode() * 67) + index) and 0x7FFFFFFF
                PendingIntent.getBroadcast(context, requestCode, intent, piFlags)
            }
        } else {
            null
        }

        try {
            if (parts.size <= 1) {
                smsManager.sendTextMessage(
                    destAddress,
                    null,
                    parts.firstOrNull() ?: strippedBody,
                    sentIntents.firstOrNull(),
                    deliveredIntents?.firstOrNull()
                )
            } else {
                smsManager.sendMultipartTextMessage(
                    destAddress,
                    null,
                    parts,
                    ArrayList(sentIntents),
                    deliveredIntents?.let { ArrayList(it) }
                )
            }

            // Extract primitive messageId and messageUri before passing to Handler to prevent
            // Realm access from incorrect thread (IllegalStateException)
            val messageId = message.id
            val messageUri = tryOrNull { message.getUri() }

            // Fallback watchdog: if Android OS fails to broadcast sent confirmation,
            // automatically mark as SENT after 15s so it doesn't get stuck on "Sending..." forever
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    Realm.getDefaultInstance()?.use { realm ->
                        realm.executeTransaction {
                            val m = realm.where(Message::class.java).equalTo("id", messageId).findFirst()
                            if (m != null && m.isValid && m.boxId == Sms.MESSAGE_TYPE_OUTBOX) {
                                m.boxId = Sms.MESSAGE_TYPE_SENT
                            }
                        }
                        if (messageUri != null) {
                            val values = ContentValues()
                            values.put(Sms.TYPE, Sms.MESSAGE_TYPE_SENT)
                            try {
                                context.contentResolver.update(messageUri, values, null, null)
                            } catch (t: Throwable) {
                                // ignore
                            }
                        }
                    }
                } catch (t: Throwable) {
                    // ignore
                }
            }, 15000L)
        } catch (e: Throwable) {
            val messageId = tryOrNull { message.id } ?: 0L
            Timber.e(e, "Message send error: ${e.message}")
            android.util.Log.e("MessageRepo", "Message send error: ${e.message}", e)
            if (messageId != 0L) {
                markFailed(messageId, Telephony.MmsSms.ERR_TYPE_GENERIC)
            }
        }
    }

    override fun resendMms(message: Message) {
        val subId = message.subId
        val threadId = message.threadId
        val pdu = tryOrNull {
            PduPersister.getPduPersister(context).load(message.getUri()) as MultimediaMessagePdu
        } ?: return

        val addresses = pdu.to.map { it.string }.filter { it.isNotBlank() }
        val parts = message.parts.mapNotNull { part ->
            val bytes = tryOrNull(false) {
                context.contentResolver.openInputStream(part.getUri())?.use { inputStream -> inputStream.readBytes() }
            } ?: return@mapNotNull null

            MMSPart(part.name.orEmpty(), part.type, bytes)
        }

        Transaction(context).sendNewMessage(subId, threadId, addresses, parts, message.subject, message.getUri())
    }

    override fun cancelDelayedSms(id: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(getIntentForDelayedSms(id))
    }

    private fun getIntentForDelayedSms(id: Long): PendingIntent {
        val intent = Intent(context, SendSmsReceiver::class.java).putExtra("id", id)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        return PendingIntent.getBroadcast(context, id.toInt(), intent, flags)
    }

    override fun insertSentSms(subId: Int, threadId: Long, address: String, body: String, date: Long): Message {

        // Insert the message to Realm
        val message = Message().apply {
            this.threadId = threadId
            this.address = address
            this.body = body
            this.date = date
            this.subId = subId

            id = messageIds.newId()
            boxId = Sms.MESSAGE_TYPE_OUTBOX
            type = "sms"
            read = true
            seen = true
        }
        val realm = Realm.getDefaultInstance()
        var managedMessage: Message? = null
        realm.executeTransaction { managedMessage = realm.copyToRealmOrUpdate(message) }

        // Insert the message to the native content provider
        val values = contentValuesOf(
                Sms.ADDRESS to address,
                Sms.BODY to body,
                Sms.DATE to System.currentTimeMillis(),
                Sms.READ to true,
                Sms.SEEN to true,
                Sms.TYPE to Sms.MESSAGE_TYPE_OUTBOX,
                Sms.THREAD_ID to threadId
        )

        if (prefs.canUseSubId.get()) {
            values.put(Sms.SUBSCRIPTION_ID, message.subId)
        }

        val uri = context.contentResolver.insert(Sms.CONTENT_URI, values)

        // Update the contentId after the message has been inserted to the content provider
        // The message might have been deleted by now, so only proceed if it's valid
        //
        // We do this after inserting the message because it might be slow, and we want the message
        // to be inserted into Realm immediately. We don't need to do this after receiving one
        uri?.lastPathSegment?.toLong()?.let { id ->
            realm.executeTransaction { managedMessage?.takeIf { it.isValid }?.contentId = id }
        }
        realm.close()

        // On some devices, we can't obtain a threadId until after the first message is sent in a
        // conversation. In this case, we need to update the message's threadId after it gets added
        // to the native ContentProvider
        if (threadId == 0L) {
            uri?.let(syncRepository::syncMessage)
        }

        return message
    }

    override fun insertReceivedSms(subId: Int, address: String, body: String, sentTime: Long): Message {

        // Insert the message to Realm
        val message = Message().apply {
            this.address = address
            this.body = body
            this.dateSent = sentTime
            this.date = System.currentTimeMillis()
            this.subId = subId

            id = messageIds.newId()
            threadId = TelephonyCompat.getOrCreateThreadId(context, address)
            boxId = Sms.MESSAGE_TYPE_INBOX
            type = "sms"
            read = activeConversationManager.getActiveConversation() == threadId
        }
        val realm = Realm.getDefaultInstance()
        var managedMessage: Message? = null
        realm.executeTransaction { managedMessage = realm.copyToRealmOrUpdate(message) }

        // Insert the message to the native content provider
        val values = contentValuesOf(
                Sms.ADDRESS to address,
                Sms.BODY to body,
                Sms.DATE_SENT to sentTime
        )

        if (prefs.canUseSubId.get()) {
            values.put(Sms.SUBSCRIPTION_ID, message.subId)
        }

        context.contentResolver.insert(Sms.Inbox.CONTENT_URI, values)?.lastPathSegment?.toLong()?.let { id ->
            // Update the contentId after the message has been inserted to the content provider
            realm.executeTransaction { managedMessage?.contentId = id }
        }

        realm.close()

        return message
    }

    /**
     * Marks the message as sending, in case we need to retry sending it
     */
    override fun markSending(id: Long) {
        Realm.getDefaultInstance().use { realm ->
            realm.refresh()

            val message = realm.where(Message::class.java).equalTo("id", id).findFirst()
            message?.let {
                // Update the message in realm
                realm.executeTransaction {
                    message.boxId = when (message.isSms()) {
                        true -> Sms.MESSAGE_TYPE_OUTBOX
                        false -> Mms.MESSAGE_BOX_OUTBOX
                    }
                }

                // Update the message in the native ContentProvider
                val values = when (message.isSms()) {
                    true -> contentValuesOf(Sms.TYPE to Sms.MESSAGE_TYPE_OUTBOX)
                    false -> contentValuesOf(Mms.MESSAGE_BOX to Mms.MESSAGE_BOX_OUTBOX)
                }
                context.contentResolver.update(message.getUri(), values, null, null)
            }
        }
    }

    override fun markSent(id: Long) {
        Realm.getDefaultInstance().use { realm ->
            realm.refresh()

            val message = realm.where(Message::class.java).equalTo("id", id).findFirst()
            message?.let {
                // Update the message in realm
                realm.executeTransaction {
                    message.boxId = Sms.MESSAGE_TYPE_SENT
                }

                // Update the message in the native ContentProvider
                val values = ContentValues()
                values.put(Sms.TYPE, Sms.MESSAGE_TYPE_SENT)
                context.contentResolver.update(message.getUri(), values, null, null)
            }
        }
    }

    override fun markFailed(id: Long, resultCode: Int) {
        Realm.getDefaultInstance().use { realm ->
            realm.refresh()

            val message = realm.where(Message::class.java).equalTo("id", id).findFirst()
            message?.let {
                // Update the message in realm
                realm.executeTransaction {
                    message.boxId = Sms.MESSAGE_TYPE_FAILED
                    message.errorCode = resultCode
                }

                // Update the message in the native ContentProvider
                val values = ContentValues()
                values.put(Sms.TYPE, Sms.MESSAGE_TYPE_FAILED)
                values.put(Sms.ERROR_CODE, resultCode)
                context.contentResolver.update(message.getUri(), values, null, null)
            }
        }
    }

    override fun markDelivered(id: Long) {
        Realm.getDefaultInstance().use { realm ->
            realm.refresh()

            val message = realm.where(Message::class.java).equalTo("id", id).findFirst()
            message?.let {
                // Update the message in realm
                realm.executeTransaction {
                    message.deliveryStatus = Sms.STATUS_COMPLETE
                    message.dateSent = System.currentTimeMillis()
                    message.read = true
                }

                // Update the message in the native ContentProvider
                val values = ContentValues()
                values.put(Sms.STATUS, Sms.STATUS_COMPLETE)
                values.put(Sms.DATE_SENT, System.currentTimeMillis())
                values.put(Sms.READ, true)
                context.contentResolver.update(message.getUri(), values, null, null)
            }
        }
    }

    override fun markDeliveryFailed(id: Long, resultCode: Int) {
        Realm.getDefaultInstance().use { realm ->
            realm.refresh()

            val message = realm.where(Message::class.java).equalTo("id", id).findFirst()
            message?.let {
                // Update the message in realm
                realm.executeTransaction {
                    message.deliveryStatus = Sms.STATUS_FAILED
                    message.dateSent = System.currentTimeMillis()
                    message.read = true
                    message.errorCode = resultCode
                }

                // Update the message in the native ContentProvider
                val values = ContentValues()
                values.put(Sms.STATUS, Sms.STATUS_FAILED)
                values.put(Sms.DATE_SENT, System.currentTimeMillis())
                values.put(Sms.READ, true)
                values.put(Sms.ERROR_CODE, resultCode)
                context.contentResolver.update(message.getUri(), values, null, null)
            }
        }
    }

    override fun deleteMessages(vararg messageIds: Long) {
        Realm.getDefaultInstance().use { realm ->
            realm.refresh()

            val messages = realm.where(Message::class.java)
                    .anyOf("id", messageIds)
                    .findAll()

            val uris = messages.map { it.getUri() }

            realm.executeTransaction { messages.deleteAllFromRealm() }

            uris.forEach { uri -> context.contentResolver.delete(uri, null, null) }
        }
    }

    override fun getOldMessageCounts(maxAgeDays: Int): Map<Long, Int> {
        return Realm.getDefaultInstance().use { realm ->
            realm.where(Message::class.java)
                    .lessThan("date", now() - TimeUnit.DAYS.toMillis(maxAgeDays.toLong()))
                    .findAll()
                    .groupingBy { message -> message.threadId }
                    .eachCount()
        }
    }

    override fun deleteOldMessages(maxAgeDays: Int) {
        return Realm.getDefaultInstance().use { realm ->
            val messages = realm.where(Message::class.java)
                    .lessThan("date", now() - TimeUnit.DAYS.toMillis(maxAgeDays.toLong()))
                    .findAll()

            val uris = messages.map { it.getUri() }

            realm.executeTransaction { messages.deleteAllFromRealm() }

            uris.forEach { uri -> context.contentResolver.delete(uri, null, null) }
        }
    }
}
