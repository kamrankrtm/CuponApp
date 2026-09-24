package com.moez.QKSMS.feature.smart.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
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
import com.moez.QKSMS.feature.smart.SenderIdentity
import com.moez.QKSMS.feature.smart.SmartSmsClassifier
import com.moez.QKSMS.feature.smart.model.SmsCategory
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
        const val VIEW_TYPE_BANK = 2

        private val AMOUNT = Regex("([0-9,٬]+)\\s*(ریال|تومان)")
    }

    /** The Banking tab reads each conversation as a transaction rather than a message. */
    var bankingMode: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            notifyDataSetChanged()
        }

    /** Banking reads per conversation, keyed by the last message id they were made from. */
    private val bankingCache = HashMap<Long, Pair<Long, SmsCategory.Banking?>>()

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
        if (bankingMode) return VIEW_TYPE_BANK
        val conversation = getItem(position)
        return if (conversation.unread) VIEW_TYPE_UNREAD else VIEW_TYPE_NORMAL
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QkViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)

        if (viewType == VIEW_TYPE_HEADER) {
            val view = layoutInflater.inflate(R.layout.frequent_contacts_header, parent, false)
            return FrequentHeaderViewHolder(view)
        }

        if (viewType == VIEW_TYPE_BANK) {
            val view = layoutInflater.inflate(R.layout.bank_transaction_item, parent, false)
            return BankViewHolder(view).apply {
                view.setOnClickListener {
                    val pos = adapterPosition
                    if (pos != RecyclerView.NO_POSITION && pos in 0 until itemCount) {
                        navigator.showConversation(getItem(pos).id)
                    }
                }
            }
        }

        val view = layoutInflater.inflate(R.layout.conversation_list_item, parent, false)

        if (viewType == VIEW_TYPE_UNREAD) {
            // Same repair as onBindViewHolder, which overwrites this a moment later; keeping
            // them in step avoids a flash of unreadable text on the first frame.
            val textColorPrimary = ContrastUtils.ensureReadable(
                parent.context.resolveThemeColor(android.R.attr.textColorPrimary),
                parent.context.resolveThemeColor(android.R.attr.windowBackground)
            )
            view.snippet.setTextColor(textColorPrimary)
            view.unread.isVisible = true
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

    inner class BankViewHolder(view: View) : QkViewHolder(view) {
        private val name: TextView = view.findViewById(R.id.bankName)
        private val meta: TextView = view.findViewById(R.id.bankMeta)
        private val amount: TextView = view.findViewById(R.id.bankAmount)
        private val unit: TextView = view.findViewById(R.id.bankUnit)
        private val chevron: View = view.findViewById(R.id.bankChevron)
        private val separator: View = view.findViewById(R.id.bankSeparator)

        fun bind(position: Int) {
            val conversation = getItem(position)
            if (!conversation.isValid) return
            val banking = bankingOf(conversation)
            val first = position == (if (hasHeader) 1 else 0)
            val last = position == itemCount - 1

            name.text = banking?.bankName ?: conversation.getTitle()
            val kind = when (banking?.isDeposit) {
                true -> "واریز وجه"
                false -> "برداشت / تراکنش"
                null -> "تراکنش بانکی"
            }
            val time = conversation.date.takeIf { it > 0 }?.let(dateFormatter::getConversationTimestamp).orEmpty()
            meta.text = if (time.isEmpty()) kind else "$kind · $time"

            val match = banking?.amount?.let { AMOUNT.find(it) }
            if (banking != null && match != null) {
                val deposit = banking.isDeposit == true
                val sign = when (banking.isDeposit) {
                    true -> "+"
                    false -> "−"
                    null -> ""
                }
                amount.text = sign + match.groupValues[1]
                amount.setTextColor(if (deposit) ContextCompat.getColor(context, R.color.success)
                        else itemView.context.resolveThemeColor(android.R.attr.textColorPrimary))
                unit.text = match.groupValues[2]
                amount.visibility = View.VISIBLE
                unit.visibility = View.VISIBLE
                chevron.visibility = View.GONE
            } else {
                amount.visibility = View.GONE
                unit.visibility = View.GONE
                chevron.visibility = View.VISIBLE
            }

            itemView.setBackgroundResource(when {
                first && last -> R.drawable.group_single
                first -> R.drawable.group_top
                last -> R.drawable.group_bottom
                else -> R.drawable.group_middle
            })
            separator.visibility = if (last) View.GONE else View.VISIBLE
        }
    }

    /** What the bank said in the conversation's latest message, read once per message. */
    private fun bankingOf(conversation: Conversation): SmsCategory.Banking? {
        val lastMessage = conversation.lastMessage ?: return null
        val cached = bankingCache[conversation.id]
        if (cached != null && cached.first == lastMessage.id) return cached.second
        val sender = conversation.recipients.firstOrNull()?.address ?: lastMessage.address
        val category = SmartSmsClassifier.classify(sender, lastMessage.body, lastMessage.date, conversation.id)
        val banking = category as? SmsCategory.Banking
        bankingCache[conversation.id] = Pair(lastMessage.id, banking)
        return banking
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
        if (holder is BankViewHolder) {
            holder.bind(position)
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
        val brand = conversation.recipients.singleOrNull()
                ?.takeIf { it.contact == null }
                ?.let { SenderIdentity.brandFor(it.address) }
        holder.itemView.title.text = buildSpannedString {
            append(brand?.en ?: conversation.getTitle())
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

        holder.itemView.title.setTextColor(textColorPrimary)
        holder.itemView.date.setTextColor(textColorSecondary)
        holder.itemView.snippet.setTextColor(if (isUnread) textColorPrimary else textColorSecondary)
        if (isUnread) holder.itemView.unread.setTint(ContextCompat.getColor(themed, R.color.blue_500))
    }
}
