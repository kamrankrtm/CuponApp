/*
 * Copyright (C) 2017 Moez Bhatti <moez.bhatti@gmail.com>
 *
 * This file is part of QKSMS.
 *
 * QKSMS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * QKSMS is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with QKSMS.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.moez.QKSMS.feature.main

import android.Manifest
import android.animation.ObjectAnimator
import android.app.AlertDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewStub
import android.widget.LinearLayout
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.ItemTouchHelper
import com.google.android.material.snackbar.Snackbar
import com.jakewharton.rxbinding2.view.clicks
import com.jakewharton.rxbinding2.widget.textChanges
import com.moez.QKSMS.R
import com.moez.QKSMS.common.Navigator
import com.moez.QKSMS.common.androidxcompat.drawerOpen
import com.moez.QKSMS.common.base.QkThemedActivity
import com.moez.QKSMS.common.util.extensions.autoScrollToStart
import com.moez.QKSMS.common.util.extensions.dismissKeyboard
import com.moez.QKSMS.common.util.extensions.resolveThemeColor
import com.moez.QKSMS.common.util.extensions.scrapViews
import com.moez.QKSMS.common.util.extensions.setBackgroundTint
import com.moez.QKSMS.common.util.extensions.setTint
import com.moez.QKSMS.common.util.extensions.setVisible
import com.moez.QKSMS.common.widget.AvatarView
import com.moez.QKSMS.feature.blocking.BlockingDialog
import com.moez.QKSMS.feature.changelog.ChangelogDialog
import com.moez.QKSMS.feature.conversations.ConversationItemTouchCallback
import com.moez.QKSMS.feature.conversations.ConversationsAdapter
import com.moez.QKSMS.manager.ChangelogManager
import com.moez.QKSMS.repository.SyncRepository
import com.uber.autodispose.android.lifecycle.scope
import com.uber.autodispose.autoDisposable
import dagger.android.AndroidInjection
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import kotlinx.android.synthetic.main.drawer_view.*
import kotlinx.android.synthetic.main.main_activity.*
import kotlinx.android.synthetic.main.main_permission_hint.*
import kotlinx.android.synthetic.main.main_syncing.*
import com.moez.QKSMS.common.util.DateFormatter
import com.moez.QKSMS.common.widget.CategoryBar
import com.moez.QKSMS.feature.smart.SmartDataManager
import com.moez.QKSMS.feature.smart.promo.BrandRegistry
import com.moez.QKSMS.feature.smart.SmartSmsClassifier
import com.moez.QKSMS.feature.smart.TrustedSenders
import com.moez.QKSMS.feature.smart.model.SmsCategory
import com.moez.QKSMS.feature.smart.ui.FilteredConversationsAdapter
import com.moez.QKSMS.feature.smart.ui.OtpCodesAdapter
import com.moez.QKSMS.feature.smart.ui.PromoCodesAdapter
import com.moez.QKSMS.feature.smart.ui.SpamSwipeCallback
import com.moez.QKSMS.model.Conversation
import javax.inject.Inject

class MainActivity : QkThemedActivity(), MainView {

    @Inject lateinit var blockingDialog: BlockingDialog
    @Inject lateinit var disposables: CompositeDisposable
    @Inject lateinit var navigator: Navigator
    @Inject lateinit var conversationsAdapter: ConversationsAdapter
    @Inject lateinit var drawerBadgesExperiment: DrawerBadgesExperiment
    @Inject lateinit var searchAdapter: SearchAdapter
    @Inject lateinit var itemTouchCallback: ConversationItemTouchCallback
    @Inject lateinit var viewModelFactory: ViewModelProvider.Factory
    @Inject lateinit var dateFormatter: DateFormatter

    private val promoCodesAdapter by lazy {
        PromoCodesAdapter(
            context = this,
            onDataChanged = { rebuildPromoChips() },
            onUndoAvailable = { message, undo -> showPromoUndo(message, undo) }
        )
    }
    private val otpCodesAdapter by lazy { OtpCodesAdapter(this) }
    private val filteredConversationsAdapter by lazy {
        FilteredConversationsAdapter(colors, this, dateFormatter, navigator, phoneNumberUtils)
    }
    private var currentTabPosition = 0
    private var currentConversationsList: List<Conversation> = emptyList()
    private var cachedPersonalIds = HashSet<Long>()
    private var cachedBankingIds = HashSet<Long>()
    private var cachedSpamIds = HashSet<Long>()
    private var isClassificationReady = false
    private var isClassifying = false
    private var pendingReclassify = false
    private var lastScannedConversationCount = -1
    private var lastScannedConversationId: Long = -1
    private var lastSyncProgress: SyncRepository.SyncProgress = SyncRepository.SyncProgress.Idle
    private var currentState: MainState? = null

    override val onNewIntentIntent: Subject<Intent> = PublishSubject.create()
    override val activityResumedIntent: Subject<Boolean> = PublishSubject.create()
    override val queryChangedIntent by lazy { toolbarSearch.textChanges() }
    override val composeIntent by lazy { compose.clicks() }
    override val drawerOpenIntent: Observable<Boolean> by lazy {
        drawerLayout
                .drawerOpen(Gravity.START)
                .doOnNext { dismissKeyboard() }
    }
    override val homeIntent: Subject<Unit> = PublishSubject.create()
    override val navigationIntent: Observable<NavItem> by lazy {
        Observable.merge(listOf(
                backPressedSubject,
                inbox.clicks().map { NavItem.INBOX },
                archived.clicks().map { NavItem.ARCHIVED },
                backup.clicks().map { NavItem.BACKUP },
                scheduled.clicks().map { NavItem.SCHEDULED },
                blocking.clicks().map { NavItem.BLOCKING },
                settings.clicks().map { NavItem.SETTINGS },
                plus.clicks().map { NavItem.PLUS },
                help.clicks().map { NavItem.HELP },
                invite.clicks().map { NavItem.INVITE }))
    }
    override val optionsItemIntent: Subject<Int> = PublishSubject.create()
    override val plusBannerIntent by lazy { plusBanner.clicks() }
    override val dismissRatingIntent by lazy { rateDismiss.clicks() }
    override val rateIntent by lazy { rateOkay.clicks() }
    override val conversationsSelectedIntent by lazy { conversationsAdapter.selectionChanges }
    override val confirmDeleteIntent: Subject<List<Long>> = PublishSubject.create()
    override val swipeConversationIntent by lazy { itemTouchCallback.swipes }
    override val changelogMoreIntent by lazy { changelogDialog.moreClicks }
    override val undoArchiveIntent: Subject<Unit> = PublishSubject.create()
    override val snackbarButtonIntent: Subject<Unit> = PublishSubject.create()

    private val viewModel by lazy { ViewModelProviders.of(this, viewModelFactory)[MainViewModel::class.java] }
    private val toggle by lazy { ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.main_drawer_open_cd, 0) }
    private val itemTouchHelper by lazy { ItemTouchHelper(itemTouchCallback) }
    private val spamSwipeCallback by lazy { SpamSwipeCallback(this) { id -> markNotSpam(id) } }
    private val spamSwipeHelper by lazy { ItemTouchHelper(spamSwipeCallback) }
    private val progressAnimator by lazy { ObjectAnimator.ofInt(syncingProgress, "progress", 0, 0) }
    private val changelogDialog by lazy { ChangelogDialog(this) }
    private val snackbar by lazy { findViewById<View>(R.id.snackbar) }
    private val syncing by lazy { findViewById<View>(R.id.syncing) }
    private val backPressedSubject: Subject<NavItem> = PublishSubject.create()

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)
        viewModel.bindView(this)
        onNewIntentIntent.onNext(intent)

        (snackbar as? ViewStub)?.setOnInflateListener { _, _ ->
            snackbarButton.clicks()
                    .autoDisposable(scope(Lifecycle.Event.ON_DESTROY))
                    .subscribe(snackbarButtonIntent)
        }

        (syncing as? ViewStub)?.setOnInflateListener { _, _ ->
            syncingProgress?.progressTintList = ColorStateList.valueOf(theme.blockingFirst().theme)
            syncingProgress?.indeterminateTintList = ColorStateList.valueOf(theme.blockingFirst().theme)
        }

        toggle.syncState()
        try {
            // Restore saved discount codes before the discounts tab can ask for them.
            SmartDataManager.init(this)
            setupSmartTabs()
            setupDiscountsFilterBar()
        } catch (t: Throwable) {
            android.util.Log.e("MainActivity", "Error setting up smart tabs", t)
        }
        toolbar.setNavigationOnClickListener {
            dismissKeyboard()
            homeIntent.onNext(Unit)
        }

        itemTouchCallback.adapter = conversationsAdapter
        conversationsAdapter.autoScrollToStart(recyclerView)

        // Don't allow clicks to pass through the drawer layout
        drawer.clicks().autoDisposable(scope()).subscribe()

        // Set the theme color tint to the recyclerView, progressbar, and FAB
        theme
                .autoDisposable(scope())
                .subscribe { theme ->
                    // Set the color for the drawer icons
                    val states = arrayOf(
                            intArrayOf(android.R.attr.state_activated),
                            intArrayOf(-android.R.attr.state_activated))

                    resolveThemeColor(android.R.attr.textColorSecondary)
                            .let { textSecondary -> ColorStateList(states, intArrayOf(theme.theme, textSecondary)) }
                            .let { tintList ->
                                inboxIcon.imageTintList = tintList
                                archivedIcon.imageTintList = tintList
                            }

                    // Miscellaneous views
                    listOf(plusBadge1, plusBadge2).forEach { badge ->
                        badge.setBackgroundTint(theme.theme)
                        badge.setTextColor(theme.textPrimary)
                    }
                    syncingProgress?.progressTintList = ColorStateList.valueOf(theme.theme)
                    syncingProgress?.indeterminateTintList = ColorStateList.valueOf(theme.theme)
                    plusIcon.setTint(theme.theme)
                    rateIcon.setTint(theme.theme)
                }

        // These theme attributes don't apply themselves on API 21
        if (Build.VERSION.SDK_INT <= 22) {
            toolbarSearch.setBackgroundTint(resolveThemeColor(R.attr.bubbleColor))
        }

        // Check for updates from GitHub Releases
        com.moez.QKSMS.feature.update.AppUpdateChecker.checkForUpdate(this)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.run(onNewIntentIntent::onNext)
    }

    override fun render(state: MainState) {
        if (state.hasError) {
            finish()
            return
        }

        val addContact = when (state.page) {
            is Inbox -> state.page.addContact
            is Archived -> state.page.addContact
            else -> false
        }

        val markPinned = when (state.page) {
            is Inbox -> state.page.markPinned
            is Archived -> state.page.markPinned
            else -> true
        }

        val markRead = when (state.page) {
            is Inbox -> state.page.markRead
            is Archived -> state.page.markRead
            else -> true
        }

        val selectedConversations = when (state.page) {
            is Inbox -> state.page.selected
            is Archived -> state.page.selected
            else -> 0
        }

        // The large title and categories belong to the plain inbox; selection mode, search and
        // the archive use the compact toolbar title instead.
        val plainInbox = state.page is Inbox && state.page.selected == 0
        toolbarSearch.setVisible(plainInbox || state.page is Searching)
        toolbarTitle.setVisible(!plainInbox && state.page !is Searching)
        largeTitle?.setVisible(plainInbox)

        toolbar.menu.findItem(R.id.archive)?.isVisible = state.page is Inbox && selectedConversations != 0
        toolbar.menu.findItem(R.id.unarchive)?.isVisible = state.page is Archived && selectedConversations != 0
        toolbar.menu.findItem(R.id.delete)?.isVisible = selectedConversations != 0
        toolbar.menu.findItem(R.id.add)?.isVisible = addContact && selectedConversations != 0
        toolbar.menu.findItem(R.id.pin)?.isVisible = markPinned && selectedConversations != 0
        toolbar.menu.findItem(R.id.unpin)?.isVisible = !markPinned && selectedConversations != 0
        toolbar.menu.findItem(R.id.read)?.isVisible = markRead && selectedConversations != 0
        toolbar.menu.findItem(R.id.unread)?.isVisible = !markRead && selectedConversations != 0
        toolbar.menu.findItem(R.id.block)?.isVisible = selectedConversations != 0
        toolbar.menu.findItem(R.id.mark_all_read)?.isVisible = state.page is Inbox && selectedConversations == 0

        listOf(plusBadge1, plusBadge2).forEach { badge ->
            badge.isVisible = drawerBadgesExperiment.variant && !state.upgraded
        }
        plus.isVisible = state.upgraded
        plusBanner.isVisible = !state.upgraded
        rateLayout.setVisible(state.showRating)

        compose.setVisible(state.page is Inbox || state.page is Archived)
        conversationsAdapter.emptyView = empty.takeIf { state.page is Inbox || state.page is Archived }
        searchAdapter.emptyView = empty.takeIf { state.page is Searching }

        currentState = state
        categoryBar?.setVisible(plainInbox)
        if (!plainInbox) {
            applySurface(grouped = false)
            recyclerView.setPadding(0, recyclerView.paddingTop, 0, dp(24))
        }

        when (state.page) {
            is Inbox -> {
                showBackButton(state.page.selected > 0)
                title = getString(R.string.main_title_selected, state.page.selected)
                if (state.page.selected > 0) {
                    if (recyclerView.adapter !== conversationsAdapter) recyclerView.adapter = conversationsAdapter
                    conversationsAdapter.updateData(state.page.data)
                    useSwipe(itemTouchHelper)
                    empty.setText(R.string.inbox_empty_text)
                } else {
                    val rawData = state.page.data
                    val count = rawData?.size ?: 0
                    val firstId = rawData?.firstOrNull()?.id ?: -1L
                    val countOrStructureChanged = count != lastScannedConversationCount || firstId != lastScannedConversationId

                    currentConversationsList = rawData?.toList() ?: emptyList()
                    conversationsAdapter.updateData(rawData)

                    if (countOrStructureChanged) {
                        lastScannedConversationCount = count
                        lastScannedConversationId = firstId
                        classifyConversationsImmediately(currentConversationsList)
                        preClassifyConversations()
                    }
                    applyTabFilter()
                }
            }

            is Searching -> {
                showBackButton(true)
                if (recyclerView.adapter !== searchAdapter) recyclerView.adapter = searchAdapter
                searchAdapter.data = state.page.data ?: listOf()
                useSwipe(null)
                empty.setText(R.string.inbox_search_empty_text)
                setEmptyIcon(R.drawable.ic_lc_search)
            }

            is Archived -> {
                showBackButton(state.page.selected > 0)
                title = when (state.page.selected != 0) {
                    true -> getString(R.string.main_title_selected, state.page.selected)
                    false -> getString(R.string.title_archived)
                }
                if (recyclerView.adapter !== conversationsAdapter) recyclerView.adapter = conversationsAdapter
                conversationsAdapter.updateData(state.page.data)
                useSwipe(null)
                empty.setText(R.string.archived_empty_text)
                setEmptyIcon(R.drawable.ic_lc_archive)
            }
        }

        inbox.isActivated = state.page is Inbox
        archived.isActivated = state.page is Archived

        if (drawerLayout.isDrawerOpen(GravityCompat.START) && !state.drawerOpen) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else if (!drawerLayout.isDrawerVisible(GravityCompat.START) && state.drawerOpen) {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        val wasSyncing = lastSyncProgress is SyncRepository.SyncProgress.Running
        val isNowIdle = state.syncing is SyncRepository.SyncProgress.Idle
        if (wasSyncing && isNowIdle) {
            preClassifyConversations()
        }
        lastSyncProgress = state.syncing

        when (state.syncing) {
            is SyncRepository.SyncProgress.Idle -> {
                syncing.isVisible = false
                snackbar.isVisible = !state.defaultSms || !state.smsPermission || !state.contactPermission
            }

            is SyncRepository.SyncProgress.Running -> {
                syncing.isVisible = true
                syncingProgress.max = state.syncing.max
                progressAnimator.apply { setIntValues(syncingProgress.progress, state.syncing.progress) }.start()
                syncingProgress.isIndeterminate = state.syncing.indeterminate
                snackbar.isVisible = false
            }
        }

        when {
            !state.defaultSms -> {
                snackbarTitle?.setText(R.string.main_default_sms_title)
                snackbarMessage?.setText(R.string.main_default_sms_message)
                snackbarButton?.setText(R.string.main_default_sms_change)
            }

            !state.smsPermission -> {
                snackbarTitle?.setText(R.string.main_permission_required)
                snackbarMessage?.setText(R.string.main_permission_sms)
                snackbarButton?.setText(R.string.main_permission_allow)
            }

            !state.contactPermission -> {
                snackbarTitle?.setText(R.string.main_permission_required)
                snackbarMessage?.setText(R.string.main_permission_contacts)
                snackbarButton?.setText(R.string.main_permission_allow)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        activityResumedIntent.onNext(true)
    }

    override fun onPause() {
        super.onPause()
        activityResumedIntent.onNext(false)
    }

    override fun onDestroy() {
        super.onDestroy()
        disposables.dispose()
    }

    override fun showBackButton(show: Boolean) {
        toggle.onDrawerSlide(drawer, if (show) 1f else 0f)
        // Both states sit on the same round button, so both use the label colour
        toggle.drawerArrowDrawable.color = resolveThemeColor(android.R.attr.textColorPrimary)
    }

    override fun requestDefaultSms() {
        navigator.showDefaultSmsDialog(this)
    }

    override fun requestPermissions() {
        ActivityCompat.requestPermissions(this, arrayOf(
                Manifest.permission.READ_SMS,
                Manifest.permission.SEND_SMS,
                Manifest.permission.READ_CONTACTS), 0)
    }

    override fun clearSearch() {
        dismissKeyboard()
        toolbarSearch.text = null
    }

    override fun clearSelection() {
        conversationsAdapter.clearSelection()
    }

    override fun themeChanged() {
        recyclerView.scrapViews()
    }

    override fun showBlockingDialog(conversations: List<Long>, block: Boolean) {
        blockingDialog.show(this, conversations, block)
    }

    override fun showDeleteDialog(conversations: List<Long>) {
        val count = conversations.size
        AlertDialog.Builder(this)
                .setTitle(R.string.dialog_delete_title)
                .setMessage(resources.getQuantityString(R.plurals.dialog_delete_message, count, count))
                .setPositiveButton(R.string.button_delete) { _, _ -> confirmDeleteIntent.onNext(conversations) }
                .setNegativeButton(R.string.button_cancel, null)
                .show()
    }

    override fun showChangelog(changelog: ChangelogManager.CumulativeChangelog) {
        changelogDialog.show(changelog)
    }

    override fun showArchivedSnackbar() {
        Snackbar.make(drawerLayout, R.string.toast_archived, Snackbar.LENGTH_LONG).apply {
            setAction(R.string.button_undo) { undoArchiveIntent.onNext(Unit) }
            setActionTextColor(colors.theme().theme)
            show()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.mark_all_read) {
            android.widget.Toast.makeText(this, "همه پیام‌ها خوانده شدند", android.widget.Toast.LENGTH_SHORT).show()
            recyclerView.postDelayed({
                conversationsAdapter.notifyDataSetChanged()
                filteredConversationsAdapter.notifyDataSetChanged()
            }, 300)
        }
        optionsItemIntent.onNext(item.itemId)
        return true
    }

    override fun onBackPressed() {
        backPressedSubject.onNext(NavItem.BACK)
    }

    private fun setupSmartTabs() {
        val bar = categoryBar ?: return
        fun color(res: Int) = ContextCompat.getColor(this, res)
        bar.setCategories(listOf(
                CategoryBar.Category("All", R.drawable.ic_lc_inbox, color(R.color.tabAll)),
                CategoryBar.Category("Personal", R.drawable.ic_lc_user, color(R.color.tabPersonal)),
                CategoryBar.Category("Banking", R.drawable.ic_lc_landmark, color(R.color.tabBanking)),
                CategoryBar.Category("OTP", R.drawable.ic_lc_key, color(R.color.tabOtp)),
                CategoryBar.Category("Discounts", R.drawable.ic_lc_ticket, color(R.color.tabDiscounts)),
                CategoryBar.Category("Spam", R.drawable.ic_lc_spam, color(R.color.tabSpam))))

        val defaultTab = prefs.defaultTab.get().coerceIn(0, 5)
        currentTabPosition = defaultTab
        bar.select(defaultTab)

        bar.onCategorySelected = { position, _ ->
            currentTabPosition = position
            applyTabFilter()
            mainAppBar?.setExpanded(true, false)
            recyclerView.post { recyclerView.scrollToPosition(0) }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private var surfaceColor = 0

    /**
     * Card-based categories (codes, discounts, bank transactions) sit on the grey grouped
     * background; lists of conversations sit on plain white (or black at night).
     */
    private fun applySurface(grouped: Boolean) {
        val color = resolveThemeColor(if (grouped) R.attr.groupedBackground else android.R.attr.windowBackground)
        if (color == surfaceColor) return
        surfaceColor = color
        mainContent?.setBackgroundColor(color)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) window.statusBarColor = color
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) window.navigationBarColor = color
    }

    /** The empty state leads with the category's own glyph, drawn large and quiet. */
    private fun setEmptyIcon(res: Int) {
        val size = dp(44)
        val icon = ContextCompat.getDrawable(this, res)?.mutate()?.apply {
            setBounds(0, 0, size, size)
            setTint(resolveThemeColor(android.R.attr.textColorTertiary))
        }
        empty.setCompoundDrawablesRelative(null, icon, null, null)
    }

    private val classificationCache = java.util.concurrent.ConcurrentHashMap<Long, Pair<Long, SmsCategory>>()

    private fun classifyConversationsImmediately(conversations: List<Conversation>) {
        if (conversations.isEmpty()) return
        val personal = HashSet<Long>(conversations.size)
        val banking = HashSet<Long>()
        val spam = HashSet<Long>()

        for (conv in conversations) {
            if (!conv.isValid) continue
            val id = conv.id
            val lastMsg = conv.lastMessage
            val lastMsgId = lastMsg?.id ?: -1L

            // Check if cached
            val cached = classificationCache[id]
            if (cached != null && cached.first == lastMsgId) {
                when (cached.second) {
                    is SmsCategory.Personal -> personal.add(id)
                    is SmsCategory.Banking -> banking.add(id)
                    is SmsCategory.Spam -> spam.add(id)
                    else -> Unit
                }
                continue
            }

            val hasSavedContact = conv.recipients.any { it.contact != null }
            val sender = conv.recipients.firstOrNull()?.address ?: ""
            if (hasSavedContact || SmartSmsClassifier.isPersonalNumber(sender) || TrustedSenders.isTrusted(sender)) {
                personal.add(id)
            }
        }

        cachedPersonalIds = personal
        if (banking.isNotEmpty()) cachedBankingIds = banking
        if (spam.isNotEmpty()) cachedSpamIds = spam
        isClassificationReady = true
    }

    private fun preClassifyConversations() {
        if (isClassifying) {
            pendingReclassify = true
            return
        }
        isClassifying = true
        pendingReclassify = false

        io.reactivex.schedulers.Schedulers.io().scheduleDirect {
            val realm = io.realm.Realm.getDefaultInstance()
            try {
                val conversations = realm.where(Conversation::class.java)
                    .notEqualTo("id", 0L)
                    .equalTo("archived", false)
                    .equalTo("blocked", false)
                    .isNotEmpty("recipients")
                    .beginGroup()
                    .isNotNull("lastMessage")
                    .or()
                    .isNotEmpty("draft")
                    .endGroup()
                    .sort(
                        arrayOf("pinned", "draft", "lastMessage.date"),
                        arrayOf(io.realm.Sort.DESCENDING, io.realm.Sort.DESCENDING, io.realm.Sort.DESCENDING)
                    )
                    .findAll()

                val personal = HashSet<Long>(conversations.size)
                val banking = HashSet<Long>()
                val spam = HashSet<Long>()
                val newPromos = mutableListOf<com.moez.QKSMS.feature.smart.model.PromoItem>()
                val newOtps = mutableListOf<com.moez.QKSMS.feature.smart.model.OtpItem>()

                for (conv in conversations) {
                    if (!conv.isValid) continue
                    val id = conv.id
                    val lastMsg = conv.lastMessage
                    val lastMsgId = lastMsg?.id ?: -1L

                    // Check fast cache
                    val cached = classificationCache[id]
                    val cat = if (cached != null && cached.first == lastMsgId) {
                        cached.second
                    } else {
                        val hasSavedContact = conv.recipients.any { it.contact != null }
                        val sender = conv.recipients.firstOrNull()?.address ?: ""
                        val body = lastMsg?.body ?: ""
                        val msgDate = lastMsg?.date ?: System.currentTimeMillis()

                        // A sender the user marked "not spam" belongs with their contacts
                        val computedCat = if (hasSavedContact || TrustedSenders.isTrusted(sender)) {
                            SmsCategory.Personal
                        } else {
                            SmartSmsClassifier.classify(sender, body, msgDate, id)
                        }
                        classificationCache[id] = Pair(lastMsgId, computedCat)
                        computedCat
                    }

                    when (cat) {
                        is SmsCategory.Personal -> personal.add(id)
                        is SmsCategory.Banking -> banking.add(id)
                        is SmsCategory.Spam -> spam.add(id)
                        is SmsCategory.Promo -> if (!cat.promo.isExpired()) newPromos.add(cat.promo)
                        is SmsCategory.Otp -> newOtps.add(cat.otp)
                    }
                }

                // Scan the inbox for OTPs and promo codes with their exact timestamps.
                //
                // Promo parsing is incremental: saved codes are already in memory, so only
                // messages newer than the last scan need to be re-read. OTPs are still swept
                // over a short window because they are pruned to the last day anyway.
                val inboxType: Int = android.provider.Telephony.Sms.MESSAGE_TYPE_INBOX
                val lastScannedId = com.moez.QKSMS.feature.smart.promo.PromoStore.getLastScannedMessageId()
                val isFirstScan = lastScannedId == 0L

                val recentMessages = realm.where(com.moez.QKSMS.model.Message::class.java)
                    .equalTo("type", "sms")
                    .equalTo("boxId", inboxType)
                    .sort("date", io.realm.Sort.DESCENDING)
                    .limit(if (isFirstScan) 300 else 100)
                    .findAll()

                var newestScannedId = lastScannedId
                for (msg in recentMessages) {
                    if (!msg.isValid) continue
                    val text = msg.body.trim()
                    if (msg.id > newestScannedId) newestScannedId = msg.id

                    if (SmartSmsClassifier.isOtpMessage(text)) {
                        val cat = SmartSmsClassifier.classify(msg.address, text, msg.date, msg.threadId)
                        if (cat is SmsCategory.Otp) {
                            newOtps.add(cat.otp)
                        }
                    } else if (isFirstScan || msg.id > lastScannedId) {
                        val promo = SmartSmsClassifier.extractPromo(msg.address, text, msg.date, msg.threadId)
                        if (promo != null && !promo.isExpired()) {
                            newPromos.add(promo)
                        }
                    }
                }

                newOtps.sortByDescending { it.receivedAt }
                newPromos.sortByDescending { it.receivedAt }

                if (isFirstScan) {
                    // A full sweep is authoritative, so it may replace the cached set.
                    SmartDataManager.setPromosAndOtps(newPromos, newOtps)
                } else {
                    // An incremental pass only ever adds: replacing the set would drop stored
                    // codes whose original messages have scrolled out of the scan window.
                    // Oldest first, so the newest ends up at the head of the list.
                    newPromos.asReversed().forEach { SmartDataManager.addPromo(it) }
                    SmartDataManager.setOtps(newOtps)
                }
                com.moez.QKSMS.feature.smart.promo.PromoStore.setLastScannedMessageId(newestScannedId)

                // Warn about anything valuable that is about to run out.
                val expiringNotified = com.moez.QKSMS.feature.smart.promo.PromoExpiryNotifier
                    .notifyExpiring(applicationContext, SmartDataManager.getPromos(), prefs.notifyPromoExpiry.get())
                if (expiringNotified > 0) {
                    android.util.Log.d("MainActivity", "Posted $expiringNotified expiry reminders")
                }

                runOnUiThread {
                    cachedPersonalIds = personal
                    cachedBankingIds = banking
                    cachedSpamIds = spam
                    isClassificationReady = true
                    isClassifying = false
                    applyTabFilter()

                    if (pendingReclassify) {
                        preClassifyConversations()
                    }
                }
            } catch (t: Throwable) {
                android.util.Log.e("MainActivity", "Error in background classification", t)
                runOnUiThread {
                    isClassifying = false
                }
            } finally {
                realm.close()
            }
        }
    }

    private var activePromoCategory = "all"

    /**
     * Rebuilds the category chips from the codes actually held.
     *
     * The bar used to be five chips hard-coded in the layout, whose slugs did not even match
     * the ones on the model. Categories with no chip — supermarket, fintech, telecom,
     * services — were unreachable, and a chip for an empty category was a dead end. Building
     * the row from the data fixes both.
     */
    private fun rebuildPromoChips() {
        val container = promoChipContainer ?: return
        val counts = promoCodesAdapter.categoryCounts()
        val total = counts["all"] ?: 0

        val entries = ArrayList<Pair<String, String>>()
        entries.add("all" to "همه")
        for ((slug, label) in BrandRegistry.CATEGORY_LABELS) {
            if ((counts[slug] ?: 0) > 0) entries.add(slug to label)
        }

        // Keep whatever the user had selected if it still exists.
        if (entries.none { it.first == activePromoCategory }) {
            activePromoCategory = "all"
        }

        container.removeAllViews()
        // A secondary filter, so its selection is neutral: the label colour, not another accent
        val selectedFill = resolveThemeColor(android.R.attr.textColorPrimary)
        val selectedText = resolveThemeColor(android.R.attr.windowBackground)
        val bubbleColor = resolveThemeColor(R.attr.bubbleColor)
        val idleText = resolveThemeColor(android.R.attr.textColorPrimary)
        val density = resources.displayMetrics.density
        categoryBar?.setCount(4, if (total > 0) com.moez.QKSMS.feature.smart.promo.PromoValueParser.toPersianDigits(total.toString()) else null)

        for ((index, entry) in entries.withIndex()) {
            val (slug, label) = entry
            val count = if (slug == "all") total else (counts[slug] ?: 0)
            val selected = slug == activePromoCategory

            val chip = androidx.appcompat.widget.AppCompatTextView(this).apply {
                text = if (count > 0) {
                    "$label  ${com.moez.QKSMS.feature.smart.promo.PromoValueParser.toPersianDigits(count.toString())}"
                } else {
                    label
                }
                gravity = android.view.Gravity.CENTER
                androidx.core.widget.TextViewCompat.setTextAppearance(this, R.style.TextAppearance_App_FilterChip)
                includeFontPadding = false
                setPadding((14 * density).toInt(), 0, (14 * density).toInt(), 0)
                setBackgroundResource(R.drawable.rounded_rectangle_24dp)
                backgroundTintList = ColorStateList.valueOf(if (selected) selectedFill else bubbleColor)
                setTextColor(if (selected) selectedText else idleText)
                setOnClickListener {
                    activePromoCategory = slug
                    // filter() reports back through onDataChanged, which rebuilds this bar.
                    promoCodesAdapter.filter(
                        query = etPromoSearch?.text?.toString() ?: "",
                        category = slug
                    )
                }
            }

            val params = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                (32 * density).toInt()
            )
            if (index > 0) params.marginStart = (8 * density).toInt()
            container.addView(chip, params)
        }
    }

    /** Offers to reverse the last "used" / "doesn't work" action. */
    private fun showPromoUndo(message: String, undo: () -> Unit) {
        val root = findViewById<View>(android.R.id.content) ?: return
        com.google.android.material.snackbar.Snackbar
            .make(root, message, com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
            .setAction("بازگرداندن") { undo() }
            .show()
    }

    private fun setupDiscountsFilterBar() {
        etPromoSearch?.textChanges()
            ?.autoDisposable(scope())
            ?.subscribe { text ->
                promoCodesAdapter.filter(query = text.toString(), category = activePromoCategory)
            }

        rebuildPromoChips()

        btnQuickAiScan?.setOnClickListener {
            val apiKey = prefs.aiApiKey.get()
            if (apiKey.isBlank()) {
                val input = android.widget.EditText(this).apply {
                    hint = "AvalAI (aa-...) or OpenAI API key"
                    setPadding(48, 32, 48, 32)
                }
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("AI Discount Setup")
                    .setMessage("Enter your AvalAI / OpenAI API key to scan SMS and extract discount coupons:")
                    .setView(input)
                    .setPositiveButton("Save & Scan") { _, _ ->
                        val key = input.text.toString().trim()
                        if (key.isNotBlank()) {
                            prefs.aiApiKey.set(key)
                            startAiScan()
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            } else {
                startAiScan()
            }
        }
    }

    /**
     * Runs the AI fallback over messages the local engine could not read.
     *
     * Consent is checked before anything leaves the device; the dialog only appears once.
     */
    private fun startAiScan() {
        com.moez.QKSMS.feature.smart.ai.AiConsentDialog.ensureConsent(this, onGranted = {
            val progressDialog = android.app.ProgressDialog(this).apply {
                setMessage("در حال بررسی پیامک‌های تبلیغاتی با هوش مصنوعی...")
                setCancelable(false)
                show()
            }

            com.moez.QKSMS.feature.smart.ai.AiPromoExtractor.extractPromos(this, prefs) { success, msg, _ ->
                if (progressDialog.isShowing && !isFinishing) progressDialog.dismiss()
                if (success) {
                    val updatedPromos = SmartDataManager.getPromos()
                    promoCodesAdapter.updateData(updatedPromos)
                    empty?.setVisible(updatedPromos.isEmpty())
                }
                android.widget.Toast.makeText(
                    this,
                    if (success) msg else "تحلیل ناموفق بود: $msg",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        })
    }

    private fun applyTabFilter() {
        try {
            val state = currentState ?: return
            if (state.page !is Inbox || state.page.selected > 0) {
                discountsFilterBar?.visibility = View.GONE
                spamInfoBar?.visibility = View.GONE
                return
            }

            discountsFilterBar?.visibility = if (currentTabPosition == 4) View.VISIBLE else View.GONE
            spamInfoBar?.visibility = if (currentTabPosition == 5) View.VISIBLE else View.GONE

            // Search and compose float at the bottom of the conversation lists only
            val conversationList = currentTabPosition == 0 || currentTabPosition == 1
            toolbarSearch.setVisible(conversationList)
            recyclerView.setPadding(0, recyclerView.paddingTop, 0, dp(if (conversationList) 104 else 24))
            applySurface(grouped = currentTabPosition in 2..4)
            filteredConversationsAdapter.bankingMode = currentTabPosition == 2
            setEmptyIcon(when (currentTabPosition) {
                1 -> R.drawable.ic_lc_user
                2 -> R.drawable.ic_lc_landmark
                3 -> R.drawable.ic_lc_key
                4 -> R.drawable.ic_lc_ticket
                5 -> R.drawable.ic_lc_spam
                else -> R.drawable.ic_lc_inbox
            })

            when (currentTabPosition) {
                0 -> {
                    // All messages
                    if (recyclerView.adapter !== conversationsAdapter) recyclerView.adapter = conversationsAdapter
                    useSwipe(itemTouchHelper)
                    compose.setVisible(true)
                    empty.setText(R.string.inbox_empty_text)
                    empty.setVisible(currentConversationsList.isEmpty())
                }
                1 -> {
                    // Personal: Saved contacts or 09... personal numbers
                    val list = if (cachedPersonalIds.isNotEmpty()) {
                        currentConversationsList.filter { it.isValid && cachedPersonalIds.contains(it.id) }
                    } else {
                        currentConversationsList.filter { conv ->
                            if (!conv.isValid) return@filter false
                            if (conv.recipients.any { it.contact != null }) return@filter true
                            val sender = conv.recipients.firstOrNull()?.address ?: ""
                            if (TrustedSenders.isTrusted(sender)) return@filter true
                            val body = conv.lastMessage?.body ?: ""
                            SmartSmsClassifier.classify(sender, body) is SmsCategory.Personal
                        }
                    }
                    filteredConversationsAdapter.frequentContacts = getFrequentContacts(list)
                    filteredConversationsAdapter.data = list
                    if (recyclerView.adapter !== filteredConversationsAdapter) recyclerView.adapter = filteredConversationsAdapter
                    itemTouchCallback.adapter = filteredConversationsAdapter
                    useSwipe(itemTouchHelper)
                    compose.setVisible(true)
                    empty.text = "No personal messages"
                    empty.setVisible(list.isEmpty())
                }
                2 -> {
                    // Banking messages
                    filteredConversationsAdapter.frequentContacts = emptyList()
                    val list = currentConversationsList.filter { it.isValid && cachedBankingIds.contains(it.id) }
                    filteredConversationsAdapter.data = list
                    if (recyclerView.adapter !== filteredConversationsAdapter) recyclerView.adapter = filteredConversationsAdapter
                    itemTouchCallback.adapter = filteredConversationsAdapter
                    useSwipe(itemTouchHelper)
                    compose.setVisible(false)
                    empty.text = "No banking messages"
                    empty.setVisible(list.isEmpty() && isClassificationReady)
                }
                3 -> {
                    // OTP / Verification codes
                    val otps = SmartDataManager.getOtps()
                    otpCodesAdapter.updateData(otps)
                    if (recyclerView.adapter !== otpCodesAdapter) recyclerView.adapter = otpCodesAdapter
                    useSwipe(null)
                    compose.setVisible(false)
                    empty.text = "No OTP or verification codes"
                    empty.setVisible(otps.isEmpty())
                }
                4 -> {
                    // Discount Promo codes
                    val promos = SmartDataManager.getPromos()
                    promoCodesAdapter.updateData(promos)
                    promoCodesAdapter.filter(query = etPromoSearch?.text?.toString() ?: "", category = activePromoCategory)
                    if (recyclerView.adapter !== promoCodesAdapter) recyclerView.adapter = promoCodesAdapter
                    useSwipe(null)
                    compose.setVisible(false)
                    empty.text = "No active discount codes found"
                    empty.setVisible(promos.isEmpty())
                }
                5 -> {
                    // Spam & promotional ads
                    filteredConversationsAdapter.frequentContacts = emptyList()
                    val list = currentConversationsList.filter { it.isValid && cachedSpamIds.contains(it.id) }
                    filteredConversationsAdapter.data = list
                    if (recyclerView.adapter !== filteredConversationsAdapter) recyclerView.adapter = filteredConversationsAdapter
                    // Swiping right here marks the sender "not spam" instead of the usual action
                    spamSwipeCallback.adapter = filteredConversationsAdapter
                    useSwipe(spamSwipeHelper)
                    compose.setVisible(false)
                    empty.text = "Spam inbox is empty"
                    empty.setVisible(list.isEmpty() && isClassificationReady)
                }
            }
        } catch (t: Throwable) {
            android.util.Log.e("MainActivity", "Error applying tab filter", t)
        }
    }

    /** Attaches at most one swipe handler: the Spam tab has its own, other lists use the settings' actions. */
    private fun useSwipe(helper: ItemTouchHelper?) {
        if (helper !== itemTouchHelper) itemTouchHelper.attachToRecyclerView(null)
        if (helper !== spamSwipeHelper) spamSwipeHelper.attachToRecyclerView(null)
        helper?.attachToRecyclerView(recyclerView)
    }

    /**
     * Moves a conversation out of Spam. Its senders become trusted, so from now on it is listed
     * under Personal and its messages notify normally; the snackbar offers to undo that.
     */
    private fun markNotSpam(conversationId: Long) {
        val conversation = currentConversationsList.firstOrNull { it.isValid && it.id == conversationId } ?: return
        val addresses = conversation.recipients.map { it.address }.filter { it.isNotBlank() }
        if (addresses.isEmpty()) return
        val title = conversation.getTitle()

        TrustedSenders.trust(addresses)
        reclassify(conversationId, toPersonal = true)

        Snackbar.make(drawerLayout, getString(R.string.spam_moved_to_personal, title), Snackbar.LENGTH_LONG)
                .setAction(R.string.button_undo) {
                    TrustedSenders.untrust(addresses)
                    reclassify(conversationId, toPersonal = false)
                }
                .setActionTextColor(colors.theme().theme)
                .show()
    }

    private fun reclassify(conversationId: Long, toPersonal: Boolean) {
        classificationCache.remove(conversationId)
        if (toPersonal) {
            cachedSpamIds = HashSet(cachedSpamIds).apply { remove(conversationId) }
            cachedPersonalIds = HashSet(cachedPersonalIds).apply { add(conversationId) }
        } else {
            cachedPersonalIds = HashSet(cachedPersonalIds).apply { remove(conversationId) }
        }
        applyTabFilter()
        // The background pass settles the exact category, e.g. banking or spam again after an undo
        preClassifyConversations()
    }

    private fun getFrequentContacts(personalList: List<Conversation>): List<Conversation> {
        val oneMonthAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
        val messageCountsByThread = HashMap<Long, Int>()

        try {
            val realm = io.realm.Realm.getDefaultInstance()
            try {
                val recentMessages = realm.where(com.moez.QKSMS.model.Message::class.java)
                    .greaterThan("date", oneMonthAgo)
                    .findAll()

                for (msg in recentMessages) {
                    val tid = msg.threadId
                    messageCountsByThread[tid] = (messageCountsByThread[tid] ?: 0) + 1
                }
            } finally {
                realm.close()
            }
        } catch (t: Throwable) {
            android.util.Log.e("MainActivity", "Error counting recent messages for frequent contacts", t)
        }

        val eligible = personalList
            .filter { conv ->
                conv.isValid &&
                conv.recipients.size == 1 &&
                (conv.recipients.firstOrNull()?.contact != null || SmartSmsClassifier.isPersonalNumber(conv.recipients.firstOrNull()?.address ?: ""))
            }

        // Sort primarily by count of messages in the past month descending, then by date of last message descending
        val sorted = eligible.sortedWith(
            compareByDescending<Conversation> { messageCountsByThread[it.id] ?: 0 }
                .thenByDescending { it.date }
        )

        // Prioritize contacts that had actual messages in the past month
        val activeThisMonth = sorted.filter { (messageCountsByThread[it.id] ?: 0) > 0 }
        return if (activeThisMonth.isNotEmpty()) {
            activeThisMonth.take(5)
        } else {
            sorted.take(5)
        }
    }
}
