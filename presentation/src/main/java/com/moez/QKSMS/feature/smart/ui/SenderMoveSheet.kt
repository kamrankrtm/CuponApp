package com.moez.QKSMS.feature.smart.ui

import android.app.Activity
import android.app.Dialog
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.moez.QKSMS.R
import com.moez.QKSMS.common.widget.GroupAvatarView
import com.moez.QKSMS.feature.smart.SenderOverrides.Tab
import com.moez.QKSMS.model.Recipient

/**
 * What a long press on a conversation opens: an action sheet with the sender at the top and
 * the tabs it can be moved to, plus "Select" in the All list, where a long press used to
 * start multi-select straight away.
 */
object SenderMoveSheet {

    fun show(
        activity: Activity,
        title: String,
        recipients: List<Recipient>,
        current: Tab?,
        offerSelect: Boolean,
        onMove: (Tab) -> Unit,
        onSelect: () -> Unit
    ) {
        // Views take the activity's theme (colours, Dubai); the dialog theme only shapes the window
        val inflater = LayoutInflater.from(activity)
        val dialog = Dialog(activity, R.style.ActionSheetWindow)
        val view = inflater.inflate(R.layout.sender_move_sheet, null, false)
        view.findViewById<TextView>(R.id.sheetTitle).text = title
        view.findViewById<GroupAvatarView>(R.id.sheetAvatar).recipients = recipients
        val rows = view.findViewById<LinearLayout>(R.id.sheetRows)

        fun addRow(@StringRes label: Int, @DrawableRes icon: Int, @ColorRes tile: Int, action: () -> Unit) {
            val row = inflater.inflate(R.layout.sender_move_row, rows, false)
            row.findViewById<ImageView>(R.id.rowIcon).apply {
                setImageResource(icon)
                backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(activity, tile))
            }
            row.findViewById<TextView>(R.id.rowLabel).setText(label)
            row.setOnClickListener {
                dialog.dismiss()
                action()
            }
            rows.addView(row)
        }

        if (current != Tab.PERSONAL) {
            addRow(R.string.sender_move_personal, R.drawable.ic_lc_user, R.color.tabPersonal) { onMove(Tab.PERSONAL) }
        }
        if (current != Tab.BANKING) {
            addRow(R.string.sender_move_banking, R.drawable.ic_lc_landmark, R.color.tabBanking) { onMove(Tab.BANKING) }
        }
        if (current != Tab.SPAM) {
            addRow(R.string.sender_move_spam, R.drawable.ic_lc_spam, R.color.tabSpam) { onMove(Tab.SPAM) }
        }
        if (offerSelect) {
            addRow(R.string.sender_select, R.drawable.ic_lc_circle_check, R.color.tileGray, onSelect)
        }

        dialog.setContentView(view)
        dialog.setCanceledOnTouchOutside(true)
        dialog.window?.run {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
        }
        dialog.show()
    }
}
