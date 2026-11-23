package ir.scanfa.app

import ir.scanfa.app.util.Calendars
import org.junit.Assert.assertEquals
import org.junit.Test

/** آزمون تبدیل تاریخ‌ها بر پایهٔ نمونه‌های شناخته‌شده. */
class CalendarTest {

    @Test
    fun gregorianToJalali() {
        val jdn = Calendars.g2d(2026, 11, 23)
        val j = Calendars.d2j(jdn)
        assertEquals(1405, j.jy)
        assertEquals(9, j.jm)
        assertEquals(2, j.jd)
    }

    @Test
    fun jalaliToGregorian() {
        val jdn = Calendars.j2d(1405, 9, 2)
        assertEquals(Calendars.g2d(2026, 11, 23), jdn)
        val g = Calendars.d2g(jdn)
        assertEquals(2026, g.first)
        assertEquals(11, g.second)
        assertEquals(23, g.third)
    }

    @Test
    fun weekdayMatches() {
        // 2026-11-23 دوشنبه است
        assertEquals("دوشنبه", Calendars.weekdayName(Calendars.g2d(2026, 11, 23)))
    }

    @Test
    fun hijriIsSane() {
        val h = Calendars.toHijri(Calendars.g2d(2026, 11, 23))
        assertEquals(1448, h.hy)
        assertEquals(6, h.hm)
        assert(h.hd in 10..14) { "unexpected hijri day: ${h.hd}" }
    }
}
