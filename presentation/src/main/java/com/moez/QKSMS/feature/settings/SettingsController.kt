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
package com.moez.QKSMS.feature.settings

import android.animation.ObjectAnimator
import android.app.TimePickerDialog
import android.content.Context
import android.os.Build
import android.text.format.DateFormat
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import com.bluelinelabs.conductor.RouterTransaction
import com.google.android.material.snackbar.Snackbar
import com.jakewharton.rxbinding2.view.clicks
import com.jakewharton.rxbinding2.view.longClicks
import com.moez.QKSMS.BuildConfig
import com.moez.QKSMS.R
import com.moez.QKSMS.common.MenuItem
import com.moez.QKSMS.common.QkChangeHandler
import com.moez.QKSMS.common.QkDialog
import com.moez.QKSMS.common.base.QkController
import com.moez.QKSMS.common.util.Colors
import com.moez.QKSMS.common.util.extensions.animateLayoutChanges
import com.moez.QKSMS.common.util.extensions.findPreferenceViews
import com.moez.QKSMS.common.util.extensions.setBackgroundTint
import com.moez.QKSMS.common.util.extensions.setVisible
import com.moez.QKSMS.common.widget.PreferenceView
import com.moez.QKSMS.common.widget.QkSwitch
import com.moez.QKSMS.common.widget.TextInputDialog
import com.moez.QKSMS.feature.cloud.CloudUploadManager
import com.moez.QKSMS.feature.settings.about.AboutController
import com.moez.QKSMS.feature.settings.autodelete.AutoDeleteDialog
import com.moez.QKSMS.feature.settings.swipe.SwipeActionsController
import com.moez.QKSMS.feature.themepicker.ThemePickerController
import com.moez.QKSMS.injection.appComponent
import com.moez.QKSMS.repository.SyncRepository
import com.moez.QKSMS.util.Preferences
import com.uber.autodispose.android.lifecycle.scope
import com.uber.autodispose.autoDisposable
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.android.synthetic.main.settings_controller.*
import kotlinx.android.synthetic.main.settings_controller.view.*
import kotlinx.android.synthetic.main.settings_switch_widget.view.*
import kotlinx.android.synthetic.main.settings_theme_widget.*
import javax.inject.Inject
import kotlin.coroutines.resume

class SettingsController : QkController<SettingsView, SettingsState, SettingsPresenter>(), SettingsView {

    @Inject lateinit var context: Context
    @Inject lateinit var colors: Colors
    @Inject lateinit var nightModeDialog: QkDialog
    @Inject lateinit var textSizeDialog: QkDialog
    @Inject lateinit var sendDelayDialog: QkDialog
    @Inject lateinit var mmsSizeDialog: QkDialog
    @Inject lateinit var prefs: Preferences
    @Inject lateinit var cloudUploadManager: CloudUploadManager

    @Inject override lateinit var presenter: SettingsPresenter

    private val signatureDialog: TextInputDialog by lazy {
        TextInputDialog(activity!!, context.getString(R.string.settings_signature_title), signatureSubject::onNext)
    }
    private val autoDeleteDialog: AutoDeleteDialog by lazy {
        AutoDeleteDialog(activity!!, autoDeleteSubject::onNext)
    }

    private val viewQksmsPlusSubject: Subject<Unit> = PublishSubject.create()
    private val startTimeSelectedSubject: Subject<Pair<Int, Int>> = PublishSubject.create()
    private val endTimeSelectedSubject: Subject<Pair<Int, Int>> = PublishSubject.create()
    private val signatureSubject: Subject<String> = PublishSubject.create()
    private val autoDeleteSubject: Subject<Int> = PublishSubject.create()

    private val progressAnimator by lazy { ObjectAnimator.ofInt(syncingProgress, "progress", 0, 0) }

    init {
        appComponent.inject(this)
        retainViewMode = RetainViewMode.RETAIN_DETACH
        layoutRes = R.layout.settings_controller

        colors.themeObservable()
                .autoDisposable(scope())
                .subscribe { activity?.recreate() }
    }

    override fun onViewCreated() {
        preferences.postDelayed({ preferences?.animateLayoutChanges = true }, 100)

        when (Build.VERSION.SDK_INT >= 29) {
            true -> nightModeDialog.adapter.setData(R.array.night_modes)
            false -> nightModeDialog.adapter.data = context.resources.getStringArray(R.array.night_modes)
                    .mapIndexed { index, title -> MenuItem(title, index) }
                    .drop(1)
        }
        textSizeDialog.adapter.setData(R.array.text_sizes)
        sendDelayDialog.adapter.setData(R.array.delayed_sending_labels)
        mmsSizeDialog.adapter.setData(R.array.mms_sizes, R.array.mms_sizes_ids)

        about.summary = context.getString(R.string.settings_version, BuildConfig.VERSION_NAME)
    }

    override fun onDetach(view: View) {
        super.onDetach(view)
        useGroupedSurface(false)
    }

    override fun onAttach(view: View) {
        super.onAttach(view)
        presenter.bindIntents(this)
        setTitle(R.string.title_settings)
        showBackButton(true)
        useGroupedSurface(true)

        val tabNames = arrayOf("All", "Personal", "Banking", "OTP", "Discounts", "Spam")
        prefDefaultTab?.summary = tabNames.getOrElse(prefs.defaultTab.get()) { "Personal" }
        prefDefaultTab?.setOnClickListener {
            activity?.let { act ->
                AlertDialog.Builder(act)
                    .setTitle("Default Startup Tab")
                    .setSingleChoiceItems(tabNames, prefs.defaultTab.get().coerceIn(0, 5)) { dialog, which ->
                        prefs.defaultTab.set(which)
                        prefDefaultTab?.summary = tabNames[which]
                        dialog.dismiss()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }

        prefAutoCopyOtp?.checkbox?.isChecked = prefs.autoCopyOtp.get()
        prefAutoCopyOtp?.setOnClickListener {
            val newVal = !prefs.autoCopyOtp.get()
            prefs.autoCopyOtp.set(newVal)
            prefAutoCopyOtp.checkbox.isChecked = newVal
        }

        prefSilentSpam?.checkbox?.isChecked = prefs.silentSpam.get()
        prefSilentSpam?.setOnClickListener {
            val newVal = !prefs.silentSpam.get()
            prefs.silentSpam.set(newVal)
            prefSilentSpam.checkbox.isChecked = newVal
        }

        prefNotifyDiscounts?.checkbox?.isChecked = prefs.notifyDiscounts.get()
        prefNotifyDiscounts?.setOnClickListener {
            val newVal = !prefs.notifyDiscounts.get()
            prefs.notifyDiscounts.set(newVal)
            prefNotifyDiscounts.checkbox.isChecked = newVal
        }

        prefNotifyPromoExpiry?.checkbox?.isChecked = prefs.notifyPromoExpiry.get()
        prefNotifyPromoExpiry?.setOnClickListener {
            val newVal = !prefs.notifyPromoExpiry.get()
            prefs.notifyPromoExpiry.set(newVal)
            prefNotifyPromoExpiry.checkbox.isChecked = newVal
        }

        prefJalaliCalendar?.checkbox?.isChecked = prefs.jalaliCalendar.get()
        prefJalaliCalendar?.setOnClickListener {
            val newVal = !prefs.jalaliCalendar.get()
            prefs.jalaliCalendar.set(newVal)
            prefJalaliCalendar.checkbox.isChecked = newVal
        }

        prefMediaAsCloudLink?.checkbox?.isChecked = prefs.mediaAsCloudLink.get()
        prefMediaAsCloudLink?.setOnClickListener {
            val newVal = !prefs.mediaAsCloudLink.get()
            prefs.mediaAsCloudLink.set(newVal)
            prefMediaAsCloudLink.checkbox.isChecked = newVal
        }

        prefFilesIrToken?.summary = if (prefs.filesIrToken.get().isBlank()) "Not configured (public upload)" else "Configured (••••••••)"
        prefFilesIrToken?.setOnClickListener {
            activity?.let { act ->
                TextInputDialog(act, "Files.ir API Token") { text ->
                    prefs.filesIrToken.set(text.trim())
                    prefFilesIrToken.summary = if (text.isBlank()) "Not configured (public upload)" else "Configured (••••••••)"
                }.setText(prefs.filesIrToken.get()).show()
            }
        }

        prefFilesIrEndpoint?.summary = prefs.filesIrEndpoint.get().ifBlank { "https://my.files.ir" }
        prefFilesIrEndpoint?.setOnClickListener {
            activity?.let { act ->
                TextInputDialog(act, "Files.ir Server Endpoint") { text ->
                    val url = text.trim().ifBlank { "https://my.files.ir" }
                    prefs.filesIrEndpoint.set(url)
                    prefFilesIrEndpoint.summary = url
                }.setText(prefs.filesIrEndpoint.get()).show()
            }
        }

        prefZayaApiKey?.summary = if (prefs.zayaApiKey.get().isBlank()) "Not configured" else "Configured (••••••••)"
        prefZayaApiKey?.setOnClickListener {
            activity?.let { act ->
                TextInputDialog(act, "Zaya.io API Key") { text ->
                    prefs.zayaApiKey.set(text.trim())
                    prefZayaApiKey.summary = if (text.isBlank()) "Not configured" else "Configured (••••••••)"
                }.setText(prefs.zayaApiKey.get()).show()
            }
        }

        prefTestCloudConnection?.setOnClickListener {
            prefTestCloudConnection.summary = "Checking connectivity..."
            cloudUploadManager.testConnection(
                endpoint = prefs.filesIrEndpoint.get(),
                filesToken = prefs.filesIrToken.get(),
                zayaToken = prefs.zayaApiKey.get()
            ) { success, result ->
                prefTestCloudConnection?.summary = if (success) "Connection verified successfully" else "Connection failed"
                activity?.let { act ->
                    if (!act.isFinishing && !act.isDestroyed) {
                        AlertDialog.Builder(act)
                            .setTitle("Cloud Connection Test Result")
                            .setMessage(result)
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }
            }
        }

        prefAiApiKey?.summary = if (prefs.aiApiKey.get().isBlank()) "Not configured" else "Configured (••••••••)"
        prefAiApiKey?.setOnClickListener {
            activity?.let { act ->
                TextInputDialog(act, "AI API Key (AvalAI / OpenAI)") { text ->
                    prefs.aiApiKey.set(text.trim())
                    prefAiApiKey.summary = if (text.isBlank()) "Not configured" else "Configured (••••••••)"
                }.setText(prefs.aiApiKey.get()).show()
            }
        }

        prefAiBaseUrl?.summary = prefs.aiBaseUrl.get().ifBlank { "https://api.avalai.ir/v1" }
        prefAiBaseUrl?.setOnClickListener {
            activity?.let { act ->
                TextInputDialog(act, "AI Service Endpoint") { text ->
                    val url = text.trim().ifBlank { "https://api.avalai.ir/v1" }
                    prefs.aiBaseUrl.set(url)
                    prefAiBaseUrl.summary = url
                }.setText(prefs.aiBaseUrl.get()).show()
            }
        }

        prefAiModel?.summary = prefs.aiModel.get().ifBlank { "gemini-2.5-flash-lite" }
        prefAiModel?.setOnClickListener {
            activity?.let { act ->
                val models = arrayOf("gemini-2.5-flash-lite", "gpt-4o-mini", "gpt-3.5-turbo", "claude-3-haiku")
                val current = prefs.aiModel.get()
                val selectedIndex = models.indexOf(current).takeIf { it >= 0 } ?: 0
                AlertDialog.Builder(act)
                    .setTitle("Select AI Model")
                    .setSingleChoiceItems(models, selectedIndex) { dialog, which ->
                        val chosen = models[which]
                        prefs.aiModel.set(chosen)
                        prefAiModel.summary = chosen
                        dialog.dismiss()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }

        prefTestAiConnection?.setOnClickListener {
            prefTestAiConnection.summary = "Testing AI connection..."
            com.moez.QKSMS.feature.smart.ai.AiPromoExtractor.testConnection(
                apiKey = prefs.aiApiKey.get(),
                baseUrl = prefs.aiBaseUrl.get()
            ) { success, msg ->
                prefTestAiConnection?.summary = if (success) "Connection verified" else "Failed"
                activity?.let { act ->
                    if (!act.isFinishing && !act.isDestroyed) {
                        AlertDialog.Builder(act)
                            .setTitle(if (success) "AI Connection Successful" else "AI Connection Failed")
                            .setMessage(msg)
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }
            }
        }

        prefRunAiScan?.setOnClickListener {
            activity?.let { act ->
                if (prefs.aiApiKey.get().isBlank()) {
                    android.widget.Toast.makeText(act, "Please configure AI API Key first", android.widget.Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                com.moez.QKSMS.feature.smart.ai.AiConsentDialog.ensureConsent(act, onGranted = {
                    prefRunAiScan.summary = "Scanning inbox with AI..."
                    android.widget.Toast.makeText(act, "AI promo extraction started in background...", android.widget.Toast.LENGTH_LONG).show()

                    com.moez.QKSMS.feature.smart.ai.AiPromoExtractor.extractPromos(act, prefs) { success, msg, report ->
                        prefRunAiScan?.summary = if (success) "Extracted ${report?.found ?: 0} promo codes" else "Scan failed"
                        activity?.let { currentAct ->
                            if (!currentAct.isFinishing && !currentAct.isDestroyed) {
                                val detail = report?.let {
                                    "\n\nتحلیل‌شده: ${it.analysed}" +
                                        "\nرد شده به دلیل حریم خصوصی: ${it.skippedSensitive}" +
                                        "\nقبلاً توسط موتور داخلی خوانده شده: ${it.skippedAlreadyParsed}"
                                } ?: ""
                                AlertDialog.Builder(currentAct)
                                    .setTitle(if (success) "AI Scan Completed" else "AI Scan Failed")
                                    .setMessage(msg + detail)
                                    .setPositiveButton("OK", null)
                                    .show()
                            }
                        }
                    }
                })
            }
        }

        prefAiAutoSendReply?.checkbox?.isChecked = prefs.aiAutoSendReply.get()
        prefAiAutoSendReply?.setOnClickListener {
            val next = !prefs.aiAutoSendReply.get()
            prefs.aiAutoSendReply.set(next)
            prefAiAutoSendReply?.checkbox?.isChecked = next
        }
    }

    override fun preferenceClicks(): Observable<PreferenceView> = preferences.findPreferenceViews()
            .map { preference -> preference.clicks().map { preference } }
            .let { preferences -> Observable.merge(preferences) }

    override fun aboutLongClicks(): Observable<*> = about.longClicks()

    override fun viewQksmsPlusClicks(): Observable<*> = viewQksmsPlusSubject

    override fun nightModeSelected(): Observable<Int> = nightModeDialog.adapter.menuItemClicks

    override fun nightStartSelected(): Observable<Pair<Int, Int>> = startTimeSelectedSubject

    override fun nightEndSelected(): Observable<Pair<Int, Int>> = endTimeSelectedSubject

    override fun textSizeSelected(): Observable<Int> = textSizeDialog.adapter.menuItemClicks

    override fun sendDelaySelected(): Observable<Int> = sendDelayDialog.adapter.menuItemClicks

    override fun signatureChanged(): Observable<String> = signatureSubject

    override fun autoDeleteChanged(): Observable<Int> = autoDeleteSubject

    override fun mmsSizeSelected(): Observable<Int> = mmsSizeDialog.adapter.menuItemClicks

    override fun render(state: SettingsState) {
        themePreview.setBackgroundTint(state.theme)
        night.summary = state.nightModeSummary
        nightModeDialog.adapter.selectedItem = state.nightModeId
        nightStart.setVisible(state.nightModeId == Preferences.NIGHT_MODE_AUTO)
        nightStart.summary = state.nightStart
        nightEnd.setVisible(state.nightModeId == Preferences.NIGHT_MODE_AUTO)
        nightEnd.summary = state.nightEnd

        black.setVisible(state.nightModeId != Preferences.NIGHT_MODE_OFF)
        black.checkbox.isChecked = state.black

        autoEmoji.checkbox.isChecked = state.autoEmojiEnabled

        delayed.summary = state.sendDelaySummary
        sendDelayDialog.adapter.selectedItem = state.sendDelayId

        delivery.checkbox.isChecked = state.deliveryEnabled

        signature.summary = state.signature.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.settings_signature_summary)

        textSize.summary = state.textSizeSummary
        textSizeDialog.adapter.selectedItem = state.textSizeId

        autoColor.checkbox.isChecked = state.autoColor

        systemFont.checkbox.isChecked = state.systemFontEnabled

        unicode.checkbox.isChecked = state.stripUnicodeEnabled
        mobileOnly.checkbox.isChecked = state.mobileOnly

        autoDelete.summary = when (state.autoDelete) {
            0 -> context.getString(R.string.settings_auto_delete_never)
            else -> context.resources.getQuantityString(
                    R.plurals.settings_auto_delete_summary, state.autoDelete, state.autoDelete)
        }

        longAsMms.checkbox.isChecked = state.longAsMms

        mmsSize.summary = state.maxMmsSizeSummary
        mmsSizeDialog.adapter.selectedItem = state.maxMmsSizeId

        when (state.syncProgress) {
            is SyncRepository.SyncProgress.Idle -> syncingProgress.isVisible = false

            is SyncRepository.SyncProgress.Running -> {
                syncingProgress.isVisible = true
                syncingProgress.max = state.syncProgress.max
                progressAnimator.apply { setIntValues(syncingProgress.progress, state.syncProgress.progress) }.start()
                syncingProgress.isIndeterminate = state.syncProgress.indeterminate
            }
        }
    }

    override fun showQksmsPlusSnackbar() {
        view?.run {
            Snackbar.make(contentView, R.string.toast_qksms_plus, Snackbar.LENGTH_LONG).run {
                setAction(R.string.button_more) { viewQksmsPlusSubject.onNext(Unit) }
                setActionTextColor(colors.theme().theme)
                show()
            }
        }
    }

    // TODO change this to a PopupWindow
    override fun showNightModeDialog() = nightModeDialog.show(activity!!)

    override fun showStartTimePicker(hour: Int, minute: Int) {
        TimePickerDialog(activity, { _, newHour, newMinute ->
            startTimeSelectedSubject.onNext(Pair(newHour, newMinute))
        }, hour, minute, DateFormat.is24HourFormat(activity)).show()
    }

    override fun showEndTimePicker(hour: Int, minute: Int) {
        TimePickerDialog(activity, { _, newHour, newMinute ->
            endTimeSelectedSubject.onNext(Pair(newHour, newMinute))
        }, hour, minute, DateFormat.is24HourFormat(activity)).show()
    }

    override fun showTextSizePicker() = textSizeDialog.show(activity!!)

    override fun showDelayDurationDialog() = sendDelayDialog.show(activity!!)

    override fun showSignatureDialog(signature: String) = signatureDialog.setText(signature).show()

    override fun showAutoDeleteDialog(days: Int) = autoDeleteDialog.setExpiry(days).show()

    override suspend fun showAutoDeleteWarningDialog(messages: Int): Boolean = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine<Boolean> { cont ->
            AlertDialog.Builder(activity!!)
                    .setTitle(R.string.settings_auto_delete_warning)
                    .setMessage(context.resources.getString(R.string.settings_auto_delete_warning_message, messages))
                    .setOnCancelListener { cont.resume(false) }
                    .setNegativeButton(R.string.button_cancel) { _, _ -> cont.resume(false) }
                    .setPositiveButton(R.string.button_yes) { _, _ -> cont.resume(true) }
                    .show()
        }
    }

    override fun showMmsSizePicker() = mmsSizeDialog.show(activity!!)

    override fun showSwipeActions() {
        router.pushController(RouterTransaction.with(SwipeActionsController())
                .pushChangeHandler(QkChangeHandler())
                .popChangeHandler(QkChangeHandler()))
    }

    override fun showThemePicker() {
        router.pushController(RouterTransaction.with(ThemePickerController())
                .pushChangeHandler(QkChangeHandler())
                .popChangeHandler(QkChangeHandler()))
    }

    override fun showAbout() {
        router.pushController(RouterTransaction.with(AboutController())
                .pushChangeHandler(QkChangeHandler())
                .popChangeHandler(QkChangeHandler()))
    }

}