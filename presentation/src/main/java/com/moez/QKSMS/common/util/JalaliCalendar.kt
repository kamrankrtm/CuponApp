package com.moez.QKSMS.common.util

import java.util.Calendar

data class JalaliDate(
    val year: Int,
    val month: Int, // 1 to 12
    val day: Int,   // 1 to 31
    val dayOfWeek: Int // Calendar.DAY_OF_WEEK
)

object JalaliCalendar {

    private val PERSIAN_MONTH_NAMES = arrayOf(
        "فروردین", "اردیبهشت", "خرداد",
        "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر",
        "دی", "بهمن", "اسفند"
    )

    private val PERSIAN_WEEKDAY_NAMES = mapOf(
        Calendar.SATURDAY to "شنبه",
        Calendar.SUNDAY to "یکشنبه",
        Calendar.MONDAY to "دوشنبه",
        Calendar.TUESDAY to "سه‌شنبه",
        Calendar.WEDNESDAY to "چهارشنبه",
        Calendar.THURSDAY to "پنج‌شنبه",
        Calendar.FRIDAY to "جمعه"
    )

    fun getMonthName(month: Int): String {
        return if (month in 1..12) PERSIAN_MONTH_NAMES[month - 1] else ""
    }

    fun getWeekdayName(dayOfWeek: Int): String {
        return PERSIAN_WEEKDAY_NAMES[dayOfWeek] ?: ""
    }

    fun toPersianDigits(text: String): String {
        val persianDigits = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')
        val chars = text.toCharArray()
        for (i in chars.indices) {
            val c = chars[i]
            if (c in '0'..'9') {
                chars[i] = persianDigits[c - '0']
            }
        }
        return String(chars)
    }

    fun fromMillis(millis: Long): JalaliDate {
        val cal = Calendar.getInstance()
        cal.timeInMillis = millis
        val gYear = cal.get(Calendar.YEAR)
        val gMonth = cal.get(Calendar.MONTH) + 1
        val gDay = cal.get(Calendar.DAY_OF_MONTH)
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)

        val (jYear, jMonth, jDay) = gregorianToJalali(gYear, gMonth, gDay)
        return JalaliDate(jYear, jMonth, jDay, dayOfWeek)
    }

    fun gregorianToJalali(gy: Int, gm: Int, gd: Int): Triple<Int, Int, Int> {
        val gDaysInMonth = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        val jDaysInMonth = intArrayOf(31, 31, 31, 31, 31, 31, 30, 30, 30, 30, 30, 29)

        val gy2 = gy - 1600
        val gm2 = gm - 1
        val gd2 = gd - 1

        var gDayNo = 365 * gy2 + (gy2 + 3) / 4 - (gy2 + 99) / 100 + (gy2 + 399) / 400
        for (i in 0 until gm2) {
            gDayNo += gDaysInMonth[i]
        }
        if (gm2 > 1 && ((gy2 % 4 == 0 && gy2 % 100 != 0) || (gy2 % 400 == 0))) {
            gDayNo++
        }
        gDayNo += gd2

        var jDayNo = gDayNo - 79

        val jNp = jDayNo / 12053
        jDayNo %= 12053

        var jy = 979 + 33 * jNp + 4 * (jDayNo / 1461)
        jDayNo %= 1461

        if (jDayNo >= 366) {
            jy += (jDayNo - 1) / 365
            jDayNo = (jDayNo - 1) % 365
        }

        var jm = 0
        for (i in 0..11) {
            if (jDayNo < jDaysInMonth[i]) {
                jm = i + 1
                break
            }
            jDayNo -= jDaysInMonth[i]
        }

        // The month table caps Esfand at 29 days, so the 366th day of a Jalali leap year
        // falls through the loop. That day is Esfand 30.
        if (jm == 0) {
            return Triple(jy, 12, 30)
        }

        val jd = jDayNo + 1

        return Triple(jy, jm, jd)
    }

    /**
     * Inverse of [gregorianToJalali]: converts a Jalali date back to a Gregorian one.
     *
     * Needed so a deadline written as a Shamsi date in an SMS can become a real instant that
     * the app can compare against the clock.
     */
    fun jalaliToGregorian(jy: Int, jm: Int, jd: Int): Triple<Int, Int, Int> {
        val gDaysInMonth = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        val jDaysInMonth = intArrayOf(31, 31, 31, 31, 31, 31, 30, 30, 30, 30, 30, 29)

        val jy2 = jy - 979
        val jm2 = jm - 1
        val jd2 = jd - 1

        var jDayNo = 365 * jy2 + (jy2 / 33) * 8 + (jy2 % 33 + 3) / 4
        for (i in 0 until jm2) {
            jDayNo += jDaysInMonth[i]
        }
        jDayNo += jd2

        var gDayNo = jDayNo + 79

        var gy = 1600 + 400 * (gDayNo / 146097)
        gDayNo %= 146097

        var leap = true
        if (gDayNo >= 36525) {
            gDayNo--
            gy += 100 * (gDayNo / 36524)
            gDayNo %= 36524
            if (gDayNo >= 365) gDayNo++ else leap = false
        }

        gy += 4 * (gDayNo / 1461)
        gDayNo %= 1461

        if (gDayNo >= 366) {
            leap = false
            gDayNo--
            gy += gDayNo / 365
            gDayNo %= 365
        }

        var i = 0
        while (true) {
            val monthLength = gDaysInMonth[i] + (if (i == 1 && leap) 1 else 0)
            if (gDayNo < monthLength) break
            gDayNo -= monthLength
            i++
        }

        return Triple(gy, i + 1, gDayNo + 1)
    }

    /**
     * Turns a Jalali date into a timestamp, at the start of that day or its last millisecond.
     *
     * Discount deadlines are inclusive — "valid until 5 Mehr" means the code still works all
     * through 5 Mehr — so callers pass [endOfDay] when interpreting an expiry.
     */
    fun toMillis(jYear: Int, jMonth: Int, jDay: Int, endOfDay: Boolean = false): Long {
        val (gy, gm, gd) = jalaliToGregorian(jYear, jMonth, jDay)
        val cal = Calendar.getInstance()
        cal.set(Calendar.YEAR, gy)
        cal.set(Calendar.MONTH, gm - 1)
        cal.set(Calendar.DAY_OF_MONTH, gd)
        if (endOfDay) {
            cal.set(Calendar.HOUR_OF_DAY, 23)
            cal.set(Calendar.MINUTE, 59)
            cal.set(Calendar.SECOND, 59)
            cal.set(Calendar.MILLISECOND, 999)
        } else {
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    /**
     * Formats time (e.g. 14:05) in Persian digits.
     */
    fun formatTime(cal: Calendar): String {
        val hour = String.format("%02d", cal.get(Calendar.HOUR_OF_DAY))
        val minute = String.format("%02d", cal.get(Calendar.MINUTE))
        return toPersianDigits("$hour:$minute")
    }

    fun formatTimeWithSeconds(cal: Calendar): String {
        val hour = String.format("%02d", cal.get(Calendar.HOUR_OF_DAY))
        val minute = String.format("%02d", cal.get(Calendar.MINUTE))
        val second = String.format("%02d", cal.get(Calendar.SECOND))
        return toPersianDigits("$hour:$minute:$second")
    }
}
