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
import android.util.AttributeSet
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import com.moez.QKSMS.R
import com.moez.QKSMS.common.util.Colors
import com.moez.QKSMS.common.util.extensions.resolveThemeColor
import com.moez.QKSMS.injection.appComponent
import com.moez.QKSMS.util.Preferences
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * A switch in the style of the system one: a green capsule when on, a grey one when off, with
 * a white knob. The knob is never tinted, so its shadow survives; a disabled switch fades.
 */
class QkSwitch @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : SwitchCompat(context, attrs) {

    @Inject lateinit var colors: Colors
    @Inject lateinit var prefs: Preferences

    init {
        if (!isInEditMode) {
            appComponent.inject(this)
        }

        setThumbResource(R.drawable.switch_thumb)
        setTrackResource(R.drawable.switch_track)
        switchMinWidth = (51 * resources.displayMetrics.density).roundToInt()
        showText = false
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        if (!isInEditMode) {
            val states = arrayOf(
                    intArrayOf(-android.R.attr.state_enabled),
                    intArrayOf(android.R.attr.state_checked),
                    intArrayOf())

            thumbTintList = null
            trackTintList = ColorStateList(states, intArrayOf(
                    context.resolveThemeColor(R.attr.switchTrackDisabled),
                    ContextCompat.getColor(context, R.color.switchOn),
                    context.resolveThemeColor(R.attr.switchTrackEnabled)))
        }
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        alpha = if (drawableState.contains(android.R.attr.state_enabled)) 1f else 0.45f
    }
}
