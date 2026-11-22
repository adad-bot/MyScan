package ir.scanfa.app.image

import android.graphics.Bitmap
import android.graphics.PointF
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min

/**
 * تشخیص خودکار قاب سند.
 *
 * روش: کوچک‌سازی تصویر، خاکستری‌سازی، Otsu برای جدا کردن کاغذ روشن از پس‌زمینه،
 * انتخاب بزرگ‌ترین مؤلفهٔ متصل و برازش چهار خط مرزی روی نقاط لبهٔ آن.
 * اگر نتیجه معتبر نباشد، قاب پیش‌فرض نزدیک لبهٔ تصویر برگردانده می‌شود.
 */
object DocDetector {

    private const val WORK_MAX = 480
    private const val MIN_AREA_RATIO = 0.12f

    fun detect(src: Bitmap): Quad {
        val fallback = Quad.inset(src.width, src.height, 0.03f)
        return try {
            detectInternal(src) ?: fallback
        } catch (t: Throwable) {
            fallback
        }
    }

    private class Component(
        val label: IntArray,
        val count: Int,
        val minX: Int,
        val maxX: Int,
        val minY: Int,
        val maxY: Int
    )

    private fun detectInternal(src: Bitmap): Quad? {
        val scale = WORK_MAX.toFloat() / max(src.width, src.height)
        val w = max(32, (src.width * scale).toInt())
        val h = max(32, (src.height * scale).toInt())
        val small = Bitmap.createScaledBitmap(src, w, h, true)
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        if (small !== src) small.recycle()

        val gray = FloatArray(w * h)
        for (i in px.indices) {
            val c = px[i]
            gray[i] = 0.299f * ((c shr 16) and 0xFF) + 0.587f * ((c shr 8) and 0xFF) + 0.114f * (c and 0xFF)
        }
        val smooth = Blur.box(gray, w, h, 2)
        val threshold = otsu(smooth)

        val mask = BooleanArray(w * h) { smooth[it] > threshold }
        val comp = largestComponent(mask, w, h) ?: return null
        if (comp.count < (w * h) * MIN_AREA_RATIO) return null

        val leftPts = ArrayList<PointF>()
        val rightPts = ArrayList<PointF>()
        val topPts = ArrayList<PointF>()
        val bottomPts = ArrayList<PointF>()
        val spanX = (comp.maxX - comp.minX).toFloat()
        val spanY = (comp.maxY - comp.minY).toFloat()

        for (y in comp.minY..comp.maxY) {
            var lo = -1
            var hi = -1
            val row = y * w
            for (x in comp.minX..comp.maxX) {
                if (comp.label[row + x] == 1) {
                    if (lo < 0) lo = x
                    hi = x
                }
            }
            if (lo >= 0 && hi - lo > spanX * 0.35f) {
                leftPts.add(PointF(lo.toFloat(), y.toFloat()))
                rightPts.add(PointF(hi.toFloat(), y.toFloat()))
            }
        }
        for (x in comp.minX..comp.maxX) {
            var lo = -1
            var hi = -1
            for (y in comp.minY..comp.maxY) {
                if (comp.label[y * w + x] == 1) {
                    if (lo < 0) lo = y
                    hi = y
                }
            }
            if (lo >= 0 && hi - lo > spanY * 0.35f) {
                topPts.add(PointF(x.toFloat(), lo.toFloat()))
                bottomPts.add(PointF(x.toFloat(), hi.toFloat()))
            }
        }

        val left = fit(leftPts, true) ?: return null
        val right = fit(rightPts, true) ?: return null
        val top = fit(topPts, false) ?: return null
        val bottom = fit(bottomPts, false) ?: return null

        val tl = intersect(left, top) ?: return null
        val tr = intersect(right, top) ?: return null
        val br = intersect(right, bottom) ?: return null
        val bl = intersect(left, bottom) ?: return null

        val ordered = orderQuad(listOf(tl, tr, br, bl)) ?: return null
        if (ordered.area() < w * h * MIN_AREA_RATIO) return null
        if (!inside(ordered, w, h)) return null

        val k = src.width.toFloat() / w
        return Quad(
            PointF(ordered.tl.x * k, ordered.tl.y * k),
            PointF(ordered.tr.x * k, ordered.tr.y * k),
            PointF(ordered.br.x * k, ordered.br.y * k),
            PointF(ordered.bl.x * k, ordered.bl.y * k)
        )
    }

    private fun inside(q: Quad, w: Int, h: Int): Boolean {
        val fx = w * 0.08f
        val fy = h * 0.08f
        return q.points().all { it.x >= -fx && it.x <= w + fx && it.y >= -fy && it.y <= h + fy }
    }

    private fun orderQuad(pts: List<PointF>): Quad? {
        if (pts.size != 4) return null
        var cx = 0f
        var cy = 0f
        for (p in pts) {
            cx += p.x
            cy += p.y
            if (p.x.isNaN() || p.y.isNaN() || p.x.isInfinite() || p.y.isInfinite()) return null
        }
        cx /= 4f
        cy /= 4f
        val sorted = pts.sortedBy { atan2((it.y - cy).toDouble(), (it.x - cx).toDouble()) }
        var start = 0
        var best = Float.MAX_VALUE
        for (i in sorted.indices) {
            val s = sorted[i].x + sorted[i].y
            if (s < best) {
                best = s
                start = i
            }
        }
        val r = listOf(
            sorted[start],
            sorted[(start + 1) % 4],
            sorted[(start + 2) % 4],
            sorted[(start + 3) % 4]
        )
        return Quad(r[0], r[1], r[2], r[3])
    }

    private fun intersect(vertical: Pair<Float, Float>, horizontal: Pair<Float, Float>): PointF? {
        val (al, bl) = vertical
        val (at, bt) = horizontal
        val den = 1f - al * at
        if (abs(den) < 1e-4f) return null
        val x = (al * bt + bl) / den
        val y = at * x + bt
        if (x.isNaN() || y.isNaN()) return null
        return PointF(x, y)
    }

    private fun fit(points: List<PointF>, vertical: Boolean): Pair<Float, Float>? {
        if (points.size < 6) return null
        var ab = leastSquares(points, vertical) ?: return null
        val errors = points.map { p ->
            if (vertical) abs(ab.first * p.y + ab.second - p.x)
            else abs(ab.first * p.x + ab.second - p.y)
        }
        val sorted = errors.sorted()
        val limit = sorted[(sorted.size * 0.75f).toInt().coerceIn(0, sorted.size - 1)]
        val kept = points.filterIndexed { i, _ -> errors[i] <= max(limit, 2f) }
        if (kept.size >= 6) {
            ab = leastSquares(kept, vertical) ?: ab
        }
        return ab
    }

    private fun leastSquares(points: List<PointF>, vertical: Boolean): Pair<Float, Float>? {
        var n = 0.0
        var sx = 0.0
        var sy = 0.0
        var sxx = 0.0
        var sxy = 0.0
        for (p in points) {
            val x = if (vertical) p.y.toDouble() else p.x.toDouble()
            val y = if (vertical) p.x.toDouble() else p.y.toDouble()
            n += 1.0
            sx += x
            sy += y
            sxx += x * x
            sxy += x * y
        }
        val den = n * sxx - sx * sx
        if (abs(den) < 1e-6) return null
        val a = (n * sxy - sx * sy) / den
        val b = (sy - a * sx) / n
        if (a.isNaN() || b.isNaN()) return null
        if (abs(a) > 12.0) return null
        return a.toFloat() to b.toFloat()
    }

    private fun otsu(gray: FloatArray): Float {
        val hist = IntArray(256)
        for (v in gray) {
            hist[v.toInt().coerceIn(0, 255)]++
        }
        val total = gray.size.toDouble()
        var sumAll = 0.0
        for (i in 0 until 256) sumAll += i * hist[i].toDouble()
        var sumB = 0.0
        var wB = 0.0
        var best = 0.0
        var threshold = 128
        for (t in 0 until 256) {
            wB += hist[t]
            if (wB == 0.0) continue
            val wF = total - wB
            if (wF == 0.0) break
            sumB += t * hist[t].toDouble()
            val mB = sumB / wB
            val mF = (sumAll - sumB) / wF
            val between = wB * wF * (mB - mF) * (mB - mF)
            if (between > best) {
                best = between
                threshold = t
            }
        }
        return threshold.toFloat()
    }

    private fun largestComponent(mask: BooleanArray, w: Int, h: Int): Component? {
        val visited = BooleanArray(mask.size)
        val stack = IntArray(mask.size)
        var bestSize = 0
        var bestSeed = -1
        var maxLabel = 0
        val label = IntArray(mask.size)

        for (start in mask.indices) {
            if (!mask[start] || visited[start]) continue
            var top = 0
            stack[top++] = start
            visited[start] = true
            maxLabel++
            val currentLabel = maxLabel
            var count = 0
            var minX = Int.MAX_VALUE
            var maxX = Int.MIN_VALUE
            var minY = Int.MAX_VALUE
            var maxY = Int.MIN_VALUE
            while (top > 0) {
                val idx = stack[--top]
                val x = idx % w
                val y = idx / w
                label[idx] = currentLabel
                count++
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
                if (x > 0) push(idx - 1, mask, visited, stack, top).let { top = it }
                if (x < w - 1) push(idx + 1, mask, visited, stack, top).let { top = it }
                if (y > 0) push(idx - w, mask, visited, stack, top).let { top = it }
                if (y < h - 1) push(idx + w, mask, visited, stack, top).let { top = it }
            }
            if (count > bestSize) {
                bestSize = count
                bestSeed = currentLabel
            }
        }
        if (bestSeed < 0) return null
        val labels = IntArray(mask.size) { if (label[it] == bestSeed) 1 else 0 }
        var minX = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        var minY = Int.MAX_VALUE
        var maxY = Int.MIN_VALUE
        for (i in labels.indices) {
            if (labels[i] == 1) {
                val x = i % w
                val y = i / w
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
        }
        return Component(labels, bestSize, minX, maxX, minY, maxY)
    }

    private fun push(idx: Int, mask: BooleanArray, visited: BooleanArray, stack: IntArray, top: Int): Int {
        if (mask[idx] && !visited[idx]) {
            visited[idx] = true
            stack[top] = idx
            return top + 1
        }
        return top
    }
}
