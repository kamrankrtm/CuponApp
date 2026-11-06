package com.moez.QKSMS.feature.design

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.EditText
import com.google.android.material.appbar.AppBarLayout
import com.moez.QKSMS.R
import com.moez.QKSMS.common.widget.CategoryBar

/**
 * Six independently composed experiences; this is not the legacy color picker.
 * The inbox, send and receive logic and message database are deliberately shared.
 */
enum class Experience(
    val title: String,
    val description: String,
    val headline: String,
    val accent: Int,
    val surface: Int,
    val ink: Int
) {
    MICROSOFT("Microsoft", "Fluent Workspace", "مرکز پیام‌ها", Color.rgb(38, 99, 210), Color.rgb(236, 244, 255), Color.rgb(24, 49, 88)),
    APPLE("Apple", "Pure iOS", "پیام‌ها", Color.rgb(38, 91, 169), Color.rgb(247, 249, 252), Color.rgb(29, 43, 61)),
    AVATR("AVATR", "Future Cockpit", "SMS PRO / COMMAND", Color.rgb(166, 142, 255), Color.rgb(27, 24, 48), Color.WHITE),
    PINTEREST("Pinterest", "Visual Boards", "دنیای پیام‌های من", Color.rgb(210, 43, 61), Color.rgb(255, 244, 242), Color.rgb(93, 35, 46)),
    TELEGRAM("Telegram", "Fast Messenger", "smsPRO", Color.rgb(43, 156, 216), Color.rgb(235, 247, 255), Color.rgb(25, 68, 94)),
    META("Meta", "Social Flow", "ارتباطات", Color.rgb(104, 84, 224), Color.rgb(244, 240, 255), Color.rgb(59, 47, 120));

    val darkHeader: Boolean get() = this == AVATR
    val usesOriginalCategoryBar: Boolean get() = this == APPLE || this == TELEGRAM
}

object ExperienceStore {
    private const val PREFS = "smspro_experience_v1"
    private const val KEY = "selected_experience"

    fun get(context: Context): Experience {
        val id = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, Experience.TELEGRAM.name)
        return Experience.values().firstOrNull { it.name == id } ?: Experience.TELEGRAM
    }

    fun set(context: Context, experience: Experience) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, experience.name).apply()
    }

    fun dp(context: Context, size: Int): Int =
        (context.resources.displayMetrics.density * size + 0.5f).toInt()

    fun shape(color: Int, radius: Int, strokeColor: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius.toFloat()
            if (strokeColor != null) setStroke(2, strokeColor)
        }
}

/**
 * A real navigation-composition swap on the existing, stable inbox activity.
 * The category navigator below is wired to CategoryBar.select(), preserving its
 * backing classifier, search, unread state and each page's existing adapter.
 */
class ExperienceInbox(private val activity: Activity) {
    val experience: Experience = ExperienceStore.get(activity)
    private val categoryBar: CategoryBar = activity.findViewById(R.id.categoryBar)
    private val appBar: AppBarLayout = activity.findViewById(R.id.mainAppBar)
    private val largeTitle: TextView = activity.findViewById(R.id.largeTitle)
    private val floatingBar: LinearLayout = activity.findViewById(R.id.floatingBar)
    private val compose: ImageView = activity.findViewById(R.id.compose)
    private val search: EditText = activity.findViewById(R.id.toolbarSearch)
    private val header = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        layoutDirection = View.LAYOUT_DIRECTION_RTL
        setPadding(dp(14), dp(4), dp(14), dp(9))
    }
    private val indicators = ArrayList<Pair<Int, View>>()

    fun install() {
        if (header.parent == null) {
            val params = AppBarLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { scrollFlags = AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL }
            appBar.addView(header, 2, params)
        }
        largeTitle.text = experience.headline
        largeTitle.setTextColor(if (experience.darkHeader) experience.accent else experience.ink)
        largeTitle.textSize = when (experience) {
            Experience.AVATR -> 25f
            Experience.MICROSOFT -> 31f
            Experience.PINTEREST -> 31f
            Experience.META -> 32f
            Experience.APPLE -> 36f
            Experience.TELEGRAM -> 29f
        }
        floatingBar.setPadding(dp(14), dp(7), dp(14), dp(13))
        compose.backgroundTintList = ColorStateList.valueOf(
            if (experience == Experience.AVATR) Color.rgb(94, 70, 169) else experience.accent
        )
        search.hint = when (experience) {
            Experience.MICROSOFT -> "جستجوی پیام، مخاطب یا تراکنش"
            Experience.APPLE -> "جستجو"
            Experience.AVATR -> "SEARCH / جستجوی هوشمند"
            Experience.PINTEREST -> "در پیام‌ها و کدهای تخفیف بگردید"
            Experience.TELEGRAM -> "جستجوی پیام‌ها"
            Experience.META -> "جستجو در ارتباطات"
        }
        header.removeAllViews()
        indicators.clear()
        when (experience) {
            Experience.MICROSOFT -> microsoft()
            Experience.APPLE -> apple()
            Experience.AVATR -> avatr()
            Experience.PINTEREST -> pinterest()
            Experience.TELEGRAM -> telegram()
            Experience.META -> meta()
        }
        refresh(categoryBar.selectedIndex, true)
    }

    fun refresh(selected: Int, inboxVisible: Boolean) {
        header.visibility = if (inboxVisible) View.VISIBLE else View.GONE
        categoryBar.visibility = if (inboxVisible && experience.usesOriginalCategoryBar) View.VISIBLE else View.GONE
        indicators.forEach { (position, view) ->
            val selectedNow = position == selected
            view.alpha = if (selectedNow) 1f else 0.72f
            view.isSelected = selectedNow
            if (view is TextView) view.setTypeface(null, if (selectedNow) Typeface.BOLD else Typeface.NORMAL)
            val baseColor = if (experience == Experience.AVATR) Color.rgb(53, 44, 86) else Color.WHITE
            val activeColor = if (experience == Experience.AVATR) Color.rgb(90, 71, 151)
                else blend(experience.surface, experience.accent, 0.17f)
            view.background = ExperienceStore.shape(if (selectedNow) activeColor else baseColor, dp(15),
                if (selectedNow) experience.accent else null)
        }
    }

    private fun microsoft() {
        heading("کار امروز", "میان‌برهای سریع و پیام‌های اولویت‌دار")
        val groups = listOf(listOf(0, 1, 2), listOf(3, 4, 5))
        groups.forEach { positions ->
            val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
            positions.forEach { idx ->
                val item = shortcut(idx, compact = false)
                row.addView(item, LinearLayout.LayoutParams(0, dp(61), 1f).apply {
                    setMargins(dp(3), dp(3), dp(3), dp(3))
                })
            }
            header.addView(row)
        }
    }

    private fun apple() {
        val line = label("گفتگوها، بانک، کدها و تخفیف‌ها در یک جا", 12, Color.GRAY)
        line.setPadding(dp(5), 0, dp(5), dp(6))
        header.addView(line)
    }

    private fun avatr() {
        header.background = ExperienceStore.shape(experience.surface, dp(24))
        val status = label("SYSTEM ONLINE   •   SMS INTELLIGENCE", 10, experience.accent)
        status.gravity = Gravity.CENTER
        header.addView(status)
        val console = label("✦   مرکز کنترل هوشمند   ✦", 19, Color.WHITE)
        console.gravity = Gravity.CENTER
        console.setPadding(0, dp(12), 0, dp(13))
        header.addView(console)
        val dock = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        (0..5).forEach { idx ->
            dock.addView(shortcut(idx, compact = true),
                LinearLayout.LayoutParams(0, dp(58), 1f).apply {
                    setMargins(dp(2), 0, dp(2), 0)
                })
        }
        header.addView(dock)
    }

    private fun pinterest() {
        heading("منتخب پیام‌ها", "هر دسته یک مجموعه مستقل")
        listOf(listOf(4, 3, 2), listOf(1, 0, 5)).forEachIndexed { rowIndex, positions ->
            val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
            positions.forEachIndexed { column, idx ->
                val height = if ((rowIndex + column) % 2 == 0) 72 else 58
                row.addView(shortcut(idx, compact = false),
                    LinearLayout.LayoutParams(0, dp(height), 1f).apply {
                        setMargins(dp(3), dp(3), dp(3), dp(3))
                    })
            }
            header.addView(row)
        }
    }

    private fun telegram() {
        val subtitle = label("پیام‌رسان سریع · پوشه‌های هوشمند", 12, experience.accent)
        subtitle.setPadding(dp(4), 0, dp(4), dp(4))
        header.addView(subtitle)
    }

    private fun meta() {
        heading("افراد و فعالیت‌های شما", "دسترسی سریع به مهم‌ترین ارتباطات")
        val scroll = HorizontalScrollView(activity).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        (0..5).forEach { idx ->
            val item = shortcut(idx, compact = false)
            row.addView(item, LinearLayout.LayoutParams(dp(90), dp(76)).apply {
                marginEnd = dp(7)
            })
        }
        scroll.addView(row)
        header.addView(scroll)
    }

    private fun heading(title: String, description: String) {
        val titleView = label(title, 17, experience.ink).apply {
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(4), dp(5), 0, dp(3))
        }
        val caption = label(description, 11, if (experience.darkHeader) Color.LTGRAY else Color.GRAY)
            .apply { setPadding(dp(4), 0, 0, dp(9)) }
        header.addView(titleView)
        header.addView(caption)
    }

    private fun shortcut(index: Int, compact: Boolean): View {
        val labels = arrayOf("همه", "شخصی", "بانک", "کد ورود", "تخفیف", "اسپم")
        val glyphs = intArrayOf(
            R.drawable.ic_lc_inbox, R.drawable.ic_lc_user,
            R.drawable.ic_lc_landmark, R.drawable.ic_lc_key,
            R.drawable.ic_lc_ticket, R.drawable.ic_lc_spam
        )
        val button = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            contentDescription = "نمایش " + labels[index]
            setOnClickListener { categoryBar.select(index, notify = true) }
        }
        val glyph = ImageView(activity).apply {
            setImageResource(glyphs[index])
            imageTintList = ColorStateList.valueOf(
                if (experience.darkHeader) Color.WHITE else experience.accent
            )
        }
        button.addView(glyph, LinearLayout.LayoutParams(dp(if (compact) 17 else 21),
            dp(if (compact) 17 else 21)))
        button.addView(label(labels[index], if (compact) 9 else 11,
            if (experience.darkHeader) Color.WHITE else experience.ink).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(5), 0, 0)
            })
        button.background = ExperienceStore.shape(
            if (experience.darkHeader) Color.rgb(53, 44, 86) else Color.WHITE,
            dp(if (experience == Experience.PINTEREST) 22 else 15)
        )
        indicators.add(index to button)
        return button
    }

    private fun label(value: String, size: Int, color: Int): TextView =
        TextView(activity).apply {
            text = value
            textSize = size.toFloat()
            setTextColor(color)
            gravity = Gravity.CENTER_VERTICAL
            includeFontPadding = false
        }

    private fun dp(v: Int) = ExperienceStore.dp(activity, v)

    private fun blend(a: Int, b: Int, weight: Float): Int {
        val t = weight.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(a) * (1f - t) + Color.red(b) * t).toInt(),
            (Color.green(a) * (1f - t) + Color.green(b) * t).toInt(),
            (Color.blue(a) * (1f - t) + Color.blue(b) * t).toInt()
        )
    }
}

/** Theme selection is additive: the existing color/night settings remain intact. */
object ExperienceSettings {
    fun install(activity: Activity, settingsContainer: LinearLayout) {
        if (settingsContainer.findViewWithTag<View>("experience_selector") != null) return
        val ctx = activity
        val dp = { i: Int -> ExperienceStore.dp(ctx, i) }
        val group = LinearLayout(ctx).apply {
            tag = "experience_selector"
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(18))
        }
        group.addView(TextView(ctx).apply {
            text = "سبک طراحی برنامه"
            textSize = 20f
            setTextColor(ExperienceStore.get(ctx).accent)
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(6), dp(4), 0, dp(6))
        })
        group.addView(TextView(ctx).apply {
            text = "شش رابط مستقل برای پیام‌ها، کدها و اعلان‌ها"
            textSize = 13f
            setPadding(dp(6), 0, 0, dp(12))
        })
        Experience.values().toList().chunked(2).forEach { rowStyles ->
            val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
            rowStyles.forEach { style ->
                val active = ExperienceStore.get(ctx) == style
                val card = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(12), dp(11), dp(9), dp(11))
                    background = ExperienceStore.shape(style.surface, dp(16),
                        if (active) style.accent else null)
                    isClickable = true
                    isFocusable = true
                    contentDescription = "انتخاب تم " + style.title
                    setOnClickListener {
                        if (ExperienceStore.get(ctx) != style) {
                            ExperienceStore.set(ctx, style)
                            activity.recreate()
                        }
                    }
                }
                card.addView(View(ctx).apply {
                    background = ExperienceStore.shape(style.accent, dp(5))
                }, LinearLayout.LayoutParams(dp(42), dp(5)))
                card.addView(TextView(ctx).apply {
                    text = style.title + if (active) " ✓" else ""
                    textSize = 16f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(if (style.darkHeader) style.accent else style.ink)
                    setPadding(0, dp(9), 0, dp(3))
                })
                card.addView(TextView(ctx).apply {
                    text = style.description
                    textSize = 11f
                    setTextColor(if (style.darkHeader) Color.LTGRAY else Color.DKGRAY)
                })
                row.addView(card, LinearLayout.LayoutParams(0, dp(94), 1f).apply {
                    setMargins(dp(4), dp(4), dp(4), dp(4))
                })
            }
            group.addView(row)
        }
        settingsContainer.addView(group, 0)
    }
}

/** Conversation composer shares the selected design without touching SMS transport. */
object ExperienceConversation {
    fun apply(activity: Activity) {
        val style = ExperienceStore.get(activity)
        val toolbar = activity.findViewById<View>(R.id.toolbar) ?: return
        val inputBox = activity.findViewById<View>(R.id.messageBackground)
        toolbar.setBackgroundColor(if (style.darkHeader) style.surface else style.surface)
        inputBox?.backgroundTintList = ColorStateList.valueOf(
            if (style.darkHeader) Color.rgb(65, 53, 109) else style.surface
        )
    }
}
