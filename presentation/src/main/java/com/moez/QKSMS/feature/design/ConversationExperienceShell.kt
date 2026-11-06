package com.moez.QKSMS.feature.design

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.moez.QKSMS.R

/**
 * Changes composition of an actual conversation, not just its bubble color.
 * The original send/attach/SIM/WhatsApp Views retain their listeners and IDs.
 */
object ConversationExperienceShell {
    fun apply(activity: Activity) {
        val style = ExperienceStore.get(activity)
        val header = activity.findViewById<LinearLayout>(R.id.toolbarHeader)
        val root = activity.findViewById<ConstraintLayout>(R.id.contentView)
        val background = activity.findViewById<View>(R.id.messageBackground)
        val send = activity.findViewById<ImageView>(R.id.send)
        val attach = activity.findViewById<ImageView>(R.id.attach)
        val list = activity.findViewById<RecyclerView>(R.id.messageList)
        val input = activity.findViewById<TextView>(R.id.message)

        background.backgroundTintList = null
        val corners = when (style) {
            Experience.MICROSOFT -> 12
            Experience.APPLE -> 27
            Experience.AVATR -> 8
            Experience.PINTEREST -> 24
            Experience.TELEGRAM -> 21
            Experience.META -> 29
        }
        val field = when (style) {
            Experience.MICROSOFT -> Color.rgb(229, 239, 252)
            Experience.APPLE -> Color.rgb(242, 244, 247)
            Experience.AVATR -> Color.rgb(46, 39, 72)
            Experience.PINTEREST -> Color.rgb(255, 240, 239)
            Experience.TELEGRAM -> Color.rgb(233, 247, 253)
            Experience.META -> Color.rgb(238, 232, 254)
        }
        background.background = ExperienceStore.shape(field, dp(activity,corners),
            if (style == Experience.AVATR) style.accent else null)
        (background.layoutParams as? ConstraintLayout.LayoutParams)?.let { params ->
            params.marginStart = dp(activity, when (style) {
                Experience.MICROSOFT -> 12
                Experience.APPLE -> 7
                Experience.AVATR -> 14
                Experience.PINTEREST -> 13
                Experience.TELEGRAM -> 4
                Experience.META -> 6
            })
            params.marginEnd = dp(activity, if (style == Experience.AVATR) 16 else 9)
            params.bottomMargin = dp(activity, if (style == Experience.APPLE) 16 else 10)
            background.layoutParams = params
        }
        input.setTextColor(if (style.darkHeader) Color.WHITE else style.ink)
        input.setHintTextColor(if (style.darkHeader) Color.LTGRAY else Color.GRAY)
        send.background = ExperienceStore.shape(style.accent,
            dp(activity, if (style == Experience.MICROSOFT || style == Experience.AVATR) 10 else 23))
        send.imageTintList = ColorStateList.valueOf(Color.WHITE)
        val size = when (style) {
            Experience.MICROSOFT -> 39
            Experience.APPLE -> 35
            Experience.AVATR -> 42
            Experience.PINTEREST -> 40
            Experience.TELEGRAM -> 35
            Experience.META -> 38
        }
        (send.layoutParams as? ConstraintLayout.LayoutParams)?.let {
            it.width = dp(activity,size)
            it.height = dp(activity,size)
            send.layoutParams = it
        }
        when (style) {
            Experience.APPLE -> {
                // Large centered avatar and a single clean bottom input.
                header.orientation = LinearLayout.VERTICAL
                header.gravity = Gravity.CENTER
                list.setPadding(list.paddingLeft, list.paddingTop, list.paddingRight, dp(activity, 8))
            }
            Experience.TELEGRAM -> {
                horizontalHeader(header, activity)
                attach.setPadding(dp(activity, 6), dp(activity, 6), dp(activity, 6), dp(activity, 6))
                list.setPadding(list.paddingLeft, dp(activity, 2), list.paddingRight, dp(activity, 2))
            }
            Experience.MICROSOFT -> {
                horizontalHeader(header, activity)
                addTools(root, list, activity, style, listOf(
                    Triple("پیوست", R.id.attach, R.drawable.ic_lc_plus),
                    Triple("مشخصات", R.id.toolbarHeader, R.drawable.ic_lc_user)
                ))
            }
            Experience.AVATR -> {
                horizontalHeader(header, activity)
                list.setBackgroundColor(Color.rgb(23, 23, 39))
                addTools(root, list, activity, style, listOf(
                    Triple("MEDIA", R.id.attach, R.drawable.ic_lc_plus),
                    Triple("CONTACT", R.id.toolbarHeader, R.drawable.ic_lc_user),
                    Triple("LINK", R.id.sendWhatsApp, R.drawable.ic_whatsapp)
                ))
            }
            Experience.PINTEREST -> {
                header.orientation = LinearLayout.VERTICAL
                header.gravity = Gravity.CENTER
                addTools(root, list, activity, style, listOf(
                    Triple("افزودن تصویر", R.id.attach, R.drawable.ic_lc_plus),
                    Triple("جزئیات مخاطب", R.id.toolbarHeader, R.drawable.ic_lc_user)
                ))
            }
            Experience.META -> {
                horizontalHeader(header, activity)
                addTools(root, list, activity, style, listOf(
                    Triple("پروفایل", R.id.toolbarHeader, R.drawable.ic_lc_user),
                    Triple("واتساپ", R.id.sendWhatsApp, R.drawable.ic_whatsapp)
                ))
            }
        }
    }

    /** ComposeActivity recalculates header alignment on every render. Call after its bind. */
    fun refreshHeader(activity: Activity, conversationHeader: Boolean) {
        if (!conversationHeader) return
        val style = ExperienceStore.get(activity)
        if (style == Experience.APPLE || style == Experience.PINTEREST) return
        val header = activity.findViewById<LinearLayout>(R.id.toolbarHeader)
        val lp = header.layoutParams as? Toolbar.LayoutParams ?: return
        lp.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        lp.marginStart = dp(activity, 49)
        header.layoutParams = lp
        activity.findViewById<TextView>(R.id.toolbarTitle).textSize =
            if (style == Experience.AVATR) 16f else 17f
    }

    private fun horizontalHeader(header: LinearLayout, activity: Activity) {
        if (header.childCount < 3) return
        val titleAndChevron = header.getChildAt(1)
        val subtitle = header.getChildAt(2)
        header.removeView(subtitle)
        header.removeView(titleAndChevron)
        val names = LinearLayout(header.context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        names.addView(titleAndChevron)
        names.addView(subtitle)
        header.addView(names, LinearLayout.LayoutParams(-2,-2).apply {
            marginStart = dp(activity, 8)
        })
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        val avatar = header.findViewById<View>(R.id.toolbarAvatar)
        avatar.layoutParams = LinearLayout.LayoutParams(
            dp(activity, 39), dp(activity, 39)
        )
    }

    private fun addTools(
        root: ConstraintLayout,
        list: RecyclerView,
        activity: Activity,
        style: Experience,
        actions: List<Triple<String, Int, Int>>
    ) {
        val bar = LinearLayout(activity).apply {
            id = View.generateViewId()
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(activity, 9),dp(activity, 5),dp(activity, 9),dp(activity, 5))
            background = ExperienceStore.shape(
                if (style.darkHeader) Color.rgb(48, 39, 79) else style.surface, dp(activity, 16))
        }
        actions.forEach { (label, clickId, icon) ->
            val target = activity.findViewById<View>(clickId)
            val item = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                contentDescription = label
                setOnClickListener { target?.performClick() }
            }
            item.addView(ImageView(activity).apply {
                setImageResource(icon)
                imageTintList = ColorStateList.valueOf(style.accent)
            }, LinearLayout.LayoutParams(dp(activity, 18),dp(activity, 18)))
            item.addView(TextView(activity).apply {
                text = label
                textSize = 11f
                setTypeface(null, Typeface.BOLD)
                setTextColor(if (style.darkHeader) Color.WHITE else style.ink)
                setPadding(dp(activity, 5), 0, 0, 0)
            })
            bar.addView(item, LinearLayout.LayoutParams(0,dp(activity, 33),1f))
        }
        root.addView(bar, ConstraintLayout.LayoutParams(0,dp(activity, 45)).apply {
            startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            bottomToTop = R.id.messageBackground
            marginStart = dp(activity, 16)
            marginEnd = dp(activity, 16)
            bottomMargin = dp(activity, 8)
        })
        list.setPadding(list.paddingLeft, list.paddingTop, list.paddingRight, dp(activity, 59))
    }

    private fun dp(activity: Activity, value: Int) = ExperienceStore.dp(activity,value)
}
