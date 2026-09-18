package com.cuponapp.smsdiscounts

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * صف کدهای تخفیفی که در پس‌زمینه پیدا شده‌اند.
 *
 * وقتی پیامک می‌رسد اپ معمولاً بسته است، پس نتیجه جایی نگه داشته می‌شود
 * تا دفعه بعد که رابط کاربری بالا می‌آید آن را برداشته و به لیست خود
 * اضافه کند.
 */
object PendingPromoStore {

    private const val STORE = "CuponAppPending"
    private const val KEY = "pendingPromos"
    private const val MAX = 100

    fun add(context: Context, promo: AvalAiClient.Promo, sender: String, body: String, timestamp: Long, simLabel: String) {
        val prefs = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
        val existing = try {
            JSONArray(prefs.getString(KEY, "[]"))
        } catch (e: Exception) {
            JSONArray()
        }

        val item = JSONObject().apply {
            put("brand", promo.brand)
            put("code", promo.code)
            put("discountAmount", promo.discountAmount)
            put("minOrder", promo.minOrder ?: "")
            put("instructions", promo.instructions)
            put("expiryDateText", promo.expiryDateText)
            put("expiresAt", promo.expiresAt ?: "")
            put("categorySlug", promo.categorySlug)
            put("sender", sender)
            put("body", body)
            put("timestamp", timestamp)
            put("simLabel", simLabel)
        }

        existing.put(item)

        // فقط تازه‌ترین‌ها نگه داشته می‌شوند تا صف بی‌نهایت رشد نکند
        val trimmed = if (existing.length() > MAX) {
            JSONArray().also { out ->
                for (i in (existing.length() - MAX) until existing.length()) {
                    out.put(existing.get(i))
                }
            }
        } else existing

        prefs.edit().putString(KEY, trimmed.toString()).apply()
    }

    /** خواندن و خالی کردن صف؛ لایه وب پس از برداشتن، مالک داده است */
    fun drain(context: Context): JSONArray {
        val prefs = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
        val result = try {
            JSONArray(prefs.getString(KEY, "[]"))
        } catch (e: Exception) {
            JSONArray()
        }
        prefs.edit().remove(KEY).apply()
        return result
    }

    /** اثر انگشت پیامک‌های تحلیل‌شده، تا یک پیامک دوبار هزینه نداشته باشد */
    fun isAlreadySeen(context: Context, fingerprint: String): Boolean {
        val prefs = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
        val seen = prefs.getStringSet("seen", emptySet()) ?: emptySet()
        return seen.contains(fingerprint)
    }

    fun markSeen(context: Context, fingerprint: String) {
        val prefs = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
        val seen = HashSet(prefs.getStringSet("seen", emptySet()) ?: emptySet())
        seen.add(fingerprint)
        // سقف ساده برای جلوگیری از رشد بی‌پایان مجموعه
        val bounded = if (seen.size > 2000) seen.toList().takeLast(2000).toSet() else seen
        prefs.edit().putStringSet("seen", bounded).apply()
    }
}
