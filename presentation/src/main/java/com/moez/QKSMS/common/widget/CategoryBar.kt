package com.moez.QKSMS.common.widget

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.transition.ChangeBounds
import android.transition.Fade
import android.transition.TransitionManager
import android.transition.TransitionSet
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.widget.TextViewCompat
import com.moez.QKSMS.R
import com.moez.QKSMS.common.util.extensions.resolveThemeColor

/**
 * The inbox's category switcher, after the category buttons in the system Mail app.
 *
 * The selected category is a capsule in its own colour showing its glyph and name; the others
 * shrink to a grey capsule with just the glyph. Changing the selection animates the widths.
 */
class CategoryBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : HorizontalScrollView(context, attrs) {

    data class Category(val label: String, @DrawableRes val icon: Int, @ColorInt val color: Int)

    /** Called with the chosen position, and whether it was already selected. */
    var onCategorySelected: ((position: Int, reselected: Boolean) -> Unit)? = null

    var selectedIndex = -1
        private set

    private val row = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    private val chips = ArrayList<Chip>()
    private var categories: List<Category> = emptyList()

    private val density = resources.displayMetrics.density
    private fun dp(value: Int) = (value * density).toInt()

    private val idleFill = context.resolveThemeColor(R.attr.bubbleColor)
    private val idleGlyph = context.resolveThemeColor(android.R.attr.textColorSecondary)
    private val ripple = context.resolveThemeColor(R.attr.colorControlHighlight, Color.argb(40, 0, 0, 0))

    private inner class Chip(val root: LinearLayout, val icon: ImageView, val label: AppCompatTextView, val count: AppCompatTextView)

    init {
        isHorizontalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
        clipToPadding = false
        addView(row, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
    }

    fun setCategories(list: List<Category>) {
        categories = list
        row.removeAllViews()
        chips.clear()
        list.forEachIndexed { index, category ->
            val chip = buildChip(category)
            chip.root.setOnClickListener { select(index, notify = true) }
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(40))
            if (index > 0) params.marginStart = dp(8)
            row.addView(chip.root, params)
            chips += chip
        }
        selectedIndex = -1
    }

    fun select(index: Int, notify: Boolean = false) {
        if (index !in categories.indices) return
        val reselected = index == selectedIndex
        if (!reselected) {
            if (selectedIndex >= 0) {
                TransitionManager.beginDelayedTransition(row, TransitionSet()
                        .addTransition(ChangeBounds())
                        .addTransition(Fade())
                        .setDuration(180))
            }
            selectedIndex = index
            chips.forEachIndexed { i, chip -> style(chip, categories[i], i == index) }
            scrollToChip(index)
        }
        if (notify) onCategorySelected?.invoke(index, reselected)
    }

    /** A small count on the selected capsule, e.g. how many discount codes are live. */
    fun setCount(index: Int, text: String?) {
        val chip = chips.getOrNull(index) ?: return
        chip.count.text = text
        chip.count.visibility = if (!text.isNullOrEmpty() && index == selectedIndex) View.VISIBLE else View.GONE
    }

    private fun buildChip(category: Category): Chip {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            minimumWidth = dp(46)
            contentDescription = category.label
            isClickable = true
            isFocusable = true
        }
        val icon = ImageView(context).apply {
            setImageResource(category.icon)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val label = AppCompatTextView(context).apply {
            text = category.label
            maxLines = 1
            TextViewCompat.setTextAppearance(this, R.style.TextAppearance_App_CategoryLabel)
            setTextColor(Color.WHITE)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val count = AppCompatTextView(context).apply {
            TextViewCompat.setTextAppearance(this, R.style.TextAppearance_App_CategoryCount)
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                setColor(Color.argb(56, 255, 255, 255))
            }
            setPadding(dp(8), 0, dp(8), 0)
            visibility = View.GONE
        }
        root.addView(icon, LinearLayout.LayoutParams(dp(19), dp(19)))
        root.addView(label, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            marginStart = dp(7)
        })
        root.addView(count, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            marginStart = dp(6)
        })
        return Chip(root, icon, label, count)
    }

    private fun style(chip: Chip, category: Category, selected: Boolean) {
        val fill = GradientDrawable().apply {
            cornerRadius = dp(20).toFloat()
            setColor(if (selected) category.color else idleFill)
        }
        chip.root.background = RippleDrawable(ColorStateList.valueOf(ripple), fill, null)
        chip.root.isSelected = selected
        chip.root.setPadding(if (selected) dp(12) else 0, 0, if (selected) dp(14) else 0, 0)
        chip.icon.imageTintList = ColorStateList.valueOf(if (selected) Color.WHITE else idleGlyph)
        chip.label.visibility = if (selected) View.VISIBLE else View.GONE
        chip.count.visibility = if (selected && !chip.count.text.isNullOrEmpty()) View.VISIBLE else View.GONE
    }

    private fun scrollToChip(index: Int) {
        post {
            val chip = chips.getOrNull(index)?.root ?: return@post
            val left = chip.left - dp(16)
            val right = chip.right + dp(16) - width
            when {
                left < scrollX -> smoothScrollTo(left, 0)
                right > scrollX -> smoothScrollTo(right, 0)
            }
        }
    }

    companion object {
        /** Resolves a dimension written in dp, for callers laying the bar out in code. */
        fun dp(context: Context, value: Float): Int = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, context.resources.displayMetrics).toInt()
    }
}
