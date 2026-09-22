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
import com.moez.QKSMS.common.util.ContrastUtils
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

    companion object {
        const val VIEW_TYPE_HEADER = -1
        const val VIEW_TYPE_NORMAL = 0
        const val VIEW_TYPE_UNREAD = 1
    }

    init {
        setHasStableIds(true)
    }

    var frequentContacts: List<Conversation> = emptyList()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    private val hasHeader: Boolean
        get() = frequentContacts.isNotEmpty()

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

    override fun getItemCount(): Int = data.size + (if (hasHeader) 1 else 0)

    fun getItem(position: Int): Conversation {
        val actualPos = if (hasHeader) position - 1 else position
        return data[actualPos]
    }

    override fun getItemId(position: Int): Long {
        if (hasHeader && position == 0) return -999999L
        val actualPos = if (hasHeader) position - 1 else position
        return data.getOrNull(actualPos)?.id ?: position.toLong()
    }

    override fun getItemViewType(position: Int): Int {
        if (hasHeader && position == 0) return VIEW_TYPE_HEADER
        val conversation = getItem(position)
        return if (conversation.unread) VIEW_TYPE_UNREAD else VIEW_TYPE_NORMAL
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QkViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)

        if (viewType == VIEW_TYPE_HEADER) {
            val view = layoutInflater.inflate(R.layout.frequent_contacts_header, parent, false)
            return FrequentHeaderViewHolder(view)
        }

        val view = layoutInflater.inflate(R.layout.conversation_list_item, parent, false)

        if (viewType == VIEW_TYPE_UNREAD) {
            // Same repair as onBindViewHolder, which overwrites this a moment later; keeping
            // them in step avoids a flash of unreadable text on the first frame.
            val textColorPrimary = ContrastUtils.ensureReadable(
                parent.context.resolveThemeColor(android.R.attr.textColorPrimary),
                parent.context.resolveThemeColor(android.R.attr.windowBackground)
            )
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
                    if (hasHeader && pos == 0) return@setOnClickListener
                    val conversation = getItem(pos)
                    navigator.showConversation(conversation.id)
                }
            }
        }
    }

    inner class FrequentHeaderViewHolder(view: View) : QkViewHolder(view) {
        private val container: android.widget.LinearLayout = view.findViewById(R.id.frequentContactsContainer)

        fun bind(contacts: List<Conversation>, navigator: Navigator) {
            container.removeAllViews()
            val inflater = LayoutInflater.from(itemView.context)
            for (conv in contacts) {
                val recipient = conv.recipients.firstOrNull() ?: continue
                val item = inflater.inflate(R.layout.frequent_contact_item, container, false)
                val avatar = item.findViewById<com.moez.QKSMS.common.widget.AvatarView>(R.id.frequentAvatar)
                val nameText = item.findViewById<com.moez.QKSMS.common.widget.QkTextView>(R.id.frequentName)
                avatar.setRecipient(recipient)
                nameText.text = recipient.contact?.name?.split(" ")?.firstOrNull()
                    ?: recipient.getDisplayName()
                item.setOnClickListener {
                    navigator.showConversation(conv.id)
                }
                container.addView(item)
            }
        }
    }

    override fun onBindViewHolder(holder: QkViewHolder, position: Int) {
        if (holder is FrequentHeaderViewHolder) {
            holder.bind(frequentContacts, navigator)
            return
        }

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
        // Measure the colour that is actually painted, not the one the theme nominally names.
        // The previous guard tested luminance of the raw attribute value, which ignores alpha,
        // so a translucent dark grey passed as "light enough" and the snippet rendered at
        // roughly 2.5:1 against the dark background.
        // Resolve against the view's own context so the activity's runtime theme is used.
        val themed = holder.itemView.context
        val bg = themed.resolveThemeColor(android.R.attr.windowBackground)
        val textColorPrimary = ContrastUtils.ensureReadable(
            themed.resolveThemeColor(android.R.attr.textColorPrimary), bg)
        val textColorSecondary = ContrastUtils.ensureReadable(
            themed.resolveThemeColor(android.R.attr.textColorSecondary), bg)
        val textColorTertiary = ContrastUtils.ensureReadable(
            themed.resolveThemeColor(android.R.attr.textColorTertiary), bg)

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
