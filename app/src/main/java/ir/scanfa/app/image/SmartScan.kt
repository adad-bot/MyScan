package ir.scanfa.app.image

import android.graphics.Bitmap
import android.graphics.PointF
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * تشخیص هوشمند سند بر پایهٔ رنگ پس‌زمینه:
 *  - رنگ پس‌زمینه از نوار لبه‌ها و میانهٔ اضلاع نمونه‌گیری می‌شود
 *  - هر پیکسل با آن مقایسه و ماسک سند ساخته می‌شود
 *  - برای هر سطر/ستون تعداد پیکسل‌های متفاوت شمرده می‌شود
 *  - اولین/آخرین سطر و ستون معنادار پیدا و اضافه‌ها بریده می‌شوند
 *  - از نقاط مرزی، چهار خط (بالا/پایین/چپ/راست) برازش و چهار گوشه محاسبه می‌شود
 */
object SmartScan {

    data class Result(val quad: Quad?, val confidence: Float)

    private const val WORK_MAX = 520
    private const val DIFF_THRESHOLD = 52

    fun detect(src: Bitmap): Result {
        return try {
            val internal = detectInternal(src) ?: return Result(null, 0f)
            Result(internal.first, internal.second)
        } catch (t: Throwable) {
            Result(null, 0f)
        }
    }

    fun detectQuad(src: Bitmap): Quad? = detect(src).quad

    private fun detectInternal(src: Bitmap): Pair<Quad, Float>? {
        val scale = WORK_MAX.toFloat() / max(src.width, src.height)
        val w = max(40, (src.width * scale).toInt())
        val h = max(40, (src.height * scale).toInt())
        val small = Bitmap.createScaledBitmap(src, w, h, true)
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        if (small !== src) small.recycle()

        val bg = backgroundColor(px, w, h)
        val mask = BooleanArray(w * h)
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                val c = px[row + x]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                val diff = abs(r - bg[0]) + abs(g - bg[1]) + abs(b - bg[2])
                mask[row + x] = diff > DIFF_THRESHOLD
            }
        }

        val cleaned = denoise(mask, w, h)

        // اولین/آخرین سطر و ستون معنادار
        val minCount = max(3, (min(w, h) * 0.02f).toInt())
        var top = -1
        var bottom = -1
        var left = -1
        var right = -1
        val rowCounts = IntArray(h)
        val colCounts = IntArray(w)
        for (y in 0 until h) {
            var c = 0
            val row = y * w
            for (x in 0 until w) {
                if (cleaned[row + x]) {
                    c++
                    colCounts[x]++
                }
            }
            rowCounts[y] = c
            if (c >= minCount) {
                if (top < 0) top = y
                bottom = y
            }
        }
        for (x in 0 until w) {
            if (colCounts[x] >= minCount) {
                if (left < 0) left = x
                right = x
            }
        }
        if (top < 0 || left < 0 || right <= left || bottom <= top) return null
        val spanX = (right - left).toFloat()
        val spanY = (bottom - top).toFloat()
        if (spanX < w * 0.12f || spanY < h * 0.12f) return null

        // نقاط مرزی چپ/راست برای هر سطر
        val leftPts = ArrayList<PointF>()
        val rightPts = ArrayList<PointF>()
        val step = max(1, (spanY / 140f).toInt())
        for (y in top..bottom step step) {
            var lo = -1
            var hi = -1
            val row = y * w
            for (x in left..right) {
                if (cleaned[row + x]) {
                    if (lo < 0) lo = x
                    hi = x
                }
            }
            if (lo >= 0 && hi - lo > spanX * 0.4f) {
                leftPts.add(PointF(lo.toFloat(), y.toFloat()))
                rightPts.add(PointF(hi.toFloat(), y.toFloat()))
            }
        }

        // نقاط مرزی بالا/پایین برای هر ستون
        val topPts = ArrayList<PointF>()
        val bottomPts = ArrayList<PointF>()
        val colStep = max(1, (spanX / 140f).toInt())
        for (x in left..right step colStep) {
            var hiY = -1
            var loY = -1
            for (y in top..bottom) {
                if (cleaned[y * w + x]) {
                    if (hiY < 0) hiY = y
                    loY = y
                }
            }
            if (hiY >= 0 && loY - hiY > spanY * 0.4f) {
                topPts.add(PointF(x.toFloat(), hiY.toFloat()))
                bottomPts.add(PointF(x.toFloat(), loY.toFloat()))
            }
        }
        if (leftPts.size < 6 || topPts.size < 6) return null

        val leftLine = fitVertical(leftPts) ?: return null
        val rightLine = fitVertical(rightPts) ?: return null
        val topLine = fitHorizontal(topPts) ?: return null
        val bottomLine = fitHorizontal(bottomPts) ?: return null

        val tl = intersect(leftLine, topLine) ?: return null
        val tr = intersect(rightLine, topLine) ?: return null
        val br = intersect(rightLine, bottomLine) ?: return null
        val bl = intersect(leftLine, bottomLine) ?: return null

        val quad = Quad(tl, tr, br, bl)
        val area = quad.area() / (w.toFloat() * h)
        if (area < 0.08f || area > 0.995f) return null
        val confidence = when {
            area < 0.12f -> 0.2f
            area > 0.98f -> 0.3f
            else -> 0.9f
        }
        return quad to confidence
    }

    /** بریدن لبه‌های اضافی بر اساس همان منطق پس‌زمینه. */
    fun trimEdges(src: Bitmap): Bitmap {
        return try {
            val scale = WORK_MAX.toFloat() / max(src.width, src.height)
            val w = max(40, (src.width * scale).toInt())
            val h = max(40, (src.height * scale).toInt())
            val small = Bitmap.createScaledBitmap(src, w, h, true)
            val px = IntArray(w * h)
            small.getPixels(px, 0, w, 0, 0, w, h)
            if (small !== src) small.recycle()

            val bg = backgroundColor(px, w, h)
            val mask = BooleanArray(w * h)
            for (i in px.indices) {
                val c = px[i]
                val diff = abs(((c shr 16) and 0xFF) - bg[0]) +
                    abs(((c shr 8) and 0xFF) - bg[1]) +
                    abs((c and 0xFF) - bg[2])
                mask[i] = diff > DIFF_THRESHOLD
            }
            val cleaned = denoise(mask, w, h)
            val minCount = max(3, (min(w, h) * 0.02f).toInt())
            var top = -1
            var bottom = -1
            var left = -1
            var right = -1
            val rowCounts = IntArray(h)
            val colCounts = IntArray(w)
            for (y in 0 until h) {
                var c = 0
                for (x in 0 until w) {
                    if (cleaned[y * w + x]) {
                        c++
                        colCounts[x]++
                    }
                }
                rowCounts[y] = c
                if (c >= minCount) {
                    if (top < 0) top = y
                    bottom = y
                }
            }
            for (x in 0 until w) {
                if (colCounts[x] >= minCount) {
                    if (left < 0) left = x
                    right = x
                }
            }
            if (top < 0 || left < 0) return src
            if ((bottom - top) < h * 0.3f || (right - left) < w * 0.3f) return src

            val marginX = (src.width * 0.004f).toInt()
            val marginY = (src.height * 0.004f).toInt()
            val x0 = ((left.toFloat() / w) * src.width).toInt().coerceIn(0, src.width - 2) - marginX
            val y0 = ((top.toFloat() / h) * src.height).toInt().coerceIn(0, src.height - 2) - marginY
            val x1 = ((right + 1f) / w * src.width).toInt().coerceIn(2, src.width) + marginX
            val y1 = ((bottom + 1f) / h * src.height).toInt().coerceIn(2, src.height) + marginY
            val cw = (x1 - x0).coerceIn(16, src.width - max(0, x0))
            val ch = (y1 - y0).coerceIn(16, src.height - max(0, y0))
            if (cw >= src.width * 0.98f && ch >= src.height * 0.98f) return src
            Bitmap.createBitmap(src, x0.coerceAtLeast(0), y0.coerceAtLeast(0), cw, ch)
        } catch (t: Throwable) {
            src
        }
    }

    // ---------- کمکی‌ها ----------

    private fun backgroundColor(px: IntArray, w: Int, h: Int): IntArray {
        var r = 0L
        var g = 0L
        var b = 0L
        var n = 0L
        fun add(c: Int) {
            r += (c shr 16) and 0xFF
            g += (c shr 8) and 0xFF
            b += c and 0xFF
            n++
        }
        val bandY = max(1, (h * 0.02f).toInt())
        val bandX = max(1, (w * 0.02f).toInt())
        // نوارهای بالا و پایین
        for (y in 0 until bandY) {
            for (x in 0 until w step max(1, w / 80)) add(px[y * w + x])
            for (x in 0 until w step max(1, w / 80)) add(px[(h - 1 - y) * w + x])
        }
        // نوارهای چپ و راست
        for (x in 0 until bandX) {
            for (y in 0 until h step max(1, h / 80)) add(px[y * w + x])
            for (y in 0 until h step max(1, h / 80)) add(px[y * w + (w - 1 - x)])
        }
        // میانهٔ اضلاع (وقتی سند تا لبه رفته)
        val midY = h / 2
        val midX = w / 2
        for (x in 0 until (w * 0.06f).toInt()) add(px[midY * w + x])
        for (x in (w * 0.94f).toInt() until w) add(px[midY * w + x])
        for (y in 0 until (h * 0.06f).toInt()) add(px[y * w + midX])
        for (y in (h * 0.94f).toInt() until h) add(px[y * w + midX])
        return intArrayOf((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
    }

    private fun denoise(mask: BooleanArray, w: Int, h: Int): BooleanArray {
        val out = BooleanArray(mask.size)
        for (y in 0 until h) {
            for (x in 0 until w) {
                var count = 0
                for (dy in -1..1) {
                    val yy = y + dy
                    if (yy !in 0 until h) continue
                    for (dx in -1..1) {
                        val xx = x + dx
                        if (xx !in 0 until w) continue
                        if (mask[yy * w + xx]) count++
                    }
                }
                out[y * w + x] = count >= 5
            }
        }
        return out
    }

    private data class Line(val a: Float, val b: Float, val vertical: Boolean)

    private fun fitVertical(points: List<PointF>): Line? {
        if (points.size < 4) return null
        var sumY = 0.0
        var sumX = 0.0
        var sumYY = 0.0
        var sumYX = 0.0
        for (p in points) {
            sumY += p.y
            sumX += p.x
            sumYY += p.y.toDouble() * p.y
            sumYX += p.y.toDouble() * p.x
        }
        val n = points.size
        val den = n * sumYY - sumY * sumY
        if (abs(den) < 1e-6) return null
        val a = ((n * sumYX - sumY * sumX) / den).toFloat()
        val b = ((sumX - a * sumY) / n).toFloat()
        return Line(a, b, true)
    }

    private fun fitHorizontal(points: List<PointF>): Line? {
        if (points.size < 4) return null
        var sumX = 0.0
        var sumY = 0.0
        var sumXX = 0.0
        var sumXY = 0.0
        for (p in points) {
            sumX += p.x
            sumY += p.y
            sumXX += p.x.toDouble() * p.x
            sumXY += p.x.toDouble() * p.y
        }
        val n = points.size
        val den = n * sumXX - sumX * sumX
        if (abs(den) < 1e-6) return null
        val a = ((n * sumXY - sumX * sumY) / den).toFloat()
        val b = ((sumY - a * sumX) / n).toFloat()
        return Line(a, b, false)
    }

    /** خط عمودی: x = a*y + b ، خط افقی: y = a*x + b */
    private fun intersect(vertical: Line, horizontal: Line): PointF? {
        val den = 1f - vertical.a * horizontal.a
        if (abs(den) < 1e-4f) return null
        val x = (vertical.a * horizontal.b + vertical.b) / den
        val y = horizontal.a * x + horizontal.b
        return PointF(x, y)
    }
}
