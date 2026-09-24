package com.moez.QKSMS.feature.smart

import com.moez.QKSMS.feature.smart.model.SmsCategory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrustedSendersTest {

    @After
    fun tearDown() {
        TrustedSenders.untrust(listOf("30001234", "09123456789", "SNAPP"))
    }

    @Test
    fun `one mobile number matches in every form it arrives in`() {
        val key = TrustedSenders.keyOf("09123456789")
        assertEquals("9123456789", key)
        assertEquals(key, TrustedSenders.keyOf("+989123456789"))
        assertEquals(key, TrustedSenders.keyOf("00989123456789"))
        assertEquals(key, TrustedSenders.keyOf("0912 345 6789"))
        assertEquals(key, TrustedSenders.keyOf("۰۹۱۲۳۴۵۶۷۸۹"))
    }

    @Test
    fun `short codes and names are kept as they are`() {
        assertEquals("30001234", TrustedSenders.keyOf("30001234"))
        assertEquals("98700717", TrustedSenders.keyOf("98700717"))
        assertEquals("SNAPP", TrustedSenders.keyOf("Snapp"))
    }

    @Test
    fun `a trusted sender is no longer spam`() {
        val ad = "جشنواره پاییزه با ارسال رایگان همه سفارش‌ها تا پایان هفته"
        assertTrue(SmartSmsClassifier.classify("30001234", ad) is SmsCategory.Spam)

        TrustedSenders.trust(listOf("30001234"))
        assertTrue(TrustedSenders.isTrusted("30001234"))
        assertEquals(SmsCategory.Personal, SmartSmsClassifier.classify("30001234", ad))

        TrustedSenders.untrust(listOf("30001234"))
        assertFalse(TrustedSenders.isTrusted("30001234"))
        assertTrue(SmartSmsClassifier.classify("30001234", ad) is SmsCategory.Spam)
    }
}
