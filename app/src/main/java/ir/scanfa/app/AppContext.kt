package ir.scanfa.app

import android.content.Context

/** نگه‌دارندهٔ Context برنامه برای ماژول‌های پردازش تصویر. */
object AppContext {
    @Volatile
    var ctx: Context? = null
}
