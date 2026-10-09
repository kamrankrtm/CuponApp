package com.moez.QKSMS.util

import java.util.Locale

/** Limits phone matching to plausible candidates instead of comparing every contact. */
class ContactAddressIndex<T>(entries: Iterable<Pair<String, T>>) {
    private val buckets = HashMap<String, MutableList<Pair<String, T>>>()

    init {
        entries.forEach { entry ->
            keys(entry.first).forEach { key -> buckets.getOrPut(key) { ArrayList() }.add(entry) }
        }
    }

    fun find(address: String, matches: (String, String) -> Boolean): T? {
        val checked = HashSet<String>()
        for (key in keys(address)) {
            for ((number, contact) in buckets[key].orEmpty()) {
                if (checked.add(number) && matches(address, number)) return contact
            }
        }
        return null
    }

    private fun keys(address: String): List<String> {
        val normalized = address.map { char ->
            when (char) {
                in '۰'..'۹' -> '0' + (char - '۰')
                in '٠'..'٩' -> '0' + (char - '٠')
                else -> char
            }
        }.joinToString("").trim().toLowerCase(Locale.ROOT)
        val digits = normalized.filter { it in '0'..'9' }
        val keys = arrayListOf("text:$normalized")
        if (digits.isNotEmpty()) keys.add("digits:$digits")
        if (digits.length >= 10) keys.add("last10:${digits.takeLast(10)}")
        if (digits.length >= 7) keys.add("last7:${digits.takeLast(7)}")
        return keys
    }
}
