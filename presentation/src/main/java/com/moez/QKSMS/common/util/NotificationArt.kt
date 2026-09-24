package com.moez.QKSMS.common.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.moez.QKSMS.R

/**
 * Large icons for notifications, drawn the way the app draws avatars: people are grey
 * monogram circles, businesses rounded tiles in their colour. The shape alone tells a
 * message from a person apart from one sent by a company.
 */
object NotificationArt {

    private const val SIZE_DP = 64

    /** A brand's tile: its colour (darkened until white reads on it) with its initial. */
    fun brandTile(context: Context, @ColorInt color: Int, letter: String): Bitmap {
        val (bitmap, canvas, size) = canvas(context)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        fill.color = ReadableColors.fillForWhiteText(color)
        canvas.drawRoundRect(RectF(0f, 0f, size, size), size * 0.27f, size * 0.27f, fill)
        drawCentredText(context, canvas, letter.take(1).toUpperCase(), size, size * 0.42f)
        return bitmap
    }

    /** A tile with a white glyph, for senders known only by their kind (a bank, say). */
    fun glyphTile(context: Context, @ColorInt color: Int, @DrawableRes glyph: Int): Bitmap {
        val (bitmap, canvas, size) = canvas(context)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        fill.color = color
        canvas.drawRoundRect(RectF(0f, 0f, size, size), size * 0.27f, size * 0.27f, fill)
        ContextCompat.getDrawable(context, glyph)?.mutate()?.let { drawable ->
            val inset = (size * 0.26f).toInt()
            drawable.setBounds(inset, inset, size.toInt() - inset, size.toInt() - inset)
            drawable.setTint(ContextCompat.getColor(context, R.color.white))
            drawable.draw(canvas)
        }
        return bitmap
    }

    /** A person without a photo: their initials on the soft grey gradient avatars use. */
    fun monogram(context: Context, name: String?): Bitmap? {
        val initials = name.orEmpty()
                .split(" ")
                .filter { part -> part.isNotEmpty() && part[0].isLetterOrDigit() }
                .map { part -> part[0].toString() }
                .let { list -> if (list.size > 1) list.first() + list.last() else list.firstOrNull() }
                ?: return null
        val (bitmap, canvas, size) = canvas(context)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        fill.shader = LinearGradient(0f, 0f, 0f, size,
                ContextCompat.getColor(context, R.color.avatarTop),
                ContextCompat.getColor(context, R.color.avatarBottom), Shader.TileMode.CLAMP)
        canvas.drawOval(RectF(0f, 0f, size, size), fill)
        drawCentredText(context, canvas, initials.toUpperCase(), size, size * 0.38f)
        return bitmap
    }

    private data class Surface(val bitmap: Bitmap, val canvas: Canvas, val size: Float)

    private fun canvas(context: Context): Surface {
        val px = (SIZE_DP * context.resources.displayMetrics.density).toInt()
        val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        return Surface(bitmap, Canvas(bitmap), px.toFloat())
    }

    private fun drawCentredText(context: Context, canvas: Canvas, text: String, size: Float, textSize: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.white)
            this.textSize = textSize
            textAlign = Paint.Align.CENTER
            typeface = try {
                ResourcesCompat.getFont(context, R.font.dubai_bold)
            } catch (e: Exception) {
                null
            } ?: Typeface.DEFAULT_BOLD
        }
        val baseline = size / 2 - (paint.descent() + paint.ascent()) / 2
        canvas.drawText(text, size / 2, baseline, paint)
    }
}
