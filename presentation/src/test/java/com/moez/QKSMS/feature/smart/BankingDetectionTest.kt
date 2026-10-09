package com.moez.QKSMS.feature.smart

import com.moez.QKSMS.feature.smart.analysis.Direction
import com.moez.QKSMS.feature.smart.analysis.MoneyUnit
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

    @Test
    fun `amounts without a unit are read, and the sign sets the direction`() {
        // No unit written means none is claimed: these used to come back as "… ریال"
        val melli = banking("+98700717", "بانك ملي ايران\nانتقال:+7,000,000\nحساب:66005\nمانده:25,817,272\n0701-13:49")
        assertEquals("7,000,000", melli?.amount)
        assertEquals(true, melli?.isDeposit)
        assertEquals(MoneyUnit.UNKNOWN, melli?.details?.transaction?.unit)

        val fee = banking("+98700717", "بانك ملي ايران\nكارمزد:-1,008,000\nحساب:56007\nمانده:14,646,341\n0628-09:29")
        assertEquals("1,008,000", fee?.amount)
        assertEquals(false, fee?.isDeposit)
        assertNull(fee?.details?.transaction)
        assertEquals(1_008_000L, fee?.details?.fee?.value)

        val mehr = banking("B.QMEHRIRAN", "300356873684\n1,000,000+\n1405/6/21-17:32\nمانده:4,405,082")
        assertEquals("1,000,000", mehr?.amount)
        assertEquals(true, mehr?.isDeposit)

        val profit = banking("Bank Mellat", "واریز سود کوتاه مدت\nحساب5786970551\nمبلغ2,319\n05/07/01")
        assertEquals("2,319", profit?.amount)
        assertEquals(true, profit?.isDeposit)

        // Stated units are kept as stated
        val debt = banking("Bank Mellat", "بانک ملت\nمشتری گرامی، پرداخت بدهی ش.ق 1404785794911 بمبلغ 94,076,000 ریال از محل حساب بشماره 7772286851 انجام شد.")
        assertEquals("94,076,000 ریال", debt?.amount)
        assertEquals(false, debt?.isDeposit)

        val wallet = banking("+981000123456", "کیف پول بازارپی شما ۱۰,۰۰۰,۰۰۰ تومان شارژ شد.")
        assertEquals("10,000,000 تومان", wallet?.amount)
        assertEquals(true, wallet?.isDeposit)
    }

    // ------------------------------------------------------------ amounts, balance and fee

    @Test
    fun `the balance is not the withdrawal even when it comes first and is signed`() {
        val category = banking("Bank Mellat", "بانک ملت\nمانده:+9,000,000؛ برداشت:500,000")
        val details = category?.details
        assertEquals(500_000L, details?.transaction?.value)
        assertEquals(9_000_000L, details?.balance?.value)
        assertEquals(Direction.DEBIT, details?.direction)
        assertEquals(false, category?.isDeposit)
    }

    @Test
    fun `transaction, balance and fee are read separately`() {
        val details = banking("Bank Melli", "بانک ملی ایران\nبرداشت: 1,200,000 ریال\nکارمزد: 7,200 ریال\nمانده: 3,450,000 ریال\n1403/07/20-14:32")?.details
        assertEquals(1_200_000L, details?.transaction?.value)
        assertEquals(7_200L, details?.fee?.value)
        assertEquals(3_450_000L, details?.balance?.value)
        assertEquals(MoneyUnit.RIAL, details?.transaction?.unit)
        assertEquals(Direction.DEBIT, details?.direction)
    }

    @Test
    fun `ungrouped amounts, Arabic digits and trailing signs`() {
        val details = banking("Bank Tejarat", "بانک تجارت\nحساب: 1234\n٢٥٠٠٠٠٠-\nمانده: ١٢٣٤٥٦٧٨")?.details
        assertEquals(2_500_000L, details?.transaction?.value)
        assertEquals(Direction.DEBIT, details?.direction)
        assertEquals(12_345_678L, details?.balance?.value)
    }

    @Test
    fun `a direction word in the amount's own line decides`() {
        val details = banking("Bank Saderat", "بانک صادرات\nانتقال از حساب 1234\nواریز: 700,000 ریال\nمانده: 2,000,000 ریال")?.details
        assertEquals(700_000L, details?.transaction?.value)
        assertEquals(Direction.CREDIT, details?.direction)
    }

    @Test
    fun `advertising words do not cancel a real receipt`() {
        val body = "بانک ملت\nخرید از فروشگاه رفاه\nمبلغ: 1,200,000 ریال\nمانده: 5,000,000 ریال\nهدیه: ۱۰٪ تخفیف در خرید بعدی"
        val category = banking("Bank Mellat", body)
        assertEquals("بانک ملت", category?.bankName)
        assertEquals(1_200_000L, category?.details?.transaction?.value)
        assertEquals(Direction.DEBIT, category?.details?.direction)
    }

    @Test
    fun `the issuer is the sender, not the destination bank`() {
        val body = "انتقال وجه به بانک ملی ایران\nمبلغ: 500,000 ریال\nمانده: 2,000,000 ریال"
        val category = banking("Bank Mellat", body)
        assertEquals("بانک ملت", category?.bankName)
        assertEquals("بانک ملی ایران", category?.details?.destinationBank)
    }

    @Test
    fun `the issuer is the signature, not the bank money went to`() {
        val body = "بانک ملت\nانتقال به حساب 0101234567001 نزد بانک ملی\nمبلغ: 500,000 ریال\nمانده: 2,000,000 ریال"
        val category = banking("+98700717", body)
        assertEquals("بانک ملت", category?.bankName)
        assertEquals("بانک ملی ایران", category?.details?.destinationBank)
    }

    @Test
    fun `loans, instalments and a bank's name alone are not receipts`() {
        assertNull(banking("+985000123", "وام ۵۰ میلیون تومانی بدون ضامن از بانک ملت با اقساط ۳۶ ماهه"))
        assertNull(banking("+985000123", "پرداخت قسط آسان با اپلیکیشن ما؛ همین حالا نصب کنید"))
        assertNull(banking("Bank Mellat", "مشتری گرامی، قسط وام شما سررسید شده است"))
        assertNull(banking("+985000123", "پول خود را در بانک پاسارگاد سرمایه‌گذاری کنید"))
    }

    @Test
    fun `an advertisement that quotes a purchase is not a receipt`() {
        assertNull(banking("+985000303112", "با خرید ۵۰۰,۰۰۰ تومانی از فروشگاه ما ۲۰٪ تخفیف بگیرید\nلغو۱۱"))
        assertNull(banking("+985000303112", "فقط ۳ روز مانده تا پایان جشنواره"))
    }

    @Test
    fun `a bank's verification code is an OTP, but its conversation stays under Banking`() {
        val body = "بانک ملت\nرمز پویا: 48291375\nمبلغ: 1,250,000 ریال"
        assertTrue(SmartSmsClassifier.classify("Bank Mellat", body) is SmsCategory.Otp)
        assertTrue(SmartSmsClassifier.classifyConversation("Bank Mellat", body, hasSavedContact = false) is SmsCategory.Banking)
    }
}
