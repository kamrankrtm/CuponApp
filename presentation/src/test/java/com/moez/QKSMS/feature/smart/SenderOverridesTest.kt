package com.moez.QKSMS.feature.smart

import com.moez.QKSMS.feature.smart.SenderOverrides.Tab
import com.moez.QKSMS.feature.smart.model.SmsCategory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SenderOverridesTest {

    private val ad = "جشنواره پاییزه با ارسال رایگان همه سفارش‌ها تا پایان هفته"

    @After
    fun tearDown() {
        SenderOverrides.move(listOf("30001234", "09123456789", "SNAPP"), null)
    }

    @Test
    fun `one mobile number matches in every form it arrives in`() {
        val key = SenderOverrides.keyOf("09123456789")
        assertEquals("9123456789", key)
        assertEquals(key, SenderOverrides.keyOf("+989123456789"))
        assertEquals(key, SenderOverrides.keyOf("00989123456789"))
        assertEquals(key, SenderOverrides.keyOf("0912 345 6789"))
        assertEquals(key, SenderOverrides.keyOf("۰۹۱۲۳۴۵۶۷۸۹"))
    }

    @Test
    fun `short codes and names are kept as they are`() {
        assertEquals("30001234", SenderOverrides.keyOf("30001234"))
        assertEquals("98700717", SenderOverrides.keyOf("98700717"))
        assertEquals("SNAPP", SenderOverrides.keyOf("Snapp"))
    }

    @Test
    fun `a sender marked not spam is no longer spam`() {
        assertTrue(SmartSmsClassifier.classify("30001234", ad) is SmsCategory.Spam)

        SenderOverrides.move(listOf("30001234"), Tab.PERSONAL)
        assertTrue(SenderOverrides.isTrusted("30001234"))
        assertEquals(SmsCategory.Personal, SmartSmsClassifier.classify("30001234", ad))

        SenderOverrides.move(listOf("30001234"), null)
        assertFalse(SenderOverrides.isTrusted("30001234"))
        assertTrue(SmartSmsClassifier.classify("30001234", ad) is SmsCategory.Spam)
    }

    @Test
    fun `a person moved to spam is silenced`() {
        val hello = "سلام، فردا جلسه ساعت ۱۰ است"
        assertEquals(SmsCategory.Personal, SmartSmsClassifier.classifyForUser("09123456789", hello))

        SenderOverrides.move(listOf("+989123456789"), Tab.SPAM)
        assertEquals(Tab.SPAM, SenderOverrides.tabFor("09123456789"))
        assertEquals(SmsCategory.Spam, SmartSmsClassifier.classifyForUser("09123456789", hello))
    }

    @Test
    fun `a sender moved to banking notifies as a bank even without a transaction`() {
        SenderOverrides.move(listOf("30001234"), Tab.BANKING)
        val category = SmartSmsClassifier.classifyForUser("30001234", ad)
        assertTrue(category is SmsCategory.Banking)
        assertNull((category as SmsCategory.Banking).isDeposit)
    }

    @Test
    fun `verification codes stay codes whatever tab the sender is in`() {
        val code = "کد تایید شما: 482913"
        SenderOverrides.move(listOf("30001234"), Tab.SPAM)
        assertTrue(SmartSmsClassifier.classifyForUser("30001234", code) is SmsCategory.Otp)

        SenderOverrides.move(listOf("30001234"), Tab.BANKING)
        assertTrue(SmartSmsClassifier.classifyForUser("30001234", code) is SmsCategory.Otp)
    }

    @Test
    fun `undo puts back exactly what was there`() {
        SenderOverrides.move(listOf("SNAPP"), Tab.SPAM)
        val before = SenderOverrides.snapshot(listOf("SNAPP", "30001234"))

        SenderOverrides.move(listOf("SNAPP", "30001234"), Tab.BANKING)
        SenderOverrides.restore(before)

        assertEquals(Tab.SPAM, SenderOverrides.tabFor("SNAPP"))
        assertNull(SenderOverrides.tabFor("30001234"))
    }
}
