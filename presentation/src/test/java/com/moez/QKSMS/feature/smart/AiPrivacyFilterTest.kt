package com.moez.QKSMS.feature.smart

import com.moez.QKSMS.feature.smart.promo.AiPrivacyFilter
import com.moez.QKSMS.feature.smart.promo.AiPrivacyFilter.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards what may leave the device.
 *
 * A regression here does not produce a visible bug — it silently uploads someone's bank
 * messages to a third party — so every rule is pinned down explicitly.
 */
class AiPrivacyFilterTest {

    @Test
    fun `bank messages never leave the device`() {
        assertEquals(
            Verdict.BLOCKED_SENSITIVE,
            AiPrivacyFilter.judge("BANK", "مبلغ 5,000,000 ریال واریز شد. موجودی: 12,000,000 ریال")
        )
        assertEquals(
            Verdict.BLOCKED_SENSITIVE,
            AiPrivacyFilter.judge("BANK", "خرید با کارت انجام شد، مانده حساب شما کاهش یافت")
        )
    }

    @Test
    fun `one time passwords never leave the device`() {
        assertEquals(Verdict.BLOCKED_SENSITIVE, AiPrivacyFilter.judge("SERVICE", "کد تایید شما: 12345"))
        assertEquals(Verdict.BLOCKED_SENSITIVE, AiPrivacyFilter.judge("X", "رمز پویا: 887766"))
        assertEquals(Verdict.BLOCKED_SENSITIVE, AiPrivacyFilter.judge("X", "Your verification code is 998877"))
    }

    @Test
    fun `card numbers ibans and national ids are recognised structurally`() {
        assertEquals(Verdict.BLOCKED_SENSITIVE, AiPrivacyFilter.judge("X", "کارت 6037 9975 1234 5678 شارژ شد"))
        assertEquals(Verdict.BLOCKED_SENSITIVE, AiPrivacyFilter.judge("X", "شبا IR820540102680020817909002 ثبت شد"))
        assertEquals(Verdict.BLOCKED_SENSITIVE, AiPrivacyFilter.judge("X", "کد ملی 0012345678 تایید شد"))
    }

    @Test
    fun `messages from personal numbers never leave the device`() {
        assertEquals(Verdict.BLOCKED_PERSONAL, AiPrivacyFilter.judge("+989121234567", "سلام، تخفیف گرفتی؟"))
        assertEquals(Verdict.BLOCKED_PERSONAL, AiPrivacyFilter.judge("09351234567", "کد تخفیف برات فرستادم"))
    }

    @Test
    fun `sensitivity outranks marketing wording`() {
        // A message can be both an advert and an account notice; the account notice wins.
        assertEquals(
            Verdict.BLOCKED_SENSITIVE,
            AiPrivacyFilter.judge("BANK", "تخفیف ویژه! موجودی شما 5,000,000 ریال است")
        )
    }

    @Test
    fun `non marketing notices are not uploaded either`() {
        assertEquals(Verdict.BLOCKED_NOT_MARKETING, AiPrivacyFilter.judge("SHOP", "سفارش شما ارسال شد"))
    }

    @Test
    fun `ordinary promotional messages are allowed`() {
        assertTrue(AiPrivacyFilter.isAllowed("SNAPPFOOD", "کد تخفیف FOOD70 با ۷۰ هزار تومان تخفیف"))
        assertTrue(AiPrivacyFilter.isAllowed("DIGIKALA", "جشنواره پاییزه با ۳۰ درصد تخفیف"))
        assertTrue(AiPrivacyFilter.isAllowed("OKALA", "اکالا: ارسال رایگان برای سفارش امروز"))
        assertTrue(AiPrivacyFilter.isAllowed("SHOP", "Get 50% off with promo code SAVE50"))
    }

    @Test
    fun `court and health notices are withheld`() {
        assertFalse(AiPrivacyFilter.isAllowed("ADLIRAN", "ابلاغیه الکترونیک در سامانه ثنا ثبت شد"))
        assertFalse(AiPrivacyFilter.isAllowed("LAB", "نتیجه تست آزمایش شما آماده است"))
    }
}
