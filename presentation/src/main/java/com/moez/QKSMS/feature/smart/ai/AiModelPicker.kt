package com.moez.QKSMS.feature.smart.ai

import android.app.Activity
import android.text.Editable
import android.text.TextWatcher
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.moez.QKSMS.common.widget.TextInputDialog
import com.moez.QKSMS.util.Preferences

object AiModelPicker {
    fun show(activity: Activity, prefs: Preferences, onSelected: (String) -> Unit) {
        val loading = AlertDialog.Builder(activity)
            .setTitle("انتخاب مدل هوش مصنوعی")
            .setMessage("در حال دریافت مدل‌های سرویس…")
            .setNegativeButton("انصراف", null)
            .show()
        AiPromoExtractor.fetchModels(prefs.aiApiKey.get(), prefs.aiBaseUrl.get()) { models, error ->
            if (activity.isFinishing || activity.isDestroyed || !loading.isShowing) return@fetchModels
            loading.dismiss()
            if (error != null) Toast.makeText(activity, error, Toast.LENGTH_LONG).show()
            showModels(activity, prefs, models, onSelected)
        }
    }

    private fun showModels(activity: Activity, prefs: Preferences, models: List<String>, onSelected: (String) -> Unit) {
        val current = prefs.aiModel.get().ifBlank { AiPromoExtractor.DEFAULT_MODEL }
        val options = AiModelCatalog.withCurrent(models, current)
        val padding = (16 * activity.resources.displayMetrics.density).toInt()
        val search = EditText(activity).apply {
            hint = "جست‌وجوی نام مدل"
            setSingleLine(true)
        }
        val adapter = ArrayAdapter(activity, android.R.layout.simple_list_item_single_choice, options.toMutableList())
        val list = ListView(activity).apply {
            choiceMode = ListView.CHOICE_MODE_SINGLE
            this.adapter = adapter
            setItemChecked(0, true)
        }
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, 0, padding, 0)
            addView(search)
            addView(list, LinearLayout.LayoutParams(-1, (320 * activity.resources.displayMetrics.density).toInt()))
        }
        fun select(model: String) {
            prefs.aiModel.set(model)
            onSelected(model)
        }
        val dialog = AlertDialog.Builder(activity)
            .setTitle("مدل فعلی: $current")
            .setView(content)
            .setNegativeButton("انصراف", null)
            .setNeutralButton("ورود دستی") { _, _ ->
                TextInputDialog(activity, "نام دقیق مدل") { text ->
                    val model = text.trim()
                    if (model.isNotEmpty()) select(model)
                    else Toast.makeText(activity, "نام مدل نمی‌تواند خالی باشد", Toast.LENGTH_SHORT).show()
                }.setText(current).show()
            }
            .create()
        list.setOnItemClickListener { _, _, position, _ ->
            adapter.getItem(position)?.let { select(it) }
            dialog.dismiss()
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val visible = options.filter { it.contains(s?.toString().orEmpty(), ignoreCase = true) }
                adapter.clear()
                adapter.addAll(visible)
                list.clearChoices()
                val selected = visible.indexOf(current)
                if (selected >= 0) list.setItemChecked(selected, true)
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        dialog.show()
    }
}
