/*
 * Copyright (C) 2017 Moez Bhatti <moez.bhatti@gmail.com>
 *
 * This file is part of QKSMS.
 *
 * QKSMS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * QKSMS is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with QKSMS.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.moez.QKSMS.common.widget

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.appcompat.widget.LinearLayoutCompat
import com.moez.QKSMS.R
import com.moez.QKSMS.common.util.extensions.resolveThemeAttribute
import com.moez.QKSMS.common.util.extensions.resolveThemeColorStateList
import com.moez.QKSMS.common.util.extensions.setVisible
import com.moez.QKSMS.injection.appComponent
import kotlinx.android.synthetic.main.preference_view.view.*

class PreferenceView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : LinearLayoutCompat(context, attrs) {

    var title: String? = null
        set(value) {
            field = value

            if (isInEditMode) {
                findViewById<TextView>(R.id.titleView).text = value
            } else {
                titleView.text = value
            }
        }

    var summary: String? = null
        set(value) {
            field = value
            val hasValue = value?.isNotEmpty() == true

            // A value row shows the setting's current value at the end, as the Settings app does
            val summaryText = findViewById<TextView>(R.id.summaryView)
            val valueText = findViewById<TextView>(R.id.valueView)
            summaryText.text = if (valueSummary) null else value
            summaryText.setVisible(!valueSummary && hasValue)
            valueText.text = if (valueSummary) value else null
            valueText.setVisible(valueSummary && hasValue)
        }

    /** Whether [summary] is the current value, shown at the end of the row rather than below. */
    var valueSummary: Boolean = false
        set(value) {
            field = value
            summary = summary
        }

    /** A chevron at the end says the row opens another screen or a picker. */
    var showChevron: Boolean = false
        set(value) {
            field = value
            findViewById<View>(R.id.chevronView).setVisible(value)
            val frame = findViewById<View>(R.id.widgetFrame)
            (frame.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
                params.marginEnd = if (value) 0 else (16 * resources.displayMetrics.density).toInt()
                frame.layoutParams = params
            }
        }

    /**
     * Puts the icon on a rounded tile of [color], white on colour like the system Settings app.
     * Rows without a tile keep a plain secondary-coloured glyph.
     */
    fun setIconTile(@ColorInt color: Int?) {
        val iconView = findViewById<android.widget.ImageView>(R.id.icon)
        val density = resources.displayMetrics.density
        if (color == null) {
            iconView.background = null
            iconView.setPadding((3 * density).toInt())
            iconView.imageTintList = context.resolveThemeColorStateList(android.R.attr.textColorSecondary)
        } else {
            iconView.background = GradientDrawable().apply {
                cornerRadius = 8 * density
                setColor(color)
            }
            iconView.setPadding((6 * density).toInt())
            iconView.imageTintList = ColorStateList.valueOf(Color.WHITE)
        }
    }

    private fun View.setPadding(all: Int) = setPadding(all, all, all, all)

    init {
        if (!isInEditMode) {
            appComponent.inject(this)
        }

        View.inflate(context, R.layout.preference_view, this)
        setBackgroundResource(context.resolveThemeAttribute(R.attr.selectableItemBackground))
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL

        icon.imageTintList = context.resolveThemeColorStateList(android.R.attr.textColorSecondary)

        context.obtainStyledAttributes(attrs, R.styleable.PreferenceView).run {
            valueSummary = getBoolean(R.styleable.PreferenceView_valueSummary, false)
            showChevron = getBoolean(R.styleable.PreferenceView_chevron, false)
            setIconTile(if (hasValue(R.styleable.PreferenceView_iconTileColor)) {
                getColor(R.styleable.PreferenceView_iconTileColor, Color.GRAY)
            } else null)
            title = getString(R.styleable.PreferenceView_title)
            summary = getString(R.styleable.PreferenceView_summary)

            // If there's a custom view used for the preference's widget, inflate it
            getResourceId(R.styleable.PreferenceView_widget, -1).takeIf { it != -1 }?.let { id ->
                View.inflate(context, id, widgetFrame)
            }

            // If an icon is being used, set up the icon view
            getResourceId(R.styleable.PreferenceView_icon, -1).takeIf { it != -1 }?.let { id ->
                icon.setVisible(true)
                icon.setImageResource(id)
            }

            recycle()
        }
    }

}