package com.moez.QKSMS.feature.smart.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.moez.QKSMS.R

/**
 * The Spam tab's own swipe: dragging a conversation to the right marks its sender "not spam".
 *
 * While the row moves, the space it uncovers shows what will happen — a green band with a
 * person-check glyph and the words "Not spam". Other tabs keep the swipe actions from settings.
 */
class SpamSwipeCallback(
    context: Context,
    private val onNotSpam: (conversationId: Long) -> Unit
) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.RIGHT) {

    /** Lets the row slide back if the list could not remove it straight away. */
    var adapter: RecyclerView.Adapter<*>? = null

    private val density = context.resources.displayMetrics.density
    private val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.notSpam)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.white)
        textSize = 15 * context.resources.displayMetrics.scaledDensity
        typeface = try {
            ResourcesCompat.getFont(context, R.font.dubai_bold)
        } catch (e: Exception) {
            null
        } ?: Typeface.DEFAULT_BOLD
    }
    private val label = context.getString(R.string.spam_not_spam)
    private val icon = ContextCompat.getDrawable(context, R.drawable.ic_user_check_24dp)?.mutate()?.apply {
        setTint(ContextCompat.getColor(context, R.color.white))
    }
    private val iconSize = (22 * density).toInt()
    private val padding = 20 * density
    private val gap = 8 * density
    private val band = RectF()

    override fun getSwipeDirs(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
        // Headers (frequent contacts) are not conversations
        if (viewHolder.itemViewType < 0) return 0
        return super.getSwipeDirs(recyclerView, viewHolder)
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean = false

    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && dX > 0) {
            val item = viewHolder.itemView
            band.set(item.left.toFloat(), item.top.toFloat(), item.left + dX, item.bottom.toFloat())
            c.drawRect(band, background)

            // Clip so the glyph and label are revealed by the row rather than drawn over it
            c.save()
            c.clipRect(band)
            val centerY = (item.top + item.bottom) / 2f
            val iconLeft = (item.left + padding).toInt()
            icon?.setBounds(iconLeft, (centerY - iconSize / 2).toInt(), iconLeft + iconSize, (centerY + iconSize / 2).toInt())
            icon?.draw(c)
            val baseline = centerY - (labelPaint.descent() + labelPaint.ascent()) / 2
            c.drawText(label, iconLeft + iconSize + gap, baseline, labelPaint)
            c.restore()
        }
        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        val id = viewHolder.itemId
        val position = viewHolder.adapterPosition
        onNotSpam(id)
        // If the row is still there (the sender could not be read), let it slide back
        if (position != RecyclerView.NO_POSITION) {
            viewHolder.itemView.post {
                val list = adapter ?: return@post
                if (position < list.itemCount && list.getItemId(position) == id) {
                    list.notifyItemChanged(position)
                }
            }
        }
    }
}
