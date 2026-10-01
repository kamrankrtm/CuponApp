package com.moez.QKSMS.common.util

import org.junit.Assert.assertEquals
import org.junit.Test

class IranPhoneNumberFormatterTest {

    @Test
    fun `country code without plus becomes local iranian number`() {
        assertEquals("09122719074", normalizeIranianPhoneForDialer("989122719074"))
    }

    @Test
    fun `plus 98 becomes local iranian number`() {
        assertEquals("09122719074", normalizeIranianPhoneForDialer("+98 912 271 9074"))
    }

    @Test
    fun `international 0098 becomes local iranian number`() {
        assertEquals("09122719074", normalizeIranianPhoneForDialer("0098-912-271-9074"))
    }

    @Test
    fun `already local formatted number is cleaned`() {
        assertEquals("09122719074", normalizeIranianPhoneForDialer("0912 271 9074"))
    }

    @Test
    fun `mobile number without zero gets local prefix`() {
        assertEquals("09122719074", normalizeIranianPhoneForDialer("9122719074"))
    }

    @Test
    fun `persian digits are supported`() {
        assertEquals("09122719074", normalizeIranianPhoneForDialer("۹۸۹۱۲۲۷۱۹۰۷۴"))
    }

    @Test
    fun `foreign and service numbers stay unchanged`() {
        assertEquals("+12025550123", normalizeIranianPhoneForDialer("+12025550123"))
        assertEquals("1000", normalizeIranianPhoneForDialer("1000"))
    }
}
