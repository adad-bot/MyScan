package ir.scanfa.app.util

import java.util.Calendar

/** تاریخ شمسی برای نمایش و خروجی. */
object DateFa {

    private val monthNames = arrayOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
    )

    fun jalali(timeMillis: Long): IntArray {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timeMillis
        return gregorianToJalali(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    fun short(timeMillis: Long): String {
        val (jy, jm, jd) = jalali(timeMillis).let { Triple(it[0], it[1], it[2]) }
        val cal = Calendar.getInstance()
        cal.timeInMillis = timeMillis
        val hh = String.format("%02d", cal.get(Calendar.HOUR_OF_DAY))
        val mm = String.format("%02d", cal.get(Calendar.MINUTE))
        return "${fa(jy)}/${fa(jm, 2)}/${fa(jd, 2)}  ${faText(hh)}:${faText(mm)}"
    }

    fun long(timeMillis: Long): String {
        val j = jalali(timeMillis)
        return "${fa(j[2])} ${monthNames[(j[1] - 1).coerceIn(0, 11)]} ${fa(j[0])}"
    }

    fun fa(value: Int, pad: Int = 0): String {
        var s = if (pad > 0) String.format("%0${pad}d", value) else value.toString()
        return faText(s)
    }

    fun faText(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            sb.append(if (ch in '0'..'9') ('۰' + (ch - '0')) else ch)
        }
        return sb.toString()
    }

    private fun gregorianToJalali(gy: Int, gm: Int, gd: Int): IntArray {
        val gDaysInMonth = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        val gy2 = if (gm > 2) gy + 1 else gy
        var days = 355666L + (365L * gy) + ((gy2 + 3) / 4) - ((gy2 + 99) / 100) + ((gy2 + 399) / 400) + gd + gDaysInMonth[gm - 1].toLong()
        var jy = -1595 + (33 * (days / 12053L))
        days %= 12053L
        jy += 4 * (days / 1461L)
        days %= 1461L
        if (days > 365) {
            jy += ((days - 1) / 365).toInt()
            days = (days - 1) % 365
        }
        val jm: Int
        val jd: Int
        if (days < 186) {
            jm = 1 + (days / 31).toInt()
            jd = 1 + (days % 31).toInt()
        } else {
            jm = 7 + ((days - 186) / 30).toInt()
            jd = 1 + ((days - 186) % 30).toInt()
        }
        return intArrayOf(jy.toInt(), jm, jd)
    }
}
