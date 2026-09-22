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
import com.google.android.material.tabs.TabLayout
import com.moez.QKSMS.common.util.DateFormatter
import com.moez.QKSMS.feature.smart.SmartDataManager
import com.moez.QKSMS.feature.smart.SmartSmsClassifier
import com.moez.QKSMS.feature.smart.model.SmsCategory
import com.moez.QKSMS.feature.smart.ui.FilteredConversationsAdapter
import com.moez.QKSMS.feature.smart.ui.OtpCodesAdapter
import com.moez.QKSMS.feature.smart.ui.PromoCodesAdapter
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

    private val promoCodesAdapter by lazy { PromoCodesAdapter(this) }
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
                    compose.setBackgroundTint(theme.theme)

                    // Set the FAB compose icon color
                    compose.setTint(theme.textPrimary)

                    // Theme Smart TabLayout
                    smartTabLayout?.setSelectedTabIndicatorColor(theme.theme)
                    smartTabLayout?.setTabTextColors(resolveThemeColor(android.R.attr.textColorSecondary), theme.theme)
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

        toolbarSearch.setVisible(state.page is Inbox && state.page.selected == 0 || state.page is Searching)
        toolbarTitle.setVisible(toolbarSearch.visibility != View.VISIBLE)

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
        smartTabLayout?.setVisible(state.page is Inbox && state.page.selected == 0)

        when (state.page) {
            is Inbox -> {
                showBackButton(state.page.selected > 0)
                title = getString(R.string.main_title_selected, state.page.selected)
                if (state.page.selected > 0) {
                    if (recyclerView.adapter !== conversationsAdapter) recyclerView.adapter = conversationsAdapter
                    conversationsAdapter.updateData(state.page.data)
                    itemTouchHelper.attachToRecyclerView(recyclerView)
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
                itemTouchHelper.attachToRecyclerView(null)
                empty.setText(R.string.inbox_search_empty_text)
            }

            is Archived -> {
                showBackButton(state.page.selected > 0)
                title = when (state.page.selected != 0) {
                    true -> getString(R.string.main_title_selected, state.page.selected)
                    false -> getString(R.string.title_archived)
                }
                if (recyclerView.adapter !== conversationsAdapter) recyclerView.adapter = conversationsAdapter
                conversationsAdapter.updateData(state.page.data)
                itemTouchHelper.attachToRecyclerView(null)
                empty.setText(R.string.archived_empty_text)
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
        toggle.drawerArrowDrawable.color = when (show) {
            true -> resolveThemeColor(android.R.attr.textColorSecondary)
            false -> resolveThemeColor(android.R.attr.textColorPrimary)
        }
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
        val tabs = smartTabLayout ?: return
        tabs.removeAllTabs()
        tabs.addTab(tabs.newTab().setText("All"))
        tabs.addTab(tabs.newTab().setText("Personal"))
        tabs.addTab(tabs.newTab().setText("Banking"))
        tabs.addTab(tabs.newTab().setText("OTP"))
        tabs.addTab(tabs.newTab().setText("Discounts"))
        tabs.addTab(tabs.newTab().setText("Spam"))

        val defaultTab = prefs.defaultTab.get().coerceIn(0, 5)
        currentTabPosition = defaultTab
        tabs.getTabAt(defaultTab)?.select()

        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                tab?.let {
                    currentTabPosition = it.position
                    applyTabFilter()
                    recyclerView.post { recyclerView.scrollToPosition(0) }
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}

            override fun onTabReselected(tab: TabLayout.Tab?) {
                applyTabFilter()
                recyclerView.post { recyclerView.scrollToPosition(0) }
            }
        })
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
            if (hasSavedContact || SmartSmsClassifier.isPersonalNumber(sender)) {
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

                        val computedCat = if (hasSavedContact) {
                            SmsCategory.Personal
                        } else {
                            SmartSmsClassifier.classify(sender, body, msgDate)
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

                // Also scan incoming SMS messages for both OTPs and Promo codes with exact timestamps
                val inboxType: Int = android.provider.Telephony.Sms.MESSAGE_TYPE_INBOX
                val recentMessages = realm.where(com.moez.QKSMS.model.Message::class.java)
                    .equalTo("type", "sms")
                    .equalTo("boxId", inboxType)
                    .sort("date", io.realm.Sort.DESCENDING)
                    .limit(300)
                    .findAll()

                for (msg in recentMessages) {
                    if (!msg.isValid) continue
                    val text = msg.body.trim()
                    if (SmartSmsClassifier.isOtpMessage(text)) {
                        val cat = SmartSmsClassifier.classify(msg.address, text, msg.date)
                        if (cat is SmsCategory.Otp) {
                            newOtps.add(cat.otp)
                        }
                    } else {
                        val promo = SmartSmsClassifier.extractPromo(msg.address, text, msg.date)
                        if (promo != null && !promo.isExpired()) {
                            newPromos.add(promo)
                        }
                    }
                }

                newOtps.sortByDescending { it.receivedAt }
                newPromos.sortByDescending { it.receivedAt }

                SmartDataManager.setPromosAndOtps(newPromos, newOtps)

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

    private fun setupDiscountsFilterBar() {
        etPromoSearch?.textChanges()
            ?.autoDisposable(scope())
            ?.subscribe { text ->
                promoCodesAdapter.filter(query = text.toString(), category = activePromoCategory)
            }

        val chips = listOf(
            Triple(chipCatAll, "all", "All Codes"),
            Triple(chipCatFood, "food", "🍔 Food"),
            Triple(chipCatShopping, "shopping", "🛍️ Shopping"),
            Triple(chipCatTravel, "travel", "✈️ Travel"),
            Triple(chipCatEnt, "entertainment", "🎬 Entertainment")
        )

        fun updateChipsUi(selectedCategory: String) {
            activePromoCategory = selectedCategory
            val accentColor = android.graphics.Color.parseColor("#0088FF")
            val bubbleColor = resolveThemeColor(R.attr.bubbleColor)
            val textColorSecondary = resolveThemeColor(android.R.attr.textColorSecondary)

            chips.forEach { (view, cat, _) ->
                if (view == null) return@forEach
                if (cat == selectedCategory) {
                    view.backgroundTintList = ColorStateList.valueOf(accentColor)
                    view.setTextColor(android.graphics.Color.WHITE)
                    view.setTypeface(null, android.graphics.Typeface.BOLD)
                } else {
                    view.backgroundTintList = ColorStateList.valueOf(bubbleColor)
                    view.setTextColor(textColorSecondary)
                    view.setTypeface(null, android.graphics.Typeface.NORMAL)
                }
            }
            promoCodesAdapter.filter(query = etPromoSearch?.text?.toString() ?: "", category = selectedCategory)
        }

        chips.forEach { (view, cat, _) ->
            view?.setOnClickListener {
                updateChipsUi(cat)
            }
        }

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

    private fun startAiScan() {
        val progressDialog = android.app.ProgressDialog(this).apply {
            setMessage("AI is scanning SMS for discounts & coupons...")
            setCancelable(false)
            show()
        }

        com.moez.QKSMS.feature.smart.ai.AiPromoExtractor.extractPromos(this, prefs) { success, msg, count ->
            progressDialog.dismiss()
            if (success) {
                val updatedPromos = com.moez.QKSMS.feature.smart.SmartDataManager.getPromos()
                promoCodesAdapter.updateData(updatedPromos)
                empty?.setVisible(updatedPromos.isEmpty())
                android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_LONG).show()
            } else {
                android.widget.Toast.makeText(this, "AI Scan failed: $msg", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun applyTabFilter() {
        try {
            val state = currentState ?: return
            if (state.page !is Inbox || state.page.selected > 0) {
                discountsFilterBar?.visibility = View.GONE
                return
            }

            discountsFilterBar?.visibility = if (currentTabPosition == 4) View.VISIBLE else View.GONE

            when (currentTabPosition) {
                0 -> {
                    // All messages
                    if (recyclerView.adapter !== conversationsAdapter) recyclerView.adapter = conversationsAdapter
                    itemTouchHelper.attachToRecyclerView(recyclerView)
                    compose.setVisible(true)
                    empty.setText(R.string.inbox_empty_text)
                    empty.setVisible(currentConversationsList.isEmpty())
                }
                1 -> {
                    // Personal: Saved contacts or 09... personal numbers
                    val list = if (cachedPersonalIds.isNotEmpty()) {
                        currentConversationsList.filter { cachedPersonalIds.contains(it.id) }
                    } else {
                        currentConversationsList.filter { conv ->
                            if (!conv.isValid) return@filter false
                            if (conv.recipients.any { it.contact != null }) return@filter true
                            val sender = conv.recipients.firstOrNull()?.address ?: ""
                            val body = conv.lastMessage?.body ?: ""
                            SmartSmsClassifier.classify(sender, body) is SmsCategory.Personal
                        }
                    }
                    filteredConversationsAdapter.frequentContacts = getFrequentContacts(list)
                    filteredConversationsAdapter.data = list
                    if (recyclerView.adapter !== filteredConversationsAdapter) recyclerView.adapter = filteredConversationsAdapter
                    itemTouchCallback.adapter = filteredConversationsAdapter
                    itemTouchHelper.attachToRecyclerView(recyclerView)
                    compose.setVisible(true)
                    empty.text = "No personal messages"
                    empty.setVisible(list.isEmpty())
                }
                2 -> {
                    // Banking messages
                    filteredConversationsAdapter.frequentContacts = emptyList()
                    val list = currentConversationsList.filter { cachedBankingIds.contains(it.id) }
                    filteredConversationsAdapter.data = list
                    if (recyclerView.adapter !== filteredConversationsAdapter) recyclerView.adapter = filteredConversationsAdapter
                    itemTouchCallback.adapter = filteredConversationsAdapter
                    itemTouchHelper.attachToRecyclerView(recyclerView)
                    compose.setVisible(false)
                    empty.text = "No banking messages"
                    empty.setVisible(list.isEmpty() && isClassificationReady)
                }
                3 -> {
                    // OTP / Verification codes
                    val otps = SmartDataManager.getOtps()
                    otpCodesAdapter.updateData(otps)
                    if (recyclerView.adapter !== otpCodesAdapter) recyclerView.adapter = otpCodesAdapter
                    itemTouchHelper.attachToRecyclerView(null)
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
                    itemTouchHelper.attachToRecyclerView(null)
                    compose.setVisible(false)
                    empty.text = "No active discount codes found"
                    empty.setVisible(promos.isEmpty())
                }
                5 -> {
                    // Spam & promotional ads
                    filteredConversationsAdapter.frequentContacts = emptyList()
                    val list = currentConversationsList.filter { cachedSpamIds.contains(it.id) }
                    filteredConversationsAdapter.data = list
                    if (recyclerView.adapter !== filteredConversationsAdapter) recyclerView.adapter = filteredConversationsAdapter
                    itemTouchCallback.adapter = filteredConversationsAdapter
                    itemTouchHelper.attachToRecyclerView(recyclerView)
                    compose.setVisible(false)
                    empty.text = "Spam inbox is empty"
                    empty.setVisible(list.isEmpty() && isClassificationReady)
                }
            }
        } catch (t: Throwable) {
            android.util.Log.e("MainActivity", "Error applying tab filter", t)
        }
    }

    private fun getFrequentContacts(personalList: List<Conversation>): List<Conversation> {
        val twoMonthsAgo = System.currentTimeMillis() - (60L * 24 * 60 * 60 * 1000)
        return personalList
            .filter { conv ->
                conv.isValid &&
                conv.date > twoMonthsAgo &&
                conv.recipients.size == 1 &&
                (conv.recipients.firstOrNull()?.contact != null || SmartSmsClassifier.isPersonalNumber(conv.recipients.firstOrNull()?.address ?: ""))
            }
            .sortedByDescending { it.date }
            .take(5)
    }
}
