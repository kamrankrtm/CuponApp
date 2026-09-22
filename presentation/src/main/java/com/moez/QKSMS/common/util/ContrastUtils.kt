package com.moez.QKSMS.common.util

/**
 * Keeps text legible against whatever background a theme ends up producing.
 *
 * The list adapters used to guard readability with
 * `if (isDarkBackground && calculateLuminance(textColor) < 0.35) useWhite()`. That test is
 * wrong in a way that only shows up on dark themes: `calculateLuminance` ignores the alpha
 * channel, so a translucent colour is judged by the colour it would be at full opacity. A
 * half-transparent dark grey reads as "light enough" and is left alone, and what the user sees
 * is grey-on-grey.
 *
 * These helpers composite first and then measure, which is what the eye does.
 *
 * Pure Kotlin, so the arithmetic can be unit-tested without a device.
 */
object ContrastUtils {

    /** WCAG AA for body text. */
    const val MIN_CONTRAST_BODY = 4.5

    /** WCAG AA for large or bold text. */
    const val MIN_CONTRAST_LARGE = 3.0

    private fun alpha(color: Int): Int = (color ushr 24) and 0xFF
    private fun red(color: Int): Int = (color shr 16) and 0xFF
    private fun green(color: Int): Int = (color shr 8) and 0xFF
    private fun blue(color: Int): Int = color and 0xFF

    private fun argb(a: Int, r: Int, g: Int, b: Int): Int =
        (a and 0xFF shl 24) or (r and 0xFF shl 16) or (g and 0xFF shl 8) or (b and 0xFF)

    /**
     * Flattens a translucent [foreground] onto an opaque [background].
     *
     * This is the colour actually painted on screen, and the only one worth measuring.
     */
    fun composite(foreground: Int, background: Int): Int {
        val a = alpha(foreground) / 255.0
        if (a >= 1.0) return foreground or (0xFF shl 24)
        val r = (red(foreground) * a + red(background) * (1 - a)).toInt()
        val g = (green(foreground) * a + green(background) * (1 - a)).toInt()
        val b = (blue(foreground) * a + blue(background) * (1 - a)).toInt()
        return argb(0xFF, r, g, b)
    }

    /** Relative luminance as WCAG defines it, on the opaque colour. */
    fun relativeLuminance(color: Int): Double {
        fun channel(value: Int): Double {
            val c = value / 255.0
            return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(red(color)) + 0.7152 * channel(green(color)) + 0.0722 * channel(blue(color))
    }

    /** Contrast ratio between two colours, 1.0 (identical) to 21.0 (black on white). */
    fun contrastRatio(foreground: Int, background: Int): Double {
        val fg = composite(foreground, background)
        val bg = background or (0xFF shl 24)
        val l1 = relativeLuminance(fg)
        val l2 = relativeLuminance(bg)
        val lighter = Math.max(l1, l2)
        val darker = Math.min(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    /**
     * The colour to paint list text in, given the background it sits on.
     *
     * On a dark background this is plain white, unconditionally. Clearing 4.5:1 is not the
     * same as looking white: the dark themes here specify 80% white for secondary text, which
     * passes comfortably and still reads as grey. Where the theme's own shade is wanted,
     * [ensureContrast] keeps it and only lifts it when it is genuinely unreadable.
     *
     * On a light background the theme's colour is kept while it is readable, because a light
     * theme's grey secondary text on white is both legible and intended.
     */
    fun ensureReadable(foreground: Int, background: Int, minRatio: Double = MIN_CONTRAST_BODY): Int {
        val backgroundIsDark = relativeLuminance(background or (0xFF shl 24)) < 0.5
        if (backgroundIsDark) return 0xFFFFFFFF.toInt()
        if (contrastRatio(foreground, background) >= minRatio) return foreground
        return 0xFF000000.toInt()
    }

    /**
     * Returns [foreground] when it is readable on [background], otherwise the nearest tint of
     * white or black that is.
     *
     * The replacement keeps the hierarchy the design intended: it steps the opacity of plain
     * white (or black, on a light background) up until the ratio is met, rather than jumping
     * straight to full white and flattening secondary text into primary.
     */
    fun ensureContrast(foreground: Int, background: Int, minRatio: Double = MIN_CONTRAST_BODY): Int {
        if (contrastRatio(foreground, background) >= minRatio) return foreground

        val backgroundIsDark = relativeLuminance(background or (0xFF shl 24)) < 0.5
        val base = if (backgroundIsDark) 0xFFFFFF else 0x000000

        // Walk up in eighths; the first step that clears the bar keeps text as soft as possible.
        for (step in 4..8) {
            val candidate = argb(step * 255 / 8, (base shr 16) and 0xFF, (base shr 8) and 0xFF, base and 0xFF)
            if (contrastRatio(candidate, background) >= minRatio) return candidate
        }

        return argb(0xFF, (base shr 16) and 0xFF, (base shr 8) and 0xFF, base and 0xFF)
    }
}
