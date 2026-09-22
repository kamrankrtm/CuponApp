package com.moez.QKSMS.feature.smart.promo

import com.moez.QKSMS.feature.smart.model.PromoItem
import org.json.JSONArray
import org.json.JSONObject

/**
 * Turns promos into JSON and back.
 *
 * Kept apart from the storage layer so the conversion can be unit-tested on a plain JVM, and
 * so the on-disk shape is defined in exactly one place.
 */
object PromoCodec {

    /** Bumped whenever the stored shape changes; unknown versions are discarded, not guessed at. */
    const val SCHEMA_VERSION = 1

    fun toJson(promo: PromoItem): JSONObject = JSONObject().apply {
        put("id", promo.id)
        put("brand", promo.brand)
        put("brandEn", promo.brandEn)
        put("category", promo.category)
        put("categorySlug", promo.categorySlug)
        put("code", promo.code)
        put("discountAmount", promo.discountAmount)
        put("description", promo.description)
        put("minOrder", promo.minOrder ?: JSONObject.NULL)
        put("instructions", promo.instructions)
        put("expiryDateText", promo.expiryDateText)
        put("sender", promo.sender)
        put("body", promo.body)
        put("receivedAt", promo.receivedAt)
        put("discountType", promo.discountType.name)
        put("discountValue", promo.discountValue)
        put("minOrderValue", promo.minOrderValue)
        put("expiresAt", promo.expiresAt ?: JSONObject.NULL)
        put("expiryIsExplicit", promo.expiryIsExplicit)
        put("confidence", promo.confidence)
        put("brandColor", promo.brandColor)
        put("appPackage", promo.appPackage ?: JSONObject.NULL)
        put("website", promo.website ?: JSONObject.NULL)
        put("isUsed", promo.isUsed)
        put("isInvalid", promo.isInvalid)
        put("isPinned", promo.isPinned)
    }

    fun fromJson(json: JSONObject): PromoItem? {
        val code = json.optString("code").trim()
        if (code.isBlank()) return null

        val typeName = json.optString("discountType", DiscountType.UNKNOWN.name)
        val discountType = try {
            DiscountType.valueOf(typeName)
        } catch (e: IllegalArgumentException) {
            DiscountType.UNKNOWN
        }

        return PromoItem(
            id = json.optString("id", "promo-${code.hashCode()}"),
            brand = json.optString("brand", "سایر فروشگاه‌ها"),
            brandEn = json.optString("brandEn", ""),
            category = json.optString("category", "سایر"),
            categorySlug = json.optString("categorySlug", BrandRegistry.SLUG_OTHER),
            code = code,
            discountAmount = json.optString("discountAmount", "تخفیف ویژه"),
            description = json.optString("description", ""),
            minOrder = json.optStringOrNull("minOrder"),
            instructions = json.optString("instructions", ""),
            expiryDateText = json.optString("expiryDateText", "مهلت اعلام نشده"),
            sender = json.optString("sender", ""),
            body = json.optString("body", ""),
            receivedAt = json.optLong("receivedAt", System.currentTimeMillis()),
            discountType = discountType,
            discountValue = json.optLong("discountValue", 0L),
            minOrderValue = json.optLong("minOrderValue", 0L),
            expiresAt = if (json.isNull("expiresAt")) null else json.optLong("expiresAt"),
            expiryIsExplicit = json.optBoolean("expiryIsExplicit", false),
            confidence = json.optInt("confidence", 100),
            brandColor = json.optInt("brandColor", 0),
            appPackage = json.optStringOrNull("appPackage"),
            website = json.optStringOrNull("website"),
            isUsed = json.optBoolean("isUsed", false),
            isInvalid = json.optBoolean("isInvalid", false),
            isPinned = json.optBoolean("isPinned", false)
        )
    }

    fun encodeList(promos: List<PromoItem>): String {
        val array = JSONArray()
        for (promo in promos) {
            array.put(toJson(promo))
        }
        return JSONObject().apply {
            put("version", SCHEMA_VERSION)
            put("promos", array)
        }.toString()
    }

    fun decodeList(raw: String?): List<PromoItem> {
        if (raw == null || raw.isBlank()) return emptyList()
        return try {
            val root = JSONObject(raw)
            if (root.optInt("version", 0) != SCHEMA_VERSION) return emptyList()
            val array = root.optJSONArray("promos") ?: return emptyList()
            val result = ArrayList<PromoItem>(array.length())
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                fromJson(obj)?.let { result.add(it) }
            }
            result
        } catch (e: Exception) {
            // A corrupt cache is not worth crashing over; rebuilding from the inbox is cheap.
            emptyList()
        }
    }

    private fun JSONObject.optStringOrNull(key: String): String? {
        if (isNull(key)) return null
        val value = optString(key)
        return if (value.isBlank()) null else value
    }
}
