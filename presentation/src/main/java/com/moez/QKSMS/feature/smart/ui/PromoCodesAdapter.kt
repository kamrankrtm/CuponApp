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
            val matchesQuery = q.isEmpty() ||
                    promo.brand.toLowerCase().contains(q) ||
                    promo.code.toLowerCase().contains(q) ||
                    promo.description.toLowerCase().contains(q)

            val b = promo.brand
            val d = promo.description
            val matchesCat = when (cat) {
                "all" -> true
                "food" -> b.contains("فود") || b.contains("اسنپ‌فود") || b.contains("تپسی‌فود") || d.contains("غذا") || d.contains("رستوران")
                "shopping" -> b.contains("دیجی") || b.contains("باسلام") || b.contains("اکالا") || b.contains("تکنولایف") || d.contains("خرید") || d.contains("فروشگاه")
                "travel" -> (b.contains("اسنپ") && !b.contains("فود")) || (b.contains("تپسی") && !b.contains("فود")) || b.contains("علی‌بابا") || d.contains("سفر") || d.contains("تاکسی")
                "entertainment" -> b.contains("فیلیمو") || b.contains("نماوا") || b.contains("سینما") || d.contains("فیلم") || d.contains("سریال")
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

            // Format date with Jalali Shamsi (default to 1 month validity if unstated)
            val expiryText = if (item.expiryDateText.isNotBlank() && item.expiryDateText != "نامشخص") {
                item.expiryDateText
            } else {
                val j = JalaliCalendar.fromMillis(item.receivedAt + (30L * 24 * 60 * 60 * 1000L))
                "${j.year}/${String.format("%02d", j.month)}/${String.format("%02d", j.day)} (۱ ماهه)"
            }
            promoExpiry.text = "مهلت استفاده: $expiryText"

            btnCopyPromo.text = "Copy Code"
            btnCopyPromo.setOnClickListener {
                ClipboardHelper.copyToClipboard(context, item.code, "PROMO", showToast = false)
                btnCopyPromo.text = "Copied! ✓"
                Toast.makeText(context, "Promo code ${item.code} copied to clipboard", Toast.LENGTH_SHORT).show()
                btnCopyPromo.postDelayed({
                    btnCopyPromo.text = "Copy Code"
                }, 2000)
            }

            btnMarkUsed.setOnClickListener {
                item.isUsed = true
                val currentPos = adapterPosition
                if (currentPos != RecyclerView.NO_POSITION && currentPos in 0 until displayedPromos.size) {
                    displayedPromos.removeAt(currentPos)
                    allPromos.remove(item)
                    notifyItemRemoved(currentPos)
                    Toast.makeText(context, "Promo code marked as used", Toast.LENGTH_SHORT).show()
                }
            }

            btnViewOriginal.setOnClickListener {
                val j = JalaliCalendar.fromMillis(item.receivedAt)
                val jalaliReceived = "${j.year}/${String.format("%02d", j.month)}/${String.format("%02d", j.day)}"
                AlertDialog.Builder(context)
                    .setTitle(item.brand)
                    .setMessage("Sender: ${item.sender}\nDate: $jalaliReceived\n\nMessage:\n${item.body}\n\nDetails:\n${item.instructions}")
                    .setPositiveButton("Copy Code") { _, _ ->
                        ClipboardHelper.copyToClipboard(context, item.code, "PROMO")
                    }
                    .setNegativeButton("Close", null)
                    .show()
            }
        }
    }
}
