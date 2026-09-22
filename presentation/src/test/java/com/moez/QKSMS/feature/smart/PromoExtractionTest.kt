package com.moez.QKSMS.feature.smart

import com.moez.QKSMS.common.util.JalaliCalendar
import com.moez.QKSMS.feature.smart.model.PromoItem
import com.moez.QKSMS.feature.smart.promo.BrandRegistry
import com.moez.QKSMS.feature.smart.promo.DiscountType
import com.moez.QKSMS.feature.smart.promo.PromoCodeExtractor
import com.moez.QKSMS.feature.smart.promo.PromoRanker
import com.moez.QKSMS.feature.smart.promo.PromoRow
import com.moez.QKSMS.feature.smart.promo.PromoSection
import com.moez.QKSMS.feature.smart.promo.PromoValueParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the discount engine against the kinds of SMS Iranian services actually send.
 *
 * Everything under test is deliberately free of Android dependencies so these run on the JVM.
 */
class PromoExtractionTest {

    /** A fixed "now" so date-sensitive assertions do not drift: 1 Mehr 1404, 10:00. */
    private val now = JalaliCalendar.toMillis(1404, 7, 1, endOfDay = false) + 10L * 60 * 60 * 1000

    private val hour = 60L * 60 * 1000
    private val day = 24 * hour

    // ------------------------------------------------------------------ brand identification

    @Test
    fun `identifies brand from body`() {
        val promo = SmartSmsClassifier.extractPromo(
            "SNAPPFOOD",
            "اسنپ‌فود: با کد تخفیف FOOD70 مبلغ ۷۰ هزار تومان تخفیف بگیرید",
            now
        )
        assertNotNull(promo)
        assertEquals("اسنپ‌فود", promo!!.brand)
        assertEquals(BrandRegistry.SLUG_FOOD, promo.categorySlug)
    }

    @Test
    fun `specific brand beats its own prefix`() {
        // "تپسی فود" must not collapse into "تپسی" just because the sender id is shared.
        val promo = SmartSmsClassifier.extractPromo(
            "TAPSI",
            "تپسی فود؛ کد تخفیف: TPF50 - ۵۰ درصد تخفیف سفارش غذا",
            now
        )
        assertNotNull(promo)
        assertEquals("تپسی‌فود", promo!!.brand)
    }

    @Test
    fun `brand keyword inside an unrelated phrase does not match`() {
        // "گوگل کروم" used to be filed under the clothing shop "کروم" purely because that
        // branch sat early in the old `when` chain.
        val promo = SmartSmsClassifier.extractPromo(
            "GOOGLE",
            "بروزرسانی گوگل کروم منتشر شد. کد تخفیف CHROME50 برای اشتراک",
            now
        )
        assertNotNull(promo)
        assertNotEquals("فروشگاه کروم", promo!!.brand)
        // Unrecognised senders fall back to the sender's own name rather than a wrong brand.
        assertEquals(BrandRegistry.SLUG_OTHER, promo.categorySlug)
    }

    @Test
    fun `zero width non joiner spelling matches the same brand`() {
        val withZwnj = SmartSmsClassifier.extractPromo(
            "X", "دیجی‌کالا کد تخفیف DGK20 با ۲۰ درصد تخفیف", now
        )
        val withSpace = SmartSmsClassifier.extractPromo(
            "X", "دیجی کالا کد تخفیف DGK20 با ۲۰ درصد تخفیف", now
        )
        assertEquals("دیجی‌کالا", withZwnj?.brand)
        assertEquals("دیجی‌کالا", withSpace?.brand)
    }

    // ------------------------------------------------------------------ code extraction

    @Test
    fun `rejects messages without a coupon code`() {
        val orderUpdate = SmartSmsClassifier.extractPromo(
            "SNAPPFOOD", "سفارش شما ثبت شد و در حال آماده‌سازی است.", now
        )
        assertNull(orderUpdate)

        val bankSms = SmartSmsClassifier.extractPromo(
            "BANK", "مبلغ 5,000,000 ریال از حساب شما برداشت شد. موجودی: 12,000,000 ریال", now
        )
        assertNull(bankSms)
    }

    @Test
    fun `does not mistake a phone number or url for a code`() {
        val promo = SmartSmsClassifier.extractPromo(
            "SHOP",
            "فروشگاه ما: 09121234567 - https://example.ir - کد تخفیف SHOP25 با ۲۵ درصد تخفیف",
            now
        )
        assertNotNull(promo)
        assertEquals("SHOP25", promo!!.code)
    }

    @Test
    fun `labelled code scores higher confidence than a bare token`() {
        val labelled = SmartSmsClassifier.extractPromo(
            "X", "دیجی‌کالا کد تخفیف: DGK30 با ۳۰ درصد تخفیف", now
        )
        val bare = SmartSmsClassifier.extractPromo(
            "X", "دیجی‌کالا تخفیف ویژه\nDGKBARE30\nهمین حالا", now
        )
        assertNotNull(labelled)
        assertNotNull(bare)
        assertTrue(
            "labelled=${labelled!!.confidence} bare=${bare!!.confidence}",
            labelled.confidence > bare.confidence
        )
    }

    // ------------------------------------------------------------------ amounts

    @Test
    fun `parses toman amounts percentages and free shipping`() {
        val amount = SmartSmsClassifier.extractPromo(
            "X", "اسنپ‌فود کد تخفیف A1CODE برای ۷۰ هزار تومان تخفیف", now
        )
        assertEquals(DiscountType.AMOUNT, amount?.discountType)
        assertEquals(70_000L, amount?.discountValue)

        val percent = SmartSmsClassifier.extractPromo(
            "X", "دیجی‌کالا کد تخفیف B2CODE با ۳۰ درصد تخفیف", now
        )
        assertEquals(DiscountType.PERCENT, percent?.discountType)
        assertEquals(30L, percent?.discountValue)

        val shipping = SmartSmsClassifier.extractPromo(
            "X", "اکالا کد تخفیف C3CODE و ارسال رایگان سفارش", now
        )
        assertEquals(DiscountType.FREE_SHIPPING, shipping?.discountType)
    }

    @Test
    fun `converts rial to toman`() {
        val promo = SmartSmsClassifier.extractPromo(
            "X", "تکنولایف کد تخفیف TL300 معادل ۳,۰۰۰,۰۰۰ ریال تخفیف", now
        )
        assertEquals(300_000L, promo?.discountValue)
    }

    @Test
    fun `reads numbers spelled out in words`() {
        val promo = SmartSmsClassifier.extractPromo(
            "X", "مدیسه: بیست و پنج درصد تخفیف پوشاک با کد تخفیف MDS25", now
        )
        assertEquals(DiscountType.PERCENT, promo?.discountType)
        assertEquals(25L, promo?.discountValue)
    }

    @Test
    fun `minimum order is not mistaken for the discount`() {
        val promo = SmartSmsClassifier.extractPromo(
            "X",
            "اسنپ‌فود کد تخفیف SF30 با ۳۰ درصد تخفیف، حداقل خرید ۲۰۰ هزار تومان",
            now
        )
        assertNotNull(promo)
        assertEquals(DiscountType.PERCENT, promo!!.discountType)
        assertEquals(30L, promo.discountValue)
        assertEquals(200_000L, promo.minOrderValue)
    }

    @Test
    fun `formats a million and a half readably`() {
        assertEquals("1.5 میلیون تومان", PromoValueParser.formatTomans(1_500_000L))
        assertEquals("2 میلیون تومان", PromoValueParser.formatTomans(2_000_000L))
        assertEquals("200 هزار تومان", PromoValueParser.formatTomans(200_000L))
    }

    // ------------------------------------------------------------------ expiry

    @Test
    fun `honours a deadline written only in the body`() {
        // The old implementation only looked at a pre-extracted expiry phrase, so a date
        // sitting in the message body was ignored entirely.
        val promo = SmartSmsClassifier.extractPromo(
            "OKALA",
            "اکالا تخفیف ویژه! کد تخفیف OKL50 برای ۵۰ هزار تومان. این پیشنهاد تا ۱۴۰۴/۰۷/۰۳ ادامه دارد.",
            now
        )
        assertNotNull(promo)
        assertTrue(promo!!.expiryIsExplicit)
        assertFalse(promo.isExpired(JalaliCalendar.toMillis(1404, 7, 2, endOfDay = false)))
        assertTrue(promo.isExpired(JalaliCalendar.toMillis(1404, 7, 4, endOfDay = false)))
    }

    @Test
    fun `tonight expires at the end of the day it arrived`() {
        val promo = SmartSmsClassifier.extractPromo(
            "X", "اسنپ‌فود کد تخفیف TONIGHT9 با ۵۰ هزار تومان تخفیف تا پایان امشب", now
        )
        assertNotNull(promo)
        assertFalse(promo!!.isExpired(now))
        assertTrue(promo.isExpired(now + day))
    }

    @Test
    fun `a stated long deadline survives past thirty days`() {
        // The old blanket "older than 30 days is expired" rule deleted these silently.
        val received = now - 60 * day
        val promo = SmartSmsClassifier.extractPromo(
            "DIGIKALA",
            "دیجی‌کالا: کد تخفیف DGK100 با ۱۰۰ هزار تومان تخفیف، معتبر تا ۱۴۰۵/۰۱/۰۱",
            received
        )
        assertNotNull(promo)
        assertTrue(promo!!.expiryIsExplicit)
        assertFalse(promo.isExpired(now))
    }

    @Test
    fun `an unstated deadline is marked as assumed and lasts a week`() {
        val promo = SmartSmsClassifier.extractPromo(
            "X", "باسلام کد تخفیف BSLREF با ۵۰ هزار تومان تخفیف اولین خرید", now
        )
        assertNotNull(promo)
        assertFalse(promo!!.expiryIsExplicit)
        assertFalse("alive inside the assumed window", promo.isExpired(now + 6 * day))
        assertTrue("gone once the assumed week is up", promo.isExpired(now + 8 * day))
    }

    @Test
    fun `countdown label reads naturally`() {
        val promo = promoExpiringIn(4 * hour)
        assertEquals("۴ ساعت مانده", promo.remainingLabel(now))
        assertTrue(promo.isUrgent(now))

        val later = promoExpiringIn(6 * day)
        assertEquals("۶ روز مانده", later.remainingLabel(now))
        assertFalse(later.isUrgent(now))
    }

    // ------------------------------------------------------------------ not an offer

    @Test
    fun `a bank login code is never a discount`() {
        // Reported from a device: this landed on the discounts tab, and the SMS Retriever hash
        // on the last line was offered as the coupon to copy.
        val promo = SmartSmsClassifier.extractPromo(
            "Bank Melli",
            "بانک ملی\nکد 36330 را جهت ورود به سامانه بام وارد نمایید.\nQVcOXy7hhZr",
            now
        )
        assertNull(promo)
    }

    @Test
    fun `a bank login code is recognised as an otp`() {
        // It contains "کد" and "ورود" but never the phrase "کد ورود", which keyword matching
        // alone required.
        assertTrue(
            SmartSmsClassifier.isOtpMessage(
                "بانک ملی\nکد 36330 را جهت ورود به سامانه بام وارد نمایید.\nQVcOXy7hhZr"
            )
        )
        assertEquals(
            "36330",
            SmartSmsClassifier.extractOtpCode("کد 36330 را جهت ورود به سامانه بام وارد نمایید.")
        )
    }

    @Test
    fun `a message with no offer wording yields nothing`() {
        assertNull(SmartSmsClassifier.extractPromo("SHOP", "سفارش شما ارسال شد. کد رهگیری AB12345", now))
        assertNull(SmartSmsClassifier.extractPromo("X", "جلسه فردا ساعت ۱۰ برگزار می‌شود ROOM42", now))
    }

    @Test
    fun `sms retriever hashes are not coupon codes`() {
        assertTrue(PromoCodeExtractor.looksLikeSmsRetrieverHash("QVcOXy7hhZr"))
        assertFalse("a real coupon is not a hash", PromoCodeExtractor.looksLikeSmsRetrieverHash("FOOD70"))
        assertFalse("all caps is not a hash", PromoCodeExtractor.looksLikeSmsRetrieverHash("SNAPPFOOD11"))
    }

    @Test
    fun `a genuine offer from a bank still works`() {
        // The gate must reject login codes, not banks.
        val promo = SmartSmsClassifier.extractPromo(
            "Bank Melli",
            "بانک ملی: با کد تخفیف MELLI30 از ۳۰ درصد تخفیف خرید اینترنتی بهره‌مند شوید",
            now
        )
        assertNotNull(promo)
        assertEquals("MELLI30", promo!!.code)
    }

    // ------------------------------------------------------------------ external extractor

    @Test
    fun `a code is only accepted when it appears in the message`() {
        // Guards the AI tier: a model that returns a wrong message index, or invents a code,
        // would otherwise produce a card whose code and brand contradict the SMS under them.
        val body = PromoValueParser.normalize("اسنپ‌فود: با کد تخفیف FOOD70 مبلغ ۷۰ هزار تومان تخفیف")
        assertTrue(PromoCodeExtractor.appearsIn("FOOD70", body))
        assertTrue("case should not matter", PromoCodeExtractor.appearsIn("food70", body))
        assertFalse("code from another message", PromoCodeExtractor.appearsIn("PAYCNN48", body))
    }

    @Test
    fun `a code split by spaces still counts as present`() {
        val body = PromoValueParser.normalize("کد تخفیف: PAY CNN48 را وارد کنید")
        assertTrue(PromoCodeExtractor.appearsIn("PAYCNN48", body))
    }

    // ------------------------------------------------------------------ ranking

    @Test
    fun `urgent valuable code outranks a fresh worthless one`() {
        val ranked = PromoRanker.rank(rankingFixture(), now)
        assertEquals("TONIGHT_BIG", ranked.first().code)
    }

    @Test
    fun `sections are built without empty headers`() {
        val rows = PromoRanker.buildRows(rankingFixture(), now)
        val headers = rows.filterIsInstance<PromoRow.Header>()
        assertTrue(headers.isNotEmpty())
        assertTrue(headers.all { it.count > 0 })
        assertEquals(PromoSection.EXPIRING_TODAY, headers.first().section)
        assertEquals(rankingFixture().size, rows.count { it is PromoRow.Item })
    }

    @Test
    fun `reminder only fires for worthwhile codes with a real deadline`() {
        val soon = PromoRanker.expiringSoon(rankingFixture(), now)
        assertEquals(1, soon.size)
        assertEquals("TONIGHT_BIG", soon.first().code)
    }

    @Test
    fun `pinned codes always sort to the top`() {
        val items = rankingFixture().toMutableList()
        val pinned = items.first { it.code == "NO_DEADLINE" }
        pinned.isPinned = true
        val ranked = PromoRanker.rank(items, now)
        assertEquals("NO_DEADLINE", ranked.first().code)
        assertEquals(PromoSection.PINNED, PromoRanker.sectionOf(pinned, now))
    }

    // ------------------------------------------------------------------ calendar

    @Test
    fun `jalali conversion round trips including leap day`() {
        // 30 Esfand exists only in a leap year and used to come back as month 0.
        assertEquals(Triple(1403, 12, 30), JalaliCalendar.gregorianToJalali(2025, 3, 20))
        assertEquals(Triple(2025, 3, 20), JalaliCalendar.jalaliToGregorian(1403, 12, 30))
        assertEquals(Triple(1404, 1, 1), JalaliCalendar.gregorianToJalali(2025, 3, 21))
    }

    // ------------------------------------------------------------------ helpers

    private fun promoExpiringIn(millis: Long) = PromoItem(
        id = "x", brand = "B", code = "X", discountAmount = "", description = "",
        receivedAt = now, discountType = DiscountType.AMOUNT, discountValue = 1000L,
        expiresAt = now + millis, expiryIsExplicit = true
    )

    private fun rankingFixture(): List<PromoItem> {
        fun make(
            code: String,
            value: Long,
            type: DiscountType,
            expiresIn: Long?,
            explicit: Boolean,
            receivedAgo: Long
        ) = PromoItem(
            id = code, brand = "B", code = code, discountAmount = "", description = "",
            receivedAt = now - receivedAgo, discountType = type, discountValue = value,
            expiresAt = if (expiresIn == null) null else now + expiresIn,
            expiryIsExplicit = explicit
        )

        return listOf(
            make("FRESH_WORTHLESS", 0L, DiscountType.UNKNOWN, 20 * day, true, hour),
            make("TONIGHT_BIG", 70_000L, DiscountType.AMOUNT, 4 * hour, true, 5 * day),
            make("WEEK_MID", 50_000L, DiscountType.AMOUNT, 5 * day, true, 2 * day),
            make("NO_DEADLINE", 100_000L, DiscountType.AMOUNT, null, false, 10 * day)
        )
    }
}
