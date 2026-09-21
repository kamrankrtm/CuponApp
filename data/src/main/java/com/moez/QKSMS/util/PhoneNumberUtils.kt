/*
 * Copyright (C) 2019 Moez Bhatti <moez.bhatti@gmail.com>
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
package com.moez.QKSMS.util

import android.content.Context
import android.telephony.PhoneNumberUtils
import io.michaelrocks.libphonenumber.android.PhoneNumberUtil
import io.michaelrocks.libphonenumber.android.Phonenumber
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PhoneNumberUtils @Inject constructor(context: Context) {

    private val countryCode = Locale.getDefault().country
    private val phoneNumberUtil = PhoneNumberUtil.createInstance(context)

    /**
     * Enhanced phone number comparison with robust Iranian & international matching
     */
    fun compare(first: String, second: String): Boolean {
        if (first.equals(second, true)) {
            return true
        }

        // Iranian & regional phone number normalization (strip +98, 98, 0, whitespace)
        val norm1 = normalizeForComparison(first)
        val norm2 = normalizeForComparison(second)
        if (norm1.isNotEmpty() && norm2.isNotEmpty()) {
            if (norm1 == norm2) return true
            if (norm1.length >= 7 && norm2.length >= 7) {
                if (norm1.endsWith(norm2) || norm2.endsWith(norm1)) {
                    return true
                }
            }
        }

        if (PhoneNumberUtils.compare(first, second)) {
            return true
        }

        try {
            val matchType = phoneNumberUtil.isNumberMatch(first, second)
            if (matchType >= PhoneNumberUtil.MatchType.SHORT_NSN_MATCH) {
                return true
            }
        } catch (t: Throwable) {
            // Ignore libphonenumber parse error
        }

        return false
    }

    private fun normalizeForComparison(number: String): String {
        // Convert Persian/Arabic digits first
        var result = cleanDestinationAddress(number)
        val digits = result.filter { it.isDigit() }
        return when {
            digits.startsWith("0098") -> digits.substring(4)
            digits.startsWith("098") -> digits.substring(3)
            digits.startsWith("98") && digits.length >= 12 -> digits.substring(2)
            digits.startsWith("0") && digits.length >= 11 -> digits.substring(1)
            else -> digits
        }
    }

    fun isPossibleNumber(number: CharSequence): Boolean {
        return parse(number) != null
    }

    fun isReallyDialable(digit: Char): Boolean {
        return PhoneNumberUtils.isReallyDialable(digit)
    }

    fun formatNumber(number: CharSequence): String {
        var numStr = cleanDestinationAddress(number.toString())
        if (numStr.startsWith("989") && numStr.length == 12 && numStr.all { it.isDigit() }) {
            numStr = "0" + numStr.substring(2)
        }
        return PhoneNumberUtils.formatNumber(numStr, countryCode) ?: numStr
    }

    fun normalizeNumber(number: String): String {
        return PhoneNumberUtils.stripSeparators(number)
    }

    /**
     * Cleans destination phone number before SMS transmission:
     * 1. Converts Persian (۰-۹) and Arabic (٠-٩) digits to ASCII (0-9)
     * 2. Strips all spaces, hyphens, brackets, parentheses
     * 3. Preserves leading '+' for international numbers
     */
    fun cleanDestinationAddress(number: String): String {
        var result = number.trim()
        val persianDigits = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')
        val arabicDigits = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')
        for (i in 0..9) {
            result = result.replace(persianDigits[i], '0' + i)
            result = result.replace(arabicDigits[i], '0' + i)
        }
        val hasLeadingPlus = result.startsWith("+")
        val digits = result.filter { it.isDigit() }
        val cleaned = if (hasLeadingPlus) "+$digits" else digits
        return if (cleaned.isNotEmpty()) cleaned else PhoneNumberUtils.stripSeparators(number.trim())
    }

    private fun parse(number: CharSequence): Phonenumber.PhoneNumber? {
        return tryOrNull(false) { phoneNumberUtil.parse(number, countryCode) }
    }

}
