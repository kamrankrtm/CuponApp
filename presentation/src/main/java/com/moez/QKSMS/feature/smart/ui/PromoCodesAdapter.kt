package com.moez.QKSMS.feature.smart.ui

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.moez.QKSMS.R
import com.moez.QKSMS.common.util.JalaliCalendar
import com.moez.QKSMS.feature.smart.ClipboardHelper
import com.moez.QKSMS.feature.smart.model.PromoItem
import java.util.Calendar

class PromoCodesAdapter(
    private val context: Context
) : RecyclerView.Adapter<PromoCodesAdapter.PromoViewHolder>() {

    private var allPromos: MutableList<PromoItem> = mutableListOf()
    private var displayedPromos: MutableList<PromoItem> = mutableListOf()
    private var currentQuery: String = ""
    private var currentCategory: String = "all"

    fun updateData(newPromos: List<PromoItem>) {
        allPromos.clear()
        allPromos.addAll(newPromos)
        applyFilter()
    }

    fun filter(query: String = currentQuery, category: String = currentCategory) {
        currentQuery = query
        currentCategory = category
        applyFilter()
    }

    private fun applyFilter() {
        val q = currentQuery.trim().toLowerCase()
        val cat = currentCategory.toLowerCase()

        val filtered = allPromos.filter { promo ->
            val b = promo.brand.toLowerCase()
            val d = promo.description.toLowerCase()
            val code = promo.code.toLowerCase()
            val body = promo.body.toLowerCase()
            val amount = promo.discountAmount.toLowerCase()
            val inst = promo.instructions.toLowerCase()
            val sender = promo.sender.toLowerCase()
            val minOrder = promo.minOrder?.toLowerCase() ?: ""
            val catSlug = promo.categorySlug.toLowerCase()

            val matchesQuery = q.isEmpty() ||
                    b.contains(q) ||
                    code.contains(q) ||
                    d.contains(q) ||
                    amount.contains(q) ||
                    inst.contains(q) ||
                    body.contains(q) ||
                    sender.contains(q) ||
                    minOrder.contains(q)

            val matchesCat = when (cat) {
                "all" -> true
                "food" -> catSlug == "food" || b.contains("فود") || b.contains("غذا") || b.contains("رستوران") ||
                        d.contains("غذا") || d.contains("رستوران") || body.contains("غذا") || body.contains("پیتزا") ||
                        body.contains("رستوران") || body.contains("شام") || body.contains("ناهار") || body.contains("کافه")
                "shopping", "ecommerce" -> catSlug == "ecommerce" || catSlug == "shopping" || b.contains("دیجی") ||
                        b.contains("باسلام") || b.contains("اکالا") || b.contains("تکنولایف") || b.contains("بانی") ||
                        b.contains("خانومی") || d.contains("خرید") || d.contains("فروشگاه") || body.contains("خرید") ||
                        body.contains("فروشگاه") || body.contains("پوشاک") || body.contains("کالا")
                "supermarket" -> catSlug == "supermarket" || b.contains("مارکت") || b.contains("کوروش") || b.contains("اکالا") ||
                        d.contains("سوپرمارکت") || body.contains("سوپرمارکت") || body.contains("هایپراستار")
                "travel", "transport" -> (catSlug == "transport" || catSlug == "travel") ||
                        ((b.contains("اسنپ") || b.contains("تپسی")) && !b.contains("فود") && !b.contains("مارکت")) ||
                        b.contains("علی‌بابا") || b.contains("فلای") || b.contains("سفر") || b.contains("بلیط") ||
                        d.contains("سفر") || d.contains("تاکسی") || body.contains("سفر") || body.contains("تاکسی")
                "entertainment" -> catSlug == "entertainment" || b.contains("فیلیمو") || b.contains("نماوا") ||
                        b.contains("سینما") || d.contains("فیلم") || d.contains("سریال") || body.contains("فیلم") ||
                        body.contains("سینما") || body.contains("سرگرمی")
                else -> true
            }

            matchesQuery && matchesCat
        }

        displayedPromos.clear()
        displayedPromos.addAll(filtered)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PromoViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.promo_list_item, parent, false)
        return PromoViewHolder(view)
    }

    override fun onBindViewHolder(holder: PromoViewHolder, position: Int) {
        holder.bind(displayedPromos[position])
    }

    override fun getItemCount(): Int = displayedPromos.size

    inner class PromoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val brandIcon: ImageView = itemView.findViewById(R.id.brandIcon)
        private val promoBrand: TextView = itemView.findViewById(R.id.promoBrand)
        private val promoDiscountAmount: TextView = itemView.findViewById(R.id.promoDiscountAmount)
        private val promoDescription: TextView = itemView.findViewById(R.id.promoDescription)
        private val promoCode: TextView = itemView.findViewById(R.id.promoCode)
        private val promoExpiry: TextView = itemView.findViewById(R.id.promoExpiry)
        private val btnCopyPromo: Button = itemView.findViewById(R.id.btnCopyPromo)
        private val btnMarkUsed: Button = itemView.findViewById(R.id.btnMarkUsed)
        private val btnViewOriginal: Button = itemView.findViewById(R.id.btnViewOriginal)

        fun bind(item: PromoItem) {
            promoBrand.text = item.brand
            promoDiscountAmount.text = item.discountAmount
            promoDescription.text = item.description

            promoCode.text = item.code

            // Format date with Jalali Shamsi (default to 1 month validity if unstated or 'اطلاع ثانوی')
            val isGenericOrBlank = item.expiryDateText.isBlank() ||
                    item.expiryDateText == "نامشخص" ||
                    item.expiryDateText.contains("اطلاع ثانوی")

            val expiryText = if (!isGenericOrBlank) {
                item.expiryDateText
            } else {
                val j = JalaliCalendar.fromMillis(item.receivedAt + (30L * 24 * 60 * 60 * 1000L))
                "${j.year}/${String.format("%02d", j.month)}/${String.format("%02d", j.day)} (۱ ماهه)"
            }
            promoExpiry.text = "مهلت استفاده: ${JalaliCalendar.toPersianDigits(expiryText)}"

            btnCopyPromo.text = "کپی کد"
            btnCopyPromo.setOnClickListener {
                ClipboardHelper.copyToClipboard(context, item.code, "PROMO", showToast = false)
                btnCopyPromo.text = "کپی شد ✓"
                Toast.makeText(context, "کد تخفیف ${item.code} کپی شد", Toast.LENGTH_SHORT).show()
                btnCopyPromo.postDelayed({
                    btnCopyPromo.text = "کپی کد"
                }, 2000)
            }

            btnMarkUsed.setOnClickListener {
                item.isUsed = true
                val currentPos = adapterPosition
                if (currentPos != RecyclerView.NO_POSITION && currentPos in 0 until displayedPromos.size) {
                    displayedPromos.removeAt(currentPos)
                    allPromos.remove(item)
                    notifyItemRemoved(currentPos)
                    Toast.makeText(context, "کد تخفیف به عنوان «استفاده شد» علامت‌گذاری شد", Toast.LENGTH_SHORT).show()
                }
            }

            btnMarkUsed.setOnLongClickListener {
                item.isInvalid = true
                item.isUsed = true
                val currentPos = adapterPosition
                if (currentPos != RecyclerView.NO_POSITION && currentPos in 0 until displayedPromos.size) {
                    displayedPromos.removeAt(currentPos)
                    allPromos.remove(item)
                    notifyItemRemoved(currentPos)
                    Toast.makeText(context, "کد تخفیف به عنوان «منقضی / کار نمی‌کنه» گزارش شد", Toast.LENGTH_SHORT).show()
                }
                true
            }

            btnViewOriginal.setOnClickListener {
                val j = JalaliCalendar.fromMillis(item.receivedAt)
                val jalaliReceived = "${j.year}/${String.format("%02d", j.month)}/${String.format("%02d", j.day)}"
                val minOrderText = if (!item.minOrder.isNullOrBlank()) "\nشرایط: ${item.minOrder}" else ""
                val instructionsText = if (item.instructions.isNotBlank()) "\n\n💡 راه و شرایط گرفتن تخفیف:\n${item.instructions}" else ""

                AlertDialog.Builder(context)
                    .setTitle("${item.brand} (${item.discountAmount})")
                    .setMessage("فرستنده: ${item.sender}\nتاریخ دریافت: $jalaliReceived$minOrderText$instructionsText\n\n📄 متن کامل پیامک:\n${item.body}")
                    .setPositiveButton("کپی کد (${item.code})") { _, _ ->
                        ClipboardHelper.copyToClipboard(context, item.code, "PROMO")
                    }
                    .setNegativeButton("بستن", null)
                    .show()
            }
        }
    }
}
