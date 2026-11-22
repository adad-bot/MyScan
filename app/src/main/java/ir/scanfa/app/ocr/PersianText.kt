package ir.scanfa.app.ocr

/** یکسان‌سازی متن فارسی برای ذخیره، جست‌وجو و خروجی Word. */
object PersianText {

    private val map = mapOf(
        'ي' to 'ی',
        'ك' to 'ک',
        'ئ' to 'ی',
        'أ' to 'ا',
        'إ' to 'ا',
        'ة' to 'ه',
        '\u0640' to '\u0000' // کشیدهٔ حرف (tatweel)
    )

    private val arabicDigits = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')
    private val persianDigits = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')

    fun normalize(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            if (ch == '\u0000') continue
            val mapped = map[ch] ?: ch
            sb.append(mapped)
        }
        var out = sb.toString()
        out = out.replace("\r\n", "\n").replace('\r', '\n')
        // فاصله‌های تکراری افقی
        out = out.replace(Regex("[ \\t]{2,}"), " ")
        // خطوط خالی پشت سر هم
        out = out.replace(Regex("\n{3,}"), "\n\n")
        return out.trim()
    }

    fun toLatinDigits(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            val ai = arabicDigits.indexOf(ch)
            val pi = persianDigits.indexOf(ch)
            when {
                ai >= 0 -> sb.append(('0' + ai))
                pi >= 0 -> sb.append(('0' + pi))
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    /** شکل ساده‌شده برای جست‌وجو: حذف اعراب، یکسان‌سازی ی/ک و ارقام. */
    fun searchKey(input: String): String {
        var s = normalize(input)
        s = toLatinDigits(s)
        s = s.replace(Regex("[\\u064B-\\u0652\\u0670]"), "")
        s = s.replace(Regex("[\\u200c\\u200f\\u200e]"), " ")
        return s.replace(Regex("\\s+"), " ").trim()
    }

    fun lineCount(text: String): Int = text.split('\n').count { it.isNotBlank() }

    fun wordCount(text: String): Int =
        text.split(Regex("\\s+")).count { it.isNotBlank() }
}
