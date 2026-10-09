package com.moez.QKSMS.feature.smart

import com.moez.QKSMS.feature.smart.analysis.SmsAnalyzer
import com.moez.QKSMS.feature.smart.analysis.SmsKind
import com.moez.QKSMS.feature.smart.model.OtpItem
import com.moez.QKSMS.feature.smart.model.PromoItem
import com.moez.QKSMS.feature.smart.model.SmsCategory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The shared pipeline: one reading per unchanged message, invalidation when anything that
 * decides it changes, and a single writer that never loses what arrived during a scan.
 * [SmartDataManager] runs without disk here (no PromoStore), which is all these need.
 */
class SmartPipelineTest {

    private val now = System.currentTimeMillis()

    @Before
    fun setUp() {
        SmartAnalysisCache.invalidateAll()
        SmartDataManager.clearForTest()
    }

    @After
    fun tearDown() {
        SmartDataManager.clearForTest()
    }

    // ------------------------------------------------------------ one reading per message

    @Test
    fun `an unchanged message is read once`() {
        val first = SmartAnalysisCache.analyze("Bank Mellat", "کد ورود: 482913", now, 1L, "sms:10")
        val runs = SmartAnalysisCache.analysesRun
        val again = SmartAnalysisCache.analyze("Bank Mellat", "کد ورود: 482913", now, 1L, "sms:10")
        assertSame(first, again)
        assertEquals(runs, SmartAnalysisCache.analysesRun)
    }

    @Test
    fun `a changed message under the same identity is read again`() {
        val first = SmartAnalysisCache.analyze("Bank Mellat", "کد ورود: 482913", now, 1L, "sms:11")
        val edited = SmartAnalysisCache.analyze("Bank Mellat", "کد ورود: 777111", now, 1L, "sms:11")
        assertNotSame(first, edited)
        assertEquals("777111", edited.otpItem?.code)
    }

    @Test
    fun `a full sync forgets every reading and moves the generation`() {
        val generation = SmartAnalysisCache.generation
        SmartAnalysisCache.analyze("Bank Mellat", "کد ورود: 482913", now, 1L, "sms:12")
        SmartAnalysisCache.invalidateAll()
        assertEquals(0, SmartAnalysisCache.size())
        assertNotEquals(generation, SmartAnalysisCache.generation)
    }

    @Test
    fun `identity is the provider's id, which a re-sync keeps`() {
        assertEquals("sms:42", SmsAnalyzer.sourceKeyOf("sms", 42L))
        assertEquals(null, SmsAnalyzer.sourceKeyOf("sms", 0L))
        // Without one, the fingerprint stands in, and is stable for the same content
        val a = SmsAnalyzer.analyze("X", "سلام", now)
        val b = SmsAnalyzer.analyze("X", "سلام", now)
        assertEquals(a.sourceKey, b.sourceKey)
        assertTrue(a.sourceKey.startsWith("fp:"))
    }

    @Test
    fun `an AI answer cannot replace the reading of a message that has since changed`() {
        val old = SmartAnalysisCache.analyze("Bank Mellat", "کد ورود: 482913", now, 1L, "sms:13")
        SmartAnalysisCache.analyze("Bank Mellat", "کد ورود: 555000", now, 1L, "sms:13")
        assertFalse(SmartAnalysisCache.replace(old.copy(kind = SmsKind.UNKNOWN)))
    }

    @Test
    fun `sender overrides move their version, so placements are redone`() {
        val before = SenderOverrides.version
        SenderOverrides.move(listOf("30001234"), SenderOverrides.Tab.SPAM)
        assertNotEquals(before, SenderOverrides.version)
        SenderOverrides.move(listOf("30001234"), null)
    }

    @Test
    fun `unknown messages are not spam`() {
        val category = SmartSmsClassifier.classify("SERVICE", "بسته‌ی شما تحویل شد", now)
        assertTrue(category is SmsCategory.Unknown)
        assertFalse(SmartSmsClassifier.classifyForUser("SERVICE", "بسته‌ی شما تحویل شد", now) is SmsCategory.Spam)
        // Advertising wording still files a message as spam
        assertTrue(SmartSmsClassifier.classify("30001234", "جشنواره پاییزه با ارسال رایگان\nلغو11", now) is SmsCategory.Spam)
    }

    // ------------------------------------------------------------ the single writer

    private fun otp(code: String, at: Long, key: String) =
        OtpItem("otp-$key", code, "سرویس", "BANK", "کد ورود: $code", at, sourceKey = key)

    @Test
    fun `a scan merges codes and never drops one that arrived while it ran`() {
        val scanned = listOf(otp("111111", now - 60_000, "sms:1"))
        // While the scan was reading the inbox, a new code arrived and was listed
        SmartDataManager.addOtp(otp("222222", now, "sms:2"))
        SmartDataManager.mergeOtps(scanned)
        assertEquals(listOf("222222", "111111"), SmartDataManager.getOtps().map { it.code })
    }

    @Test
    fun `a late reading is listed under a newer code, never above it`() {
        SmartDataManager.addOtp(otp("222222", now, "sms:2"))
        SmartDataManager.addOtp(otp("111111", now - 60_000, "sms:1"))
        assertEquals("222222", SmartDataManager.getOtps().first().code)
        assertTrue(SmartDataManager.hasNewerOtp(now - 60_000, "sms:1"))
        assertFalse(SmartDataManager.hasNewerOtp(now, "sms:2"))
    }

    @Test
    fun `the same message read twice is listed once`() {
        SmartDataManager.addOtp(otp("111111", now, "sms:1"))
        SmartDataManager.mergeOtps(listOf(otp("111111", now, "sms:1")))
        SmartDataManager.addOtp(otp("111111", now, "sms:1"))
        assertEquals(1, SmartDataManager.getOtps().size)
    }

    private fun promo(code: String, at: Long = now, key: String = "k-$code") = PromoItem(
        id = "p-$code", brand = "اسنپ‌فود", code = code, discountAmount = "۵۰ هزار تومان", description = "",
        sender = "SNAPPFOOD", body = "کد تخفیف $code", receivedAt = at, expiresAt = now + 86_400_000L, sourceKey = key)

    @Test
    fun `a rebuild keeps pinned, used and broken marks`() {
        val pinned = promo("PIN1")
        val used = promo("USED1")
        val broken = promo("BAD1")
        SmartDataManager.setPromos(listOf(pinned, used, broken))
        // The user marks cards on the list
        SmartDataManager.setPinned(pinned, true)
        SmartDataManager.markUsed(used, true)
        SmartDataManager.markInvalid(broken, true)

        // A full re-scan produces fresh, unmarked copies of the same offers
        SmartDataManager.setPromos(listOf(promo("PIN1"), promo("USED1"), promo("BAD1"), promo("NEW1")))
        val live = SmartDataManager.getPromos().map { it.code }
        assertTrue("live=$live", "PIN1" in live && "NEW1" in live)
        assertTrue(SmartDataManager.getPromos().first { it.code == "PIN1" }.isPinned)
        assertFalse("live=$live", "USED1" in live)
        assertFalse("live=$live", "BAD1" in live)
        val archived = SmartDataManager.getArchivedPromos().map { it.code }
        assertTrue("archived=$archived", "USED1" in archived && "BAD1" in archived)
    }

    @Test
    fun `concurrent writers lose nothing`() {
        val threads = (0 until 8).map { t ->
            Thread {
                for (i in 0 until 50) SmartDataManager.addOtp(otp("$t$i".padStart(6, '0'), now - (t * 50 + i), "sms:$t-$i"))
            }
        }
        val scanner = Thread { repeat(20) { SmartDataManager.mergeOtps(emptyList()) } }
        threads.forEach { it.start() }
        scanner.start()
        threads.forEach { it.join() }
        scanner.join()
        assertEquals(400, SmartDataManager.getOtps().size)
    }
}
