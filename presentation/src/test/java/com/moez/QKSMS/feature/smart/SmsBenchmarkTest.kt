package com.moez.QKSMS.feature.smart

import com.moez.QKSMS.feature.smart.model.SmsCategory
import org.junit.Assume
import org.junit.Test
import java.io.File
import java.util.Random

/**
 * Synthetic labelled inbox benchmark. Runs only with SMS_BENCHMARK_OUT set; uses only APIs that
 * exist before and after the engine rewrite, so the same file measures both.
 */
class SmsBenchmarkTest {

    private class Sample(val sender: String, val body: String, val kind: String, val otp: String? = null,
                         val amount: Long? = null, val deposit: Boolean? = null)

    private val fa = "۰۱۲۳۴۵۶۷۸۹"
    private val ar = "٠١٢٣٤٥٦٧٨٩"

    private fun script(r: Random, s: String): String = when (r.nextInt(3)) {
        0 -> s.map { if (it in '0'..'9') fa[it - '0'] else it }.joinToString("")
        1 -> s.map { if (it in '0'..'9') ar[it - '0'] else it }.joinToString("")
        else -> s
    }

    private fun digits(r: Random, n: Int, leadingZero: Boolean = false): String {
        val sb = StringBuilder()
        for (i in 0 until n) sb.append(if (i == 0 && !leadingZero) ('1' + r.nextInt(9)) else ('0' + r.nextInt(10)))
        return sb.toString()
    }

    private fun group(v: Long): String = "%,d".format(v)

    private fun generate(n: Int, seed: Long): List<Sample> {
        val r = Random(seed)
        val banks = listOf("Bank Mellat" to "بانک ملت", "BMI" to "بانک ملی ایران", "Bank Saderat" to "بانک صادرات",
            "Bank Tejarat" to "بانک تجارت", "B.QMEHRIRAN" to "بانک مهر ایران")
        val out = ArrayList<Sample>(n)
        while (out.size < n) {
            when (r.nextInt(16)) {
                0 -> { val c = digits(r, 4 + r.nextInt(5)); out += Sample("+98100${r.nextInt(999)}", script(r, "کد تایید شما: $c"), "otp", c) }
                1 -> { val c = digits(r, 6); out += Sample("BANK", script(r, "کارت ${digits(r, 8)}؛ کد تایید شما: $c"), "otp", c) }
                2 -> { val c = "0" + digits(r, 5, true); out += Sample("DIGIKALA", script(r, "کد ورود: $c\nاین کد را در اختیار دیگران قرار ندهید"), "otp", c) }
                3 -> { val c = digits(r, 6); out += Sample("GOOGLE", "G-$c is your Google verification code.", "otp", c) }
                4 -> { val c = digits(r, 8); val b = banks[r.nextInt(banks.size)]
                    out += Sample(b.first, script(r, "${b.second}\nرمز پویا خرید\nمبلغ: ${group(100_000L * (1 + r.nextInt(90)))} ریال\nکارت: 6104****${digits(r, 4)}\nرمز: $c\nمهلت: 2 دقیقه"), "otp", c) }
                5 -> out += Sample("SNAPP", script(r, "کد ورود: ${digits(r, 10)}"), "unknown")
                6 -> { val a = 10_000L * (1 + r.nextInt(5000)); val bal = a + 1_000_000L * r.nextInt(50); val b = banks[r.nextInt(banks.size)]
                    out += Sample(b.first, script(r, "${b.second}\nمانده:+${group(bal)}؛ برداشت:${group(a)}"), "banking", amount = a, deposit = false) }
                7 -> { val a = 10_000L * (1 + r.nextInt(5000)); val b = banks[r.nextInt(banks.size)]
                    out += Sample(b.first, script(r, "${b.second}\nانتقال:+${group(a)}\nحساب:${digits(r, 13)}\nمانده:${group(a * 3)}\n0626-10:31"), "banking", amount = a, deposit = true) }
                8 -> { val a = 10_000L * (1 + r.nextInt(5000)); val b = banks[r.nextInt(banks.size)]
                    out += Sample(b.first, script(r, "${b.second}\nخرید از فروشگاه رفاه\nمبلغ: ${group(a)} ریال\nمانده: ${group(a * 2)} ریال\nهدیه: ۱۰٪ تخفیف در خرید بعدی"), "banking", amount = a, deposit = false) }
                9 -> { val a = 10_000L * (1 + r.nextInt(5000)); out += Sample("B.QMEHRIRAN", script(r, "${digits(r, 12)}\n${group(a)}-\n05/06/26_12:40\nمانده:${group(a + 5_000_000)}"), "banking", amount = a, deposit = false) }
                10 -> out += Sample("+985000${r.nextInt(999)}", script(r, "وام ${10 + r.nextInt(90)} میلیون تومانی بدون ضامن از بانک ملت با اقساط ۳۶ ماهه\nلغو11"), "spam")
                11 -> { val code = "FOOD" + digits(r, 2); out += Sample("SNAPPFOOD", script(r, "اسنپ‌فود: ${10 + r.nextInt(50)} هزار تومان تخفیف با کد تخفیف $code تا پایان هفته"), "promo") }
                12 -> out += Sample("+989998${digits(r, 6)}", script(r, "جشنواره پاییزه با ارسال رایگان همه سفارش‌ها\nلغو11"), "spam")
                13 -> out += Sample("0912${digits(r, 7)}", script(r, "سلام، فردا ساعت ${1 + r.nextInt(11)} می‌بینمت"), "personal")
                14 -> out += Sample("POST", script(r, "مرسوله ${digits(r, 6)} شما تحویل شد"), "unknown")
                else -> out += Sample("TAPSI", script(r, "سفر شما با موفقیت به پایان رسید. امتیاز خود را ثبت کنید"), "unknown")
            }
        }
        return out
    }

    private fun kindOf(c: SmsCategory): String = when (c) {
        is SmsCategory.Otp -> "otp"
        is SmsCategory.Banking -> "banking"
        is SmsCategory.Promo -> "promo"
        is SmsCategory.Personal -> "personal"
        is SmsCategory.Spam -> "spam"
        else -> "unknown"
    }

    /** The new engine's "would auto-copy" flag, by reflection; null on the old engine. */
    private fun autoCopyEligible(s: Sample, date: Long): Boolean? = try {
        val cls = Class.forName("com.moez.QKSMS.feature.smart.analysis.SmsAnalyzer")
        val inst = cls.getField("INSTANCE").get(null)
        val m = cls.methods.first { it.name == "analyze" && it.parameterTypes.size == 5 }
        val analysis = m.invoke(inst, s.sender, s.body, date, 0L, null)
        val otp = analysis.javaClass.getMethod("getOtp").invoke(analysis)
        otp.javaClass.getMethod("getAutoCopyEligible").invoke(otp) as Boolean
    } catch (e: ClassNotFoundException) {
        null
    }

    @Test
    fun benchmark() {
        val outPath = System.getenv("SMS_BENCHMARK_OUT")
        Assume.assumeTrue(outPath != null)
        val report = StringBuilder()
        val date = 1_800_000_000_000L
        // Warm-up so JIT and class loading are not counted
        generate(300, 1L).forEach { SmartSmsClassifier.classify(it.sender, it.body, date) }

        for (n in listOf(1_000, 10_000)) {
            val samples = generate(n, 42L)
            val rt = Runtime.getRuntime()
            System.gc(); Thread.sleep(200)
            val before = rt.totalMemory() - rt.freeMemory()
            val results = ArrayList<SmsCategory>(n)
            val t0 = System.nanoTime()
            for (s in samples) results.add(SmartSmsClassifier.classify(s.sender, s.body, date))
            val elapsedMs = (System.nanoTime() - t0) / 1e6
            val after = rt.totalMemory() - rt.freeMemory()
            System.gc(); Thread.sleep(200)
            val retained = rt.totalMemory() - rt.freeMemory() - before

            var correct = 0
            val perKind = HashMap<String, IntArray>()
            var otpTotal = 0; var otpRight = 0
            var amtTotal = 0; var amtRight = 0; var dirRight = 0
            var wrongCopies = 0; var rightCopies = 0
            var unknownAsSpam = 0
            for ((i, s) in samples.withIndex()) {
                val c = results[i]
                val k = kindOf(c)
                val stat = perKind.getOrPut(s.kind) { IntArray(2) }
                stat[1]++
                if (k == s.kind) { correct++; stat[0]++ }
                if (s.kind == "unknown" && k == "spam") unknownAsSpam++
                if (s.otp != null) {
                    otpTotal++
                    if ((c as? SmsCategory.Otp)?.otp?.code == s.otp) otpRight++
                }
                if (s.amount != null) {
                    amtTotal++
                    val b = c as? SmsCategory.Banking
                    if (b?.amount?.filter { it.isDigit() }?.toLongOrNull() == s.amount) amtRight++
                    if (b?.isDeposit == s.deposit) dirRight++
                }
                // Copies the app would make on arrival with auto-copy on
                val code = (c as? SmsCategory.Otp)?.otp?.code
                if (code != null) {
                    val eligible = autoCopyEligible(s, date) ?: true
                    if (eligible) { if (code == s.otp) rightCopies++ else wrongCopies++ }
                }
            }
            report.append("== inbox of $n messages ==\n")
            report.append("classification accuracy: %.1f%% (%d/%d)\n".format(100.0 * correct / n, correct, n))
            for ((k, v) in perKind.toSortedMap()) report.append("  %-9s %.1f%% (%d/%d)\n".format(k, 100.0 * v[0] / v[1], v[0], v[1]))
            report.append("unknown filed as spam: $unknownAsSpam\n")
            report.append("OTP code exact: %.1f%% (%d/%d)\n".format(100.0 * otpRight / otpTotal, otpRight, otpTotal))
            report.append("banking amount exact: %.1f%% (%d/%d); direction: %.1f%%\n".format(100.0 * amtRight / amtTotal, amtRight, amtTotal, 100.0 * dirRight / amtTotal))
            report.append("auto-copies: correct=$rightCopies wrong=$wrongCopies\n")
            report.append("latency: total %.0f ms, %.3f ms/message\n".format(elapsedMs, elapsedMs / n))
            report.append("heap: +%.1f MB during run, %.1f MB retained after GC (results list)\n\n".format((after - before) / 1e6, retained / 1e6))
        }
        File(outPath!!).writeText(report.toString())
    }
}
