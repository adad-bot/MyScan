package ir.scanfa.app.util

import java.util.Calendar
import java.util.Locale
import kotlin.random.Random

/** نام‌گذاری یکتای اسناد و فایل‌ها: MyScan-تاریخ-ساعت-شماره */
object Naming {

    fun subject(prefix: String = "MyScan"): String {
        val c = Calendar.getInstance()
        val stamp = String.format(
            Locale.US,
            "%04d%02d%02d-%02d%02d%02d",
            c.get(Calendar.YEAR),
            c.get(Calendar.MONTH) + 1,
            c.get(Calendar.DAY_OF_MONTH),
            c.get(Calendar.HOUR_OF_DAY),
            c.get(Calendar.MINUTE),
            c.get(Calendar.SECOND)
        )
        val serial = Random.nextInt(100, 1000)
        return "$prefix-$stamp-$serial"
    }
}
