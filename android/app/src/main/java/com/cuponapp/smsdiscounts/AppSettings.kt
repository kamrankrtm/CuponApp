package com.cuponapp.smsdiscounts

import android.content.Context
import org.json.JSONObject

/**
 * خواندن تنظیماتی که لایه وب ذخیره کرده است.
 *
 * پلاگین Preferences کپسیتور مقادیر را در SharedPreferences با نام
 * «CapacitorStorage» می‌نویسد، پس کد نیتیو می‌تواند همان کلید را بخواند
 * بدون آنکه لازم باشد WebView اجرا شده باشد.
 */
object AppSettings {

    private const val STORE = "CapacitorStorage"
    private const val SETTINGS_KEY = "cuponapp.ai.settings"

    data class Settings(
        val apiKey: String,
        val model: String,
        val baseUrl: String,
        val strictness: String
    ) {
        val isUsable: Boolean get() = apiKey.isNotBlank()
    }

    fun read(context: Context): Settings {
        val prefs = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
        val raw = prefs.getString(SETTINGS_KEY, null) ?: return empty()
        return try {
            val json = JSONObject(raw)
            Settings(
                apiKey = json.optString("apiKey", ""),
                model = json.optString("model", "gemini-2.5-flash-lite"),
                baseUrl = json.optString("baseUrl", "https://api.avalai.ir/v1").trimEnd('/'),
                strictness = json.optString("strictness", "balanced")
            )
        } catch (e: Exception) {
            empty()
        }
    }

    private fun empty() = Settings("", "gemini-2.5-flash-lite", "https://api.avalai.ir/v1", "balanced")
}
