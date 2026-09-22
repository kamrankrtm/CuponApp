package com.moez.QKSMS.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.moez.QKSMS.manager.NotificationManager
import com.moez.QKSMS.repository.ConversationRepository
import com.moez.QKSMS.repository.MessageRepository
import dagger.android.AndroidInjection
import timber.log.Timber
import javax.inject.Inject

class TestSmsReceiver : BroadcastReceiver() {

    @Inject lateinit var messageRepo: MessageRepository
    @Inject lateinit var conversationRepo: ConversationRepository
    @Inject lateinit var notificationManager: NotificationManager

    override fun onReceive(context: Context, intent: Intent) {
        try {
            AndroidInjection.inject(this, context)
        } catch (e: Throwable) {
            Timber.e(e, "TestSmsReceiver inject failed")
            return
        }

        val sender = intent.getStringExtra("sender") ?: "09121111111"
        val body = intent.getStringExtra("body") ?: "تست پیامک"
        val subId = intent.getIntExtra("subId", -1)

        val message = messageRepo.insertReceivedSms(subId, sender, body, System.currentTimeMillis())
        conversationRepo.updateConversations(message.threadId)
        val conversation = conversationRepo.getOrCreateConversation(message.threadId) ?: return
        notificationManager.update(conversation.id)
        Timber.i("TestSmsReceiver: injected SMS from $sender, threadId=${conversation.id}")

    }
}
