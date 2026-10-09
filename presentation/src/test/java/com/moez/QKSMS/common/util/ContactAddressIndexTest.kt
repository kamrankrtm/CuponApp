package com.moez.QKSMS.common.util

import com.moez.QKSMS.util.ContactAddressIndex
import org.junit.Assert.*
import org.junit.Test

class ContactAddressIndexTest {
    @Test fun `Iranian prefixes separators and Persian digits reach the same candidate`() {
        val index = ContactAddressIndex(listOf("+98 912-345-6789" to "Ali"))
        for (address in listOf("09123456789", "00989123456789", "۰۹۱۲۳۴۵۶۷۸۹", "٠٩١٢٣٤٥٦٧٨٩")) {
            var compared = ""
            assertEquals("Ali", index.find(address) { _, candidate -> compared = candidate; true })
            assertEquals("+98 912-345-6789", compared)
        }
    }

    @Test fun `matching does not compare thousands of unrelated contacts`() {
        val entries = (0 until 5000).map { "0910" + it.toString().padStart(7, '0') to it }
        val index = ContactAddressIndex(entries)
        var comparisons = 0
        assertEquals(4321, index.find("+989100004321") { _, candidate ->
            comparisons++
            candidate == "09100004321"
        })
        assertEquals(1, comparisons)
        assertNull(index.find("09129999999") { _, _ -> comparisons++; true })
        assertEquals(1, comparisons)
    }

    @Test fun `suffix collisions must pass the original phone validator`() {
        val index = ContactAddressIndex(listOf("01112345678" to "wrong", "02212345678" to "right"))
        var comparisons = 0
        assertEquals("right", index.find("03312345678") { _, candidate ->
            comparisons++
            candidate == "02212345678"
        })
        assertEquals(2, comparisons)
        assertNull(index.find("04412345678") { _, _ -> false })
    }

    @Test fun `short codes and alphabetic addresses remain searchable`() {
        val index = ContactAddressIndex(listOf("1234" to "short", "SHOP" to "shop"))
        assertEquals("short", index.find("۱۲۳۴") { _, candidate -> candidate == "1234" })
        assertEquals("shop", index.find("shop") { first, second -> first.equals(second, true) })
        assertNull(index.find("4321") { _, _ -> true })
    }
}
