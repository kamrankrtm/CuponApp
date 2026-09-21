package com.moez.QKSMS.feature.smart.model

import com.moez.QKSMS.common.util.JalaliCalendar
import java.util.regex.Pattern

data class PromoItem(
    val id: String,
    val brand: String,
    val brandEn: String = "",
    val category: String = "سایر",
    val categorySlug: String = "all",
    val code: String,
    val discountAmount: String,
    val description: String,
    val minOrder: String? = null,
    val instructions: String = "",
    val expiryDateText: String = "معتبر تا اطلاع ثانوی",
    val sender: String = "",
    val body: String = "",
    val receivedAt: Long = System.currentTimeMillis(),
    var isUsed: Boolean = false,
    var isInvalid: Boolean = false
) {
    companion object {
        private val PERSIAN_MONTHS = listOf(
            "فروردین", "اردیبهشت", "خرداد",
            "تیر", "مرداد", "شهریور",
            "مهر", "آبان", "آذر",
            "دی", "بهمن", "اسفند"
        )

        private fun normalize(text: String): String {
            val chars = text.toCharArray()
            for (i in chars.indices) {
                when (chars[i]) {
                    in '۰'..'۹' -> chars[i] = '0' + (chars[i] - '۰')
                    in '٠'..'٩' -> chars[i] = '0' + (chars[i] - '٠')
                }
            }
            return String(chars)
        }
    }

    /**
     * Determines whether this promotion has passed its validity or expiration date.
     */
    fun isExpired(nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (isInvalid || isUsed) return true

        val normExpiry = normalize(expiryDateText).trim()
        val normBody = normalize(body).trim()
        val combinedText = "$normExpiry $normBody"

        val curJalali = JalaliCalendar.fromMillis(nowMillis)
        val recJalali = JalaliCalendar.fromMillis(receivedAt)
        val curDateNum = curJalali.year * 10000 + curJalali.month * 100 + curJalali.day

        // 1. Check for explicit Jalali date like 1403/07/05 or 1402/12/29 or 1403-7-5
        val datePattern = Pattern.compile("(?:13|14)?(\\d{2})[/-](\\d{1,2})[/-](\\d{1,2})")
        val dateMatcher = datePattern.matcher(normExpiry)
        if (dateMatcher.find()) {
            val rawYear = dateMatcher.group(1)?.toIntOrNull() ?: 0
            val year = if (rawYear < 100) 1400 + rawYear else rawYear
            val month = dateMatcher.group(2)?.toIntOrNull() ?: 1
            val day = dateMatcher.group(3)?.toIntOrNull() ?: 1
            val promoDateNum = year * 10000 + month * 100 + day
            return promoDateNum < curDateNum
        }

        // 2. Check for Persian month name e.g. "تا ۵ مهر" or "۲۸ اسفند" in expiry text or body
        val monthPattern = Pattern.compile("(\\d{1,2})\\s*(فروردین|اردیبهشت|خرداد|تیر|مرداد|شهریور|مهر|آبان|آذر|دی|بهمن|اسفند)")
        val monthMatcher = monthPattern.matcher(combinedText)
        if (monthMatcher.find()) {
            val day = monthMatcher.group(1)?.toIntOrNull() ?: 1
            val monthName = monthMatcher.group(2) ?: ""
            val monthIdx = PERSIAN_MONTHS.indexOf(monthName)
            if (monthIdx != -1) {
                val month = monthIdx + 1
                val targetYear = if (month >= recJalali.month) recJalali.year else recJalali.year + 1
                val promoDateNum = targetYear * 10000 + month * 100 + day
                return promoDateNum < curDateNum
            }
        }

        // 3. Relative terms: "امشب", "امروز", "ساعت ۲۴" -> expires at end of day received
        if (combinedText.contains("امشب") || combinedText.contains("امروز") || combinedText.contains("ساعت 24") || combinedText.contains("ساعت ۲۴")) {
            val isSameDay = curJalali.year == recJalali.year && curJalali.month == recJalali.month && curJalali.day == recJalali.day
            if (!isSameDay && nowMillis > receivedAt) {
                return true
            }
            if (nowMillis > receivedAt + (24L * 60 * 60 * 1000L)) {
                return true
            }
        }

        // 4. "فردا" -> expires within 48h from receipt
        if (combinedText.contains("فردا") && nowMillis > receivedAt + (48L * 60 * 60 * 1000L)) {
            return true
        }

        // 5. Explicit hourly duration
        if (combinedText.contains("24 ساعت") && nowMillis > receivedAt + (24L * 60 * 60 * 1000L)) {
            return true
        }
        if (combinedText.contains("48 ساعت") && nowMillis > receivedAt + (48L * 60 * 60 * 1000L)) {
            return true
        }
        if (combinedText.contains("72 ساعت") && nowMillis > receivedAt + (72L * 60 * 60 * 1000L)) {
            return true
        }
        if ((combinedText.contains("3 روز") || combinedText.contains("۳ روز")) && nowMillis > receivedAt + (72L * 60 * 60 * 1000L)) {
            return true
        }
        if ((combinedText.contains("7 روز") || combinedText.contains("۷ روز") || combinedText.contains("یک هفته") || combinedText.contains("1 هفته"))
            && nowMillis > receivedAt + (7L * 24 * 60 * 60 * 1000L)) {
            return true
        }

        // 6. Max default validity: any Iranian discount code received more than 30 days ago is expired
        if (nowMillis - receivedAt > (30L * 24 * 60 * 60 * 1000L)) {
            return true
        }

        return false
    }
}
