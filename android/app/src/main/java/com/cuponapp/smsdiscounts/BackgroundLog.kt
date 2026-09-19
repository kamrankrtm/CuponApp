package com.cuponapp.smsdiscounts

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * دفترچه رخدادهای پس‌زمینه.
 *
 * وقتی پیامک می‌رسد اپ بسته است و هیچ راهی برای دیدن آنچه گذشته وجود
 * ندارد. بدون این دفترچه، «نوتیفیکیشن نیامد» می‌تواند ده علت متفاوت داشته
 * باشد: گیرنده اجرا نشد، مجوز نبود، فیلتر رد کرد، شبکه قطع بود، یا کلید
 * اشتباه بود. هر مرحله اینجا ثبت می‌شود تا علت دقیق دیده شود.
 */
object BackgroundLog {

    private const val STORE = "CuponAppDiag"
    private const val KEY = "events"
    private const val MAX = 40

    /** ثبت یک رخداد؛ هرگز نباید خطا بدهد چون در مسیر بحرانی گیرنده است */
    fun record(context: Context, stage: String, detail: String) {
        try {
            val prefs = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
            val events = try {
                JSONArray(prefs.getString(KEY, "[]"))
            } catch (e: Exception) {
                JSONArray()
            }

            events.put(JSONObject().apply {
                put("at", System.currentTimeMillis())
                put("stage", stage)
                put("detail", detail)
            })

            val trimmed = if (events.length() > MAX) {
                JSONArray().also { out ->
                    for (i in (events.length() - MAX) until events.length()) out.put(events.get(i))
                }
            } else events

            prefs.edit().putString(KEY, trimmed.toString()).apply()
        } catch (e: Exception) {
            // ثبت لاگ نباید هیچ‌وقت جریان اصلی را بشکند
        }
    }

    fun read(context: Context): JSONArray = try {
        JSONArray(context.getSharedPreferences(STORE, Context.MODE_PRIVATE).getString(KEY, "[]"))
    } catch (e: Exception) {
        JSONArray()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(STORE, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }
}
