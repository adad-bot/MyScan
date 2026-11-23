package ir.scanfa.app.util

import java.util.Calendar

/**
 * تبدیل تاریخ‌ها: شمسی (جلالی)، میلادی و قمری.
 * الگوریتم شمسی پورت‌شده از کتابخانهٔ MyC (jalCal/g2d/d2g/j2d/d2j) است.
 */
object Calendars {

    data class JDate(val jy: Int, val jm: Int, val jd: Int)
    data class HDate(val hy: Int, val hm: Int, val hd: Int)

    private val BREAKS = intArrayOf(
        -61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181,
        1210, 1635, 2060, 2097, 2192, 2262, 2324, 2394, 2456, 3178
    )

    val jalaliMonths = arrayOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
    )

    val hijriMonths = arrayOf(
        "محرم", "صفر", "ربیع‌الاول", "ربیع‌الثانی", "جمادی‌الاول", "جمادی‌الثانی",
        "رجب", "شعبان", "رمضان", "شوال", "ذی‌القعده", "ذی‌الحجه"
    )

    val weekdays = arrayOf("شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه")

    val gregorianMonths = arrayOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    )

    // مطابق jalaali-js: تقسیم و باقیمانده با کوتاه‌سازی به سمت صفر
    private fun div(a: Int, b: Int): Int = a / b
    private fun mod(a: Int, b: Int): Int = a % b

    /** leap, gy, march */
    private fun jalCal(jy: Int): Triple<Int, Int, Int> {
        val gy = jy + 621
        var leapJ = -14
        var jp = BREAKS[0]
        var jump = 0
        var i = 1
        while (i < BREAKS.size) {
            val jm = BREAKS[i]
            jump = jm - jp
            if (jy < jm) break
            leapJ += div(jump, 33) * 8 + div(mod(jump, 33), 4)
            jp = jm
            i++
        }
        var n = jy - jp
        leapJ += div(n, 33) * 8 + div(mod(n, 33) + 3, 4)
        if (mod(jump, 33) == 4 && jump - n == 4) leapJ += 1
        val leapG = div(gy, 4) - div((div(gy, 100) + 1) * 3, 4) - 150
        val march = 20 + leapJ - leapG
        if (jump - n < 6) n = n - jump + div(jump + 4, 33) * 33
        var leap = mod(mod(n + 1, 33) - 1, 4)
        if (leap == -1) leap = 4
        return Triple(leap, gy, march)
    }

    fun g2d(gy: Int, gm: Int, gd: Int): Long {
        var d = div((gy + div(gm - 8, 6) + 100100) * 1461, 4).toLong() +
            div(153 * mod(gm + 9, 12) + 2, 5).toLong() + gd.toLong() - 34840408L
        d = d - div(div(gy + 100100 + div(gm - 8, 6), 100) * 3, 4).toLong() + 752L
        return d
    }

    fun d2g(jdn: Long): Triple<Int, Int, Int> {
        var j = 4L * jdn + 139361631L
        j = j + ((4L * jdn + 183187720L) / 146097L) * 3L / 4L * 4L - 3908L
        val i = (j % 1461L) / 4L * 5L + 308L
        val gd = (i % 153L) / 5L + 1L
        val gm = (i / 153L) % 12L + 1L
        val gy = j / 1461L - 100100L + (8L - gm) / 6L
        return Triple(gy.toInt(), gm.toInt(), gd.toInt())
    }

    fun j2d(jy: Int, jm: Int, jd: Int): Long {
        val r = jalCal(jy)
        return g2d(r.second, 3, r.third) + (jm - 1) * 31L - div(jm, 7) * (jm - 7).toLong() + jd - 1L
    }

    fun d2j(jdn: Long): JDate {
        val gy = d2g(jdn).first
        var jy = gy - 621
        val r = jalCal(jy)
        val jdn1f = g2d(gy, 3, r.third)
        var k = jdn - jdn1f
        if (k >= 0) {
            if (k <= 185) {
                return JDate(jy, 1 + div(k.toInt(), 31), mod(k.toInt(), 31) + 1)
            }
            k -= 186
        } else {
            jy -= 1
            k += 179
            if (r.first == 1) k += 1
        }
        return JDate(jy, 7 + div(k.toInt(), 30), mod(k.toInt(), 30) + 1)
    }

    fun isLeapJalali(jy: Int): Boolean = jalCal(jy).first == 0

    fun jalaliMonthLength(jy: Int, jm: Int): Int = when {
        jm <= 6 -> 31
        jm <= 11 -> 30
        else -> if (isLeapJalali(jy)) 30 else 29
    }

    fun todayJdn(): Long {
        val c = Calendar.getInstance()
        return g2d(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    fun todayJalali(): JDate = d2j(todayJdn())

    /** ۰=شنبه … ۶=جمعه */
    fun weekdayIndex(jdn: Long): Int {
        val v = Math.floorMod(jdn - 2451545L, 7L).toInt()
        return v
    }

    fun weekdayName(jdn: Long): String = weekdays[weekdayIndex(jdn)]

    fun toHijri(jdn: Long): HDate {
        var l = jdn - 1948440L + 10632L
        val n = (l - 1L) / 10631L
        l = l - 10631L * n + 354L
        val j = ((10985L - l) / 5316L) * ((50L * l) / 17719L) + (l / 5670L) * ((43L * l) / 15238L)
        l = l - ((30L - j) / 15L) * ((17719L * j) / 50L) - (j / 16L) * ((15238L * j) / 43L) + 29L
        val m = (24L * l) / 709L
        val d = l - (709L * m) / 24L
        val y = 30L * n + j - 30L
        return HDate(y.toInt(), m.toInt(), d.toInt())
    }

    fun fa(value: Int, pad: Int = 0): String {
        val text = if (pad > 0) {
            String.format(java.util.Locale.US, "%0" + pad + "d", value)
        } else {
            value.toString()
        }
        return DateFa.faText(text)
    }

    fun jalaliText(jdn: Long): String {
        val j = d2j(jdn)
        return "${fa(j.jd)} ${jalaliMonths[(j.jm - 1).coerceIn(0, 11)]} ${fa(j.jy)}"
    }

    fun jalaliShort(jdn: Long): String {
        val j = d2j(jdn)
        return "${fa(j.jy)}/${fa(j.jm, 2)}/${fa(j.jd, 2)}"
    }

    fun hijriText(jdn: Long): String {
        val h = toHijri(jdn)
        return "${fa(h.hd)} ${hijriMonths[(h.hm - 1).coerceIn(0, 11)]} ${fa(h.hy)}"
    }

    fun gregorianText(jdn: Long): String {
        val g = d2g(jdn)
        val month = gregorianMonths[(g.second - 1).coerceIn(0, 11)]
        return "${g.third} $month ${g.first}"
    }

    fun jdnFromGregorian(year: Int, month: Int, day: Int): Long = g2d(year, month, day)
}
