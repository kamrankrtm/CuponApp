package com.moez.QKSMS.model

import java.util.concurrent.ConcurrentHashMap

object ContactNameCache {
    private val cache = ConcurrentHashMap<String, String>()

    fun get(last10Digits: String): String? = cache[last10Digits]

    fun put(last10Digits: String, name: String) {
        if (last10Digits.isNotBlank() && name.isNotBlank()) {
            cache[last10Digits] = name
        }
    }

    fun clear() {
        cache.clear()
    }
}
