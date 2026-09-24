package com.moez.QKSMS.feature.smart.promo

/**
 * A single Iranian service/brand known to the discount engine.
 *
 * [keywords] are matched against the normalized SMS body and sender, [senderIds] against the
 * raw sender only. Sender matches score higher than body matches, and a longer keyword scores
 * higher than a short one, so the engine no longer depends on the order of a `when` chain.
 */
data class Brand(
    val fa: String,
    val en: String,
    val category: String,
    val categorySlug: String,
    /** ARGB colour used for the brand avatar on the promo card. */
    val color: Int,
    val keywords: List<String>,
    val senderIds: List<String> = emptyList(),
    /** Android package of the brand's own app, opened by the "copy & go" button. */
    val appPackage: String? = null,
    val website: String? = null
)

object BrandRegistry {

    const val SLUG_FOOD = "food"
    const val SLUG_SUPERMARKET = "supermarket"
    const val SLUG_ECOMMERCE = "ecommerce"
    const val SLUG_TRANSPORT = "transport"
    const val SLUG_ENTERTAINMENT = "entertainment"
    const val SLUG_FINTECH = "fintech"
    const val SLUG_TELECOM = "telecom"
    const val SLUG_SERVICES = "services"
    const val SLUG_OTHER = "other"

    /** Display names for the category chips, keyed by slug. */
    val CATEGORY_LABELS: List<Pair<String, String>> = listOf(
        SLUG_FOOD to "غذا",
        SLUG_SUPERMARKET to "سوپرمارکت",
        SLUG_ECOMMERCE to "فروشگاه",
        SLUG_TRANSPORT to "سفر و تاکسی",
        SLUG_ENTERTAINMENT to "سرگرمی",
        SLUG_FINTECH to "پرداخت",
        SLUG_TELECOM to "اپراتور",
        SLUG_SERVICES to "خدمات",
        SLUG_OTHER to "سایر"
    )

    fun categoryLabel(slug: String): String =
        CATEGORY_LABELS.firstOrNull { it.first == slug }?.second ?: "سایر"

    val UNKNOWN = Brand(
        fa = "سایر فروشگاه‌ها",
        en = "Store",
        category = "سایر",
        categorySlug = SLUG_OTHER,
        color = 0xFF607D8B.toInt(),
        keywords = emptyList()
    )

    val BRANDS: List<Brand> = listOf(
        // ---------- Food & restaurants ----------
        Brand("اسنپ‌فود", "SnappFood", "غذا و رستوران", SLUG_FOOD, 0xFFE21A5C.toInt(),
            listOf("اسنپ فود", "اسنپ‌فود", "اسنپفود", "snappfood", "snpf"),
            listOf("snappfood", "+983000445", "10000445"),
            "com.zoodfood.android", "https://snappfood.ir"),
        Brand("تپسی‌فود", "TapsiFood", "غذا و رستوران", SLUG_FOOD, 0xFFFF5722.toInt(),
            listOf("تپسی فود", "تپسی‌فود", "تپسیفود", "tapsifood"),
            emptyList(), "food.tapsi.ir", "https://tapsi.food"),
        Brand("چیلیوری", "Chilivery", "غذا و رستوران", SLUG_FOOD, 0xFF00A99D.toInt(),
            listOf("چیلیوری", "chilivery"), emptyList(), "com.chilivery", "https://chilivery.com"),
        Brand("ریحون", "Reyhoon", "غذا و رستوران", SLUG_FOOD, 0xFF8BC34A.toInt(),
            listOf("ریحون", "reyhoon"), emptyList(), "com.reyhoon.android", "https://reyhoon.com"),
        Brand("دلینو", "Delino", "غذا و رستوران", SLUG_FOOD, 0xFFEF5350.toInt(),
            listOf("دلینو", "delino")),
        Brand("اسنپ‌کافه", "SnappCafe", "کافه و نوشیدنی", SLUG_FOOD, 0xFF795548.toInt(),
            listOf("اسنپ کافه", "اسنپ‌کافه", "snappcafe")),

        // ---------- Supermarket ----------
        Brand("اسنپ‌مارکت", "SnappMarket", "سوپرمارکت", SLUG_SUPERMARKET, 0xFF00B074.toInt(),
            listOf("اسنپ مارکت", "اسنپ‌مارکت", "اسنپمارکت", "snappmarket"),
            emptyList(), "ir.snapp.market", "https://snapp.market"),
        Brand("اکالا", "Okala", "سوپرمارکت", SLUG_SUPERMARKET, 0xFFE91E63.toInt(),
            listOf("اکالا", "okala", "افق کوروش"),
            listOf("okala"), "com.okala", "https://okala.com"),
        Brand("تپسی‌مارکت", "TapsiMarket", "سوپرمارکت", SLUG_SUPERMARKET, 0xFFFF7043.toInt(),
            listOf("تپسی مارکت", "تپسی‌مارکت", "tpmk", "tapsimarket")),
        Brand("دیجی‌کالا فرش", "DigikalaFresh", "سوپرمارکت", SLUG_SUPERMARKET, 0xFF4CAF50.toInt(),
            listOf("دیجی کالا فرش", "دیجی‌کالا فرش", "digifresh", "دیجی فرش")),
        Brand("هایپراستار", "Hyperstar", "سوپرمارکت", SLUG_SUPERMARKET, 0xFF0D47A1.toInt(),
            listOf("هایپراستار", "hyperstar")),
        Brand("جانبو", "Janbo", "سوپرمارکت", SLUG_SUPERMARKET, 0xFFF9A825.toInt(),
            listOf("جانبو", "janbo")),

        // ---------- E-commerce & retail ----------
        Brand("دیجی‌کالا", "Digikala", "فروشگاه آنلاین", SLUG_ECOMMERCE, 0xFFEF4056.toInt(),
            listOf("دیجی کالا", "دیجی‌کالا", "دیجیکالا", "digikala", "دیجی پلاس", "دیجی‌پلاس", "dgkl"),
            listOf("digikala", "+983000101"), "com.digikala", "https://digikala.com"),
        Brand("دیجی‌استایل", "DigiStyle", "مد و پوشاک", SLUG_ECOMMERCE, 0xFF6A1B9A.toInt(),
            listOf("دیجی استایل", "دیجی‌استایل", "digistyle"),
            emptyList(), "com.digistyle", "https://digistyle.com"),
        Brand("باسلام", "Basalam", "فروشگاه آنلاین", SLUG_ECOMMERCE, 0xFF00897B.toInt(),
            listOf("باسلام", "basalam"), emptyList(), "com.basalam.app", "https://basalam.com"),
        Brand("ترب", "Torob", "مقایسه قیمت", SLUG_ECOMMERCE, 0xFF3F51B5.toInt(),
            listOf("ترب", "torob"), emptyList(), "ir.torob", "https://torob.com"),
        Brand("ایمالز", "Emalls", "مقایسه قیمت", SLUG_ECOMMERCE, 0xFF1976D2.toInt(),
            listOf("ایمالز", "emalls")),
        Brand("خانومی", "Khanoumi", "آرایشی و بهداشتی", SLUG_ECOMMERCE, 0xFFD81B60.toInt(),
            listOf("خانومی", "khanoumi"), emptyList(), "com.khanoumi", "https://khanoumi.com"),
        Brand("تکنولایف", "TechnoLife", "کالای دیجیتال", SLUG_ECOMMERCE, 0xFF0288D1.toInt(),
            listOf("تکنولایف", "تکنو لایف", "technolife"), emptyList(), "com.technolife", "https://technolife.ir"),
        Brand("بانی‌مد", "Banimode", "مد و پوشاک", SLUG_ECOMMERCE, 0xFFAD1457.toInt(),
            listOf("بانی مد", "بانی‌مد", "بانیمد", "banimode")),
        Brand("مدیسه", "Modiseh", "مد و پوشاک", SLUG_ECOMMERCE, 0xFF7B1FA2.toInt(),
            listOf("مدیسه", "modiseh")),
        Brand("زیبامو", "Zibamo", "آرایشی و بهداشتی", SLUG_ECOMMERCE, 0xFFC2185B.toInt(),
            listOf("زیبامو", "zibamo")),
        Brand("مسترکالا", "Masterkala", "کالای دیجیتال", SLUG_ECOMMERCE, 0xFF455A64.toInt(),
            listOf("مسترکالا", "مستر کالا", "masterkala")),
        Brand("گوشی‌شاپ", "Gooshishop", "کالای دیجیتال", SLUG_ECOMMERCE, 0xFF37474F.toInt(),
            listOf("گوشی شاپ", "گوشی‌شاپ", "gooshishop")),
        Brand("چرم مَنط", "MantLeather", "پوشاک و چرم", SLUG_ECOMMERCE, 0xFF5D4037.toInt(),
            listOf("چرم منط", "چرم مَنط", "مَنط")),
        Brand("نوین‌چرم", "NovinLeather", "پوشاک و چرم", SLUG_ECOMMERCE, 0xFF6D4C41.toInt(),
            listOf("نوین چرم", "نوین‌چرم", "نوینچرم")),
        Brand("تخفیفان", "Takhfifan", "کوپن و تخفیف", SLUG_ECOMMERCE, 0xFFF57C00.toInt(),
            listOf("تخفیفان", "takhfifan")),
        Brand("نت‌برگ", "Netbarg", "کوپن و تخفیف", SLUG_ECOMMERCE, 0xFFFF6F00.toInt(),
            listOf("نت برگ", "نت‌برگ", "نتبرگ", "netbarg")),
        Brand("دیوار", "Divar", "آگهی و نیازمندی", SLUG_ECOMMERCE, 0xFFA62626.toInt(),
            listOf("دیوار", "divar"), emptyList(), "ir.divar", "https://divar.ir"),
        Brand("شیپور", "Sheypoor", "آگهی و نیازمندی", SLUG_ECOMMERCE, 0xFF00796B.toInt(),
            listOf("شیپور", "sheypoor")),
        Brand("کافه‌بازار", "CafeBazaar", "اپلیکیشن", SLUG_ECOMMERCE, 0xFF00B0FF.toInt(),
            listOf("کافه بازار", "کافه‌بازار", "کافه‌ بازار", "cafebazaar"),
            emptyList(), "com.farsitel.bazaar"),
        Brand("فروشگاه کروم", "Crom", "پوشاک", SLUG_ECOMMERCE, 0xFF424242.toInt(),
            // "کروم" alone collides with "گوگل کروم"; require the shop context.
            listOf("فروشگاه کروم", "پوشاک کروم", "crom.ir")),

        // ---------- Transport & travel ----------
        Brand("اسنپ", "Snapp", "تاکسی اینترنتی", SLUG_TRANSPORT, 0xFF04B159.toInt(),
            listOf("اسنپ", "snapp"), listOf("snapp"), "cab.snapp.passenger", "https://snapp.ir"),
        Brand("تپسی", "Tapsi", "تاکسی اینترنتی", SLUG_TRANSPORT, 0xFFFF5F00.toInt(),
            listOf("تپسی", "tapsi"), listOf("tapsi"), "taxi.tap30.passenger", "https://tapsi.ir"),
        Brand("ماکسیم", "Maxim", "تاکسی اینترنتی", SLUG_TRANSPORT, 0xFFFFC107.toInt(),
            listOf("ماکسیم", "maxim")),
        Brand("الوپیک", "Alopeyk", "ارسال بسته و پیک", SLUG_TRANSPORT, 0xFF00BCD4.toInt(),
            listOf("الوپیک", "الو پیک", "alopeyk")),
        Brand("میهن‌پست", "Mihanpost", "ارسال بسته و پیک", SLUG_TRANSPORT, 0xFF1565C0.toInt(),
            listOf("میهن پست", "میهن‌پست", "mihanpost")),
        Brand("علی‌بابا", "Alibaba", "گردشگری و سفر", SLUG_TRANSPORT, 0xFF0D9488.toInt(),
            listOf("علی بابا", "علی‌بابا", "علیبابا", "alibaba"),
            emptyList(), "ir.alibaba.alibaba", "https://alibaba.ir"),
        Brand("اسنپ‌تریپ", "SnappTrip", "گردشگری و سفر", SLUG_TRANSPORT, 0xFF0097A7.toInt(),
            listOf("اسنپ تریپ", "اسنپ‌تریپ", "snapptrip")),
        Brand("فلای‌تودی", "Flytoday", "گردشگری و سفر", SLUG_TRANSPORT, 0xFF1E88E5.toInt(),
            listOf("فلای تودی", "فلای‌تودی", "flytoday")),
        Brand("مستر بلیط", "MrBilit", "گردشگری و سفر", SLUG_TRANSPORT, 0xFF283593.toInt(),
            listOf("مستربلیط", "مستر بلیط", "mrbilit")),
        Brand("جاباما", "Jabama", "اقامتگاه و هتل", SLUG_TRANSPORT, 0xFFEC407A.toInt(),
            listOf("جاباما", "jabama")),
        Brand("اتاقک", "Otaghak", "اقامتگاه و هتل", SLUG_TRANSPORT, 0xFF26A69A.toInt(),
            listOf("اتاقک", "otaghak")),

        // ---------- Entertainment ----------
        Brand("فیلیمو", "Filimo", "فیلم و سریال", SLUG_ENTERTAINMENT, 0xFF00C2A8.toInt(),
            listOf("فیلیمو", "filimo"), emptyList(), "com.sabaidea.filimo", "https://filimo.com"),
        Brand("نماوا", "Namava", "فیلم و سریال", SLUG_ENTERTAINMENT, 0xFFE53935.toInt(),
            listOf("نماوا", "namava"), emptyList(), "com.namava.mobile", "https://namava.ir"),
        Brand("فیلم‌نت", "Filmnet", "فیلم و سریال", SLUG_ENTERTAINMENT, 0xFF7E57C2.toInt(),
            listOf("فیلم نت", "فیلم‌نت", "فیلمنت", "filmnet")),
        Brand("سینماتیکت", "CinemaTicket", "تفریح و سینما", SLUG_ENTERTAINMENT, 0xFFD32F2F.toInt(),
            listOf("سینماتیکت", "سینما تیکت", "cinematicket")),
        Brand("تیوال", "Tiwall", "تفریح و سینما", SLUG_ENTERTAINMENT, 0xFF512DA8.toInt(),
            listOf("تیوال", "tiwall")),
        Brand("ایران‌کنسرت", "IranConcert", "تفریح و سینما", SLUG_ENTERTAINMENT, 0xFF303F9F.toInt(),
            listOf("ایران کنسرت", "ایران‌کنسرت", "iranconcert")),
        Brand("نوا", "Nava", "موسیقی", SLUG_ENTERTAINMENT, 0xFF8E24AA.toInt(),
            listOf("نوامیوزیک", "نوا موزیک")),

        // ---------- Fintech & payment ----------
        Brand("اسنپ‌پی", "SnappPay", "پرداخت اقساطی", SLUG_FINTECH, 0xFF00A651.toInt(),
            listOf("اسنپ پی", "اسنپ‌پی", "snapppay")),
        Brand("دیجی‌پی", "Digipay", "پرداخت اقساطی", SLUG_FINTECH, 0xFFE53935.toInt(),
            listOf("دیجی پی", "دیجی‌پی", "digipay", "dgpay")),
        Brand("تارا", "Tara", "پرداخت اعتباری", SLUG_FINTECH, 0xFF00695C.toInt(),
            listOf("تاراکارت", "تارا کارت", "اعتبار تارا")),
        Brand("لندو", "Lendo", "پرداخت اقساطی", SLUG_FINTECH, 0xFF0277BD.toInt(),
            listOf("لندو", "lendo")),
        Brand("قسطا", "Ghesta", "پرداخت اقساطی", SLUG_FINTECH, 0xFF00838F.toInt(),
            listOf("قسطا", "ghesta")),
        Brand("آپ", "AsanPardakht", "کیف پول", SLUG_FINTECH, 0xFF1E88E5.toInt(),
            listOf("آسان پرداخت", "اپلیکیشن آپ", "ap.ir")),
        Brand("ایوا", "Iva", "کیف پول", SLUG_FINTECH, 0xFF43A047.toInt(),
            listOf("ایوا", "سداد")),
        Brand("زرین‌پال", "ZarinPal", "درگاه پرداخت", SLUG_FINTECH, 0xFFFDD835.toInt(),
            listOf("زرین پال", "زرین‌پال", "zarinpal")),
        Brand("درگاه اوزون", "Ozon", "پرداخت و تخفیف", SLUG_FINTECH, 0xFF5E35B1.toInt(),
            listOf("اوزون", "ozone")),
        Brand("ازکی", "Azki", "بیمه آنلاین", SLUG_FINTECH, 0xFF00ACC1.toInt(),
            listOf("ازکی", "azki")),
        Brand("بیمه دات‌کام", "Bimeh.com", "بیمه آنلاین", SLUG_FINTECH, 0xFF039BE5.toInt(),
            listOf("بیمه دات کام", "بیمه‌دات‌کام", "bmeh.me", "bimeh.com")),

        // ---------- Telecom ----------
        Brand("همراه اول", "MCI", "اپراتور تلفن همراه", SLUG_TELECOM, 0xFF0066B3.toInt(),
            listOf("همراه اول", "hamrah", "mci"), listOf("hamrahaval", "mci", "98100")),
        Brand("ایرانسل", "Irancell", "اپراتور تلفن همراه", SLUG_TELECOM, 0xFFFFD100.toInt(),
            listOf("ایرانسل", "irancell", "mtn"), listOf("irancell", "mtn", "98700")),
        Brand("رایتل", "Rightel", "اپراتور تلفن همراه", SLUG_TELECOM, 0xFF8E24AA.toInt(),
            listOf("رایتل", "rightel"), listOf("rightel")),
        Brand("شاتل", "Shatel", "اینترنت ثابت", SLUG_TELECOM, 0xFFEF6C00.toInt(),
            listOf("شاتل", "shatel")),
        Brand("زی‌تل", "Zitel", "اینترنت ثابت", SLUG_TELECOM, 0xFF00ACC1.toInt(),
            listOf("زی تل", "زی‌تل", "زیتل", "zitel")),
        Brand("مخابرات", "TCI", "اینترنت ثابت", SLUG_TELECOM, 0xFF1565C0.toInt(),
            listOf("مخابرات", "tci")),

        // ---------- Services ----------
        Brand("دیجی‌واش", "DigiWash", "خشکشویی آنلاین", SLUG_SERVICES, 0xFF26C6DA.toInt(),
            listOf("دیجی واش", "دیجی‌واش", "irdgw", "digiwash")),
        Brand("اکتیو کلینرز", "ActiveCleaners", "خشکشویی آنلاین", SLUG_SERVICES, 0xFF0097A7.toInt(),
            listOf("اکتیوکلینرز", "اکتیو کلینرز", "activecleaners")),
        Brand("اسنپ‌دکتر", "SnappDoctor", "سلامت", SLUG_SERVICES, 0xFF00ACC1.toInt(),
            listOf("اسنپ دکتر", "اسنپ‌دکتر", "snappdoctor")),
        Brand("دکترتو", "Doctoreto", "سلامت", SLUG_SERVICES, 0xFF3949AB.toInt(),
            listOf("دکترتو", "doctoreto")),
        Brand("اسنپ‌شاپ", "SnappShop", "خدمات خودرو", SLUG_SERVICES, 0xFF43A047.toInt(),
            listOf("اسنپ شاپ", "اسنپ‌شاپ", "snappshop")),
        Brand("کارنامه", "Karnameh", "خدمات خودرو", SLUG_SERVICES, 0xFF00796B.toInt(),
            listOf("کارنامه", "karnameh")),
        Brand("همیار", "Hamyar", "خدمات منزل", SLUG_SERVICES, 0xFF7CB342.toInt(),
            listOf("همیارسرویس", "همیار سرویس"))
    )

    /** Every keyword, longest first, so the most specific name is always tried before a prefix. */
    private val KEYWORD_INDEX: List<Pair<String, Brand>> = BRANDS
        .flatMap { brand -> brand.keywords.map { it.toLowerCase() to brand } }
        .sortedByDescending { it.first.length }

    /** Named sender ids plus keywords, for the sweep over the sender field. */
    private val SENDER_INDEX: List<Pair<String, Brand>> = BRANDS
        .flatMap { brand -> (brand.senderIds + brand.keywords).filterNot { isNumber(it) }.map { it.toLowerCase() to brand } }
        .distinctBy { it.first + "|" + it.second.en }
        .sortedByDescending { it.first.length }

    /** Numeric sender ids, without the country prefix, compared as whole numbers. */
    private val NUMBER_INDEX: List<Pair<String, Brand>> = BRANDS
        .flatMap { brand -> brand.senderIds.filter { isNumber(it) }.map { localNumber(it) to brand } }

    private const val BODY_BASE = 1000
    private const val SENDER_BASE = 600

    /**
     * Scores brands over one field using longest-match-wins.
     *
     * Characters claimed by a longer keyword are marked consumed, so once "تپسی فود" matches,
     * the "تپسی" sitting inside it cannot also score. Without that, a brand whose name is a
     * prefix of another's would win on nothing but being shorter — the same ordering trap the
     * old hand-written `when` chain fell into.
     */
    private fun scoreField(
        text: String,
        index: List<Pair<String, Brand>>,
        base: Int,
        scores: HashMap<Brand, Int>
    ) {
        if (text.length == 0) return
        val consumed = BooleanArray(text.length)

        for ((keyword, brand) in index) {
            if (keyword.length == 0 || keyword.length > text.length) continue
            var idx = text.indexOf(keyword)
            while (idx >= 0) {
                var overlaps = false
                for (i in idx until idx + keyword.length) {
                    if (consumed[i]) {
                        overlaps = true
                        break
                    }
                }
                if (!overlaps) {
                    for (i in idx until idx + keyword.length) {
                        consumed[i] = true
                    }
                    val score = base + keyword.length * 10
                    if (score > (scores[brand] ?: 0)) scores[brand] = score
                    break
                }
                idx = text.indexOf(keyword, idx + 1)
            }
        }
    }

    /**
     * Picks the brand a promotional message is about, or null when none is recognisable.
     *
     * The body outranks the sender: a short code like "TAPSI" is shared by a company's whole
     * family of apps, while the name written in the ad copy identifies the actual service.
     *
     * @param normalizedSender sender, digits/characters already normalized and lower-cased
     * @param normalizedBody SMS body, digits/characters already normalized and lower-cased
     */
    fun match(normalizedSender: String, normalizedBody: String): Brand? {
        val scores = HashMap<Brand, Int>()
        scoreField(normalizedBody, KEYWORD_INDEX, BODY_BASE, scores)
        if (isNumber(normalizedSender)) {
            scoreNumber(normalizedSender, scores)
        } else {
            scoreField(normalizedSender, SENDER_INDEX, SENDER_BASE, scores)
        }
        return scores.maxBy { it.value }?.key
    }

    /**
     * A numeric sender belongs to a brand only when it is that brand's number. Matching the
     * digits anywhere inside the sender labelled every "+98 1000…" bulk line as MCI (98100)
     * and Bank Melli's 700717 line as Irancell (98700). A long number may carry a suffix.
     */
    private fun scoreNumber(sender: String, scores: HashMap<Brand, Int>) {
        val number = localNumber(sender)
        for ((id, brand) in NUMBER_INDEX) {
            if (number == id || (id.length >= 7 && number.startsWith(id))) {
                val score = SENDER_BASE + id.length * 10
                if (score > (scores[brand] ?: 0)) scores[brand] = score
            }
        }
    }

    private fun isNumber(sender: String): Boolean =
        sender.any { it.isDigit() } && sender.all { it.isDigit() || it == '+' || it == ' ' || it == '-' }

    /** Digits only, without +98 / 0098: "+983000445" becomes "3000445". */
    private fun localNumber(sender: String): String {
        val digits = sender.filter { it.isDigit() }
        return when {
            digits.startsWith("0098") -> digits.substring(4)
            digits.startsWith("98") -> digits.substring(2)
            else -> digits
        }
    }

    /** Looks a brand up by its exact Persian name, used when rehydrating persisted promos. */
    fun byPersianName(fa: String): Brand? = BRANDS.firstOrNull { it.fa == fa }

    /**
     * Stable fallback colour for a brand the registry does not know, derived from its name so
     * the same unknown sender always gets the same avatar colour.
     */
    fun fallbackColor(name: String): Int {
        val palette = intArrayOf(
            0xFF5C6BC0.toInt(), 0xFF26A69A.toInt(), 0xFFEF5350.toInt(), 0xFFAB47BC.toInt(),
            0xFF42A5F5.toInt(), 0xFF66BB6A.toInt(), 0xFFFFA726.toInt(), 0xFF8D6E63.toInt()
        )
        if (name.isBlank()) return palette[0]
        var hash = 0
        for (c in name) hash = (hash * 31 + c.toInt()) and 0x7FFFFFFF
        return palette[hash % palette.size]
    }
}
