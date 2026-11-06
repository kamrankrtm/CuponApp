package com.moez.QKSMS.feature.design

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.google.android.material.appbar.AppBarLayout
import com.moez.QKSMS.R
import com.moez.QKSMS.common.widget.CategoryBar
import kotlin.math.min

data class ExperienceContact(val threadId: Long, val title: String, val unread: Boolean)
data class ExperienceCounts(val conversations: Int, val unread: Int, val banks: Int, val codes: Int, val discounts: Int)

/**
 * Real Android view hierarchies: six page architectures sharing only the SMS engine.
 * Workspace rail / minimal iOS / automotive cockpit / visual pinboard /
 * dense Telegram / contact-first social navigation.
 */
class ExperienceShell(
    private val activity: Activity,
    val design: Experience,
    private val onCategory: (Int) -> Unit,
    private val onAlarm: () -> Unit,
    private val onOpenThread: (Long) -> Unit
) {
    private val appBar: AppBarLayout = activity.findViewById(R.id.mainAppBar)
    private val content: LinearLayout = activity.findViewById(R.id.mainContent)
    private val coordinator: CoordinatorLayout = activity.findViewById(R.id.mainCoordinator)
    private val toolbar: View = activity.findViewById(R.id.toolbar)
    private val title: TextView = activity.findViewById(R.id.largeTitle)
    private val categories: CategoryBar = activity.findViewById(R.id.categoryBar)
    private val search: EditText = activity.findViewById(R.id.toolbarSearch)
    private val floatingBar: LinearLayout = activity.findViewById(R.id.floatingBar)
    private val bottomArea: LinearLayout = activity.findViewById(R.id.bottomArea)
    private val compose: ImageView = activity.findViewById(R.id.compose)
    private val recycler: RecyclerView = activity.findViewById(R.id.recyclerView)
    private val header = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        layoutDirection = View.LAYOUT_DIRECTION_RTL
        setPadding(dp(14), dp(5), dp(14), dp(10))
    }
    private var topSearch: LinearLayout? = null
    private var peopleHost: LinearLayout? = null
    private var originalLayoutManager: RecyclerView.LayoutManager? = null
    private var people: List<ExperienceContact> = emptyList()
    private var counts = ExperienceCounts(0, 0, 0, 0, 0)
    private val indicators = ArrayList<Pair<Int, View>>()
    private val metrics = ArrayList<Pair<TextView, (ExperienceCounts) -> String>>()
    private val names = arrayOf("همه", "شخصی", "بانک", "رمزها", "تخفیف", "اسپم")
    private val icons = intArrayOf(R.drawable.ic_lc_inbox, R.drawable.ic_lc_user,
        R.drawable.ic_lc_landmark, R.drawable.ic_lc_key, R.drawable.ic_lc_ticket, R.drawable.ic_lc_spam)

    fun install() {
        originalLayoutManager = recycler.layoutManager
        appBar.addView(header, 2, AppBarLayout.LayoutParams(-1, -2).apply {
            scrollFlags = AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL
        })
        title.text = design.headline
        title.textSize = when (design) {
            Experience.APPLE -> 36f
            Experience.META -> 32f
            Experience.PINTEREST -> 31f
            Experience.AVATR -> 24f
            Experience.MICROSOFT -> 27f
            Experience.TELEGRAM -> 29f
        }
        title.setTextColor(design.ink)
        compose.backgroundTintList = ColorStateList.valueOf(design.accent)
        when (design) {
            Experience.MICROSOFT -> microsoft()
            Experience.APPLE -> apple()
            Experience.AVATR -> avatr()
            Experience.PINTEREST -> pinterest()
            Experience.TELEGRAM -> telegram()
            Experience.META -> meta()
        }
        refresh(categories.selectedIndex, true)
    }

    fun refresh(index: Int, inboxVisible: Boolean) {
        header.visibility = if (inboxVisible) View.VISIBLE else View.GONE
        categories.visibility = if (inboxVisible &&
            (design == Experience.APPLE || design == Experience.TELEGRAM)) View.VISIBLE else View.GONE
        title.visibility = if (inboxVisible &&
            (design == Experience.APPLE || design == Experience.MICROSOFT || design == Experience.TELEGRAM))
            View.VISIBLE else View.GONE
        topSearch?.visibility = if (search.visibility == View.VISIBLE) View.VISIBLE else View.GONE
        indicators.forEach { (category, view) ->
            view.isSelected = category == index
            view.alpha = if (category == index) 1f else 0.66f
            view.scaleX = if (category == index) 1f else 0.95f
            view.scaleY = view.scaleX
        }
        if (design == Experience.PINTEREST) {
            val shouldGrid = inboxVisible && index == 4
            val isGrid = recycler.layoutManager is StaggeredGridLayoutManager
            if (shouldGrid && !isGrid) {
                recycler.layoutManager =
                    StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
            } else if (!shouldGrid && isGrid) {
                recycler.layoutManager = originalLayoutManager ?: LinearLayoutManager(activity)
            }
        }
        if (design == Experience.AVATR) recycler.setBackgroundColor(Color.rgb(23, 22, 39))
    }

    fun update(recent: List<ExperienceContact>, newCounts: ExperienceCounts) {
        counts = newCounts
        metrics.forEach { (view, show) -> view.text = show(newCounts) }
        if (recent != people) {
            people = recent
            peopleHost?.let { populatePeople(it) }
        }
    }

    private fun microsoft() {
        // Permanently move the CoordinatorLayout into a real two-column workspace.
        (coordinator.parent as ViewGroup).removeView(coordinator)
        val workspace = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        content.addView(workspace, LinearLayout.LayoutParams(-1, 0, 1f))
        val rail = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(3), dp(10), dp(3), 0)
            setBackgroundColor(Color.rgb(228, 238, 253))
        }
        workspace.addView(rail, LinearLayout.LayoutParams(dp(76), -1))
        workspace.addView(coordinator, LinearLayout.LayoutParams(0, -1, 1f))
        caption(rail, "WORK", 11, design.accent, true)
        (0..5).forEach { idx ->
            rail.addView(destination(idx, false, 21), LinearLayout.LayoutParams(dp(68), dp(65)).apply {
                topMargin = dp(4)
            })
        }
        val alarm = card("امنیت", "دزدگیر", R.drawable.ic_lc_shield, Color.WHITE, design.ink).apply {
            setOnClickListener { onAlarm() }
        }
        rail.addView(alarm, LinearLayout.LayoutParams(dp(68), dp(70)).apply { topMargin = dp(12) })
        heading("فضای کاری", "اقدام‌های ضروری در یک نگاه")
        val stats = row()
        stats.addView(metric("گفتگوها", { it.conversations.toString() }),
            LinearLayout.LayoutParams(0, dp(80), 1f).apply { marginEnd = dp(5) })
        stats.addView(metric("خوانده‌نشده", { it.unread.toString() }),
            LinearLayout.LayoutParams(0, dp(80), 1f))
        header.addView(stats)
        val actions = row().apply { setPadding(0, dp(8), 0, 0) }
        listOf(3, 4, 2).forEach { idx ->
            actions.addView(card(names[idx], "باز کردن", icons[idx], Color.WHITE, design.ink).apply {
                setOnClickListener { onCategory(idx) }
            }, LinearLayout.LayoutParams(0, dp(82), 1f).apply { marginEnd = dp(4) })
        }
        header.addView(actions)
    }

    private fun apple() {
        moveSearch()
        caption(header, "همه مکالمه‌ها در یک جا", 12, Color.GRAY, false)
        // System-style folders stay at the top, compose is the only bottom control.
        floatingBar.gravity = Gravity.END or Gravity.CENTER_VERTICAL
        floatingBar.setPadding(dp(15), 0, dp(18), dp(12))
        compose.layoutParams = LinearLayout.LayoutParams(dp(55), dp(55))
    }

    private fun avatr() {
        toolbar.setBackgroundColor(Color.rgb(29, 27, 49))
        header.background = ExperienceStore.shape(Color.rgb(29, 27, 49), dp(18))
        header.setPadding(dp(7), dp(10), dp(7), dp(10))
        caption(header, "AVATR   /   SMART MESSAGE COMMAND", 10, design.accent, true)
        val stage = FrameLayout(activity)
        header.addView(stage, LinearLayout.LayoutParams(-1, dp(274)))
        stage.addView(CommandRing(activity, design.accent),
            FrameLayout.LayoutParams(dp(182), dp(182), Gravity.CENTER))
        val places = listOf(
            Gravity.TOP or Gravity.START, Gravity.TOP or Gravity.END,
            Gravity.CENTER_VERTICAL or Gravity.START, Gravity.CENTER_VERTICAL or Gravity.END,
            Gravity.BOTTOM or Gravity.START, Gravity.BOTTOM or Gravity.END
        )
        (0..5).forEach { idx ->
            val button = destination(idx, true, 15)
            stage.addView(button, FrameLayout.LayoutParams(dp(104), dp(42), places[idx]).apply {
                topMargin = dp(12); bottomMargin = dp(12)
            })
        }
        val footer = row()
        footer.addView(card("ALARM", "کنترل دزدگیر", R.drawable.ic_lc_shield,
            Color.rgb(73, 58, 112), Color.WHITE).apply {
            setOnClickListener { onAlarm() }
        }, LinearLayout.LayoutParams(0, dp(73), 1f).apply { marginEnd = dp(6) })
        footer.addView(metric("پیام‌های جدید", { it.unread.toString() }),
            LinearLayout.LayoutParams(0, dp(73), 1f))
        header.addView(footer)
    }

    private fun pinterest() {
        moveSearch()
        heading("بردهای من", "پیشنهادها و کدها را تصویری مرور کن")
        val columns = row()
        val hero = card("TODAY'S PICKS", "کدهای تخفیف", R.drawable.ic_lc_ticket,
            Color.rgb(223, 65, 88), Color.WHITE).apply { setOnClickListener { onCategory(4) } }
        columns.addView(hero, LinearLayout.LayoutParams(0, dp(186), 1.2f).apply {
            marginEnd = dp(7)
        })
        val stack = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        stack.addView(card("CODES", "رمزهای ورود", R.drawable.ic_lc_key,
            Color.rgb(246, 216, 151), Color.rgb(77, 48, 32)).apply {
            setOnClickListener { onCategory(3) }
        }, LinearLayout.LayoutParams(-1, 0, 1f).apply { bottomMargin = dp(7) })
        stack.addView(card("MONEY", "بانک‌ها", R.drawable.ic_lc_landmark,
            Color.rgb(77, 131, 153), Color.WHITE).apply { setOnClickListener { onCategory(2) } },
            LinearLayout.LayoutParams(-1, 0, 1f))
        columns.addView(stack, LinearLayout.LayoutParams(0, dp(186), 0.9f))
        header.addView(columns)
        val collectionBar = row().apply { setPadding(0, dp(10), 0, 0) }
        listOf(0, 1, 5).forEach { idx ->
            collectionBar.addView(destination(idx, true, 18),
                LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginEnd = dp(4) })
        }
        header.addView(collectionBar)
        caption(header, "تخفیف‌ها در چیدمان دو ستونه نمایش داده می‌شوند.", 11,
            Color.rgb(137, 90, 99), false)
    }

    private fun telegram() {
        moveSearch()
        val pinned = row()
        peopleHost = pinned
        header.addView(pinned, LinearLayout.LayoutParams(-1, dp(91)))
        populatePeople(pinned)
        caption(header, "گفتگوهای اخیر  ·  پوشه‌های سریع", 12, design.accent, false)
        floatingBar.gravity = Gravity.END or Gravity.CENTER_VERTICAL
        compose.layoutParams = LinearLayout.LayoutParams(dp(52), dp(52))
    }

    private fun meta() {
        moveSearch()
        heading("مخاطبین من", "فعالیت‌ها و ارتباط‌های اخیر")
        val stories = row()
        peopleHost = stories
        val scroll = HorizontalScrollView(activity).apply { isHorizontalScrollBarEnabled = false }
        scroll.addView(stories)
        header.addView(scroll, LinearLayout.LayoutParams(-1, dp(102)))
        populatePeople(stories)
        val activityCard = row().apply {
            setPadding(dp(7), dp(7), dp(7), dp(7))
            background = ExperienceStore.shape(design.surface, dp(17))
        }
        activityCard.addView(metric("جدید", { it.unread.toString() }),
            LinearLayout.LayoutParams(0, dp(77), 1f).apply { marginEnd = dp(5) })
        activityCard.addView(metric("رمز ورود", { it.codes.toString() }),
            LinearLayout.LayoutParams(0, dp(77), 1f))
        header.addView(activityCard)

        // Six actual fixed bottom destinations rather than reusing the top capsule bar.
        val bottomTabs = row().apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(5), dp(4), dp(5))
            background = ExperienceStore.shape(Color.WHITE, dp(20))
            elevation = dp(5).toFloat()
        }
        (0..5).forEach { idx ->
            bottomTabs.addView(destination(idx, false, 19),
                LinearLayout.LayoutParams(0, dp(61), 1f))
        }
        bottomArea.addView(bottomTabs, 1, LinearLayout.LayoutParams(-1, dp(71)).apply {
            leftMargin = dp(8); rightMargin = dp(8); bottomMargin = dp(4)
        })
        floatingBar.gravity = Gravity.END or Gravity.CENTER_VERTICAL
        floatingBar.setPadding(dp(12), 0, dp(18), dp(3))
    }

    private fun moveSearch() {
        (search.parent as ViewGroup).removeView(search)
        val top = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(16), dp(3), dp(16), dp(7))
        }
        content.addView(top, 1, LinearLayout.LayoutParams(-1, -2))
        top.addView(search, LinearLayout.LayoutParams(-1, dp(48)))
        topSearch = top
    }

    private fun populatePeople(host: LinearLayout) {
        host.removeAllViews()
        if (people.isEmpty()) {
            caption(host, "مخاطبین پرتکرار پس از دریافت پیام اینجا دیده می‌شوند.", 12,
                Color.GRAY, false)
            return
        }
        people.take(7).forEach { person ->
            val column = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                isFocusable = true
                isClickable = true
                contentDescription = "باز کردن گفتگوی " + person.title
                setOnClickListener { onOpenThread(person.threadId) }
            }
            val avatar = TextView(activity).apply {
                text = person.title.trim().take(1).ifBlank { "•" }
                gravity = Gravity.CENTER
                textSize = 21f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
                background = ExperienceStore.shape(
                    if (person.unread) design.accent else Color.rgb(100, 122, 150), dp(30),
                    if (person.unread) Color.rgb(238, 173, 58) else null
                )
            }
            column.addView(avatar, LinearLayout.LayoutParams(dp(51), dp(51)))
            column.addView(TextView(activity).apply {
                text = person.title
                textSize = 10f
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setTextColor(design.ink)
                setPadding(0, dp(4), 0, 0)
            }, LinearLayout.LayoutParams(dp(69), dp(24)))
            host.addView(column, LinearLayout.LayoutParams(dp(76), -1))
        }
    }

    private fun metric(label: String, show: (ExperienceCounts) -> String): View {
        val wrapper = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(9), dp(9), dp(9))
            background = ExperienceStore.shape(
                if (design.darkHeader) Color.rgb(53, 44, 84) else Color.WHITE, dp(14))
        }
        val value = TextView(activity).apply {
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
            setTextColor(design.accent)
            text = show(counts)
        }
        metrics.add(value to show)
        wrapper.addView(value)
        caption(wrapper, label, 11, if (design.darkHeader) Color.LTGRAY else design.ink, false)
        return wrapper
    }

    private fun card(kicker: String, label: String, icon: Int, fill: Int, ink: Int): LinearLayout {
        val view = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(11), dp(8), dp(9), dp(8))
            background = ExperienceStore.shape(fill, dp(17))
            isClickable = true
            isFocusable = true
        }
        view.addView(ImageView(activity).apply {
            setImageResource(icon)
            imageTintList = ColorStateList.valueOf(ink)
        }, LinearLayout.LayoutParams(dp(24), dp(24)))
        caption(view, kicker, 10, ink, false)
        caption(view, label, 14, ink, true)
        return view
    }

    private fun destination(index: Int, horizontal: Boolean, iconSize: Int): LinearLayout {
        val view = LinearLayout(activity).apply {
            orientation = if (horizontal) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            contentDescription = "نمایش " + names[index]
            setOnClickListener { onCategory(index) }
            background = ExperienceStore.shape(
                if (design.darkHeader) Color.rgb(65, 51, 104) else Color.rgb(234, 242, 252),
                dp(13))
        }
        view.addView(ImageView(activity).apply {
            setImageResource(icons[index])
            imageTintList = ColorStateList.valueOf(
                if (design.darkHeader) Color.WHITE else design.accent)
        }, LinearLayout.LayoutParams(dp(iconSize), dp(iconSize)))
        view.addView(TextView(activity).apply {
            text = names[index]
            textSize = if (horizontal) 10f else 9f
            gravity = Gravity.CENTER
            setTextColor(if (design.darkHeader) Color.WHITE else design.ink)
        }, LinearLayout.LayoutParams(-2, -2).apply {
            if (horizontal) marginStart = dp(4) else topMargin = dp(4)
        })
        indicators.add(index to view)
        return view
    }

    private fun row() = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutDirection = View.LAYOUT_DIRECTION_RTL
    }
    private fun heading(head: String, sub: String) {
        caption(header, head, 22, design.ink, true)
        caption(header, sub, 12, if (design.darkHeader) Color.LTGRAY else Color.GRAY, false)
    }
    private fun caption(parent: LinearLayout, title: String, size: Int, ink: Int, bold: Boolean) {
        parent.addView(TextView(activity).apply {
            text = title
            textSize = size.toFloat()
            setTextColor(ink)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(3), dp(3), dp(3), dp(4))
            if (bold) setTypeface(null, Typeface.BOLD)
        })
    }
    private fun dp(value: Int) = ExperienceStore.dp(activity, value)
}

/** Custom vector instrument for AVATR; not a generic round card. */
private class CommandRing(activity: Activity, private val accent: Int) : View(activity) {
    private val brush = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val x = width / 2f
        val y = height / 2f
        val r = min(x, y) - 11f
        brush.style = Paint.Style.STROKE
        brush.strokeWidth = 3f
        brush.color = Color.rgb(75, 64, 116)
        canvas.drawCircle(x, y, r, brush)
        brush.strokeWidth = 8f
        brush.strokeCap = Paint.Cap.ROUND
        brush.color = accent
        canvas.drawArc(RectF(x - r, y - r, x + r, y + r), -90f, 252f, false, brush)
        brush.style = Paint.Style.FILL
        brush.color = Color.WHITE
        brush.textAlign = Paint.Align.CENTER
        brush.typeface = Typeface.DEFAULT_BOLD
        brush.textSize = 18f * resources.displayMetrics.scaledDensity
        canvas.drawText("SMS PRO", x, y + 2f, brush)
        brush.typeface = Typeface.DEFAULT
        brush.textSize = 9f * resources.displayMetrics.scaledDensity
        brush.color = accent
        canvas.drawText("COMMAND HUB", x, y + 23f, brush)
    }
}
