package com.moez.QKSMS.feature.design

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.RippleDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.moez.QKSMS.R

/**
 * Shared data, different list/card treatments. Called only when a row is bound so
 * it does not trigger additional data reads, notification reclassification or network work.
 */
object ExperienceRows {
    enum class Kind { CHAT, BANK, DISCOUNT, OTP, HISTORY }

    fun styleRow(view: View, kind: Kind) {
        val experience = ExperienceStore.get(view.context)
        val dp = { size: Int -> ExperienceStore.dp(view.context, size) }
        val margin = when (experience) {
            Experience.MICROSOFT -> 6
            Experience.APPLE -> 0
            Experience.AVATR -> 7
            Experience.PINTEREST -> 9
            Experience.TELEGRAM -> 0
            Experience.META -> 7
        }
        (view.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
            if (params.leftMargin != dp(margin) || params.rightMargin != dp(margin)) {
                params.setMargins(dp(margin), dp(if (margin > 0) 3 else 0),
                    dp(margin), dp(if (margin > 0) 3 else 0))
                view.layoutParams = params
            }
        }
        val (fill, radius, raised) = when (experience) {
            Experience.MICROSOFT -> Triple(Color.rgb(244, 247, 254), 15, true)
            Experience.APPLE -> Triple(Color.TRANSPARENT, 0, false)
            Experience.AVATR -> Triple(Color.rgb(45, 39, 70), 17, true)
            Experience.PINTEREST -> Triple(if (kind == Kind.DISCOUNT) Color.rgb(255, 233, 236)
                else Color.rgb(255, 246, 245), 21, true)
            Experience.TELEGRAM -> Triple(Color.TRANSPARENT, 0, false)
            Experience.META -> Triple(Color.rgb(244, 239, 255), 19, true)
        }
        if (radius == 0) {
            // Keep the platform's own touch feedback on compact, divider-based lists.
            return
        }
        val stroke = if (experience == Experience.AVATR) Color.rgb(90, 75, 134)
            else if (experience == Experience.PINTEREST && kind == Kind.DISCOUNT) experience.accent
            else null
        view.background = RippleDrawable(
            ColorStateList.valueOf(if (experience.darkHeader) 0x55FFFFFF else 0x22000000),
            ExperienceStore.shape(fill, dp(radius), stroke), null
        )
        view.elevation = if (raised) dp(1).toFloat() else 0f
        if (experience == Experience.AVATR) {
            contrastForDarkSurface(view)
        } else {
            contrastForLightSurface(view, experience.ink)
        }
    }

    fun styleBubble(body: TextView, outgoing: Boolean, emojiOnly: Boolean) {
        if (emojiOnly) return
        val style = ExperienceStore.get(body.context)
        val color = when (style) {
            Experience.MICROSOFT -> if (outgoing) Color.rgb(50, 105, 210) else Color.rgb(225, 235, 250)
            Experience.APPLE -> if (outgoing) Color.rgb(36, 127, 238) else Color.rgb(231, 233, 239)
            Experience.AVATR -> if (outgoing) Color.rgb(88, 68, 162) else Color.rgb(45, 41, 73)
            Experience.PINTEREST -> if (outgoing) Color.rgb(209, 48, 71) else Color.rgb(255, 237, 236)
            Experience.TELEGRAM -> if (outgoing) Color.rgb(204, 239, 255) else Color.rgb(239, 241, 244)
            Experience.META -> if (outgoing) Color.rgb(110, 89, 226) else Color.rgb(232, 224, 253)
        }
        val lightText = (style == Experience.MICROSOFT || style == Experience.APPLE ||
            style == Experience.AVATR || style == Experience.PINTEREST || style == Experience.META) && outgoing ||
            (style == Experience.AVATR && !outgoing)
        body.backgroundTintList = ColorStateList.valueOf(color)
        body.setTextColor(if (lightText) Color.WHITE else Color.rgb(30, 38, 49))
        body.setLinkTextColor(if (lightText) Color.WHITE else style.accent)
    }

    private fun contrastForLightSurface(view: View, textColor: Int) {
        if (view is TextView && view !is android.widget.Button) {
            // Coupon actions and bank amount indicators preserve their existing meaning.
            if (view.id != R.id.bankAmount && view.id != R.id.promoDiscountAmount) {
                view.setTextColor(textColor)
            }
        } else if (view is ViewGroup) {
            for (index in 0 until view.childCount) contrastForLightSurface(view.getChildAt(index), textColor)
        }
    }

    private fun contrastForDarkSurface(view: View) {
        if (view is TextView) {
            // Amounts and statuses retain their informative red/green coloration.
            if (view.id != R.id.bankAmount && view.id != R.id.promoDiscountAmount) {
                view.setTextColor(Color.rgb(240, 237, 249))
            }
        } else if (view is ViewGroup) {
            for (index in 0 until view.childCount) contrastForDarkSurface(view.getChildAt(index))
        }
    }
}
