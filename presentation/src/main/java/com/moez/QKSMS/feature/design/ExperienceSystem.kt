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
    var onOpenConversation: (Long) -> Unit = {}
    var onAlarmSettings: () -> Unit = {}

    private val nativeBar: CategoryBar = activity.findViewById(R.id.categoryBar)
    private val screen = ExperienceShell(
        activity, experience,
        onCategory = { nativeBar.select(it, notify = true) },
        onAlarm = { onAlarmSettings() },
        onOpenThread = { onOpenConversation(it) }
    )

    fun install() = screen.install()

    fun refresh(category: Int, inboxVisible: Boolean) = screen.refresh(category, inboxVisible)

    fun updateData(contacts: List<ExperienceContact>, counters: ExperienceCounts) {
        screen.update(contacts, counters)
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
    fun apply(activity: Activity) = ConversationExperienceShell.apply(activity)
}
