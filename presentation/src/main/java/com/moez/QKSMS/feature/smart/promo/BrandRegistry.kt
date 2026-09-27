package com.moez.QKSMS.feature.smart.promo

/** The part a brand plays in an offer. */
enum class BrandRole {
    /** A shop or service where the code is typed in. */
    MERCHANT,

    /** A wallet, instalment service or gateway; often sponsors codes used at other shops. */
    PAYMENT,

    BANK,

    TELECOM
}

/**
 * A single Iranian service/brand known to the discount engine.
 *
 * [keywords] are matched against the normalized SMS body and sender, [senderIds] against the
 * raw sender only. A keyword only counts as a whole word, and a longer keyword claims its
 * characters before a shorter one can, so the engine never depends on list order.
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
    val website: String? = null,
    val role: BrandRole = BrandRole.MERCHANT,
    /** Umbrella company ("snapp") shared by every service of one group. */
    val family: String? = null,
    /** The bare group name ("اسنپ"), which on its own does not say which service is meant. */
    val isFamilyRoot: Boolean = false,
    /** Words that point to this service within its family: "غذا" → اسنپ‌فود, "هتل" → اسنپ‌تریپ. */
    val cues: List<String> = emptyList(),
    /**
     * Names that are also everyday words ("دیوار" is a wall, "باسلام" opens letters). They
     * count only after a word like "در" or "اپلیکیشن", or when the sender is the brand.
     */
    val weakKeywords: List<String> = emptyList(),
    /** How sure a bare family name makes us when no cue says which service is meant. */
    val rootConfidence: Int = 80,
    /**
     * Fragments of a coupon code that point to this service within its family: "TPSBOXH24"
     * is a courier code, "SFOOD30" a food one. Weaker than a word in the text, but often the
     * only hint a terse message gives.
     */
    val codeHints: List<String> = emptyList(),
    /**
     * What to call a family's bare name when nothing says which service is meant: "سرویس‌های
     * اسنپ" rather than guessing "تاکسی اینترنتی" for a code that may be for the shop or the food app.
     */
    val familyLabel: String? = null
)

/** One place a brand is named in a message. */
data class BrandMention(val brand: Brand, val start: Int, val end: Int, val keyword: String)

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

    private const val SNAPP = "snapp"
    private const val TAPSI = "tapsi"
    private const val DIGIKALA = "digikala"

    private val FOOD_CUES = listOf(
        "غذا", "رستوران", "پیتزا", "برگر", "ساندویچ", "فست فود", "کباب", "شیرینی", "نوشیدنی",
        "صبحانه", "ناهار", "شام", "سفارش غذا", "food", "پیک رایگان"
    )
    private val GROCERY_CUES = listOf(
        "سوپرمارکت", "سوپر مارکت", "خواربار", "میوه", "لبنیات", "هایپرمارکت", "هایپر مارکت",
        "مواد غذایی", "بقالی", "market"
    )
    private val RIDE_CUES = listOf(
        "سفر", "سفرهای", "مقصد", "مبدا", "راننده", "تاکسی", "سواری", "درخواست خودرو", "موتور",
        "بایک", "کرایه", "مسافر", "ride", "cab"
    )
    private val PARCEL_CUES = listOf(
        "ارسال بسته", "بسته", "مرسوله", "پیک موتوری", "موتوپیک", "موتو پیک", "باربری", "ارسال مرسوله"
    )
    private val GADGET_CUES = listOf(
        "گوشی", "موبایل", "لپ تاپ", "لپتاپ", "لوازم خانگی", "کالای دیجیتال", "هدفون", "تلویزیون",
        "خرید اینترنتی", "فروشگاه اینترنتی"
    )
    private val INSTALMENT_CUES = listOf(
        "اقساط", "اقساطی", "قسطی", "قسط", "خرید قسطی", "اعتبار خرید", "پرداخت اقساطی", "کیف پول"
    )

    val BRANDS: List<Brand> = listOf(
        // ---------- Food & restaurants ----------
        Brand("اسنپ‌فود", "SnappFood", "غذا و رستوران", SLUG_FOOD, 0xFFE21A5C.toInt(),
            listOf("اسنپ فود", "snappfood", "snapp food", "snpf", "زودفود", "zoodfood"),
            listOf("snappfood", "+983000445", "10000445"),
            "com.zoodfood.android", "https://snappfood.ir",
            family = SNAPP, cues = FOOD_CUES, codeHints = listOf("food")),
        Brand("تپسی‌فود", "TapsiFood", "غذا و رستوران", SLUG_FOOD, 0xFFFF5722.toInt(),
            listOf("تپسی فود", "tapsifood", "tapsi food", "tapsi.food"),
            emptyList(), "food.tapsi.ir", "https://tapsi.food",
            family = TAPSI, cues = FOOD_CUES, codeHints = listOf("food")),
        Brand("چیلیوری", "Chilivery", "غذا و رستوران", SLUG_FOOD, 0xFF00A99D.toInt(),
            listOf("چیلیوری", "chilivery"), emptyList(), "com.chilivery", "https://chilivery.com"),
        Brand("ریحون", "Reyhoon", "غذا و رستوران", SLUG_FOOD, 0xFF8BC34A.toInt(),
            listOf("ریحون", "reyhoon"), emptyList(), "com.reyhoon.android", "https://reyhoon.com"),
        Brand("دلینو", "Delino", "غذا و رستوران", SLUG_FOOD, 0xFFEF5350.toInt(),
            listOf("دلینو", "delino")),
        Brand("اسنپ‌کافه", "SnappCafe", "کافه و نوشیدنی", SLUG_FOOD, 0xFF795548.toInt(),
            listOf("اسنپ کافه", "snappcafe"), family = SNAPP),

        // ---------- Supermarket ----------
        Brand("اسنپ‌مارکت", "SnappMarket", "سوپرمارکت", SLUG_SUPERMARKET, 0xFF00B074.toInt(),
            listOf("اسنپ مارکت", "snappmarket", "snapp market"),
            emptyList(), "ir.snapp.market", "https://snapp.market",
            family = SNAPP, cues = GROCERY_CUES, codeHints = listOf("market", "mart")),
        Brand("اسنپ‌اکسپرس", "SnappExpress", "سوپرمارکت", SLUG_SUPERMARKET, 0xFF00A86B.toInt(),
            listOf("اسنپ اکسپرس", "snappexpress", "snapp express"), family = SNAPP),
        Brand("اکالا", "Okala", "سوپرمارکت", SLUG_SUPERMARKET, 0xFFE91E63.toInt(),
            listOf("اکالا", "okala", "افق کوروش"),
            listOf("okala"), "com.okala", "https://okala.com"),
        Brand("تپسی‌مارکت", "TapsiMarket", "سوپرمارکت", SLUG_SUPERMARKET, 0xFFFF7043.toInt(),
            listOf("تپسی مارکت", "tpmk", "tapsimarket"), family = TAPSI),
        Brand("دیجی‌کالا جت", "DigikalaJet", "سوپرمارکت", SLUG_SUPERMARKET, 0xFF4CAF50.toInt(),
            listOf("دیجی کالا جت", "دیجیکالا جت", "دیجی کالاجت", "digikalajet", "digikala jet",
                "دیجی کالا فرش", "دیجیکالا فرش", "دیجی فرش", "digifresh"),
            family = DIGIKALA, cues = GROCERY_CUES + listOf("jet"), codeHints = listOf("jet")),
        Brand("هایپراستار", "Hyperstar", "سوپرمارکت", SLUG_SUPERMARKET, 0xFF0D47A1.toInt(),
            listOf("هایپراستار", "هایپر استار", "hyperstar")),
        Brand("هایپرمی", "Hyperme", "سوپرمارکت", SLUG_SUPERMARKET, 0xFFE53935.toInt(),
            listOf("هایپرمی", "hyperme")),
        Brand("جانبو", "Janbo", "سوپرمارکت", SLUG_SUPERMARKET, 0xFFF9A825.toInt(),
            listOf("جانبو", "janbo")),

        // ---------- E-commerce & retail ----------
        Brand("دیجی‌کالا", "Digikala", "فروشگاه آنلاین", SLUG_ECOMMERCE, 0xFFEF4056.toInt(),
            listOf("دیجی کالا", "digikala", "dgkl", "دیجی پلاس", "digiplus", "دیجی کلاب", "digiclub"),
            listOf("digikala", "+983000101"), "com.digikala", "https://digikala.com",
            family = DIGIKALA, isFamilyRoot = true, cues = GADGET_CUES, rootConfidence = 80),
        Brand("دیجی‌استایل", "DigiStyle", "مد و پوشاک", SLUG_ECOMMERCE, 0xFF6A1B9A.toInt(),
            listOf("دیجی استایل", "digistyle"),
            emptyList(), "com.digistyle", "https://digistyle.com",
            family = DIGIKALA, cues = listOf("پوشاک", "لباس", "کفش", "اکسسوری", "مد و", "فشن"),
            codeHints = listOf("style")),
        Brand("اسنپ‌شاپ", "SnappShop", "فروشگاه اینترنتی", SLUG_ECOMMERCE, 0xFF1FAA59.toInt(),
            // "فروشگاه اسنپ" is what Snapp itself calls the shop in its texts
            listOf("اسنپ شاپ", "snappshop", "snapp shop", "فروشگاه اسنپ", "اسنپ فروشگاه"),
            emptyList(), null, "https://snappshop.ir",
            family = SNAPP, cues = GADGET_CUES + listOf("مارکت پلیس", "shop"),
            codeHints = listOf("shop")),
        Brand("باسلام", "Basalam", "فروشگاه آنلاین", SLUG_ECOMMERCE, 0xFF00897B.toInt(),
            listOf("basalam"), emptyList(), "com.basalam.app", "https://basalam.com",
            weakKeywords = listOf("باسلام")),
        Brand("ترب", "Torob", "مقایسه قیمت", SLUG_ECOMMERCE, 0xFF3F51B5.toInt(),
            listOf("torob"), emptyList(), "ir.torob", "https://torob.com",
            weakKeywords = listOf("ترب")),
        Brand("ایمالز", "Emalls", "مقایسه قیمت", SLUG_ECOMMERCE, 0xFF1976D2.toInt(),
            listOf("ایمالز", "emalls")),
        Brand("خانومی", "Khanoumi", "آرایشی و بهداشتی", SLUG_ECOMMERCE, 0xFFD81B60.toInt(),
            listOf("خانومی", "khanoumi"), emptyList(), "com.khanoumi", "https://khanoumi.com"),
        Brand("تکنولایف", "TechnoLife", "کالای دیجیتال", SLUG_ECOMMERCE, 0xFF0288D1.toInt(),
            listOf("تکنولایف", "تکنو لایف", "technolife"), emptyList(), "com.technolife", "https://technolife.ir"),
        Brand("بانی‌مد", "Banimode", "مد و پوشاک", SLUG_ECOMMERCE, 0xFFAD1457.toInt(),
            listOf("بانی مد", "banimode")),
        Brand("مدیسه", "Modiseh", "مد و پوشاک", SLUG_ECOMMERCE, 0xFF7B1FA2.toInt(),
            listOf("مدیسه", "modiseh")),
        Brand("زیبامو", "Zibamo", "آرایشی و بهداشتی", SLUG_ECOMMERCE, 0xFFC2185B.toInt(),
            listOf("زیبامو", "zibamo")),
        Brand("مسترکالا", "Masterkala", "کالای دیجیتال", SLUG_ECOMMERCE, 0xFF455A64.toInt(),
            listOf("مستر کالا", "masterkala")),
        Brand("گوشی‌شاپ", "Gooshishop", "کالای دیجیتال", SLUG_ECOMMERCE, 0xFF37474F.toInt(),
            listOf("گوشی شاپ", "gooshishop")),
        Brand("چرم مَنط", "MantLeather", "پوشاک و چرم", SLUG_ECOMMERCE, 0xFF5D4037.toInt(),
            listOf("چرم منط", "چرم مَنط", "مَنط")),
        Brand("نوین‌چرم", "NovinLeather", "پوشاک و چرم", SLUG_ECOMMERCE, 0xFF6D4C41.toInt(),
            listOf("نوین چرم")),
        Brand("چرم مشهد", "CharmMashhad", "پوشاک و چرم", SLUG_ECOMMERCE, 0xFF4E342E.toInt(),
            listOf("چرم مشهد", "charmmashhad")),
        Brand("ال‌سی‌وایکیکی", "LCWaikiki", "مد و پوشاک", SLUG_ECOMMERCE, 0xFF1565C0.toInt(),
            listOf("ال سی وایکیکی", "lc waikiki", "lcwaikiki")),
        Brand("کوتون", "Koton", "مد و پوشاک", SLUG_ECOMMERCE, 0xFF212121.toInt(),
            listOf("koton"), weakKeywords = listOf("کوتون")),
        Brand("پیندو", "Pindo", "آگهی و نیازمندی", SLUG_ECOMMERCE, 0xFF26A69A.toInt(),
            listOf("پیندو", "pindo")),
        Brand("تخفیفان", "Takhfifan", "کوپن و تخفیف", SLUG_ECOMMERCE, 0xFFF57C00.toInt(),
            listOf("تخفیفان", "takhfifan")),
        Brand("نت‌برگ", "Netbarg", "کوپن و تخفیف", SLUG_ECOMMERCE, 0xFFFF6F00.toInt(),
            listOf("نت برگ", "netbarg")),
        Brand("دیوار", "Divar", "آگهی و نیازمندی", SLUG_ECOMMERCE, 0xFFA62626.toInt(),
            listOf("divar"), emptyList(), "ir.divar", "https://divar.ir",
            weakKeywords = listOf("دیوار")),
        Brand("شیپور", "Sheypoor", "آگهی و نیازمندی", SLUG_ECOMMERCE, 0xFF00796B.toInt(),
            listOf("sheypoor"), weakKeywords = listOf("شیپور")),
        Brand("کافه‌بازار", "CafeBazaar", "اپلیکیشن", SLUG_ECOMMERCE, 0xFF00B0FF.toInt(),
            listOf("کافه بازار", "cafebazaar"),
            emptyList(), "com.farsitel.bazaar"),
        Brand("مایکت", "Myket", "اپلیکیشن", SLUG_ECOMMERCE, 0xFF1E88E5.toInt(),
            listOf("مایکت", "myket"), emptyList(), "ir.mservices.market", "https://myket.ir"),
        Brand("فروشگاه کروم", "Crom", "پوشاک", SLUG_ECOMMERCE, 0xFF424242.toInt(),
            // "کروم" alone collides with "گوگل کروم"; require the shop context.
            listOf("فروشگاه کروم", "پوشاک کروم", "crom.ir")),

        // ---------- Transport & travel ----------
        Brand("اسنپ", "Snapp", "تاکسی اینترنتی", SLUG_TRANSPORT, 0xFF04B159.toInt(),
            listOf("اسنپ", "snapp"), listOf("snapp"), "cab.snapp.passenger", "https://snapp.ir",
            family = SNAPP, isFamilyRoot = true, cues = RIDE_CUES, rootConfidence = 60,
            codeHints = listOf("ride", "taxi"), familyLabel = "سرویس‌های اسنپ"),
        Brand("تپسی", "Tapsi", "تاکسی اینترنتی", SLUG_TRANSPORT, 0xFFFF5F00.toInt(),
            listOf("تپسی", "tapsi", "tap30"), listOf("tapsi", "tap30"), "taxi.tap30.passenger", "https://tapsi.ir",
            family = TAPSI, isFamilyRoot = true, cues = RIDE_CUES, rootConfidence = 65,
            codeHints = listOf("ride", "taxi"), familyLabel = "سرویس‌های تپسی"),
        // Named after what Tapsi's own texts call the service: "تخفیف موتوپیک"
        Brand("تپسی موتوپیک", "TapsiPeyk", "ارسال بسته و پیک", SLUG_TRANSPORT, 0xFFFF8A50.toInt(),
            listOf("تپسی موتوپیک", "موتوپیک تپسی", "تپسی پیک", "تپسی پک", "tapsipack"),
            family = TAPSI, cues = PARCEL_CUES, codeHints = listOf("box", "pack", "peyk")),
        Brand("اسنپ‌باکس", "SnappBox", "ارسال بسته و پیک", SLUG_TRANSPORT, 0xFF00C853.toInt(),
            listOf("اسنپ باکس", "snappbox", "snapp box"), emptyList(), null, "https://snapp-box.com",
            family = SNAPP, cues = PARCEL_CUES, codeHints = listOf("box", "peyk")),
        Brand("ماکسیم", "Maxim", "تاکسی اینترنتی", SLUG_TRANSPORT, 0xFFFFC107.toInt(),
            listOf("ماکسیم", "maxim")),
        Brand("الوپیک", "Alopeyk", "ارسال بسته و پیک", SLUG_TRANSPORT, 0xFF00BCD4.toInt(),
            listOf("الوپیک", "الو پیک", "alopeyk")),
        Brand("میهن‌پست", "Mihanpost", "ارسال بسته و پیک", SLUG_TRANSPORT, 0xFF1565C0.toInt(),
            listOf("میهن پست", "mihanpost")),
        Brand("علی‌بابا", "Alibaba", "گردشگری و سفر", SLUG_TRANSPORT, 0xFF0D9488.toInt(),
            listOf("علی بابا", "alibaba"),
            emptyList(), "ir.alibaba.alibaba", "https://alibaba.ir"),
        Brand("اسنپ‌تریپ", "SnappTrip", "گردشگری و سفر", SLUG_TRANSPORT, 0xFF0097A7.toInt(),
            listOf("اسنپ تریپ", "snapptrip", "snapp trip"), emptyList(), null, "https://www.snapptrip.com",
            family = SNAPP, cues = listOf(
                "هتل", "بلیط", "بلیت", "پرواز", "اقامت", "اقامتگاه", "تور", "ویلا", "رزرو", "قطار",
                "اتوبوس", "trip"
            ), codeHints = listOf("trip", "hotel")),
        Brand("فلای‌تودی", "Flytoday", "گردشگری و سفر", SLUG_TRANSPORT, 0xFF1E88E5.toInt(),
            listOf("فلای تودی", "flytoday")),
        Brand("مستر بلیط", "MrBilit", "گردشگری و سفر", SLUG_TRANSPORT, 0xFF283593.toInt(),
            listOf("مستر بلیط", "مستر بلیت", "mrbilit")),
        Brand("جاباما", "Jabama", "اقامتگاه و هتل", SLUG_TRANSPORT, 0xFFEC407A.toInt(),
            listOf("جاباما", "jabama")),
        Brand("اتاقک", "Otaghak", "اقامتگاه و هتل", SLUG_TRANSPORT, 0xFF26A69A.toInt(),
            listOf("otaghak"), weakKeywords = listOf("اتاقک")),

        // ---------- Entertainment & books ----------
        Brand("فیلیمو", "Filimo", "فیلم و سریال", SLUG_ENTERTAINMENT, 0xFF00C2A8.toInt(),
            listOf("فیلیمو", "filimo"), emptyList(), "com.sabaidea.filimo", "https://filimo.com"),
        Brand("نماوا", "Namava", "فیلم و سریال", SLUG_ENTERTAINMENT, 0xFFE53935.toInt(),
            listOf("نماوا", "namava"), emptyList(), "com.namava.mobile", "https://namava.ir"),
        Brand("فیلم‌نت", "Filmnet", "فیلم و سریال", SLUG_ENTERTAINMENT, 0xFF7E57C2.toInt(),
            listOf("فیلم نت", "filmnet")),
        Brand("سینماتیکت", "CinemaTicket", "تفریح و سینما", SLUG_ENTERTAINMENT, 0xFFD32F2F.toInt(),
            listOf("سینماتیکت", "سینما تیکت", "cinematicket")),
        Brand("تیوال", "Tiwall", "تفریح و سینما", SLUG_ENTERTAINMENT, 0xFF512DA8.toInt(),
            listOf("تیوال", "tiwall")),
        Brand("ایران‌کنسرت", "IranConcert", "تفریح و سینما", SLUG_ENTERTAINMENT, 0xFF303F9F.toInt(),
            listOf("ایران کنسرت", "iranconcert")),
        Brand("نوا", "Nava", "موسیقی", SLUG_ENTERTAINMENT, 0xFF8E24AA.toInt(),
            listOf("نوامیوزیک", "نوا موزیک")),
        Brand("فیدیبو", "Fidibo", "کتاب و کتاب صوتی", SLUG_ENTERTAINMENT, 0xFFEC6A2A.toInt(),
            listOf("فیدیبو", "fidibo"), emptyList(), null, "https://fidibo.com"),
        Brand("طاقچه", "Taaghche", "کتاب و کتاب صوتی", SLUG_ENTERTAINMENT, 0xFF2E7D32.toInt(),
            listOf("taaghche"), emptyList(), null, "https://taaghche.com",
            weakKeywords = listOf("طاقچه")),
        Brand("کتابراه", "Ketabrah", "کتاب و کتاب صوتی", SLUG_ENTERTAINMENT, 0xFF0277BD.toInt(),
            listOf("کتابراه", "ketabrah")),
        Brand("نوار", "Navaar", "کتاب صوتی", SLUG_ENTERTAINMENT, 0xFF6A1B9A.toInt(),
            listOf("navaar"), weakKeywords = listOf("نوار")),

        // ---------- Payment, instalments & insurance ----------
        Brand("اسنپ‌پی", "SnappPay", "پرداخت اقساطی", SLUG_FINTECH, 0xFF00A651.toInt(),
            listOf("اسنپ پی", "snapppay", "snapp pay"), emptyList(), null, "https://snapppay.ir",
            role = BrandRole.PAYMENT, family = SNAPP, cues = INSTALMENT_CUES, codeHints = listOf("pay")),
        Brand("دیجی‌پی", "Digipay", "پرداخت اقساطی", SLUG_FINTECH, 0xFFE53935.toInt(),
            listOf("دیجی پی", "digipay", "dgpay", "digi pay", "mydigipay"),
            emptyList(), "com.mydigipay.app.android", "https://www.mydigipay.com",
            role = BrandRole.PAYMENT, family = DIGIKALA, cues = INSTALMENT_CUES, codeHints = listOf("pay")),
        Brand("تارا", "Tara", "پرداخت اعتباری", SLUG_FINTECH, 0xFF00695C.toInt(),
            listOf("تاراکارت", "تارا کارت", "اعتبار تارا", "tara360"),
            role = BrandRole.PAYMENT, weakKeywords = listOf("تارا")),
        Brand("لندو", "Lendo", "پرداخت اقساطی", SLUG_FINTECH, 0xFF0277BD.toInt(),
            listOf("لندو", "lendo"), role = BrandRole.PAYMENT),
        Brand("قسطا", "Ghesta", "پرداخت اقساطی", SLUG_FINTECH, 0xFF00838F.toInt(),
            listOf("قسطا", "ghesta"), role = BrandRole.PAYMENT),
        Brand("آپ", "AsanPardakht", "کیف پول", SLUG_FINTECH, 0xFF1E88E5.toInt(),
            listOf("آسان پرداخت", "اپلیکیشن آپ", "ap.ir", "asanpardakht"), role = BrandRole.PAYMENT),
        Brand("ایوا", "Iva", "کیف پول", SLUG_FINTECH, 0xFF43A047.toInt(),
            listOf("ایوا", "سداد"), role = BrandRole.PAYMENT),
        Brand("بازارپی", "BazaarPay", "کیف پول", SLUG_FINTECH, 0xFF00B0FF.toInt(),
            listOf("بازار پی", "bazaarpay"), role = BrandRole.PAYMENT),
        Brand("زرین‌پال", "ZarinPal", "درگاه پرداخت", SLUG_FINTECH, 0xFFFDD835.toInt(),
            listOf("زرین پال", "zarinpal"), role = BrandRole.PAYMENT),
        Brand("درگاه اوزون", "Ozon", "پرداخت و تخفیف", SLUG_FINTECH, 0xFF5E35B1.toInt(),
            listOf("ozone"), role = BrandRole.PAYMENT, weakKeywords = listOf("اوزون")),
        Brand("ازکی", "Azki", "بیمه آنلاین", SLUG_FINTECH, 0xFF00ACC1.toInt(),
            listOf("ازکی", "azki")),
        Brand("بیمه دات‌کام", "Bimeh.com", "بیمه آنلاین", SLUG_FINTECH, 0xFF039BE5.toInt(),
            listOf("بیمه دات کام", "bmeh.me", "bimeh.com")),
        Brand("بیمه‌بازار", "BimeBazar", "بیمه آنلاین", SLUG_FINTECH, 0xFF0288D1.toInt(),
            listOf("بیمه بازار", "bimebazar")),
        Brand("اسنپ‌بیمه", "SnappInsurance", "بیمه آنلاین", SLUG_FINTECH, 0xFF00A86B.toInt(),
            listOf("اسنپ بیمه", "snappbime", "snapp bime", "snapp insurance"),
            family = SNAPP, cues = listOf("بیمه", "بیمه ثالث", "بیمه بدنه", "بیمه نامه", "insurance"),
            codeHints = listOf("bime")),

        // ---------- Banks: sponsors of card offers far more often than the shop ----------
        bank("بانک ملی", "Melli", 0xFF0D47A1.toInt(), listOf("بانک ملی", "bmi"), listOf("melli", "bmi")),
        bank("بانک ملت", "Mellat", 0xFFC62828.toInt(), listOf("بانک ملت", "mellat"), listOf("mellat")),
        bank("بانک صادرات", "Saderat", 0xFF1A237E.toInt(), listOf("بانک صادرات", "saderat"), listOf("saderat")),
        bank("بانک تجارت", "Tejarat", 0xFF283593.toInt(), listOf("بانک تجارت", "tejarat"), listOf("tejarat")),
        bank("بانک سپه", "Sepah", 0xFF1B5E20.toInt(), listOf("بانک سپه", "sepah"), listOf("sepah")),
        bank("بانک پاسارگاد", "Pasargad", 0xFFFFB300.toInt(), listOf("بانک پاسارگاد", "pasargad", "bpi"), listOf("pasargad", "bpi")),
        bank("بانک سامان", "Saman", 0xFF1565C0.toInt(), listOf("بانک سامان", "sb24"), listOf("saman", "sb24")),
        bank("بانک پارسیان", "Parsian", 0xFFB71C1C.toInt(), listOf("بانک پارسیان", "parsian"), listOf("parsian")),
        bank("بانک آینده", "Ayandeh", 0xFF6A1B9A.toInt(), listOf("بانک آینده", "آبانک", "ayandeh"), listOf("ayandeh")),
        bank("بانک کشاورزی", "Keshavarzi", 0xFF2E7D32.toInt(), listOf("بانک کشاورزی", "keshavarzi"), listOf("keshavarzi")),
        bank("بانک مسکن", "Maskan", 0xFFE65100.toInt(), listOf("بانک مسکن", "maskan"), listOf("maskan")),
        bank("بانک رفاه", "Refah", 0xFF0277BD.toInt(), listOf("بانک رفاه", "رفاه کارگران"), listOf("refah")),
        bank("بانک شهر", "Shahr", 0xFFD32F2F.toInt(), listOf("بانک شهر"), listOf("shahrbank")),
        bank("بانک سینا", "Sina", 0xFF00838F.toInt(), listOf("بانک سینا"), listOf("sinabank")),
        bank("بانک دی", "Dey", 0xFF00695C.toInt(), listOf("بانک دی"), listOf("bankdey")),
        bank("بانک اقتصاد نوین", "EN Bank", 0xFF6D4C41.toInt(), listOf("اقتصاد نوین", "enbank"), listOf("enbank", "eghtesad")),
        bank("بانک کارآفرین", "Karafarin", 0xFF00897B.toInt(), listOf("بانک کارآفرین", "karafarin"), listOf("karafarin")),
        bank("بانک گردشگری", "Gardeshgari", 0xFF1E88E5.toInt(), listOf("بانک گردشگری"), listOf("gardeshgari")),
        bank("بانک ایران زمین", "IranZamin", 0xFF5D4037.toInt(), listOf("بانک ایران زمین", "ایران زمین"), listOf("iranzamin")),
        bank("بانک سرمایه", "Sarmayeh", 0xFF455A64.toInt(), listOf("بانک سرمایه"), listOf("sarmayeh")),
        bank("پست بانک", "PostBank", 0xFF2E7D32.toInt(), listOf("پست بانک"), listOf("postbank")),
        bank("بانک رسالت", "Resalat", 0xFF00796B.toInt(), listOf("بانک رسالت", "قرض الحسنه رسالت"), listOf("resalat")),
        bank("بانک مهر ایران", "MehrIran", 0xFF388E3C.toInt(), listOf("بانک مهر ایران", "مهر ایران"), listOf("mehriran", "qmehr")),
        bank("بانک خاورمیانه", "Middle East Bank", 0xFF37474F.toInt(), listOf("بانک خاورمیانه"), listOf("middleeast")),
        bank("بلوبانک", "Blu Bank", 0xFF1565C0.toInt(), listOf("بلوبانک", "بلو بانک", "blubank", "blu bank"), listOf("blubank", "blu")),
        bank("ویپاد", "Wepod", 0xFF00ACC1.toInt(), listOf("ویپاد", "wepod"), listOf("wepod")),

        // ---------- Telecom ----------
        Brand("همراه اول", "MCI", "اپراتور تلفن همراه", SLUG_TELECOM, 0xFF0066B3.toInt(),
            listOf("همراه اول", "hamrah", "hamrahaval", "mci"), listOf("hamrahaval", "mci", "98100"),
            role = BrandRole.TELECOM),
        Brand("ایرانسل", "Irancell", "اپراتور تلفن همراه", SLUG_TELECOM, 0xFFFFD100.toInt(),
            listOf("ایرانسل", "irancell", "mtn"), listOf("irancell", "mtn", "98700"),
            role = BrandRole.TELECOM),
        Brand("رایتل", "Rightel", "اپراتور تلفن همراه", SLUG_TELECOM, 0xFF8E24AA.toInt(),
            listOf("رایتل", "rightel"), listOf("rightel"), role = BrandRole.TELECOM),
        Brand("شاتل", "Shatel", "اینترنت ثابت", SLUG_TELECOM, 0xFFEF6C00.toInt(),
            listOf("شاتل", "shatel"), role = BrandRole.TELECOM),
        Brand("زی‌تل", "Zitel", "اینترنت ثابت", SLUG_TELECOM, 0xFF00ACC1.toInt(),
            listOf("زی تل", "zitel"), role = BrandRole.TELECOM),
        Brand("مبین‌نت", "MobinNet", "اینترنت ثابت", SLUG_TELECOM, 0xFF1976D2.toInt(),
            listOf("مبین نت", "mobinnet"), role = BrandRole.TELECOM),
        Brand("مخابرات", "TCI", "اینترنت ثابت", SLUG_TELECOM, 0xFF1565C0.toInt(),
            listOf("مخابرات", "tci"), role = BrandRole.TELECOM),

        // ---------- Services ----------
        Brand("دیجی‌واش", "DigiWash", "خشکشویی آنلاین", SLUG_SERVICES, 0xFF26C6DA.toInt(),
            listOf("دیجی واش", "irdgw", "digiwash")),
        Brand("اکتیو کلینرز", "ActiveCleaners", "خشکشویی آنلاین", SLUG_SERVICES, 0xFF0097A7.toInt(),
            listOf("اکتیوکلینرز", "اکتیو کلینرز", "activecleaners")),
        Brand("اسنپ‌دکتر", "SnappDoctor", "سلامت", SLUG_SERVICES, 0xFF00ACC1.toInt(),
            listOf("اسنپ دکتر", "snappdoctor", "snapp.doctor"), emptyList(), null, "https://snapp.doctor",
            family = SNAPP, cues = listOf("پزشک", "دکتر", "ویزیت", "مشاوره پزشکی", "درمان", "doctor"),
            codeHints = listOf("doc")),
        Brand("دکترتو", "Doctoreto", "سلامت", SLUG_SERVICES, 0xFF3949AB.toInt(),
            listOf("دکترتو", "doctoreto")),
        Brand("اسنپ‌کارفیکس", "SnappCarFix", "خدمات خودرو", SLUG_SERVICES, 0xFF43A047.toInt(),
            listOf("اسنپ کارفیکس", "اسنپ کار فیکس", "کارفیکس", "snappcarfix", "carfix"),
            emptyList(), null, "https://snappcarfix.com",
            family = SNAPP, cues = listOf(
                "خودرو", "تعویض روغن", "روغن موتور", "لوازم یدکی", "کارواش", "سرویس خودرو",
                "باتری ماشین", "ماشین", "carfix"
            ), codeHints = listOf("carfix", "car")),
        Brand("کارنامه", "Karnameh", "خدمات خودرو", SLUG_SERVICES, 0xFF00796B.toInt(),
            listOf("karnameh"), weakKeywords = listOf("کارنامه")),
        Brand("همیار", "Hamyar", "خدمات منزل", SLUG_SERVICES, 0xFF7CB342.toInt(),
            listOf("همیارسرویس", "همیار سرویس")),
        Brand("آچاره", "Achareh", "خدمات منزل", SLUG_SERVICES, 0xFF3949AB.toInt(),
            listOf("آچاره", "achareh")),
        Brand("مکتب‌خونه", "Maktabkhooneh", "آموزش آنلاین", SLUG_SERVICES, 0xFF00897B.toInt(),
            listOf("مکتب خونه", "maktabkhooneh")),
        Brand("فرادرس", "Faradars", "آموزش آنلاین", SLUG_SERVICES, 0xFF1565C0.toInt(),
            listOf("فرادرس", "faradars"))
    )

    private fun bank(fa: String, en: String, color: Int, keywords: List<String>, senderIds: List<String>) =
        Brand(fa, en, "بانک", SLUG_FINTECH, color, keywords, senderIds, role = BrandRole.BANK)

    // ---------------------------------------------------------------- indexes

    private class Entry(val keyword: String, val brand: Brand, val weak: Boolean)

    /** Lower-cased, normalized spelling, so "اسنپ‌فود" and "اسنپ فود" are one keyword. */
    private fun canonical(keyword: String): String = PromoValueParser.normalize(keyword).toLowerCase().trim()

    /** Every spelling of a keyword: as written, and run together ("اسنپ فود" → "اسنپفود"). */
    private fun spellings(keyword: String): List<String> {
        val base = canonical(keyword)
        val joined = base.replace(" ", "")
        return if (joined != base && joined.length >= 3) listOf(base, joined) else listOf(base)
    }

    private fun domainOf(website: String?): String? = website
        ?.toLowerCase()
        ?.removePrefix("https://")
        ?.removePrefix("http://")
        ?.removePrefix("www.")
        ?.substringBefore('/')
        ?.takeIf { it.contains('.') }

    /** Every keyword, longest first, so the most specific name is always tried before a prefix. */
    private val BODY_INDEX: List<Entry> = BRANDS
        .flatMap { brand ->
            brand.keywords.flatMap { k -> spellings(k).map { Entry(it, brand, false) } } +
                brand.weakKeywords.flatMap { k -> spellings(k).map { Entry(it, brand, true) } } +
                listOfNotNull(domainOf(brand.website)).map { Entry(it, brand, false) }
        }
        .filter { it.keyword.isNotEmpty() }
        .distinctBy { it.keyword + "|" + it.brand.en }
        .sortedByDescending { it.keyword.length }

    /** Named sender ids plus keywords, for the sweep over the sender field. */
    private val SENDER_INDEX: List<Pair<String, Brand>> = BRANDS
        .flatMap { brand ->
            (brand.senderIds + brand.keywords + brand.weakKeywords)
                .filterNot { isNumber(it) }
                .flatMap { k -> spellings(k) }
                .map { it to brand }
        }
        .distinctBy { it.first + "|" + it.second.en }
        .sortedByDescending { it.first.length }

    /** Numeric sender ids, without the country prefix, compared as whole numbers. */
    private val NUMBER_INDEX: List<Pair<String, Brand>> = BRANDS
        .flatMap { brand -> brand.senderIds.filter { isNumber(it) }.map { localNumber(it) to brand } }

    private val CUE_INDEX: Map<String, List<Pair<String, Brand>>> = BRANDS
        .filter { it.family != null }
        .groupBy { it.family!! }
        .mapValues { (_, members) ->
            members.flatMap { m -> m.cues.map { canonical(it) to m } }.sortedByDescending { it.first.length }
        }

    // ---------------------------------------------------------------- matching

    /** Words after which an everyday word is clearly a brand name: "در دیوار", "اپلیکیشن طاقچه". */
    private val CONTEXT_BEFORE = setOf(
        "در", "از", "اپ", "اپلیکیشن", "برنامه", "سایت", "وبسایت", "فروشگاه", "پلتفرم", "به", "روی"
    )

    /** Persian endings that still leave a brand name whole: "دیجی‌کالای", "اسنپ‌فودی‌ها". */
    private val SUFFIXES = listOf("های", "ها", "ی")

    /**
     * Whether [text] holds [keyword] at [start] as a word, not as part of a longer one: "ترب"
     * in "تربیت" or "تور" in "موتور" is not a match.
     */
    private fun isWholeWord(text: String, start: Int, end: Int): Boolean {
        if (start > 0 && text[start - 1].isLetter()) return false
        if (end >= text.length || !text[end].isLetter()) return true
        val latin = text[end - 1] in 'a'..'z'
        if (latin) return false
        return SUFFIXES.any { suffix ->
            text.startsWith(suffix, end) && (end + suffix.length >= text.length || !text[end + suffix.length].isLetter())
        }
    }

    private fun hasContext(text: String, start: Int, brand: Brand, senderBrand: Brand?): Boolean {
        if (senderBrand == brand) return true
        val before = text.substring(0, start).trimEnd()
        val lastWord = before.split(' ', '\n').lastOrNull()?.trim(':', '،', '(', '«', '"') ?: return false
        return lastWord in CONTEXT_BEFORE
    }

    /** Latin names and sender ids, lower-cased: a token equal to one is a brand, not a code. */
    private val BRAND_WORDS: Set<String> = BRANDS
        .flatMap { brand -> brand.keywords + brand.senderIds + brand.weakKeywords + listOf(brand.en) }
        .map { canonical(it).replace(" ", "") }
        .filter { word -> word.isNotEmpty() && word.all { it in 'a'..'z' || it in '0'..'9' || it == '.' } }
        .toSet()

    /** Whether [token] is just a brand's name ("SNAPPFOOD", "Digikala") rather than a code. */
    fun isBrandWord(token: String): Boolean = BRAND_WORDS.contains(token.toLowerCase())

    /**
     * Every brand named in [text], in reading order.
     *
     * Longest match wins: characters claimed by "تپسی فود" can no longer count for "تپسی".
     * [excluded] spans — the coupon codes themselves — never produce a mention, so a code like
     * "TAPSI20" in a Tapsi Food message does not vote for the taxi app.
     *
     * @param text normalized, lower-cased message body
     */
    fun findMentions(text: String, excluded: List<IntRange> = emptyList(), senderBrand: Brand? = null): List<BrandMention> {
        if (text.isEmpty()) return emptyList()
        val matchText = text.replace('_', ' ').replace('#', ' ')
        val consumed = BooleanArray(matchText.length)
        for (span in excluded) {
            for (i in maxOf(0, span.first)..minOf(matchText.length - 1, span.last)) consumed[i] = true
        }

        val mentions = ArrayList<BrandMention>()
        for (entry in BODY_INDEX) {
            val keyword = entry.keyword
            if (keyword.length > matchText.length) continue
            var idx = matchText.indexOf(keyword)
            while (idx >= 0) {
                val end = idx + keyword.length
                var free = true
                for (i in idx until end) {
                    if (consumed[i]) {
                        free = false
                        break
                    }
                }
                if (free && isWholeWord(matchText, idx, end) &&
                    (!entry.weak || hasContext(matchText, idx, entry.brand, senderBrand))
                ) {
                    for (i in idx until end) consumed[i] = true
                    mentions.add(BrandMention(entry.brand, idx, end, keyword))
                }
                idx = matchText.indexOf(keyword, idx + 1)
            }
        }
        return mentions.sortedBy { it.start }
    }

    private val SENDER_SEPARATORS = Regex("[\\s.\\-_]+")

    /**
     * The brand behind a sender id such as "SnappFood", "B.QMEHRIRAN" or "+983000445".
     *
     * @param sender normalized, lower-cased sender
     */
    fun matchSender(sender: String): Brand? {
        val trimmed = sender.trim()
        if (trimmed.isEmpty()) return null
        if (isNumber(trimmed)) return matchNumber(trimmed)

        val compact = trimmed.replace(" ", "").replace("-", "").replace("_", "")
        val words = trimmed.split(SENDER_SEPARATORS).filter { it.isNotEmpty() }
        var best: Brand? = null
        var bestLength = 0
        for ((keyword, brand) in SENDER_INDEX) {
            val key = keyword.replace(" ", "")
            if (key.length <= bestLength) continue
            // Very short ids ("mci", "blu") must be the whole sender or a word of it, not a fragment
            val hit = if (key.length <= 3) compact == key || words.contains(key) else compact.contains(key)
            if (hit) {
                best = brand
                bestLength = key.length
            }
        }
        return best
    }

    /**
     * A numeric sender belongs to a brand only when it is that brand's number. Matching the
     * digits anywhere inside the sender labelled every "+98 1000…" bulk line as MCI (98100)
     * and Bank Melli's 700717 line as Irancell (98700). A long number may carry a suffix.
     */
    private fun matchNumber(sender: String): Brand? {
        val number = localNumber(sender)
        var best: Brand? = null
        var bestLength = 0
        for ((id, brand) in NUMBER_INDEX) {
            if ((number == id || (id.length >= 7 && number.startsWith(id))) && id.length > bestLength) {
                best = brand
                bestLength = id.length
            }
        }
        return best
    }

    /**
     * Which service of [family] the words in [text] point to, and how strongly.
     *
     * "اسنپ" alone could be the taxi, the food app or the hotel site; "غذا" or "هتل" in the
     * same message settles it. A fragment of the [code] ("BOX" in "TPSBOXH24") counts as one
     * more hint. Returns null when nothing points anywhere or two services tie.
     */
    fun bestFamilyMember(family: String, text: String, code: String = ""): Pair<Brand, Int>? {
        val index = CUE_INDEX[family] ?: return null
        val consumed = BooleanArray(text.length)
        val scores = LinkedHashMap<Brand, Int>()
        for ((cue, brand) in index) {
            if (cue.isEmpty() || cue.length > text.length) continue
            var idx = text.indexOf(cue)
            while (idx >= 0) {
                val end = idx + cue.length
                var free = true
                for (i in idx until end) {
                    if (consumed[i]) {
                        free = false
                        break
                    }
                }
                if (free && isWholeWord(text, idx, end)) {
                    for (i in idx until end) consumed[i] = true
                    scores[brand] = (scores[brand] ?: 0) + 1
                }
                idx = text.indexOf(cue, idx + 1)
            }
        }
        val lowerCode = code.toLowerCase()
        if (lowerCode.isNotEmpty()) {
            for (brand in BRANDS) {
                if (brand.family == family && brand.codeHints.any { lowerCode.contains(it) }) {
                    scores[brand] = (scores[brand] ?: 0) + 1
                }
            }
        }
        if (scores.isEmpty()) return null
        val top = scores.values.max() ?: return null
        val leaders = scores.filterValues { it == top }.keys
        if (leaders.size == 1) return leaders.first() to top
        // A tie is settled only in favour of the family's own name
        return leaders.firstOrNull { it.isFamilyRoot }?.let { it to top }
    }

    fun sameFamily(a: Brand?, b: Brand?): Boolean {
        if (a == null || b == null) return false
        if (a == b) return true
        return a.family != null && a.family == b.family
    }

    /**
     * Picks the brand a promotional message is about, or null when none is recognisable.
     *
     * @param normalizedSender sender, digits/characters already normalized and lower-cased
     * @param normalizedBody SMS body, digits/characters already normalized and lower-cased
     */
    fun match(normalizedSender: String, normalizedBody: String): Brand? =
        BrandResolver.resolve(normalizedSender, normalizedBody).merchant

    /**
     * Looks a brand up by a name someone wrote: the AI's "m" field, or a persisted promo.
     * Accepts Persian or English names and any registered spelling.
     */
    fun byName(name: String): Brand? {
        val wanted = canonical(name)
        if (wanted.isEmpty()) return null
        val squashed = wanted.replace(" ", "")
        BRANDS.firstOrNull { brand ->
            canonical(brand.fa).replace(" ", "") == squashed ||
                brand.en.toLowerCase().replace(" ", "") == squashed ||
                (brand.keywords + brand.weakKeywords).any { canonical(it).replace(" ", "") == squashed }
        }?.let { return it }
        // "اپلیکیشن اسنپ‌فود" or "SnappFood app": the longest brand inside the name
        return findMentions(wanted, senderBrand = null).maxBy { it.end - it.start }?.brand
    }

    /** Looks a brand up by its exact Persian name, used when rehydrating persisted promos. */
    fun byPersianName(fa: String): Brand? = BRANDS.firstOrNull { it.fa == fa }

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
