package com.moez.QKSMS.feature.smart.ai

import org.json.JSONObject

/** Reads model IDs supplied by the connected service instead of maintaining an outdated list. */
object AiModelCatalog {
    fun parse(response: String): List<String> {
        val data = JSONObject(response).optJSONArray("data") ?: return emptyList()
        return (0 until data.length()).mapNotNull { index ->
            (data.optJSONObject(index)?.opt("id") as? String)?.trim()?.takeIf { it.isNotEmpty() }
        }.distinct().sortedBy { it.toLowerCase(java.util.Locale.ROOT) }
    }

    fun withCurrent(models: List<String>, current: String): List<String> =
        (listOf(current.trim()).filter { it.isNotEmpty() } + models).distinct()
}
