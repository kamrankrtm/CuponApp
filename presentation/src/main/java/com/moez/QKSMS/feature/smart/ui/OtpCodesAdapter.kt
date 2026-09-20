package com.moez.QKSMS.feature.smart.ui

import android.content.Context
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.moez.QKSMS.R
import com.moez.QKSMS.feature.smart.ClipboardHelper
import com.moez.QKSMS.feature.smart.model.OtpItem

class OtpCodesAdapter(
    private val context: Context,
    private var otps: MutableList<OtpItem> = mutableListOf()
) : RecyclerView.Adapter<OtpCodesAdapter.OtpViewHolder>() {

    fun updateData(newOtps: List<OtpItem>) {
        otps.clear()
        otps.addAll(newOtps)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OtpViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.otp_list_item, parent, false)
        return OtpViewHolder(view)
    }

    override fun onBindViewHolder(holder: OtpViewHolder, position: Int) {
        holder.bind(otps[position])
    }

    override fun getItemCount(): Int = otps.size

    inner class OtpViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val otpService: TextView = itemView.findViewById(R.id.otpService)
        private val otpSender: TextView = itemView.findViewById(R.id.otpSender)
        private val otpCode: TextView = itemView.findViewById(R.id.otpCode)
        private val otpDate: TextView = itemView.findViewById(R.id.otpDate)
        private val btnCopyOtp: Button = itemView.findViewById(R.id.btnCopyOtp)

        fun bind(item: OtpItem) {
            otpService.text = item.serviceName
            otpSender.text = "فرستنده: ${item.sender}"
            otpCode.text = item.code.chunked(1).joinToString(" ")

            val relativeTime = DateUtils.getRelativeTimeSpanString(
                item.receivedAt,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS
            )
            otpDate.text = relativeTime

            btnCopyOtp.text = "کپی مجدد"
            btnCopyOtp.setOnClickListener {
                ClipboardHelper.copyToClipboard(context, item.code, "OTP", showToast = false)
                btnCopyOtp.text = "کپی شد ✓"
                Toast.makeText(context, "کد تایید ${item.code} کپی شد", Toast.LENGTH_SHORT).show()
                btnCopyOtp.postDelayed({
                    btnCopyOtp.text = "کپی مجدد"
                }, 2000)
            }
        }
    }
}
