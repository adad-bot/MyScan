package ir.scanfa.app.util

/** مناسبت‌های شمسی و قمری (برگرفته از فهرست کتابخانهٔ MyC). */
object Occasions {

    private val jalali = mapOf(
        "1-1" to "نوروز", "1-2" to "عید نوروز", "1-3" to "عید نوروز", "1-4" to "عید نوروز",
        "1-12" to "روز جمهوری اسلامی", "1-13" to "سیزده‌به‌در",
        "2-12" to "روز معلم", "6-14" to "روز قلم",
        "11-22" to "پیروزی انقلاب اسلامی", "12-29" to "روز ملی شدن صنعت نفت"
    )

    private val hijri = mapOf(
        "1-9" to "تاسوعای حسینی", "1-10" to "عاشورا", "1-12" to "شهادت امام سجاد (ع)",
        "2-20" to "اربعین حسینی", "2-28" to "رحلت پیامبر (ص)",
        "3-17" to "میلاد پیامبر (ص)",
        "6-3" to "شهادت حضرت زهرا (س)",
        "9-21" to "شهادت امام علی (ع)",
        "10-1" to "عید فطر",
        "12-10" to "عید قربان", "12-18" to "عید غدیر"
    )

    fun forJdn(jdn: Long): List<String> {
        val result = ArrayList<String>()
        val j = Calendars.d2j(jdn)
        jalali["${j.jm}-${j.jd}"]?.let { result.add(it) }
        val h = Calendars.toHijri(jdn)
        hijri["${h.hm}-${h.hd}"]?.let { result.add(it) }
        return result
    }
}
