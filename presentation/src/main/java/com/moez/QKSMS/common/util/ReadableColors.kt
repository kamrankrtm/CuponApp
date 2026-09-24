package com.moez.QKSMS.common.util

import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils

/** Colour adjustments that keep text on coloured surfaces readable. */
object ReadableColors {

    /**
     * Darkens [color] until white text on it reaches [minContrast] (WCAG AA is 4.5:1), keeping
     * its hue, so a bright brand colour can still fill a card that carries white text.
     */
    @ColorInt
    fun fillForWhiteText(@ColorInt color: Int, minContrast: Double = 4.5): Int {
        var fill = ColorUtils.setAlphaComponent(color, 255)
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(fill, hsl)
        var steps = 0
        while (ColorUtils.calculateContrast(Color.WHITE, fill) < minContrast && hsl[2] > 0.05f && steps < 40) {
            hsl[2] = (hsl[2] - 0.02f).coerceAtLeast(0f)
            fill = ColorUtils.HSLToColor(hsl)
            steps++
        }
        return fill
    }
}
