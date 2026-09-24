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
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import com.moez.QKSMS.R
import com.moez.QKSMS.common.Navigator
import com.moez.QKSMS.common.util.Colors
import com.moez.QKSMS.common.util.ReadableColors
import com.moez.QKSMS.common.util.extensions.resolveThemeColor
import com.moez.QKSMS.common.util.extensions.setTint
import com.moez.QKSMS.feature.smart.SenderIdentity
import com.moez.QKSMS.injection.appComponent
import com.moez.QKSMS.model.Recipient
import com.moez.QKSMS.util.GlideApp
import kotlinx.android.synthetic.main.avatar_view.view.*
import javax.inject.Inject

/**
 * A sender's picture.
 *
 * People are circles: their photo, or initials on a soft grey gradient. Businesses are rounded
 * squares, like app icons: a recognised brand shows its colour and initial, anything else a
 * quiet grey tile with a building glyph. The shape alone tells a person from a company.
 */
class AvatarView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    @Inject lateinit var colors: Colors
    @Inject lateinit var navigator: Navigator

    private var lookupKey: String? = null
    private var fullName: String? = null
    private var photoUri: String? = null
    private var lastUpdated: Long? = null
    private var address: String? = null
    private var isPerson = true

    init {
        if (!isInEditMode) {
            appComponent.inject(this)
        }

        View.inflate(context, R.layout.avatar_view, this)
        clipToOutline = true
    }

    /**
     * Use the [contact] information to display the avatar.
     */
    fun setRecipient(recipient: Recipient?) {
        lookupKey = recipient?.contact?.lookupKey
        fullName = recipient?.contact?.name
        photoUri = recipient?.contact?.photoUri
        lastUpdated = recipient?.contact?.lastUpdate
        address = recipient?.address
        isPerson = recipient == null || SenderIdentity.isPerson(recipient.address, recipient.contact != null)
        updateView()
    }

    override fun onFinishInflate() {
        super.onFinishInflate()

        if (!isInEditMode) {
            updateView()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w != oldw && !isInEditMode) updateView()
    }

    private fun updateView() {
        val white = ContextCompat.getColor(context, R.color.white)
        val brand = if (isPerson) null else SenderIdentity.brandFor(address)
        val tile = GradientDrawable()
        if (isPerson) {
            tile.setShape(GradientDrawable.OVAL)
        } else {
            tile.setShape(GradientDrawable.RECTANGLE)
            tile.setCornerRadius(width * 0.27f)
        }

        when {
            isPerson -> {
                tile.setOrientation(GradientDrawable.Orientation.TOP_BOTTOM)
                tile.setColors(intArrayOf(
                        ContextCompat.getColor(context, R.color.avatarTop),
                        ContextCompat.getColor(context, R.color.avatarBottom)))
                initial.setTextColor(white)
                icon.setImageResource(R.drawable.ic_lc_user)
                icon.setTint(white)
            }

            brand != null -> {
                tile.setColor(ReadableColors.fillForWhiteText(brand.color))
                initial.setTextColor(white)
            }

            else -> {
                tile.setColor(context.resolveThemeColor(R.attr.bubbleColor))
                icon.setImageResource(R.drawable.ic_lc_building)
                icon.setTint(context.resolveThemeColor(android.R.attr.textColorSecondary))
            }
        }
        background = tile

        val initials = when {
            brand != null -> brand.en.take(1).toUpperCase()
            isPerson -> fullName
                    ?.substringBefore(',')
                    ?.split(" ").orEmpty()
                    .filter { name -> name.isNotEmpty() }
                    .map { name -> name[0] }
                    .filter { initial -> initial.isLetterOrDigit() }
                    .map { initial -> initial.toString() }
                    .let { list -> if (list.size > 1) list.first() + list.last() else list.firstOrNull().orEmpty() }
            else -> ""
        }

        if (initials.isNotEmpty()) {
            initial.text = initials
            icon.visibility = GONE
        } else {
            initial.text = null
            icon.visibility = VISIBLE
        }

        photo.setImageDrawable(null)
        photoUri?.let { photoUri ->
            GlideApp.with(photo)
                    .load(photoUri)
                    .into(photo)
        }
    }
}
