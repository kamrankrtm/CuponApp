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
package com.moez.QKSMS.feature.conversations

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.text.bold
import androidx.core.text.buildSpannedString
import androidx.core.text.color
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import com.moez.QKSMS.R
import com.moez.QKSMS.common.Navigator
import com.moez.QKSMS.common.base.QkRealmAdapter
import com.moez.QKSMS.common.base.QkViewHolder
import com.moez.QKSMS.common.util.Colors
import com.moez.QKSMS.common.util.DateFormatter
import com.moez.QKSMS.common.util.ContrastUtils
import com.moez.QKSMS.common.util.extensions.resolveThemeColor
import com.moez.QKSMS.common.util.extensions.setTint
import com.moez.QKSMS.feature.smart.SenderIdentity
import com.moez.QKSMS.model.Conversation
import com.moez.QKSMS.util.PhoneNumberUtils
import kotlinx.android.synthetic.main.conversation_list_item.*
import kotlinx.android.synthetic.main.conversation_list_item.view.*
import javax.inject.Inject

class ConversationsAdapter @Inject constructor(
    private val colors: Colors,
    private val context: Context,
    private val dateFormatter: DateFormatter,
    private val navigator: Navigator,
    private val phoneNumberUtils: PhoneNumberUtils
) : QkRealmAdapter<Conversation>() {

    init {
        // This is how we access the threadId for the swipe actions
        setHasStableIds(true)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QkViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        val view = layoutInflater.inflate(R.layout.conversation_list_item, parent, false)

        if (viewType == 1) {
            // Same repair as onBindViewHolder, which overwrites this a moment later; keeping
            // them in step avoids a flash of unreadable text on the first frame.
            val textColorPrimary = ContrastUtils.ensureReadable(
                parent.context.resolveThemeColor(android.R.attr.textColorPrimary),
                parent.context.resolveThemeColor(android.R.attr.windowBackground)
            )

            // Unread shows as the accent dot and a darker preview; the layout never changes weight
            view.snippet.setTextColor(textColorPrimary)
            view.unread.isVisible = true
        }

        return QkViewHolder(view).apply {
            view.setOnClickListener {
                val conversation = getItem(adapterPosition) ?: return@setOnClickListener
                when (toggleSelection(conversation.id, false)) {
                    true -> view.isActivated = isSelected(conversation.id)
                    false -> navigator.showConversation(conversation.id)
                }
            }
            view.setOnLongClickListener {
                val conversation = getItem(adapterPosition) ?: return@setOnLongClickListener true
                toggleSelection(conversation.id)
                view.isActivated = isSelected(conversation.id)
                true
            }
        }
    }

    override fun onBindViewHolder(holder: QkViewHolder, position: Int) {
        val conversation = getItem(position) ?: return

        // If the last message wasn't incoming, then the colour doesn't really matter anyway
        val lastMessage = conversation.lastMessage
        val recipient = when {
            conversation.recipients.size == 1 || lastMessage == null -> conversation.recipients.firstOrNull()
            else -> conversation.recipients.find { recipient ->
                phoneNumberUtils.compare(recipient.address, lastMessage.address)
            }
        }
        val theme = colors.theme(recipient).theme

        holder.containerView.isActivated = isSelected(conversation.id)

        holder.avatars.recipients = conversation.recipients
        holder.title.collapseEnabled = conversation.recipients.size > 1
        // A business sender we recognise is shown by its brand name rather than a short code
        val brand = conversation.recipients.singleOrNull()
                ?.takeIf { it.contact == null }
                ?.let { SenderIdentity.brandFor(it.address) }
        holder.title.text = buildSpannedString {
            append(brand?.en ?: conversation.getTitle())
            if (conversation.draft.isNotEmpty()) {
                color(theme) { append(" " + context.getString(R.string.main_draft)) }
            }
        }
        holder.date.text = conversation.date.takeIf { it > 0 }?.let(dateFormatter::getConversationTimestamp)
        holder.snippet.text = when {
            conversation.draft.isNotEmpty() -> conversation.draft
            conversation.me -> context.getString(R.string.main_sender_you, conversation.snippet)
            else -> conversation.snippet
        }
        holder.pinned.isVisible = conversation.pinned

        val isUnread = conversation.unread
        holder.unread.isVisible = isUnread
        // Measure the colour that is actually painted, not the one the theme nominally names.
        // The previous guard tested luminance of the raw attribute value, which ignores alpha,
        // so a translucent dark grey passed as "light enough" and the snippet rendered at
        // roughly 2.5:1 against the dark background.
        // Resolve against the view's own context, not the injected one. Dagger provides the
        // Application here (AppModule.provideContext), whose theme is the one declared in the
        // manifest, while the dark theme is applied per-activity at runtime. Reading the
        // application theme reported a light background and a grey secondary colour that
        // "passed" against it, and that grey was then painted onto the real dark background.
        // Titles looked right only because the adapter never sets their colour on read rows,
        // so they kept the value resolved from the activity at inflation time.
        val themed = holder.itemView.context
        val bg = themed.resolveThemeColor(android.R.attr.windowBackground)
        val textColorPrimary = ContrastUtils.ensureReadable(
            themed.resolveThemeColor(android.R.attr.textColorPrimary), bg)
        val textColorSecondary = ContrastUtils.ensureReadable(
            themed.resolveThemeColor(android.R.attr.textColorSecondary), bg)

        holder.title.setTextColor(textColorPrimary)
        holder.date.setTextColor(textColorSecondary)
        holder.snippet.setTextColor(if (isUnread) textColorPrimary else textColorSecondary)
        if (isUnread) holder.unread.setTint(ContextCompat.getColor(themed, R.color.blue_500))
    }

    override fun getItemId(position: Int): Long {
        return getItem(position)?.id ?: -1
    }

    override fun getItemViewType(position: Int): Int {
        return if (getItem(position)?.unread == false) 0 else 1
    }
}
