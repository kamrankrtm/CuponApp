package com.moez.QKSMS.common.util

/**
 * Converts Iranian phone numbers to the local format expected by the phone app.
 *
 * Examples:
 * 989122719074, +989122719074, 00989122719074 -> 09122719074
 *
 * Non-Iranian and short/service numbers are left unchanged.
 */
fun normalizeIranianPhoneForDialer(address: String): String {
    val normalizedDigits = buildString(address.length) {
        address.forEach { char ->
            append(
                when (char) {
                    in '۰'..'۹' -> ('0'.code + (char.code - '۰'.code)).toChar()
                    in '٠'..'٩' -> ('0'.code + (char.code - '٠'.code)).toChar()
                    else -> char
                }
            )
        }
    }

    val digits = normalizedDigits.filter { it in '0'..'9' }

    return when {
        digits.length == 15 && digits.startsWith("00980") -> digits.substring(4)
        digits.length == 14 && digits.startsWith("0098") -> "0" + digits.substring(4)
        digits.length == 13 && digits.startsWith("980") -> digits.substring(2)
        digits.length == 12 && digits.startsWith("98") -> "0" + digits.substring(2)
        digits.length == 11 && digits.startsWith("0") -> digits
        digits.length == 10 && digits.startsWith("9") -> "0$digits"
        else -> normalizedDigits.trim()
    }
}
