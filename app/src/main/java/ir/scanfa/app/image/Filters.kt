package ir.scanfa.app.image

import android.graphics.Bitmap
import ir.scanfa.app.data.FilterKind
import kotlin.math.max
import kotlin.math.min

/** فیلترهای تصویر: بهبود، حذف سایه، خاکستری و سیاه‌وسفید. */
object Filters {

    fun apply(src: Bitmap, kind: FilterKind): Bitmap {
        if (kind == FilterKind.ORIGINAL) return src
        val w = src.width
        val h = src.height
        if (w < 8 || h < 8) return src
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val out = when (kind) {
            FilterKind.GRAY -> grayscale(px)
            FilterKind.BW -> blackWhite(px, w, h)
            FilterKind.SHADOW -> shadow(px, w, h)
            FilterKind.ENHANCE -> enhance(px, w, h)
            FilterKind.ORIGINAL -> px
        }
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun lum(c: Int): Float =
        0.299f * ((c shr 16) and 0xFF) + 0.587f * ((c shr 8) and 0xFF) + 0.114f * (c and 0xFF)

    private fun grayscale(px: IntArray): IntArray {
        val g = FloatArray(px.size) { lum(px[it]) }
        val levels = estimateLevels(g)
        val out = IntArray(px.size)
        for (i in px.indices) {
            val v = stretch(g[i], levels)
            out[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        return out
    }

    private fun shadow(px: IntArray, w: Int, h: Int): IntArray {
        val g = FloatArray(px.size) { lum(px[it]) }
        val bg = Blur.box(g, w, h, max(8, min(w, h) / 16))
        val out = IntArray(px.size)
        for (i in px.indices) {
            val gain = (235f / max(30f, bg[i])).coerceIn(0.45f, 2.8f)
            out[i] = scaleChannels(px[i], gain)
        }
        return out
    }

    private fun enhance(px: IntArray, w: Int, h: Int): IntArray {
        val flat = shadow(px, w, h)
        val g = FloatArray(px.size) { lum(flat[it]) }
        val levels = estimateLevels(g)
        val out = IntArray(px.size)
        for (i in flat.indices) {
            val c = flat[i]
            val r = stretch(((c shr 16) and 0xFF).toFloat(), levels)
            val gg = stretch(((c shr 8) and 0xFF).toFloat(), levels)
            val b = stretch((c and 0xFF).toFloat(), levels)
            out[i] = (0xFF shl 24) or (r shl 16) or (gg shl 8) or b
        }
        return sharpen(out, w, h)
    }

    private fun sharpen(px: IntArray, w: Int, h: Int): IntArray {
        val out = IntArray(px.size)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                if (x == 0 || y == 0 || x == w - 1 || y == h - 1) {
                    out[i] = px[i]
                    continue
                }
                val c0 = px[i]
                val above = px[i - w]
                val below = px[i + w]
                val leftPx = px[i - 1]
                val rightPx = px[i + 1]
                val r0 = (c0 shr 16) and 0xFF
                val g0 = (c0 shr 8) and 0xFF
                val b0 = c0 and 0xFF
                val sumR = ((above shr 16) and 0xFF) + ((below shr 16) and 0xFF) + ((leftPx shr 16) and 0xFF) + ((rightPx shr 16) and 0xFF)
                val sumG = ((above shr 8) and 0xFF) + ((below shr 8) and 0xFF) + ((leftPx shr 8) and 0xFF) + ((rightPx shr 8) and 0xFF)
                val sumB = (above and 0xFF) + (below and 0xFF) + (leftPx and 0xFF) + (rightPx and 0xFF)
                val r = (r0 + 0.35f * (4 * r0 - sumR)).toInt().coerceIn(0, 255)
                val g = (g0 + 0.35f * (4 * g0 - sumG)).toInt().coerceIn(0, 255)
                val b = (b0 + 0.35f * (4 * b0 - sumB)).toInt().coerceIn(0, 255)
                out[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        return out
    }

    private fun blackWhite(px: IntArray, w: Int, h: Int): IntArray {
        val gray = IntArray(px.size) { lum(px[it]).toInt().coerceIn(0, 255) }
        val stride = w + 1
        val integral = LongArray((w + 1) * (h + 1))
        for (y in 0 until h) {
            var rowSum = 0L
            for (x in 0 until w) {
                rowSum += gray[y * w + x].toLong()
                integral[(y + 1) * stride + (x + 1)] = integral[y * stride + (x + 1)] + rowSum
            }
        }
        val radius = max(8, min(w, h) / 30)
        val out = IntArray(px.size)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val x0 = max(0, x - radius)
                val y0 = max(0, y - radius)
                val x1 = min(w - 1, x + radius)
                val y1 = min(h - 1, y + radius)
                val area = (x1 - x0 + 1) * (y1 - y0 + 1)
                val sum = integral[(y1 + 1) * stride + (x1 + 1)] -
                    integral[y0 * stride + (x1 + 1)] -
                    integral[(y1 + 1) * stride + x0] +
                    integral[y0 * stride + x0]
                val mean = sum.toFloat() / area
                val threshold = mean * 0.82f
                val v = if (gray[y * w + x].toFloat() < threshold) 0 else 255
                out[y * w + x] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
            }
        }
        return out
    }

    private fun scaleChannels(c: Int, gain: Float): Int {
        val r = (((c shr 16) and 0xFF) * gain).toInt().coerceIn(0, 255)
        val g = (((c shr 8) and 0xFF) * gain).toInt().coerceIn(0, 255)
        val b = ((c and 0xFF) * gain).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun estimateLevels(g: FloatArray): FloatArray {
        val hist = IntArray(256)
        for (v in g) hist[v.toInt().coerceIn(0, 255)]++
        val total = g.size
        var acc = 0
        var lo = 0
        var hi = 255
        for (i in 0 until 256) {
            acc += hist[i]
            if (acc >= total * 0.02f) {
                lo = i
                break
            }
        }
        acc = 0
        for (i in 255 downTo 0) {
            acc += hist[i]
            if (acc >= total * 0.02f) {
                hi = i
                break
            }
        }
        if (hi - lo < 12) {
            lo = 0
            hi = 255
        }
        return floatArrayOf(lo.toFloat(), hi.toFloat())
    }

    private fun stretch(v: Float, levels: FloatArray): Int {
        val lo = levels[0]
        val hi = levels[1]
        val out = (v - lo) * 255f / max(1f, hi - lo)
        return out.toInt().coerceIn(0, 255)
    }
}
