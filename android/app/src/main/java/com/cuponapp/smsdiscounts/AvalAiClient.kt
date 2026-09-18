package com.cuponapp.smsdiscounts

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * تماس با سرویس هوش مصنوعی از سمت نیتیو.
 *
 * فقط برای پیامک‌هایی صدا زده می‌شود که از فیلتر محلی عبور کرده‌اند.
 * از HttpURLConnection استفاده می‌کند تا وابستگی تازه‌ای به اپ اضافه نشود.
 */
object AvalAiClient {

    data class Promo(
        val brand: String,
        val code: String,
        val discountAmount: String,
        val minOrder: String?,
        val instructions: String,
        val expiryDateText: String,
        val expiresAt: String?,
        val categorySlug: String
    )

    private const val SYSTEM_PROMPT = """تو یک دستیار دقیق استخراج کد تخفیف از پیامک‌های تبلیغاتی فارسی هستی.
برای پیامک ورودی یک شیء JSON بده.

قواعد:
- اگر پیامک هیچ تخفیفی ندارد، hasPromoCode را false بگذار.
- code باید دقیقاً همان رشته کد تخفیف در متن باشد. اگر تخفیف بدون کد است "بدون کد" بگذار. کد را از خودت نساز.
- categorySlug فقط یکی از: food, transport, ecommerce, entertainment, supermarket, other.
- instructions کوتاه و عملی باشد.
- expiryDateText عیناً از متن پیامک برداشته شود.
- expiresAt تاریخ انقضا به میلادی YYYY-MM-DD. عبارت‌های نسبی مثل «تا امشب» را نسبت به receivedAt همان پیامک حساب کن، نه نسبت به امروز. اگر اشاره‌ای به انقضا نیست خالی بگذار.
- چیزی که در متن نیست را ننویس.

پاسخ فقط یک شیء JSON با کلیدهای: hasPromoCode, brand, code, discountAmount, minOrder, instructions, expiryDateText, expiresAt, categorySlug"""

    /** تحلیل یک پیامک؛ در صورت نبود کد تخفیف یا هر خطایی null برمی‌گرداند */
    fun analyze(
        settings: AppSettings.Settings,
        sender: String,
        body: String,
        receivedAt: Date
    ): Promo? {
        if (!settings.isUsable) return null

        return try {
            val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val userContent = JSONObject().apply {
                put("sender", sender)
                put("body", body.take(600))
                put("receivedAt", dateFmt.format(receivedAt))
            }.toString()

            val payload = JSONObject().apply {
                put("model", settings.model)
                put("temperature", 0)
                put("response_format", JSONObject().put("type", "json_object"))
                put("messages", JSONArray().apply {
                    put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
                    put(JSONObject().put("role", "user").put("content", userContent))
                })
            }

            val conn = (URL("${settings.baseUrl}/chat/completions").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer ${settings.apiKey}")
                doOutput = true
                connectTimeout = 20_000
                readTimeout = 40_000
            }

            conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }

            if (conn.responseCode !in 200..299) {
                conn.errorStream?.close()
                return null
            }

            val raw = conn.inputStream.bufferedReader().use(BufferedReader::readText)
            val content = JSONObject(raw)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .optString("content", "")

            parsePromo(content)
        } catch (e: Exception) {
            null
        }
    }

    private fun parsePromo(content: String): Promo? {
        val cleaned = content
            .replace(Regex("^\\s*```(?:json)?"), "")
            .replace(Regex("```\\s*$"), "")
            .trim()

        val json = try {
            JSONObject(cleaned)
        } catch (e: Exception) {
            // گاهی مدل متن اضافه می‌چسباند؛ اولین شیء JSON را جدا می‌کنیم
            val start = cleaned.indexOf('{')
            val end = cleaned.lastIndexOf('}')
            if (start < 0 || end <= start) return null
            try {
                JSONObject(cleaned.substring(start, end + 1))
            } catch (e2: Exception) {
                return null
            }
        }

        if (!json.optBoolean("hasPromoCode", false)) return null

        return Promo(
            brand = json.optString("brand", "نامشخص"),
            code = json.optString("code", "بدون کد"),
            discountAmount = json.optString("discountAmount", "تخفیف"),
            minOrder = json.optString("minOrder", "").ifBlank { null },
            instructions = json.optString("instructions", ""),
            expiryDateText = json.optString("expiryDateText", ""),
            expiresAt = json.optString("expiresAt", "").ifBlank { null },
            categorySlug = json.optString("categorySlug", "other")
        )
    }
}
