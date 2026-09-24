package com.moez.QKSMS.feature.smart.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.moez.QKSMS.R
import com.moez.QKSMS.common.util.JalaliCalendar
import com.moez.QKSMS.feature.smart.ClipboardHelper
import com.moez.QKSMS.feature.smart.model.OtpItem
import com.moez.QKSMS.feature.smart.promo.PromoValueParser
import java.util.Calendar

/**
 * The OTP tab: the newest code from today as a large card, everything else as a compact group
 * underneath. Codes from earlier days are marked expired and can no longer be copied.
 */
class OtpCodesAdapter(
    private val context: Context,
    private var otps: MutableList<OtpItem> = mutableListOf()
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private sealed class Row {
        data class Header(val title: String, val count: Int?) : Row()
        data class Latest(val otp: OtpItem) : Row()
        data class Earlier(val otp: OtpItem, val first: Boolean, val last: Boolean) : Row()
    }

    private companion object {
        const val TYPE_HEADER = 0
        const val TYPE_LATEST = 1
        const val TYPE_EARLIER = 2
    }

    private val rows = ArrayList<Row>()
    private val density = context.resources.displayMetrics.density

    fun updateData(newOtps: List<OtpItem>) {
        otps.clear()
        otps.addAll(newOtps)

        rows.clear()
        val sorted = newOtps.sortedByDescending { it.receivedAt }
        val latest = sorted.firstOrNull()?.takeIf { DateUtils.isToday(it.receivedAt) }
        val earlier = if (latest != null) sorted.drop(1) else sorted
        if (latest != null) {
            rows += Row.Header("Latest", null)
            rows += Row.Latest(latest)
        }
        if (earlier.isNotEmpty()) {
            rows += Row.Header("Earlier", earlier.size)
            earlier.forEachIndexed { index, otp ->
                rows += Row.Earlier(otp, first = index == 0, last = index == earlier.lastIndex)
            }
        }
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = rows.size

    override fun getItemViewType(position: Int): Int = when (rows[position]) {
        is Row.Header -> TYPE_HEADER
        is Row.Latest -> TYPE_LATEST
        is Row.Earlier -> TYPE_EARLIER
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderViewHolder(inflater.inflate(R.layout.promo_section_header, parent, false))
            TYPE_LATEST -> LatestViewHolder(inflater.inflate(R.layout.otp_list_item, parent, false))
            else -> EarlierViewHolder(inflater.inflate(R.layout.otp_row_item, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is Row.Header -> (holder as HeaderViewHolder).bind(row)
            is Row.Latest -> (holder as LatestViewHolder).bind(row.otp)
            is Row.Earlier -> (holder as EarlierViewHolder).bind(row)
        }
    }

    /** Digits in groups of three or four, the way people read a code aloud. */
    private fun spaced(code: String): String {
        if (!code.all { it.isDigit() }) return code
        return when (code.length) {
            6, 7 -> code.substring(0, 3) + " " + code.substring(3)
            8 -> code.substring(0, 4) + " " + code.substring(4)
            else -> code
        }
    }

    private fun timeOf(millis: Long): String =
        JalaliCalendar.formatTime(Calendar.getInstance().apply { timeInMillis = millis })

    private fun dayOf(millis: Long): String {
        if (DateUtils.isToday(millis)) return "امروز"
        val jalali = JalaliCalendar.fromMillis(millis)
        return "${jalali.year}/${String.format("%02d", jalali.month)}/${String.format("%02d", jalali.day)}"
    }

    private fun glyph(res: Int, color: Int, sizeDp: Int): Drawable? =
        ContextCompat.getDrawable(context, res)?.mutate()?.apply {
            val px = (sizeDp * density).toInt()
            setBounds(0, 0, px, px)
            setTint(color)
        }

    private fun copy(code: String) {
        ClipboardHelper.copyToClipboard(context, code, "OTP", showToast = false)
        Toast.makeText(context, "کد تایید $code کپی شد", Toast.LENGTH_SHORT).show()
    }

    private inner class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.sectionTitle)
        private val count: TextView = itemView.findViewById(R.id.sectionCount)
        private val dot: View = itemView.findViewById(R.id.sectionDot)

        fun bind(header: Row.Header) {
            title.text = header.title
            dot.visibility = View.GONE
            count.visibility = if (header.count != null) View.VISIBLE else View.GONE
            count.text = header.count?.let { PromoValueParser.toPersianDigits(it.toString()) }
        }
    }

    private inner class LatestViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val service: TextView = itemView.findViewById(R.id.otpService)
        private val sender: TextView = itemView.findViewById(R.id.otpSender)
        private val date: TextView = itemView.findViewById(R.id.otpDate)
        private val code: TextView = itemView.findViewById(R.id.otpCode)
        private val status: TextView = itemView.findViewById(R.id.otpStatus)
        private val button: Button = itemView.findViewById(R.id.btnCopyOtp)

        fun bind(item: OtpItem) {
            val time = timeOf(item.receivedAt)
            service.text = item.serviceName
            sender.text = item.sender
            date.text = time
            code.text = spaced(item.code)

            val success = ContextCompat.getColor(context, R.color.success)
            status.text = "فعال · امروز $time"
            status.setCompoundDrawablesRelative(glyph(R.drawable.ic_lc_circle_check, success, 16), null, null, null)

            val copyGlyph = glyph(R.drawable.ic_lc_copy, Color.WHITE, 18)
            val doneGlyph = glyph(R.drawable.ic_lc_circle_check, Color.WHITE, 18)
            button.text = "کپی کد"
            button.setCompoundDrawablesRelative(copyGlyph, null, null, null)
            button.setOnClickListener {
                copy(item.code)
                button.text = "کپی شد"
                button.setCompoundDrawablesRelative(doneGlyph, null, null, null)
                button.postDelayed({
                    button.text = "کپی کد"
                    button.setCompoundDrawablesRelative(copyGlyph, null, null, null)
                }, 2000)
            }
        }
    }

    private inner class EarlierViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val service: TextView = itemView.findViewById(R.id.otpRowService)
        private val meta: TextView = itemView.findViewById(R.id.otpRowMeta)
        private val code: TextView = itemView.findViewById(R.id.otpRowCode)
        private val state: TextView = itemView.findViewById(R.id.otpRowState)
        private val separator: View = itemView.findViewById(R.id.otpRowSeparator)

        fun bind(row: Row.Earlier) {
            val item = row.otp
            val today = DateUtils.isToday(item.receivedAt)
            service.text = item.serviceName
            meta.text = "${item.sender} · ${dayOf(item.receivedAt)} ${timeOf(item.receivedAt)}"
            code.text = spaced(item.code)

            // Today's codes may still be valid and copy on tap; older ones are marked expired
            state.text = if (today) "فعال" else "منقضی شده"
            state.visibility = View.VISIBLE
            itemView.isClickable = today
            itemView.setOnClickListener(if (today) View.OnClickListener { copy(item.code) } else null)

            itemView.setBackgroundResource(when {
                row.first && row.last -> R.drawable.group_single
                row.first -> R.drawable.group_top
                row.last -> R.drawable.group_bottom
                else -> R.drawable.group_middle
            })
            separator.visibility = if (row.last) View.GONE else View.VISIBLE
        }
    }
}
