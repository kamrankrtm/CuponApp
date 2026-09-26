package com.moez.QKSMS.feature.smart.ui

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.moez.QKSMS.R
import com.moez.QKSMS.common.util.ContrastUtils
import com.moez.QKSMS.common.util.JalaliCalendar
import com.moez.QKSMS.common.util.ReadableColors
import com.moez.QKSMS.common.util.extensions.resolveThemeColor
import com.moez.QKSMS.feature.smart.ClipboardHelper
import com.moez.QKSMS.feature.smart.SmartDataManager
import com.moez.QKSMS.feature.smart.model.PromoItem
import com.moez.QKSMS.feature.smart.promo.BrandRegistry
import com.moez.QKSMS.feature.smart.promo.PromoRanker
import com.moez.QKSMS.feature.smart.promo.PromoSection
import com.moez.QKSMS.feature.smart.promo.PromoRow
import com.moez.QKSMS.feature.smart.promo.PromoValueParser

/**
 * Renders the discount list as ranked, sectioned rows.
 *
 * @param onDataChanged notified whenever the visible set changes, so the host can refresh
 *   chip counts and the empty state
 * @param onUndoAvailable invoked after a destructive action with a closure that reverses it
 */
class PromoCodesAdapter(
    private val context: Context,
    private val onDataChanged: () -> Unit = {},
    private val onUndoAvailable: (String, () -> Unit) -> Unit = { _, _ -> }
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private companion object {
        const val TYPE_HEADER = 0
        const val TYPE_ITEM = 1

        /** Below this confidence the card is flagged so the user knows to double-check it. */
        const val UNCERTAIN_THRESHOLD = 60

    }

    private val density = context.resources.displayMetrics.density
    private val primaryTextColor: Int by lazy { context.resolveThemeColor(android.R.attr.textColorPrimary, Color.BLACK) }
    private val urgentColor: Int by lazy { ContextCompat.getColor(context, R.color.danger) }
    private val pinnedColor: Int by lazy { ContextCompat.getColor(context, R.color.tintDiscounts) }

    /** A line-art glyph at [sizeDp], tinted, ready to sit beside a label. */
    private fun glyph(res: Int, color: Int, sizeDp: Int = 16): Drawable? =
        ContextCompat.getDrawable(context, res)?.mutate()?.apply {
            val px = (sizeDp * density).toInt()
            setBounds(0, 0, px, px)
            setTint(color)
        }

    private fun capsule(color: Int): GradientDrawable = GradientDrawable().apply {
        cornerRadius = 100 * density
        setColor(color)
    }

    /**
     * Resolved once: the theme does not change while the list is bound.
     *
     * Passed through the contrast check for the same reason the conversation list is: on the
     * dark themes the resolved secondary colour can land too close to the background.
     */
    private val secondaryTextColor: Int by lazy {
        val background = context.resolveThemeColor(android.R.attr.windowBackground, Color.BLACK)
        ContrastUtils.ensureReadable(
            context.resolveThemeColor(android.R.attr.textColorSecondary, Color.GRAY),
            background
        )
    }

    private var allPromos: MutableList<PromoItem> = mutableListOf()
    private var rows: MutableList<PromoRow> = mutableListOf()
    private var currentQuery: String = ""
    private var currentCategory: String = "all"

    fun updateData(newPromos: List<PromoItem>) {
        allPromos.clear()
        allPromos.addAll(newPromos.filter { !it.isUsed && !it.isInvalid && !it.isExpired() })
        applyFilter()
    }

    fun filter(query: String = currentQuery, category: String = currentCategory) {
        currentQuery = query
        currentCategory = category
        applyFilter()
    }

    /** Live counts per category slug, for the chip badges. */
    fun categoryCounts(): Map<String, Int> {
        val promos = visiblePromos(ignoreCategory = true)
        val counts = HashMap<String, Int>()
        for (promo in promos) {
            counts[promo.categorySlug] = (counts[promo.categorySlug] ?: 0) + 1
        }
        counts["all"] = promos.size
        return counts
    }

    /**
     * Applies the search box and the category chip.
     *
     * Category matching is now a straight slug comparison. The old version fell back to
     * keyword-sniffing the raw SMS text, which put every message containing the word "خرید"
     * into Shopping and listed the same supermarket code under three different chips.
     */
    private fun visiblePromos(ignoreCategory: Boolean = false): List<PromoItem> {
        val query = currentQuery.trim().toLowerCase()
        val category = currentCategory.toLowerCase()

        return allPromos.filter { promo ->
            if (promo.isUsed || promo.isInvalid || promo.isExpired()) return@filter false

            val matchesCategory = ignoreCategory || category == "all" || promo.categorySlug == category
            if (!matchesCategory) return@filter false

            if (query.isEmpty()) return@filter true

            promo.brand.toLowerCase().contains(query) ||
                promo.brandEn.toLowerCase().contains(query) ||
                promo.code.toLowerCase().contains(query) ||
                promo.description.toLowerCase().contains(query) ||
                promo.discountAmount.toLowerCase().contains(query) ||
                promo.category.toLowerCase().contains(query) ||
                (promo.issuer?.toLowerCase()?.contains(query) ?: false) ||
                (promo.payWith?.toLowerCase()?.contains(query) ?: false) ||
                promo.usableAt.any { it.toLowerCase().contains(query) } ||
                promo.sender.toLowerCase().contains(query) ||
                (promo.minOrder?.toLowerCase()?.contains(query) ?: false) ||
                promo.body.toLowerCase().contains(query)
        }
    }

    private fun applyFilter() {
        rows.clear()
        rows.addAll(PromoRanker.buildRows(visiblePromos()))
        notifyDataSetChanged()
        onDataChanged()
    }

    override fun getItemViewType(position: Int): Int = when (rows[position]) {
        is PromoRow.Header -> TYPE_HEADER
        is PromoRow.Item -> TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderViewHolder(inflater.inflate(R.layout.promo_section_header, parent, false))
        } else {
            PromoViewHolder(inflater.inflate(R.layout.promo_list_item, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is PromoRow.Header -> (holder as HeaderViewHolder).bind(row)
            is PromoRow.Item -> (holder as PromoViewHolder).bind(row.promo)
        }
    }

    override fun getItemCount(): Int = rows.size

    /** Removes one code from the visible list and offers to put it back. */
    private fun removePromo(promo: PromoItem, message: String) {
        allPromos.remove(promo)
        applyFilter()
        onUndoAvailable(message, {
            SmartDataManager.restore(promo)
            if (!allPromos.contains(promo)) allPromos.add(promo)
            applyFilter()
        })
    }

    inner class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.sectionTitle)
        private val count: TextView = itemView.findViewById(R.id.sectionCount)
        private val dot: View = itemView.findViewById(R.id.sectionDot)

        fun bind(header: PromoRow.Header) {
            title.text = header.section.title
            dot.visibility = if (header.section == PromoSection.EXPIRING_TODAY) View.VISIBLE else View.GONE
            count.text = PromoValueParser.toPersianDigits(header.count.toString())
        }
    }

    inner class PromoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val promoHeader: View = itemView.findViewById(R.id.promoHeader)
        private val promoCategory: TextView = itemView.findViewById(R.id.promoCategory)
        private val promoBrand: TextView = itemView.findViewById(R.id.promoBrand)
        private val promoDiscountAmount: TextView = itemView.findViewById(R.id.promoDiscountAmount)
        private val promoDescription: TextView = itemView.findViewById(R.id.promoDescription)
        private val promoUncertain: TextView = itemView.findViewById(R.id.promoUncertain)
        private val promoCode: TextView = itemView.findViewById(R.id.promoCode)
        private val promoMinOrder: TextView = itemView.findViewById(R.id.promoMinOrder)
        private val promoExpiry: TextView = itemView.findViewById(R.id.promoExpiry)
        private val btnCopyPromo: Button = itemView.findViewById(R.id.btnCopyPromo)
        private val btnOpenApp: Button = itemView.findViewById(R.id.btnOpenApp)
        private val btnPinPromo: Button = itemView.findViewById(R.id.btnPinPromo)
        private val btnMarkUsed: Button = itemView.findViewById(R.id.btnMarkUsed)
        private val btnViewOriginal: Button = itemView.findViewById(R.id.btnViewOriginal)

        fun bind(item: PromoItem) {
            promoBrand.text = item.brand
            // "سرگرمی · از طرف دیجی‌پی · پرداخت با دیجی‌پی": who sponsors the code and how to pay
            promoCategory.text = item.subtitle()
            promoDiscountAmount.text = item.discountAmount
            promoDescription.text = item.description
            promoDescription.visibility = if (item.description.isBlank()) View.GONE else View.VISIBLE
            promoCode.text = item.code

            bindBrandColour(item)
            bindExpiry(item)

            promoUncertain.visibility = if (item.confidence < UNCERTAIN_THRESHOLD) View.VISIBLE else View.GONE

            if (!item.minOrder.isNullOrBlank()) {
                promoMinOrder.visibility = View.VISIBLE
                promoMinOrder.text = item.minOrder
                promoMinOrder.setCompoundDrawablesRelative(glyph(R.drawable.ic_lc_bag, 0xE0FFFFFF.toInt(), 15), null, null, null)
            } else {
                promoMinOrder.visibility = View.GONE
            }

            val secondary = secondaryTextColor
            btnPinPromo.setCompoundDrawablesRelative(
                glyph(R.drawable.ic_lc_pin, if (item.isPinned) pinnedColor else secondary, 18), null, null, null)
            btnMarkUsed.setCompoundDrawablesRelative(glyph(R.drawable.ic_lc_circle_check, secondary), null, null, null)
            btnViewOriginal.setCompoundDrawablesRelative(glyph(R.drawable.ic_lc_message_text, secondary), null, null, null)
            btnPinPromo.setOnClickListener {
                SmartDataManager.setPinned(item, !item.isPinned)
                applyFilter()
            }

            bindCopy(item)
            bindOpenApp(item)
            bindMarkUsed(item)
            btnViewOriginal.setOnClickListener { showOriginalMessage(item) }
        }

        /**
         * Paints the coupon's face in the brand's colour, darkened only as far as white text on
         * it needs to stay readable; the copy button takes the same colour.
         */
        private fun bindBrandColour(item: PromoItem) {
            val color = if (item.brandColor != 0) {
                item.brandColor
            } else {
                BrandRegistry.fallbackColor(item.brand)
            }
            val fill = ReadableColors.fillForWhiteText(color)
            promoHeader.setBackgroundColor(fill)
            btnCopyPromo.backgroundTintList = ColorStateList.valueOf(fill)
        }

        /** Shows a live countdown instead of a bare Shamsi date, in red when time is short. */
        private fun bindExpiry(item: PromoItem) {
            val label = item.remainingLabel()
            val suffix = if (!item.expiryIsExplicit) " (تخمینی)" else ""

            promoExpiry.text = if (item.expiresAt == null) label else "$label$suffix"
            // On the brand colour: a translucent white capsule, or solid white with red text
            // when the code runs out within the day
            val urgent = item.isUrgent()
            val textColor = if (urgent) urgentColor else Color.WHITE
            promoExpiry.background = capsule(if (urgent) Color.WHITE else 0x33FFFFFF)
            promoExpiry.setTextColor(textColor)
            promoExpiry.setCompoundDrawablesRelative(glyph(R.drawable.ic_lc_clock, textColor, 15), null, null, null)
        }

        private fun bindCopy(item: PromoItem) {
            val copyGlyph = glyph(R.drawable.ic_lc_copy, Color.WHITE, 17)
            val doneGlyph = glyph(R.drawable.ic_lc_circle_check, Color.WHITE, 17)
            btnCopyPromo.text = "کپی کد"
            btnCopyPromo.setCompoundDrawablesRelative(copyGlyph, null, null, null)
            btnCopyPromo.setOnClickListener {
                ClipboardHelper.copyToClipboard(context, item.code, "PROMO", showToast = false)
                btnCopyPromo.text = "کپی شد"
                btnCopyPromo.setCompoundDrawablesRelative(doneGlyph, null, null, null)
                Toast.makeText(context, "کد تخفیف ${item.code} کپی شد", Toast.LENGTH_SHORT).show()
                btnCopyPromo.postDelayed({
                    btnCopyPromo.text = "کپی کد"
                    btnCopyPromo.setCompoundDrawablesRelative(copyGlyph, null, null, null)
                }, 2000)
            }
        }

        /**
         * Copies the code and jumps straight into the brand's app, or its site.
         *
         * Copying alone still left the user to go find the app themselves, which is most of
         * the friction between seeing a code and using it.
         */
        private fun bindOpenApp(item: PromoItem) {
            val launchIntent = resolveLaunchIntent(item)
            if (launchIntent == null) {
                btnOpenApp.visibility = View.GONE
                return
            }

            btnOpenApp.visibility = View.VISIBLE
            btnOpenApp.setCompoundDrawablesRelative(glyph(R.drawable.ic_lc_external, primaryTextColor), null, null, null)
            btnOpenApp.setOnClickListener {
                ClipboardHelper.copyToClipboard(context, item.code, "PROMO", showToast = false)
                Toast.makeText(context, "کد ${item.code} کپی شد و ${item.brand} باز می‌شود", Toast.LENGTH_SHORT).show()
                try {
                    context.startActivity(launchIntent)
                } catch (e: Exception) {
                    Toast.makeText(context, "باز کردن ${item.brand} ممکن نشد", Toast.LENGTH_SHORT).show()
                }
            }
        }

        /** Prefers the installed app, falls back to the website, gives up quietly otherwise. */
        private fun resolveLaunchIntent(item: PromoItem): Intent? {
            item.appPackage?.let { pkg ->
                context.packageManager.getLaunchIntentForPackage(pkg)?.let { return it }
            }
            item.website?.let { url ->
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                if (intent.resolveActivity(context.packageManager) != null) return intent
            }
            return null
        }

        private fun bindMarkUsed(item: PromoItem) {
            btnMarkUsed.setOnClickListener {
                SmartDataManager.markUsed(item, true)
                removePromo(item, "کد تخفیف «استفاده شد» علامت خورد")
            }

            // Long press reports a code that did not work, which is a different signal: it
            // should not come back on the next scan either.
            btnMarkUsed.setOnLongClickListener {
                SmartDataManager.markInvalid(item, true)
                removePromo(item, "کد تخفیف به عنوان «کار نمی‌کند» گزارش شد")
                true
            }
        }

        private fun showOriginalMessage(item: PromoItem) {
            val jalali = JalaliCalendar.fromMillis(item.receivedAt)
            val received = JalaliCalendar.toPersianDigits(
                "${jalali.year}/${pad(jalali.month)}/${pad(jalali.day)}"
            )

            val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_promo_details, null)
            val dialog = AlertDialog.Builder(context).setView(dialogView).create()
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

            dialogView.findViewById<TextView>(R.id.dialogPromoBrand).text = item.brand
            dialogView.findViewById<TextView>(R.id.dialogPromoDiscountBadge).text = item.discountAmount
            dialogView.findViewById<TextView>(R.id.dialogPromoSender).text = "فرستنده: ${item.sender}"
            dialogView.findViewById<TextView>(R.id.dialogPromoDate).text = "دریافت: $received"

            // Every condition the message set: basket, cap, payment method, sponsor, shops
            val conditions = listOfNotNull(
                item.minOrder?.takeIf { it.isNotBlank() },
                item.maxDiscount?.takeIf { it.isNotBlank() },
                item.payWith?.takeIf { it.isNotBlank() }?.let { "پرداخت با $it" },
                item.issuer?.takeIf { it.isNotBlank() && it != item.brand }?.let { "از طرف $it" },
                item.usableAt.takeIf { it.isNotEmpty() }?.let { "قابل استفاده در ${it.joinToString("، ")}" }
            )
            val condition = dialogView.findViewById<TextView>(R.id.dialogPromoCondition)
            if (conditions.isNotEmpty()) {
                condition.visibility = View.VISIBLE
                condition.text = conditions.joinToString("\n")
            } else {
                condition.visibility = View.GONE
            }

            // SMS bodies routinely carry replacement characters from broken encodings.
            dialogView.findViewById<TextView>(R.id.dialogPromoBody).text =
                item.body.replace("�", " ").trim()

            val btnCopy = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.dialogBtnCopy)
            btnCopy.text = "کپی کد ${item.code}"
            btnCopy.setOnClickListener {
                ClipboardHelper.copyToClipboard(context, item.code, "PROMO")
                dialog.dismiss()
            }

            dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.dialogBtnClose)
                .setOnClickListener { dialog.dismiss() }

            dialog.show()
        }

        private fun pad(value: Int): String = if (value < 10) "0$value" else value.toString()
    }
}
