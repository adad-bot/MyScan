package ir.scanfa.app.ocr

import android.content.Context
import android.graphics.Bitmap
import com.googlecode.tesseract.android.TessBaseAPI
import ir.scanfa.app.image.Img
import java.io.File
import kotlin.math.max

/** موتور OCR فارسی روی دستگاه بر پایهٔ Tesseract با فایل آموزش‌دیدهٔ fas.traineddata. */
object OcrEngine {

    private var api: TessBaseAPI? = null
    private var activeLanguage: String? = null
    private var assetsReady = false

    @Synchronized
    fun isReady(language: String): Boolean = api != null && activeLanguage == language

    @Synchronized
    fun prepare(ctx: Context, language: String): Boolean {
        if (api != null && activeLanguage == language) return true
        release()
        copyAssets(ctx)
        val dataDir = File(ctx.filesDir, "tesseract")
        val instance = TessBaseAPI()
        val ok = try {
            instance.init(dataDir.absolutePath, language)
        } catch (t: Throwable) {
            false
        }
        if (!ok) {
            try {
                instance.recycle()
            } catch (t: Throwable) {
                // رها کردن نمونهٔ ناموفق
            }
            return false
        }
        api = instance
        activeLanguage = language
        return true
    }

    /** متن صفحه یا null در صورت شکست. */
    @Synchronized
    fun recognize(ctx: Context, bitmap: Bitmap, language: String): String? {
        if (!prepare(ctx, language)) return null
        val instance = api ?: return null
        val prepared = prepareBitmap(bitmap)
        return try {
            instance.setImage(prepared)
            val raw = instance.getUTF8Text() ?: ""
            val text = PersianText.normalize(raw)
            text.ifBlank { null }
        } catch (t: Throwable) {
            null
        } finally {
            if (prepared !== bitmap) prepared.recycle()
        }
    }

    @Synchronized
    fun stop() {
        try {
            api?.stop()
        } catch (t: Throwable) {
            // نادیده‌گرفتنی
        }
    }

    @Synchronized
    fun release() {
        val instance = api ?: return
        api = null
        activeLanguage = null
        try {
            instance.recycle()
        } catch (t: Throwable) {
            // نادیده‌گرفتنی
        }
    }

    private fun prepareBitmap(src: Bitmap): Bitmap {
        var bmp = src
        if (bmp.config != Bitmap.Config.ARGB_8888) {
            val copy = bmp.copy(Bitmap.Config.ARGB_8888, false)
            if (copy != null) bmp = copy
        }
        val longEdge = max(bmp.width, bmp.height)
        if (longEdge < 1100) {
            val k = 1100f / longEdge
            val scaled = Img.scaleTo(bmp, (bmp.width * k).toInt(), (bmp.height * k).toInt())
            if (scaled !== bmp) bmp = scaled
        }
        return bmp
    }

    private fun copyAssets(ctx: Context) {
        if (assetsReady) return
        val outDir = File(ctx.filesDir, "tesseract/tessdata")
        if (!outDir.exists()) outDir.mkdirs()
        try {
            val names = ctx.assets.list("tessdata") ?: emptyArray()
            for (name in names) {
                val target = File(outDir, name)
                if (target.exists() && target.length() > 0L) continue
                try {
                    ctx.assets.open("tessdata/$name").use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    }
                } catch (t: Throwable) {
                    // فایل بعدی
                }
            }
            assetsReady = true
        } catch (t: Throwable) {
            assetsReady = false
        }
    }

    fun decodeForOcr(file: File): Bitmap? = Img.decode(file, 2000)

    fun availableLanguages(): List<Pair<String, String>> = listOf(
        "fas" to "فارسی",
        "eng" to "انگلیسی",
        "fas+eng" to "فارسی و انگلیسی"
    )
}
