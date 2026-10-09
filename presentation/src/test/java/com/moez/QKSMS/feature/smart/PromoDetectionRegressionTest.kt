package com.moez.QKSMS.feature.smart

import com.moez.QKSMS.feature.smart.promo.PromoCodeExtractor
import com.moez.QKSMS.feature.smart.promo.PromoParser
import com.moez.QKSMS.feature.smart.promo.PromoReadPolicy
import com.moez.QKSMS.feature.smart.promo.PromoValueParser
import org.junit.Assert.*
import org.junit.Test

class PromoDetectionRegressionTest {
    private val date = 1_800_000_000_000L

    private fun analyze(body: String) = PromoParser.analyze("SNAPPFOOD", body, date, 42L)
    private fun extract(body: String) = PromoCodeExtractor.extractAll(PromoValueParser.normalize(body))

    @Test fun `numeric coupon instruction is not an OTP`() {
        val body = "اسنپ فود: ۵۰ هزار تومان تخفیف؛ با کد ۴۸۲۹۱۳ را وارد کنید"
        assertFalse(SmartSmsClassifier.isOtpMessage(body))
        assertEquals("482913", analyze(body).single().promo.code)
        assertTrue(PromoReadPolicy.shouldMarkRead(analyze(body), date))
    }

    @Test fun `generic your code label in an offer does not capture its price as OTP`() {
        val body = "۵۰۰۰۰ تومان تخفیف اسنپ فود؛ کد شما: FOOD30"
        assertFalse(SmartSmsClassifier.isOtpMessage(body))
        assertEquals("FOOD30", analyze(body).single().promo.code)
    }

    @Test fun `explicit login instructions always stay OTP even with advertising`() {
        val body = "کد ورود: ۴۸۲۹۱۳؛ امروز ۵۰ درصد تخفیف داریم"
        assertTrue(SmartSmsClassifier.isOtpMessage(body))
        assertTrue(analyze(body).isEmpty())
    }

    @Test fun `short labelled coupon and mixed case coupon are accepted`() {
        assertEquals("ABC", extract("کد تخفیف: ABC").first().code)
        assertEquals("AbCd12EfG34", extract("کد تخفیف: AbCd12EfG34").first().code)
        assertTrue(PromoReadPolicy.shouldMarkRead(analyze("۵۰ درصد تخفیف با کد ABC"), date))
    }

    @Test fun `numeric coupon does not capture dates phones or amounts`() {
        listOf("1405/07/20", "12:30", "50,000", "09122719074", "۳۰ درصد").forEach {
            assertTrue("Incorrect code: $it", extract("کد تخفیف: $it").isEmpty())
        }
    }

    @Test fun `unlabelled uncertain candidate stays unread`() {
        val result = analyze("اسنپ فود ۳۰ درصد تخفیف FOOD30")
        assertFalse(result.isEmpty())
        assertFalse(PromoReadPolicy.shouldMarkRead(result, date))
    }

    @Test fun `confirmed coupon is read even when its brand is unknown`() {
        val result = PromoParser.analyze("3000123", "کد تخفیف: SAVE50", date, 42L)
        assertTrue(PromoReadPolicy.shouldMarkRead(result, date))
    }

    @Test fun `expired coupon stays unread`() {
        val result = analyze("۵۰ درصد تخفیف با کد FOOD30 فقط تا امشب")
        assertFalse(PromoReadPolicy.shouldMarkRead(result, date + 3L * 24 * 60 * 60 * 1000))
    }

    @Test fun `AI cannot validate code substrings or link tokens`() {
        assertFalse(PromoCodeExtractor.appearsIn("FOOD30", "کد تخفیف: FOOD300"))
        assertFalse(PromoCodeExtractor.appearsIn("FOOD30", "تخفیف https://shop.ir/FOOD30"))
        assertTrue(PromoCodeExtractor.appearsIn("PAYCNN48", "کد تخفیف: PAY CNN48"))
        assertTrue(PromoCodeExtractor.appearsIn("PAY-CNN48", "کد تخفیف: PAY-CNN48"))
    }

    @Test fun `receipts and used notices never trigger auto read`() {
        listOf("تخفیف خرید؛ کد رهگیری AB12345", "کد FOOD30 روی سفارش شما اعمال شد").forEach {
            assertFalse(PromoReadPolicy.shouldMarkRead(analyze(it), date))
        }
    }
}
