package com.moez.QKSMS.feature.smart

import com.moez.QKSMS.feature.smart.model.SmsCategory
import com.moez.QKSMS.feature.smart.promo.BrandRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Bank messages that were filed under Spam or Personal, taken from real inboxes, next to the
 * look-alikes (ads, people talking about money) that must stay where they are.
 */
class BankingDetectionTest {

    private fun banking(sender: String, body: String): SmsCategory.Banking? =
            SmartSmsClassifier.classify(sender, body) as? SmsCategory.Banking

    @Test
    fun `a Melli transfer written with Arabic letters and a signed amount`() {
        val body = "بانك ملي ايران\nانتقال:+7,000,000\nحساب:0101234567001\nمانده:12,345,678\n0626-10:31"
        val category = banking("+98700717", body)
        assertEquals("بانک ملی ایران", category?.bankName)
    }

    @Test
    fun `a Mehr Iran statement that leads with the account number`() {
        val body = "300356873684\n400,000-\n05/06/26_12:40\nمانده:1,234,567"
        val category = banking("B.QMEHRIRAN", body)
        assertEquals("بانک قرض‌الحسنه مهر ایران", category?.bankName)
    }

    @Test
    fun `a wallet top up is a transaction`() {
        val body = "کاربر گرامی بازارپی\nکیف پول بازارپی شما ۱۰,۰۰۰,۰۰۰ تومان شارژ شد."
        val category = banking("+981000123456", body)
        assertEquals("بازارپی", category?.bankName)
    }

    @Test
    fun `a gateway receipt is a transaction`() {
        val body = "زرین‌پال\nخرید از درگاه: nikrealty.ir\nمبلغ: 5,000,000 ریال"
        assertEquals("زرین‌پال", banking("+9899912345", body)?.bankName)
    }

    @Test
    fun `a Blu Bank withdrawal names Blu`() {
        val body = "بلو\nبرداشت پول\nمبلغ: 250,000 تومان\nموجودی: 3,400,000 تومان"
        assertEquals("بلوبانک", banking("9830005513", body)?.bankName)
    }

    @Test
    fun `a bank saved as a contact still goes to Banking`() {
        val body = "بلو\nبرداشت پول\nمبلغ: 250,000 تومان\nموجودی: 3,400,000 تومان"
        val category = SmartSmsClassifier.classifyConversation("9830005513", body, hasSavedContact = true)
        assertTrue(category is SmsCategory.Banking)
    }

    @Test
    fun `a friend saved as a contact stays Personal whatever they write`() {
        val body = "پول رو برداشت کردم از حسابم، فردا میدم"
        assertEquals(SmsCategory.Personal,
                SmartSmsClassifier.classifyConversation("09123456789", body, hasSavedContact = true))
        assertEquals(SmsCategory.Personal, SmartSmsClassifier.classify("09123456789", body))
    }

    @Test
    fun `ads that quote money are still ads`() {
        assertNull(banking("+985000340801", "۱۰۰ هزار تومان طلای دیجیتال هدیه بازار برای شروع پاییز ✨"))
        assertNull(banking("+989998200312", "طلا می‌خوای یا ماشین؟ کسب شانس قرعه‌کشی طلاین\nلغو11"))
        assertNull(banking("+985000303112", "فقط ۱ روز تا پایان جشنواره! روز برنامه‌نویس راست‌چین"))
        assertNull(banking("+989998761175", "کراماتیان گرامی قالیشویی شهاب با ۲۰٪ تخفیف\nلغو۱۱"))
    }

    @Test
    fun `numeric senders only match a brand's own number`() {
        assertNull(BrandRegistry.match("+98700717", ""))
        assertNull(BrandRegistry.match("+981000123456", ""))
        assertEquals("Irancell", BrandRegistry.match("700", "")?.en)
        assertEquals("SnappFood", BrandRegistry.match("+983000445", "")?.en)
        assertEquals("SnappFood", BrandRegistry.match("+9830004451", "")?.en)
    }

    @Test
    fun `a short code behind the country code is not a mobile`() {
        assertFalse(SmartSmsClassifier.isPersonalNumber("9830005513"))
        assertTrue(SmartSmsClassifier.isPersonalNumber("+989123456789"))
        assertTrue(SmartSmsClassifier.isPersonalNumber("00989123456789"))
        assertTrue(SmartSmsClassifier.isPersonalNumber("0912 345 6789"))
        assertTrue(SmartSmsClassifier.isPersonalNumber("9123456789"))
        assertFalse(SmartSmsClassifier.isPersonalNumber("+989998761175"))
    }

    @Test
    fun `plain subscription notices are not banking`() {
        assertFalse(SmartSmsClassifier.classify("SNAPP",
                "کاربر عزیز اسنپ‌پرو، هزینه‌ی اشتراک اسنپ‌پرو از شنبه افزایش پیدا می‌کند") is SmsCategory.Banking)
    }
}
