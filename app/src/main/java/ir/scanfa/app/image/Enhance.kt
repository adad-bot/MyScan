package ir.scanfa.app.image

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * بهبود سند بعد از برش: کشش کنتراست، پررنگ‌ترکردن رنگ‌ها و تیزکردن متن،
 * تا خروجی شبیه اسکن واقعی شود.
 */
object Enhance {

    fun improve(src: Bitmap, sharpenStrength: Float = 0.65f): Bitmap {
        val w = src.width
        val h = src.height
        if (w < 8 || h < 8) return src

        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)

        // هیستوگرام روشنایی برای پیدا کردن نقطهٔ سیاه/سفید
        val histogram = IntArray(256)
        var samples = 0
        val step = 2
        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                val c = pixels[y * w + x]
                val lum = (((c shr 16) and 0xFF) * 299 + ((c shr 8) and 0xFF) * 587 + (c and 0xFF) * 114) / 1000
                histogram[lum]++
                samples++
                x += step
            }
            y += step
        }
        val lowTarget = (samples * 0.02f).toInt()
        val highTarget = (samples * 0.98f).toInt()
        var acc = 0
        var low = 0
        var high = 255
        for (i in 0..255) {
            acc += histogram[i]
            if (acc >= lowTarget) {
                low = i
                break
            }
        }
        acc = 0
        for (i in 255 downTo 0) {
            acc += histogram[i]
            if (acc >= samples - highTarget) {
                high = i
                break
            }
        }
        if (high - low < 30) {
            low = max(0, low - 20)
            high = min(255, high + 20)
        }
        val range = (high - low).coerceAtLeast(1)
        val lut = IntArray(256) { i ->
            (((i - low) * 255f / range).coerceIn(0f, 255f)).roundToInt()
        }

        // اعمال کنتراست + اشباع رنگ قوی‌تر
        val out = IntArray(w * h)
        for (i in pixels.indices) {
            val c = pixels[i]
            var r = lut[(c shr 16) and 0xFF]
            var g = lut[(c shr 8) and 0xFF]
            var b = lut[c and 0xFF]
            val gray = (r * 299 + g * 587 + b * 114) / 1000
            val sat = 1.28f
            r = (gray + (r - gray) * sat).roundToInt().coerceIn(0, 255)
            g = (gray + (g - gray) * sat).roundToInt().coerceIn(0, 255)
            b = (gray + (b - gray) * sat).roundToInt().coerceIn(0, 255)
            out[i] = Color.argb(255, r, g, b)
        }

        sharpen(out, w, h, sharpenStrength)
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(out, 0, w, 0, 0, w, h)
        return result
    }

    private fun sharpen(px: IntArray, w: Int, h: Int, strength: Float) {
        val copy = px.copyOf()
        val s = strength
        for (y in 1 until h - 1) {
            val row = y * w
            for (x in 1 until w - 1) {
                val center = copy[row + x]
                val up = copy[row - w + x]
                val down = copy[row + w + x]
                val left = copy[row + x - 1]
                val right = copy[row + x + 1]

                val cr = (center shr 16) and 0xFF
                val cg = (center shr 8) and 0xFF
                val cb = center and 0xFF
                val blurredR = (((up shr 16) and 0xFF) + ((down shr 16) and 0xFF) + ((left shr 16) and 0xFF) + ((right shr 16) and 0xFF)) / 4
                val blurredG = (((up shr 8) and 0xFF) + ((down shr 8) and 0xFF) + ((left shr 8) and 0xFF) + ((right shr 8) and 0xFF)) / 4
                val blurredB = ((up and 0xFF) + (down and 0xFF) + (left and 0xFF) + (right and 0xFF)) / 4

                val nr = (cr + (cr - blurredR) * s).roundToInt().coerceIn(0, 255)
                val ng = (cg + (cg - blurredG) * s).roundToInt().coerceIn(0, 255)
                val nb = (cb + (cb - blurredB) * s).roundToInt().coerceIn(0, 255)
                px[row + x] = Color.argb(255, nr, ng, nb)
            }
        }
    }
}
