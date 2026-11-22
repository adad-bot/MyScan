package ir.scanfa.app.data

import android.content.Context

/** تنظیمات کاربر: تم، کیفیت، زبان OCR و سقف حجم. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("scanner_prefs", Context.MODE_PRIVATE)

    /** system | light | dark */
    var themeMode: String
        get() = sp.getString("theme_mode", "system") ?: "system"
        set(value) = sp.edit().putString("theme_mode", value).apply()

    var ocrLanguage: String
        get() = sp.getString("ocr_lang", "fas") ?: "fas"
        set(value) = sp.edit().putString("ocr_lang", value).apply()

    var jpegQuality: Int
        get() = sp.getInt("jpeg_quality", 82)
        set(value) = sp.edit().putInt("jpeg_quality", value).apply()

    var maxPageSize: Int
        get() = sp.getInt("max_page_size", 2200)
        set(value) = sp.edit().putInt("max_page_size", value).apply()

    var keepOriginal: Boolean
        get() = sp.getBoolean("keep_original", true)
        set(value) = sp.edit().putBoolean("keep_original", value).apply()

    var defaultCapKb: Int
        get() = sp.getInt("default_cap_kb", 1024)
        set(value) = sp.edit().putInt("default_cap_kb", value).apply()

    var defaultPaper: String
        get() = sp.getString("default_paper", PaperSize.A4.id) ?: PaperSize.A4.id
        set(value) = sp.edit().putString("default_paper", value).apply()

    var autoDetect: Boolean
        get() = sp.getBoolean("auto_detect", true)
        set(value) = sp.edit().putBoolean("auto_detect", value).apply()

    /** ثبت خودکار وقتی قاب سند پایدار تشخیص داده شد. */
    var autoCapture: Boolean
        get() = sp.getBoolean("auto_capture", true)
        set(value) = sp.edit().putBoolean("auto_capture", value).apply()
}
