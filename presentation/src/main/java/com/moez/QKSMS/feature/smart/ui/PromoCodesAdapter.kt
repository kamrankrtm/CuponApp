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
import com.moez.QKSMS.feature.smart.ClipboardHelper
import com.moez.QKSMS.feature.smart.model.PromoItem

class PromoCodesAdapter(
    private val context: Context,
    private var promos: MutableList<PromoItem> = mutableListOf()
) : RecyclerView.Adapter<PromoCodesAdapter.PromoViewHolder>() {

    fun updateData(newPromos: List<PromoItem>) {
        promos.clear()
        promos.addAll(newPromos)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PromoViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.promo_list_item, parent, false)
        return PromoViewHolder(view)
    }

    override fun onBindViewHolder(holder: PromoViewHolder, position: Int) {
        val item = promos[position]
        holder.bind(item)
    }

    override fun getItemCount(): Int = promos.size

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
            promoExpiry.text = "مهلت: ${item.expiryDateText}"

            btnCopyPromo.text = "کپی کد"
            btnCopyPromo.setOnClickListener {
                ClipboardHelper.copyToClipboard(context, item.code, "PROMO", showToast = false)
                btnCopyPromo.text = "کپی شد ✓"
                Toast.makeText(context, "کد ${item.code} با موفقیت کپی شد", Toast.LENGTH_SHORT).show()
                btnCopyPromo.postDelayed({
                    btnCopyPromo.text = "کپی کد"
                }, 2000)
            }

            btnMarkUsed.setOnClickListener {
                item.isUsed = true
                val currentPos = adapterPosition
                if (currentPos != RecyclerView.NO_POSITION) {
                    promos.removeAt(currentPos)
                    notifyItemRemoved(currentPos)
                    Toast.makeText(context, "کد تخفیف به عنوان مصرف‌شده علامت‌گذاری شد", Toast.LENGTH_SHORT).show()
                }
            }

            btnViewOriginal.setOnClickListener {
                AlertDialog.Builder(context)
                    .setTitle(item.brand)
                    .setMessage("فرستنده: ${item.sender}\n\nمتن پیامک:\n${item.body}\n\nراهنما:\n${item.instructions}")
                    .setPositiveButton("کپی کد") { _, _ ->
                        ClipboardHelper.copyToClipboard(context, item.code, "PROMO")
                    }
                    .setNegativeButton("بستن", null)
                    .show()
            }
        }
    }
}
