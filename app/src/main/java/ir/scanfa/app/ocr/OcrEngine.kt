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
        try {
            instance.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO)
            instance.setVariable("preserve_interword_spaces", "1")
            instance.setVariable("user_defined_dpi", "300")
            instance.setVariable("textord_heavy_nr", "1")
        } catch (t: Throwable) {
            // تنظیمات اختیاری
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
            var raw = instance.getUTF8Text() ?: ""
            if (raw.trim().length < 24) {
                try {
                    instance.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK)
                    instance.setImage(prepared)
                    val second = instance.getUTF8Text() ?: ""
                    if (second.trim().length > raw.trim().length) raw = second
                    instance.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO)
                } catch (t: Throwable) {
                    // تلاش دوم اختیاری است
                }
            }
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
        if (longEdge > 1800) {
            val k = 1800f / longEdge
            val scaled = Img.scaleTo(bmp, (bmp.width * k).toInt(), (bmp.height * k).toInt())
            if (scaled !== bmp) bmp = scaled
        } else if (longEdge < 1400) {
            val k = 1400f / longEdge
            val scaled = Img.scaleTo(bmp, (bmp.width * k).toInt(), (bmp.height * k).toInt())
            if (scaled !== bmp) bmp = scaled
        }

        val w = bmp.width
        val h = bmp.height
        if (w <= 0 || h <= 0) return bmp
        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)

        val gray = IntArray(w * h)
        var minV = 255
        var maxV = 0
        for (i in pixels.indices) {
            val c = pixels[i]
            val g = ((c shr 16 and 0xFF) * 30 + (c shr 8 and 0xFF) * 59 + (c and 0xFF) * 11) / 100
            gray[i] = g
            if (g < minV) minV = g
            if (g > maxV) maxV = g
        }
        val range = (maxV - minV).coerceAtLeast(1)

        // کشش کنتراست و آستانهٔ خودکار (Otsu)
        var sum = 0L
        val histogram = IntArray(256)
        for (g in gray) {
            val stretched = ((g - minV) * 255 / range).coerceIn(0, 255)
            histogram[stretched]++
            sum += stretched
        }
        val total = gray.size.toLong()
        var sumB = 0L
        var wB = 0L
        var best = 0.0
        var threshold = 128
        for (t in 0..255) {
            wB += histogram[t]
            if (wB == 0L) continue
            val wF = total - wB
            if (wF == 0L) break
            sumB += t.toLong() * histogram[t]
            val mB = sumB.toDouble() / wB
            val mF = (sum - sumB).toDouble() / wF
            val between = wB.toDouble() * wF * (mB - mF) * (mB - mF)
            if (between > best) {
                best = between
                threshold = t
            }
        }

        val out = IntArray(w * h)
        for (i in pixels.indices) {
            val stretched = ((gray[i] - minV) * 255 / range).coerceIn(0, 255)
            val v = if (stretched > threshold) 255 else 0
            out[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(out, 0, w, 0, 0, w, h)
        if (result !== bmp) bmp.recycle()
        return result
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
