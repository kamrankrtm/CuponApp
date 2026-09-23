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
package com.moez.QKSMS.common.util

import android.content.Context
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.moez.QKSMS.R
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FontProvider @Inject constructor(context: Context) {

    /**
     * The app typeface (Dubai), bundled with the APK. Loading a local font is synchronous and
     * can't fail the way a downloadable one could, so there is no pending-callback queue.
     */
    private val appFont: Typeface? = try {
        ResourcesCompat.getFont(context, R.font.app_font)
    } catch (e: Exception) {
        Timber.w(e, "Failed to load the app font")
        null
    }

    fun getAppFont(callback: (Typeface) -> Unit) {
        appFont?.run(callback)
    }

}
