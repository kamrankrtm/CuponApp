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

package com.moez.QKSMS.interactor

import android.content.Context
import android.provider.Telephony
import com.moez.QKSMS.compat.TelephonyCompat
import com.moez.QKSMS.extensions.mapNotNull
import com.moez.QKSMS.model.Attachment
import com.moez.QKSMS.repository.ConversationRepository
import com.moez.QKSMS.repository.MessageRepository
import io.reactivex.Flowable
import javax.inject.Inject

class SendMessage @Inject constructor(
    private val context: Context,
    private val conversationRepo: ConversationRepository,
    private val messageRepo: MessageRepository,
    private val updateBadge: UpdateBadge
) : Interactor<SendMessage.Params>() {

    data class Params(
        val subId: Int,
        val threadId: Long,
        val addresses: List<String>,
        val body: String,
        val attachments: List<Attachment> = listOf(),
        val delay: Int = 0
    )

    private fun resolveThreadAddresses(threadId: Long): List<String> {
        if (threadId == 0L) return emptyList()
        try {
            val cursor = context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms.ADDRESS),
                "${Telephony.Sms.THREAD_ID} = ?",
                arrayOf(threadId.toString()),
                "date DESC LIMIT 1"
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val addr = it.getString(0)
                    if (!addr.isNullOrBlank()) {
                        return listOf(addr)
                    }
                }
            }
        } catch (t: Throwable) {
            // ignore
        }
        return emptyList()
    }

    override fun buildObservable(params: Params): Flowable<*> = Flowable.just(params)
            .map { p ->
                val cleanParams = p.addresses.filter { it.isNotBlank() }
                val addresses = when {
                    cleanParams.isNotEmpty() -> cleanParams
                    p.threadId != 0L -> {
                        val conv = conversationRepo.getConversation(p.threadId)
                        val fromRecipients = conv?.recipients?.map { it.address }?.filter { it.isNotBlank() }.orEmpty()
                        if (fromRecipients.isNotEmpty()) {
                            fromRecipients
                        } else {
                            val fromContact = conv?.recipients?.mapNotNull {
                                it.contact?.getDefaultNumber()?.address ?: it.contact?.numbers?.firstOrNull()?.address
                            }?.filter { it.isNotBlank() }.orEmpty()
                            if (fromContact.isNotEmpty()) {
                                fromContact
                            } else {
                                val fromLastMsg = conv?.lastMessage?.address?.takeIf { it.isNotBlank() }
                                if (fromLastMsg != null) {
                                    listOf(fromLastMsg)
                                } else {
                                    resolveThreadAddresses(p.threadId)
                                }
                            }
                        }
                    }
                    else -> listOf()
                }
                Pair(addresses, p)
            }
            .filter { (addresses, _) -> addresses.isNotEmpty() }
            .doOnNext { (addresses, p) ->
                // If a threadId isn't provided, try to obtain one
                val threadId = when (p.threadId) {
                    0L -> TelephonyCompat.getOrCreateThreadId(context, addresses.toSet())
                    else -> p.threadId
                }
                messageRepo.sendMessage(p.subId, threadId, addresses, p.body, p.attachments, p.delay)
            }
            .mapNotNull { (addresses, p) ->
                // If the threadId wasn't provided, then it's probably because it doesn't exist in Realm.
                // Sync it now and get the id
                when (p.threadId) {
                    0L -> conversationRepo.getOrCreateConversation(addresses)?.id
                    else -> p.threadId
                }
            }
            .doOnNext { threadId -> conversationRepo.updateConversations(threadId) }
            .doOnNext { threadId -> conversationRepo.markUnarchived(threadId) }
            .flatMap { updateBadge.buildObservable(Unit) } // Update the widget
}
