package com.moez.QKSMS.feature.smart

import com.moez.QKSMS.feature.smart.analysis.OtpCopyPolicy
import com.moez.QKSMS.feature.smart.analysis.OtpExtractor
import com.moez.QKSMS.feature.smart.analysis.SmsAnalyzer
import com.moez.QKSMS.feature.smart.analysis.SmsKind
import com.moez.QKSMS.feature.smart.analysis.SmsText
import com.moez.QKSMS.feature.smart.model.SmsCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verification codes: which number is the code, when it may be copied without asking, and
 * how long it lasts. Every expected code here is the one a person reading the SMS would type.
 */
class OtpExtractionTest {

    private val now = 1_800_000_000_000L

    private fun code(body: String): String? = OtpExtractor.extract(body).selected?.code

    private fun analyze(body: String, sender: String = "BANK") = SmsAnalyzer.analyze(sender, body, now)

    // ------------------------------------------------------------ choosing the right number

    @Test
    fun `a card number before the code is not the code`() {
        val result = OtpExtractor.extract("کارت 12345678؛ کد تایید شما: 482913")
        assertEquals("482913", result.selected?.code)
        assertFalse(result.isEligible("12345678"))
        assertTrue(result.autoCopyEligible)
    }

    @Test
    fun `a code starting with 09 keeps its leading zero`() {
        assertEquals("090123", code("کد ورود: 090123"))
        assertEquals("0012", code("رمز دوم شما 0012 است"))
    }

    @Test
    fun `a longer number is never cut down to a code`() {
        assertNull(code("کد ورود: 1234567890"))
        assertNull(code("کد تایید 123456789"))
        assertFalse(OtpExtractor.extract("کد ورود: 1234567890").isEligible("12345678"))
    }

    @Test
    fun `Persian and Arabic digits and invisible characters`() {
        assertEquals("482913", code("کد تایید: ۴۸۲۹۱۳"))
        assertEquals("482913", code("كد تاييد: ٤٨٢٩١٣"))
        // ZWNJ inside the label, a zero-width space and a direction mark inside the number
        assertEquals("482913", code("کد‌تایید: ۴۸۲​۹‏۱۳"))
        // Tatweel and an Arabic kaf, as some gateways send them
        assertEquals("5521", code("كـد ورود شما 5521"))
    }

    @Test
    fun `a multipart message joined from its parts`() {
        val parts = listOf("بانک ملت\nرمز پویا خرید از ", "فروشگاه اینترنتی\nمبلغ: 1,250,000 ریال\nکارت: 6104****1234\n", "رمز: 48291375\nمهلت: 2 دقیقه")
        val result = OtpExtractor.extract(parts.joinToString(""))
        assertEquals("48291375", result.selected?.code)
        assertTrue(result.autoCopyEligible)
        assertEquals(120_000L, result.validityMillis)
    }

    @Test
    fun `labels in Persian and English`() {
        assertEquals("482913", code("Your verification code is 482913. Don't share it."))
        assertEquals("482913", code("482913 is your Google verification code."))
        assertEquals("482913", code("G-482913 is your Google verification code."))
        assertEquals("73421", code("Login code: 73421"))
        assertEquals("7342", code("One-time password: 7342"))
        assertEquals("482913", code("رمز پویا شما: 482913"))
        assertEquals("4829", code("رمز دوم: 4829"))
        assertEquals("482913", code("کد فعال‌سازی حساب شما 482913 می‌باشد"))
        assertEquals("36330", code("کد 36330 را جهت ورود به سامانه بام وارد نمایید"))
        assertEquals("482913", code("رمز یکبار مصرف: 482913"))
    }

    @Test
    fun `numbers that are something else are excluded`() {
        val body = "بانک ملت\nکد تایید: 482913\nمبلغ: 500,000 ریال\nشماره پیگیری: 99887766\n" +
            "تاریخ: 1403/07/20 ساعت 14:32\nتماس: 02188776655\nhttps://bank.ir/r/123456"
        val result = OtpExtractor.extract(body)
        assertEquals("482913", result.selected?.code)
        assertEquals(listOf("482913"), result.candidates.map { it.code })
    }

    @Test
    fun `grouped card and phone numbers are excluded`() {
        val result = OtpExtractor.extract("کد تایید 4829 برای کارت 6037 9918 1234 5678 و تلفن 0912 345 6789")
        assertEquals(listOf("4829"), result.candidates.map { it.code })
    }

    @Test
    fun `masked and dashed cards are excluded`() {
        assertEquals(listOf("482913"), OtpExtractor.extract("کد تایید 482913 کارت 6037-****-****-1234").candidates.map { it.code })
        assertEquals(listOf("482913"), OtpExtractor.extract("کارت ****5678 کد تایید 482913").candidates.map { it.code })
    }

    @Test
    fun `amounts and durations are not codes`() {
        val result = OtpExtractor.extract("رمز پویا 7731 برای خرید 2500 تومان، اعتبار 120 ثانیه")
        assertEquals("7731", result.selected?.code)
        assertFalse(result.isEligible("2500"))
        assertEquals(120_000L, result.validityMillis)
    }

    @Test
    fun `several unlabelled numbers are ambiguous and never auto-copied`() {
        val result = OtpExtractor.extract("کد ورود به سامانه\n4821\n7390")
        assertFalse(result.autoCopyEligible)
        assertEquals(2, result.candidates.size)
        assertTrue(result.best!!.score < OtpExtractor.AUTO_COPY_MIN_SCORE)
    }

    @Test
    fun `a labelled code wins over another number by a clear margin`() {
        val result = OtpExtractor.extract("سفارش 55210 ثبت شد. کد تایید تحویل: 4821")
        assertEquals("4821", result.selected?.code)
        assertFalse(result.isEligible("55210"))
    }

    @Test
    fun `numeric coupons stay coupons`() {
        assertFalse(SmartSmsClassifier.isOtpMessage("اسنپ فود: ۵۰ هزار تومان تخفیف؛ با کد ۴۸۲۹۱۳ را وارد کنید"))
        val analysis = analyze("دیجی‌کالا: کد تخفیف 482913 برای ۲۰٪ تخفیف تا پایان هفته", "DIGIKALA")
        assertEquals(SmsKind.PROMO, analysis.kind)
        assertNull(analysis.otpItem)
    }

    @Test
    fun `a message without a code is no OTP`() {
        assertNull(code("کاربر گرامی، ورود شما به سامانه در ساعت 14:32 ثبت شد"))
        assertNull(code("سلام، فردا ساعت 10 می‌بینمت"))
    }

    // ------------------------------------------------------------ validity

    @Test
    fun `a stated validity sets the expiry`() {
        val analysis = analyze("کد تایید شما 482913 است. این کد تا 2 دقیقه معتبر است")
        assertEquals(now + 120_000L, analysis.otpItem?.expiresAt)
        assertEquals(now + 300_000L, analyze("Your code is 482913. Valid for 5 minutes.").otpItem?.expiresAt)
        assertEquals(now + 600_000L, analyze("کد ورود: 4829 (اعتبار: ده دقیقه)").otpItem?.expiresAt)
    }

    @Test
    fun `an unknown validity stays unknown`() {
        val otp = analyze("کد ورود: 482913").otpItem
        assertNotNull(otp)
        assertNull(otp?.expiresAt)
        // Not claimed expired, not claimed valid until midnight either
        assertFalse(otp!!.isExpired(now + 10L * 60 * 60 * 1000))
    }

    // ------------------------------------------------------------ automatic copying

    private fun decide(body: String, at: Long, supersededByNewer: Boolean = false, alreadyCopied: Boolean = false,
                       enabled: Boolean = true): OtpCopyPolicy.Decision {
        val analysis = analyze(body)
        val otp = analysis.otpItem ?: return OtpCopyPolicy.Decision.NOT_VERIFICATION
        return OtpCopyPolicy.decide(analysis.otp, otp.code, otp.receivedAt, otp.expiresAt, at, enabled,
            supersededByNewer, alreadyCopied)
    }

    @Test
    fun `a clear code is copied once, while it is fresh`() {
        assertEquals(OtpCopyPolicy.Decision.COPY, decide("کد ورود: 482913", now + 1_000))
        assertEquals(OtpCopyPolicy.Decision.ALREADY_COPIED, decide("کد ورود: 482913", now + 1_000, alreadyCopied = true))
        assertEquals(OtpCopyPolicy.Decision.DISABLED, decide("کد ورود: 482913", now + 1_000, enabled = false))
    }

    @Test
    fun `unknown validity allows copying for two minutes only`() {
        assertEquals(OtpCopyPolicy.Decision.COPY, decide("کد ورود: 482913", now + 119_000))
        assertEquals(OtpCopyPolicy.Decision.EXPIRED, decide("کد ورود: 482913", now + 121_000))
    }

    @Test
    fun `a stated validity is honoured`() {
        val body = "کد ورود: 482913. اعتبار 5 دقیقه"
        assertEquals(OtpCopyPolicy.Decision.COPY, decide(body, now + 4 * 60_000))
        assertEquals(OtpCopyPolicy.Decision.EXPIRED, decide(body, now + 5 * 60_000))
    }

    @Test
    fun `a newer code supersedes an older one`() {
        assertEquals(OtpCopyPolicy.Decision.SUPERSEDED, decide("کد ورود: 482913", now + 1_000, supersededByNewer = true))
    }

    @Test
    fun `an ambiguous message is never copied`() {
        assertEquals(OtpCopyPolicy.Decision.AMBIGUOUS, decide("کد ورود به سامانه\n4821\n7390", now + 1_000))
    }

    @Test
    fun `a code the message does not contain is never copied`() {
        val analysis = analyze("کد ورود: 482913")
        val decision = OtpCopyPolicy.decide(analysis.otp, "482914", now, null, now + 1_000, true, false, false, aiSelected = true)
        assertEquals(OtpCopyPolicy.Decision.NOT_ELIGIBLE, decision)
        // Nor a piece of a longer number, even if an AI names it
        val long = analyze("کد ورود: 1234567890")
        assertEquals(OtpCopyPolicy.Decision.NOT_ELIGIBLE,
            OtpCopyPolicy.decide(long.otp, "12345678", now, null, now + 1_000, true, false, false, aiSelected = true))
    }

    // ------------------------------------------------------------ identity

    @Test
    fun `the item carries its message identity and score`() {
        val analysis = SmsAnalyzer.analyze("BANK", "کد ورود: 482913", now, sourceKey = "sms:77")
        val otp = analysis.otpItem!!
        assertEquals("sms:77", otp.sourceKey)
        assertTrue(otp.confidence >= OtpExtractor.AUTO_COPY_MIN_SCORE)
        assertTrue(SmartSmsClassifier.classify("BANK", "کد ورود: 482913", now) is SmsCategory.Otp)
    }

    @Test
    fun `the normaliser maps offsets back to the original`() {
        val original = "کد‌تایید: ۴۸۲​۹۱۳"
        val normalized = SmsText.normalize(original)
        val start = normalized.text.indexOf("482913")
        assertEquals("۴۸۲​۹۱۳", normalized.originalSpan(start, start + 6))
    }
}
