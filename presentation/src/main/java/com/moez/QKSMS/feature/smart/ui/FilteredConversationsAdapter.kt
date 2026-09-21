package com.moez.QKSMS.feature.smart.ui

import android.content.Context
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.text.buildSpannedString
import androidx.core.text.color
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.moez.QKSMS.R
import com.moez.QKSMS.common.Navigator
import com.moez.QKSMS.common.base.QkViewHolder
import com.moez.QKSMS.common.util.Colors
import com.moez.QKSMS.common.util.DateFormatter
import com.moez.QKSMS.common.util.extensions.resolveThemeColor
import com.moez.QKSMS.common.util.extensions.setTint
import com.moez.QKSMS.model.Conversation
import com.moez.QKSMS.util.PhoneNumberUtils
import kotlinx.android.synthetic.main.conversation_list_item.view.*

class FilteredConversationsAdapter(
    private val colors: Colors,
    private val context: Context,
    private val dateFormatter: DateFormatter,
    private val navigator: Navigator,
    private val phoneNumberUtils: PhoneNumberUtils
) : RecyclerView.Adapter<QkViewHolder>() {

    init {
        setHasStableIds(true)
    }

    var data: List<Conversation> = emptyList()
        set(value) {
            field = value
            notifyDataSetChanged()
            emptyView?.isVisible = value.isEmpty()
        }

    var emptyView: View? = null
        set(value) {
            field = value
            field?.isVisible = data.isEmpty()
        }

    override fun getItemCount(): Int = data.size

    fun getItem(position: Int): Conversation = data[position]

    override fun getItemId(position: Int): Long {
        return data.getOrNull(position)?.id ?: position.toLong()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QkViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        val view = layoutInflater.inflate(R.layout.conversation_list_item, parent, false)

        if (viewType == 1) {
            val textColorPrimary = parent.context.resolveThemeColor(android.R.attr.textColorPrimary)
            view.title.setTypeface(view.title.typeface, Typeface.BOLD)
            view.snippet.setTypeface(view.snippet.typeface, Typeface.BOLD)
            view.snippet.setTextColor(textColorPrimary)
            view.snippet.maxLines = 5
            view.unread.isVisible = true
            view.date.setTypeface(view.date.typeface, Typeface.BOLD)
            view.date.setTextColor(textColorPrimary)
        }

        return QkViewHolder(view).apply {
            view.setOnClickListener {
                val pos = adapterPosition
                if (pos != RecyclerView.NO_POSITION && pos in 0 until itemCount) {
                    val conversation = getItem(pos)
                    navigator.showConversation(conversation.id)
                }
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (getItem(position).unread) 1 else 0
    }

    override fun onBindViewHolder(holder: QkViewHolder, position: Int) {
        val conversation = getItem(position)
        if (!conversation.isValid) {
            return
        }
        val lastMessage = conversation.lastMessage
        val recipient = when {
            conversation.recipients.size == 1 || lastMessage == null -> conversation.recipients.firstOrNull()
            else -> conversation.recipients.find { recipient ->
                phoneNumberUtils.compare(recipient.address, lastMessage.address)
            }
        }
        val theme = colors.theme(recipient).theme

        holder.itemView.avatars.recipients = conversation.recipients
        holder.itemView.title.collapseEnabled = conversation.recipients.size > 1
        holder.itemView.title.text = buildSpannedString {
            append(conversation.getTitle())
            if (conversation.draft.isNotEmpty()) {
                color(theme) { append(" " + context.getString(R.string.main_draft)) }
            }
        }
        holder.itemView.date.text = conversation.date.takeIf { it > 0 }?.let(dateFormatter::getConversationTimestamp)
        holder.itemView.snippet.text = when {
            conversation.draft.isNotEmpty() -> conversation.draft
            conversation.me -> context.getString(R.string.main_sender_you, conversation.snippet)
            else -> conversation.snippet
        }
        holder.itemView.pinned.isVisible = conversation.pinned

        val isUnread = conversation.unread
        holder.itemView.unread.isVisible = isUnread
        val textColorPrimary = context.resolveThemeColor(android.R.attr.textColorPrimary)
        val textColorSecondary = context.resolveThemeColor(android.R.attr.textColorSecondary)
        val textColorTertiary = context.resolveThemeColor(android.R.attr.textColorTertiary)

        if (isUnread) {
            holder.itemView.title.setTypeface(holder.itemView.title.typeface, Typeface.BOLD)
            holder.itemView.snippet.setTypeface(holder.itemView.snippet.typeface, Typeface.BOLD)
            holder.itemView.snippet.setTextColor(textColorPrimary)
            holder.itemView.snippet.maxLines = 5
            holder.itemView.date.setTypeface(holder.itemView.date.typeface, Typeface.BOLD)
            holder.itemView.date.setTextColor(textColorPrimary)
            holder.itemView.unread.setTint(theme)
        } else {
            holder.itemView.title.setTypeface(Typeface.create(holder.itemView.title.typeface, Typeface.NORMAL), Typeface.NORMAL)
            holder.itemView.snippet.setTypeface(Typeface.create(holder.itemView.snippet.typeface, Typeface.NORMAL), Typeface.NORMAL)
            holder.itemView.snippet.setTextColor(textColorSecondary)
            holder.itemView.snippet.maxLines = 1
            holder.itemView.date.setTypeface(Typeface.create(holder.itemView.date.typeface, Typeface.NORMAL), Typeface.NORMAL)
            holder.itemView.date.setTextColor(textColorTertiary)
        }
    }
}
