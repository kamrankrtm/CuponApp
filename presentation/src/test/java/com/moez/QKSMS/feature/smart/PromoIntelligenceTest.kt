package com.moez.QKSMS.feature.smart

import com.moez.QKSMS.common.util.JalaliCalendar
import com.moez.QKSMS.feature.smart.model.PromoItem
import com.moez.QKSMS.feature.smart.promo.AiEscalation
import com.moez.QKSMS.feature.smart.promo.AiFinding
import com.moez.QKSMS.feature.smart.promo.AiPromoProtocol
import com.moez.QKSMS.feature.smart.promo.BrandRegistry
import com.moez.QKSMS.feature.smart.promo.DiscountType
import com.moez.QKSMS.feature.smart.promo.PromoCodec
import com.moez.QKSMS.feature.smart.promo.PromoMemory
import com.moez.QKSMS.feature.smart.promo.PromoParser
import com.moez.QKSMS.feature.smart.promo.PromoValueParser
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar

/**
 * The discount engine against the cases that used to go wrong: the many services behind one
 * brand, codes a wallet or bank hands out for someone else's shop, and deciding which messages
 * are worth an AI request at all.
 */
class PromoIntelligenceTest {

    /** 1 Mehr 1404, 10:00. */
    private val now = JalaliCalendar.toMillis(1404, 7, 1, endOfDay = false) + 10L * 60 * 60 * 1000

    @Before
    fun resetMemory() = PromoMemory.clear()

    @After
    fun clearMemory() = PromoMemory.clear()

    private fun promo(sender: String, body: String): PromoItem {
        val found = SmartSmsClassifier.extractPromo(sender, body, now)
        assertNotNull("no promo in: $body", found)
        return found!!
    }

    // ------------------------------------------------------------------ one brand, many services

    @Test
    fun `snapp food words make a bare snapp message a snappfood code`() {
        val p = promo("SNAPP", "اسنپ: با کد FOOD40 روی سفارش غذا از رستوران‌های منتخب ۴۰٪ تخفیف بگیرید")
        assertEquals("اسنپ‌فود", p.brand)
        assertEquals(BrandRegistry.SLUG_FOOD, p.categorySlug)
    }

    @Test
    fun `snapp shop is a shop not a taxi or a garage`() {
        val p = promo("SNAPP", "اسنپ‌شاپ | ۲۰٪ تخفیف گوشی موبایل با کد SHOP20 تا سقف ۵۰۰ هزار تومان")
        assertEquals("اسنپ‌شاپ", p.brand)
        assertEquals(BrandRegistry.SLUG_ECOMMERCE, p.categorySlug)
        assertEquals(500_000L, p.maxDiscountValue)
    }

    @Test
    fun `hotel words make a bare snapp message a snapptrip code`() {
        val p = promo("Snapp", "رزرو هتل با اسنپ! کد HOTEL25 برای ۲۵٪ تخفیف اقامت")
        assertEquals("اسنپ‌تریپ", p.brand)
    }

    @Test
    fun `ride words keep a bare snapp message on the taxi`() {
        val p = promo("SNAPP", "سفر بعدی‌ات با اسنپ ۳۰٪ ارزان‌تر، کد تخفیف RIDE30")
        assertEquals("اسنپ", p.brand)
        assertEquals(BrandRegistry.SLUG_TRANSPORT, p.categorySlug)
        val analysis = PromoParser.analyze("SNAPP", "سفر بعدی‌ات با اسنپ ۳۰٪ ارزان‌تر، کد تخفیف RIDE30", now).first()
        assertTrue("the ride cue settles it: ${analysis.brandConfidence}", analysis.brandConfidence >= AiEscalation.BRAND_OK)
    }

    @Test
    fun `snapp services that were missing are recognised by name`() {
        assertEquals("اسنپ‌باکس", promo("SNAPP", "اسنپ باکس: ارسال مرسوله با کد تخفیف BOX50 نصف قیمت").brand)
        assertEquals("اسنپ‌اکسپرس", promo("SNAPP", "اسنپ‌اکسپرس؛ کد تخفیف EXP20 برای ۲۰٪ تخفیف خرید").brand)
        assertEquals("اسنپ‌کارفیکس", promo("SNAPP", "تعویض روغن در اسنپ کارفیکس با کد تخفیف CAR15 و ۱۵٪ تخفیف").brand)
    }

    @Test
    fun `a tapsi motopeyk code is a courier code, not a taxi one`() {
        // From a user's screenshot: filed as "تاکسی اینترنتی"
        val p = promo("+985000301630", "تپسی: تا ۹۰ هزار تومان تخفیف موتوپیک🛵\nکد:TPSBOXH24\nتا ۱۰ مهر\nلغو۱۱")
        assertEquals("تپسی موتوپیک", p.brand)
        assertEquals("ارسال بسته و پیک", p.category)
        assertEquals("TPSBOXH24", p.code)
        assertEquals(90_000L, p.discountValue)
        assertTrue(p.expiryIsExplicit)
    }

    @Test
    fun `snapp's store is snappshop`() {
        // From a user's screenshot: filed as "تاکسی اینترنتی"
        val p = promo(
            "Snapp",
            "۱۳۰ هزار تومن تخفیف بیشتر فروشگاه اسنپ!\nکد تخفیف: laps130\nاعتبار تا ۷ روز\n" +
                "خیلی وقته به فروشگاه اسنپ سر نزدی، الان می‌تونی با تخفیف ۱۳۰ هزار تومنی خرید کنی.\n" +
                "برای خرید روی لینک بزن:\nhttps://l.snpp.link/t9kqo\n\n\nلغو 11l.eu/r"
        )
        assertEquals("اسنپ‌شاپ", p.brand)
        assertEquals(BrandRegistry.SLUG_ECOMMERCE, p.categorySlug)
        assertEquals("laps130", p.code)
        assertEquals(130_000L, p.discountValue)
        assertTrue(p.expiryIsExplicit)
    }

    @Test
    fun `a bare snapp message is not claimed for the taxi`() {
        val body = "کد تخفیف ABC123 برای شما، ۲۰٪ تخفیف"
        val p = promo("SNAPP", body)
        assertEquals("اسنپ", p.brand)
        assertEquals("سرویس‌های اسنپ", p.category)
        assertEquals(BrandRegistry.SLUG_OTHER, p.categorySlug)
        // Still worth an AI request, which can tell the service apart
        assertEquals(AiEscalation.Verdict.SEND_UNSURE_BRAND, AiEscalation.judge("SNAPP", body, now))
    }

    @Test
    fun `the code itself can name the service`() {
        assertEquals("اسنپ‌فود", promo("SNAPP", "اسنپ: کد تخفیف SFOOD30 برای ۳۰٪ تخفیف").brand)
        assertEquals("اسنپ‌باکس", promo("SNAPP", "اسنپ: کد تخفیف BOX20 برای ۲۰٪ تخفیف").brand)
    }

    @Test
    fun `the sponsor line never ends without a name`() {
        val item = PromoItem(
            id = "x", brand = "تپسی", code = "X1", discountAmount = "", description = "",
            category = "تاکسی اینترنتی", payWith = "\u200c", issuer = " "
        )
        assertEquals("", item.sponsorLine())
        assertEquals("تاکسی اینترنتی", item.subtitle())
    }

    @Test
    fun `a snappfood sender with a bare snapp body stays snappfood`() {
        assertEquals("اسنپ‌فود", promo("SnappFood", "اسنپ: کد تخفیف SF25 برای ۲۵٪ تخفیف").brand)
    }

    @Test
    fun `tapsi food and digikala jet are told apart by their words`() {
        assertEquals("تپسی‌فود", promo("TAPSI", "تپسی: ۳۰٪ تخفیف سفارش غذا با کد تخفیف TF30").brand)
        assertEquals("دیجی‌کالا جت", promo("Digikala", "خرید میوه و لبنیات از دیجی‌کالا با کد تخفیف JET50 و ۵۰ هزار تومان تخفیف").brand)
    }

    // ------------------------------------------------------------------ sponsors and payment

    @Test
    fun `a digipay code for filimo is filed under filimo from digipay`() {
        val p = promo("DIGIPAY", "دیجی‌پی: خرید اشتراک فیلیمو با ۵۰٪ تخفیف، کد تخفیف: DPFILM50")
        assertEquals("فیلیمو", p.brand)
        assertEquals(BrandRegistry.SLUG_ENTERTAINMENT, p.categorySlug)
        assertEquals("دیجی‌پی", p.issuer)
        assertEquals("دیجی‌پی", p.payWith)
        assertTrue(p.subtitle(), p.subtitle().contains("از طرف دیجی‌پی"))
        assertEquals("com.sabaidea.filimo", p.appPackage)
    }

    @Test
    fun `a digipay code for several shops lists them`() {
        val p = promo("Digipay", "با دیجی‌پی در فیلیمو، اسنپ‌فود و علی‌بابا با کد تخفیف DP20 بیست درصد تخفیف بگیرید")
        assertEquals("دیجی‌پی", p.brand)
        assertEquals(listOf("فیلیمو", "اسنپ‌فود", "علی‌بابا"), p.usableAt)
        assertEquals(20L, p.discountValue)
    }

    @Test
    fun `digipay advertising itself is digipay`() {
        val p = promo("DIGIPAY", "دیجی‌پی: ۲۰٪ تخفیف قسط اول با کد تخفیف DPQ20")
        assertEquals("دیجی‌پی", p.brand)
        assertNull(p.issuer)
    }

    @Test
    fun `a bank card offer is the shop's code sponsored by the bank`() {
        val p = promo(
            "Bank Mellat",
            "بانک ملت: با پرداخت از طریق همراه بانک ملت در اسنپ‌فود ۲۰٪ تخفیف بگیرید. کد تخفیف: MELLAT20"
        )
        assertEquals("اسنپ‌فود", p.brand)
        assertEquals("بانک ملت", p.issuer)
        assertEquals("بانک ملت", p.payWith)
    }

    @Test
    fun `an operator offer is the partner's code sponsored by the operator`() {
        val p = promo("Irancell", "ایرانسل: ۵۰٪ تخفیف اشتراک نماوا با کد تخفیف IRC50")
        assertEquals("نماوا", p.brand)
        assertEquals("ایرانسل", p.issuer)
        assertNull(p.payWith)
    }

    @Test
    fun `an instalment app's code for a sibling shop says how to pay`() {
        val p = promo("SnappPay", "اسنپ‌پی: خرید قسطی از اسنپ‌شاپ با ۲۵٪ تخفیف قسط اول، کد SPSHOP")
        assertEquals("اسنپ‌شاپ", p.brand)
        assertEquals("اسنپ‌پی", p.payWith)
    }

    @Test
    fun `a small shop signing its message is named without the ai`() {
        val body = "فروشگاه لباس رز: حراج تابستانه تا ۵۰٪ تخفیف! کد تخفیف ROSE50 حضوری و آنلاین"
        val p = promo("+9810001234", body)
        assertEquals("فروشگاه لباس رز", p.brand)
        assertEquals(BrandRegistry.SLUG_ECOMMERCE, p.categorySlug)
        assertEquals(AiEscalation.Verdict.SKIP_CONFIDENT, AiEscalation.judge("+9810001234", body, now))
        // A greeting is not a shop name
        assertNotEquals("مشتری گرامی", promo("+9810001234", "مشتری گرامی: کد تخفیف GR20 با ۲۰٪ تخفیف").brand)
    }

    @Test
    fun `cheaper and free count as offers`() {
        assertEquals("SNP20", promo("SNAPP", "سفرهای امروزت با کد SNP20 تا ۲۰٪ ارزون‌تره! فقط امروز").code)
    }

    @Test
    fun `a short link is not a code and costs nothing`() {
        assertEquals(
            AiEscalation.Verdict.SKIP_NO_CODE_SHAPE,
            AiEscalation.judge("DIGIKALA", "دیجی‌کالا | شگفت‌انگیز پاییزه\nتا ۷۰٪ تخفیف کالای دیجیتال\ndgka.la/xY12", now)
        )
    }

    // ------------------------------------------------------------------ the saved list

    @Test
    fun `a better reading replaces the old card and keeps the user's marks`() {
        // The saved list judges expiry against the real clock
        val today = System.currentTimeMillis()
        val sender = "SNAPP"
        val body = "کد تخفیف ABC123 برای شما، ۲۰٪ تخفیف"
        val local = SmartSmsClassifier.extractPromo(sender, body, today)!!
        SmartDataManager.setPromos(emptyList())
        SmartDataManager.addPromo(local)
        SmartDataManager.setPinned(local, true)

        PromoMemory.remember(sender, today, body, listOf(AiFinding("ABC123", merchant = "اسنپ‌فود")))
        SmartDataManager.replaceForMessage(local.sourceKey, SmartSmsClassifier.extractPromos(sender, body, today))

        val cards = SmartDataManager.getPromos().filter { it.code == "ABC123" }
        assertEquals(1, cards.size)
        assertEquals("اسنپ‌فود", cards.single().brand)
        assertTrue(cards.single().isPinned)
        SmartDataManager.setPromos(emptyList())
    }

    @Test
    fun `a used code stays used when a re-scan renames its brand`() {
        val today = System.currentTimeMillis()
        val old = PromoItem(
            id = "old", brand = "اسنپ", code = "FOOD40", discountAmount = "", description = "",
            sender = "SNAPP", receivedAt = today, expiresAt = today + 3 * 24 * 60 * 60 * 1000L
        )
        SmartDataManager.setPromos(emptyList())
        SmartDataManager.addPromo(old)
        SmartDataManager.markUsed(old, true)

        val rescanned = SmartSmsClassifier.extractPromos(
            "SNAPP", "اسنپ: با کد FOOD40 روی سفارش غذا از رستوران‌های منتخب ۴۰٪ تخفیف بگیرید", today
        )
        assertEquals("اسنپ‌فود", rescanned.single().brand)
        SmartDataManager.setPromos(rescanned)
        assertTrue(SmartDataManager.getPromos().none { it.code == "FOOD40" })
        SmartDataManager.setPromos(emptyList())
    }

    // ------------------------------------------------------------------ names that are words

    @Test
    fun `everyday words are not brands without context`() {
        val greeting = promo("ShopX", "باسلام و احترام، کد تخفیف SALAM20 برای ۲۰٪ تخفیف خرید از فروشگاه ما")
        assertNotEquals("باسلام", greeting.brand)
        val torob = promo("KidsShop", "تربیت کودک؛ کد تخفیف KID20 برای ۲۰٪ تخفیف کتاب")
        assertNotEquals("ترب", torob.brand)
        val basalam = promo("X", "در باسلام با کد تخفیف BSL30 از ۳۰٪ تخفیف بهره‌مند شوید")
        assertEquals("باسلام", basalam.brand)
    }

    @Test
    fun `a brand name inside the code does not vote`() {
        val p = promo("X", "تپسی‌فود: کد تخفیف TAPSI20 برای ۲۰٪ تخفیف سفارش")
        assertEquals("تپسی‌فود", p.brand)
    }

    // ------------------------------------------------------------------ codes

    @Test
    fun `receipts and spent codes are not offers`() {
        assertNull(SmartSmsClassifier.extractPromo("SHOP", "سفارش شما با ۲۰٪ تخفیف ثبت شد. کد رهگیری AB12345", now))
        assertNull(SmartSmsClassifier.extractPromo("SNAPPFOOD", "کد تخفیف FOOD30 روی سفارش شما اعمال شد", now))
    }

    @Test
    fun `one message with two codes gives two cards with their own figures`() {
        val promos = SmartSmsClassifier.extractPromos(
            "SNAPP",
            "با کد FOOD20 از ۲۰٪ تخفیف غذا و با کد MART30 از ۳۰٪ تخفیف سوپرمارکت اسنپ استفاده کنید",
            now
        )
        assertEquals(2, promos.size)
        val food = promos.first { it.code == "FOOD20" }
        val mart = promos.first { it.code == "MART30" }
        assertEquals(20L, food.discountValue)
        assertEquals(30L, mart.discountValue)
        assertEquals("اسنپ‌فود", food.brand)
        assertEquals("اسنپ‌مارکت", mart.brand)
    }

    @Test
    fun `a code on the line after its label is found`() {
        val p = promo("OKALA", "اکالا\nکد تخفیف شما:\nSUMMER1404\nاعتبار تا ۱۵ مهر")
        assertEquals("SUMMER1404", p.code)
        assertTrue(p.confidence >= 90)
        assertTrue(p.expiryIsExplicit)
    }

    @Test
    fun `labelled numeric codes and words between label and code are handled`() {
        assertEquals("482913", promo("OKALA", "اکالا: کد تخفیف: 482913 برای ۵۰ هزار تومان تخفیف").code)
        assertEquals("FOOD30", promo("SNAPPFOOD", "اسنپ‌فود: کد تخفیف ۳۰ درصدی شما: FOOD30").code)
        assertEquals("PAY-CNN48", promo("X", "دیجی‌کالا کد تخفیف PAY-CNN48 با ۱۰٪ تخفیف").code)
    }

    @Test
    fun `a token inside a link is not a code`() {
        val p = SmartSmsClassifier.extractPromo("X", "۲۰٪ تخفیف دیجی‌کالا با لینک زیر\nhttps://dgka.la/Ab12Cd", now)
        assertNull(p)
    }

    // ------------------------------------------------------------------ values

    @Test
    fun `spelled out and compound amounts are read in full`() {
        assertEquals(150_000L, promo("X", "دیجی‌کالا: صد و پنجاه هزار تومان تخفیف با کد تخفیف GIFT150").discountValue)
        assertEquals(1_500_000L, promo("X", "علی‌بابا: یک و نیم میلیون تومان اعتبار هدیه با کد تخفیف TRIP15").discountValue)
        assertEquals(50_000L, promo("X", "اسنپ‌فود ۵۰ت تخفیف با کد تخفیف T50X").discountValue)
    }

    @Test
    fun `a product price is not the discount`() {
        val p = promo("X", "تکنولایف: گوشی سامسونگ با قیمت ۱۲ میلیون تومان، با کد تخفیف SAM5 پنج درصد تخفیف")
        assertEquals(DiscountType.PERCENT, p.discountType)
        assertEquals(5L, p.discountValue)
    }

    @Test
    fun `minimum order without a unit means thousands`() {
        assertEquals(200_000L, promo("X", "اسنپ‌فود کد تخفیف SF30 با ۳۰٪ تخفیف برای حداقل خرید ۲۰۰").minOrderValue)
    }

    @Test
    fun `cashback is labelled as cashback`() {
        val p = promo("SNAPPPAY", "اسنپ‌پی: ۲۰٪ کش‌بک خرید اقساطی با کد تخفیف SPCASH")
        assertTrue(p.isCashback)
        assertTrue(p.description, p.description.contains("کش‌بک"))
    }

    // ------------------------------------------------------------------ deadlines

    @Test
    fun `delivery times and start dates are not deadlines`() {
        assertFalse(promo("X", "اسنپ‌فود: ارسال تا ۲ ساعت؛ کد تخفیف FAST20 با ۲۰٪ تخفیف").expiryIsExplicit)
        assertFalse(promo("X", "دیجی‌کالا: از فردا کد تخفیف DK20 با ۲۰٪ تخفیف فعال است").expiryIsExplicit)
        assertFalse(promo("X", "۲۰ دیجی‌کالا کد تخفیف DK21 با ۲۱٪ تخفیف").expiryIsExplicit)
    }

    @Test
    fun `weekday and end of month deadlines`() {
        val thursday = promo("X", "دیجی‌کالا: کد تخفیف DKTH با ۲۰٪ تخفیف فقط تا پنجشنبه")
        assertTrue(thursday.expiryIsExplicit)
        val cal = Calendar.getInstance().apply { timeInMillis = thursday.expiresAt!! }
        assertEquals(Calendar.THURSDAY, cal.get(Calendar.DAY_OF_WEEK))

        val mehr = promo("X", "دیجی‌کالا: کد تخفیف DKMEHR با ۲۰٪ تخفیف تا پایان مهر")
        assertFalse(mehr.isExpired(JalaliCalendar.toMillis(1404, 7, 30, endOfDay = false)))
        assertTrue(mehr.isExpired(JalaliCalendar.toMillis(1404, 8, 1, endOfDay = true)))
    }

    // ------------------------------------------------------------------ when to ask the AI

    @Test
    fun `confident and code-less messages cost nothing`() {
        assertEquals(
            AiEscalation.Verdict.SKIP_CONFIDENT,
            AiEscalation.judge("SNAPPFOOD", "اسنپ‌فود: با کد تخفیف FOOD70 مبلغ ۷۰ هزار تومان تخفیف بگیرید", now)
        )
        assertEquals(
            AiEscalation.Verdict.SKIP_NO_CODE_SHAPE,
            AiEscalation.judge("DIGIKALA", "جشنواره پاییزه دیجی‌کالا با ۵۰٪ تخفیف، همین حالا خرید کنید", now)
        )
        assertEquals(
            AiEscalation.Verdict.SKIP_SENSITIVE,
            AiEscalation.judge("BANK", "واریز 5,000,000 ریال. موجودی: 12,000,000 ریال. تخفیف ویژه CODE12", now)
        )
    }

    @Test
    fun `unsure readings are sent`() {
        assertEquals(
            AiEscalation.Verdict.SEND_UNSURE_BRAND,
            AiEscalation.judge("SNAPP", "کد تخفیف ABC123 برای شما، ۲۰٪ تخفیف", now)
        )
        assertEquals(
            AiEscalation.Verdict.SEND_UNSURE_CODE,
            AiEscalation.judge("SNAPPFOOD", "اسنپ‌فود: با FOOD30 سفارش بده و ۳۰٪ تخفیف بگیر", now)
        )
    }

    @Test
    fun `a remembered ai answer is used and never re-sent`() {
        val sender = "SNAPP"
        val body = "کد تخفیف ABC123 برای شما، ۲۰٪ تخفیف"
        PromoMemory.remember(sender, now, body, listOf(AiFinding("ABC123", merchant = "اسنپ‌فود", category = "food")))

        assertEquals(AiEscalation.Verdict.SKIP_ALREADY_READ, AiEscalation.judge(sender, body, now))
        val p = promo(sender, body)
        assertEquals("اسنپ‌فود", p.brand)
        assertEquals(PromoItem.SOURCE_AI, p.source)
        assertEquals(20L, p.discountValue)
    }

    @Test
    fun `when the ai finds the real shop the wallet becomes its sponsor`() {
        val sender = "DIGIPAY"
        val body = "دیجی‌پی: اشتراک یک ساله با ۴۰٪ تخفیف برای کاربران فیلیمو، کد تخفیف DPF40"
        PromoMemory.remember(sender, now, body, listOf(AiFinding("DPF40", merchant = "فیلیمو", category = "entertainment")))
        val p = promo(sender, body)
        assertEquals("فیلیمو", p.brand)
        assertEquals("دیجی‌پی", p.issuer)
        assertEquals("دیجی‌پی", p.payWith)
    }

    @Test
    fun `an ai brand the message does not back up is ignored`() {
        val sender = "SHOPX"
        val body = "فروشگاه ما: کد تخفیف ZX9901 برای ۱۰٪ تخفیف"
        PromoMemory.remember(sender, now, body, listOf(AiFinding("ZX9901", merchant = "فیلیمو")))
        assertNotEquals("فیلیمو", promo(sender, body).brand)
    }

    @Test
    fun `an ai code that is not in the message is dropped`() {
        val sender = "SNAPP"
        val body = "کد تخفیف ABC123 برای شما، ۲۰٪ تخفیف"
        PromoMemory.remember(sender, now, body, listOf(AiFinding("INVENTED9", merchant = "اسنپ‌فود")))
        // The invented code is dropped; the labelled code in the text still stands on its own
        val promos = SmartSmsClassifier.extractPromos(sender, body, now)
        assertEquals(listOf("ABC123"), promos.map { it.code })

        // With nothing labelled, a failed AI reading leaves nothing behind
        val bare = "اسنپ‌فود: با FOOD30 سفارش بده و ۳۰٪ تخفیف بگیر"
        PromoMemory.remember(sender, now, bare, listOf(AiFinding("INVENTED9")))
        assertNull(SmartSmsClassifier.extractPromo(sender, bare, now))
    }

    @Test
    fun `a campaign seen once is read locally the next time`() {
        val sender = "PINKSHOP"
        val first = "سلام! هدیه ویژه پاییز برای شما آماده است. شناسه هدیه QW12ER و ۲۵ درصد تخفیف روی همه محصولات"
        val next = "سلام! هدیه ویژه پاییز برای شما آماده است. شناسه هدیه ZX98CV و ۳۰ درصد تخفیف روی همه محصولات"
        PromoMemory.remember(sender, now, first, listOf(AiFinding("QW12ER", merchant = "پینک‌شاپ", category = "ecommerce")))

        val later = now + 60_000L
        val p = SmartSmsClassifier.extractPromo(sender, next, later)
        assertNotNull(p)
        assertEquals("ZX98CV", p!!.code)
        assertEquals("پینک‌شاپ", p.brand)
        assertEquals(30L, p.discountValue)
        assertEquals(AiEscalation.Verdict.SKIP_CONFIDENT, AiEscalation.judge(sender, next, later))
    }

    @Test
    fun `memory survives export and import`() {
        PromoMemory.remember("SNAPP", now, "کد تخفیف ABC123 برای شما، ۲۰٪ تخفیف", listOf(AiFinding("ABC123", merchant = "اسنپ‌فود")))
        val saved = PromoMemory.export()
        PromoMemory.clear()
        PromoMemory.import(saved)
        assertEquals("اسنپ‌فود", promo("SNAPP", "کد تخفیف ABC123 برای شما، ۲۰٪ تخفیف").brand)
    }

    // ------------------------------------------------------------------ the request and the answer

    @Test
    fun `messages are trimmed before they are sent`() {
        val compact = AiPromoProtocol.compact(
            "کامران عزیز 🎁🎁\nکد تخفیف FOOD30 👈 https://snappfood.ir/promo?utm_source=sms&id=998877\nلغو11"
        )
        assertFalse(compact, compact.contains("کامران"))
        assertFalse(compact, compact.contains("utm_source"))
        assertFalse(compact, compact.contains("لغو"))
        assertFalse(compact, compact.contains("🎁"))
        assertTrue(compact, compact.contains("snappfood.ir"))
        assertTrue(compact, compact.contains("FOOD30"))
    }

    @Test
    fun `answers are read even when wrapped cut off or in long form`() {
        val fenced = "```json\n{\"r\":[{\"i\":0,\"c\":\"FOOD30\",\"m\":\"اسنپ‌فود\"}]}\n```"
        assertEquals("FOOD30", AiPromoProtocol.parse(fenced).single().finding.code)

        val truncated = "{\"r\":[{\"i\":0,\"c\":\"AAA111\"},{\"i\":1,\"c\":\"BBB222\",\"m\":\"دیجی"
        val parsed = AiPromoProtocol.parse(truncated)
        assertEquals(1, parsed.size)
        assertEquals("AAA111", parsed.single().finding.code)

        val longForm = "[{\"index\":2,\"code\":\"LONG1\",\"brand\":\"فیلیمو\",\"discountAmount\":\"۳۰٪\"}]"
        val long = AiPromoProtocol.parse(longForm).single()
        assertEquals(2, long.index)
        assertEquals("فیلیمو", long.finding.merchant)
        assertEquals("۳۰٪", long.finding.value)
    }

    @Test
    fun `usage is read from the response`() {
        val response = "{\"choices\":[{\"message\":{\"content\":\"{}\"}}],\"usage\":{\"prompt_tokens\":420,\"completion_tokens\":37}}"
        assertEquals(AiPromoProtocol.Usage(420, 37), AiPromoProtocol.usageOf(response))
        assertEquals("{}", AiPromoProtocol.contentOf(response))
    }

    // ------------------------------------------------------------------ storage

    @Test
    fun `new fields survive the codec and old caches still load`() {
        val p = promo("DIGIPAY", "دیجی‌پی: خرید اشتراک فیلیمو با ۵۰٪ تخفیف تا سقف ۱۰۰ هزار تومان، کد تخفیف: DPFILM50")
        val round = PromoCodec.decodeList(PromoCodec.encodeList(listOf(p))).single()
        assertEquals(p.issuer, round.issuer)
        assertEquals(p.payWith, round.payWith)
        assertEquals(p.maxDiscountValue, round.maxDiscountValue)
        assertEquals(p.sourceKey, round.sourceKey)
        assertEquals(p.source, round.source)

        val v2 = "{\"version\":2,\"promos\":[{\"code\":\"OLD1\",\"brand\":\"اسنپ\",\"isUsed\":true}]}"
        val old = PromoCodec.decodeList(v2).single()
        assertEquals("OLD1", old.code)
        assertTrue(old.isUsed)
    }

    @Test
    fun `normalization handles kashida joiners and arabic separators`() {
        assertEquals("تخفیف اسنپ فود 1,500", PromoValueParser.normalize("تخفیـــف  اسنپ‌ فود ۱٬۵۰۰"))
    }
}
