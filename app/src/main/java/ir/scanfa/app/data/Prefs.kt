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

    /** لرزش کوتاه هنگام لمس دکمه‌ها. */
    var hapticsEnabled: Boolean
        get() = sp.getBoolean("haptics_enabled", true)
        set(value) = sp.edit().putBoolean("haptics_enabled", value).apply()

    /** پوستهٔ انتخاب‌شده: emerald | ocean | amber | violet | graphite */
    var skin: String
        get() = sp.getString("skin", "emerald") ?: "emerald"
        set(value) = sp.edit().putString("skin", value).apply()

    /** قفل برنامه با رمز ۶ رقمی */
    var lockEnabled: Boolean
        get() = sp.getBoolean("lock_enabled", false)
        set(value) = sp.edit().putBoolean("lock_enabled", value).apply()

    var pinHash: String
        get() = sp.getString("pin_hash", "") ?: ""
        set(value) = sp.edit().putString("pin_hash", value).apply()

    /** هش کد مادر (در اولین ورود به تنظیمات تعیین می‌شود) */
    var masterHash: String
        get() = sp.getString("master_hash", "") ?: ""
        set(value) = sp.edit().putString("master_hash", value).apply()

    /** پیام خوش‌آمد اولیه یک‌بار نمایش داده شود */
    var onboarded: Boolean
        get() = sp.getBoolean("onboarded", false)
        set(value) = sp.edit().putBoolean("onboarded", value).apply()

    var biometricEnabled: Boolean
        get() = sp.getBoolean("biometric_enabled", true)
        set(value) = sp.edit().putBoolean("biometric_enabled", value).apply()

    /** سطح بزرگ‌نمایی ۱ تا ۱۰ (پله‌ای، نه پیوسته) */
    var uiLevel: Int
        get() = sp.getInt("ui_level", 3).coerceIn(1, 5)
        set(value) = sp.edit().putInt("ui_level", value.coerceIn(1, 5)).apply()

    val uiScale: Float
        get() = 0.85f + (uiLevel - 1) * 0.0825f
}
