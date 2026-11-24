package ir.scanfa.app.scan

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
import ir.scanfa.app.image.EdgeDetector
import ir.scanfa.app.image.Enhance
import ir.scanfa.app.image.HoughQuad
import ir.scanfa.app.image.Quad
import ir.scanfa.app.image.SmartScan
import ir.scanfa.app.image.Warp
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * موتور اسکن سند (Document Scan Engine)
 *
 * سه لایهٔ تشخیص، از دقیق به تقریبی:
 *  ۱) Canny + Hough: خطوط غالب سند
 *  ۲) ماسک کاغذ سفید (روشنایی زیاد، اشباع کم) + برازش خطوط مرزی با RANSAC
 *  ۳) رشد ناحیه‌ای از مرکز بر اساس سازگاری رنگ
 * سپس برش پرسپکتیو، بریدن لبه‌های اضافی، یکسان‌سازی روشنایی محلی و تیزکردن تطبیقی.
 */
object DocumentScanEngine {

    data class Config(
        val maxSide: Int = 2200,
        val flattenLighting: Boolean = true,
        val sharpen: Boolean = true
    )

    data class Detection(
        val quad: Quad?,
        val confidence: Float,
        val centerColor: Int
    )

    fun detect(src: Bitmap): Detection {
        // ۰) OpenCV: کانتور + approxPolyDP (مثل اسکنرهای حرفه‌ای)
        try {
            if (OpenCvScanner.isReady()) {
                val cv = OpenCvScanner.detect(src)
                if (cv.quad != null) return Detection(cv.quad, cv.confidence, Color.WHITE)
            }
        } catch (t: Throwable) {
            // ادامه
        }

        // ۱) لبه‌یابی Canny + تبدیل هاف
        try {
            val edges = EdgeDetector.canny(src, 640)
            val hough = HoughQuad.findQuad(edges, src.width, src.height)
            if (hough != null) return Detection(hough, 0.95f, Color.WHITE)
        } catch (t: Throwable) {
            // ادامه
        }

        // ۲) ماسک کاغذ
        try {
            val paper = paperQuad(src)
            if (paper != null) return Detection(paper.first, paper.second, Color.WHITE)
        } catch (t: Throwable) {
            // ادامه
        }

        // ۳) رشد از مرکز
        return try {
            centerGrowQuad(src)
        } catch (t: Throwable) {
            Detection(null, 0f, Color.WHITE)
        }
    }

    fun crop(src: Bitmap, quad: Quad?, maxSide: Int = 2200): Bitmap {
        val q = quad ?: detect(src).quad ?: Quad.inset(src.width, src.height, 0.02f)
        try {
            if (OpenCvScanner.isReady()) {
                val cv = OpenCvScanner.warp(src, q, maxSide)
                if (cv != null) {
                    val trimmed = SmartScan.trimEdges(cv)
                    if (trimmed !== cv) cv.recycle()
                    return trimmed
                }
            }
        } catch (t: Throwable) {
            // ادامه با Warp داخلی
        }
        var bmp = Warp.warp(src, q, maxSide)
        val trimmed = SmartScan.trimEdges(bmp)
        if (trimmed !== bmp) {
            bmp.recycle()
            bmp = trimmed
        }
        return bmp
    }

    fun enhance(src: Bitmap, config: Config = Config()): Bitmap {
        try {
            if (OpenCvScanner.isReady()) {
                val cv = OpenCvScanner.enhanceOnly(src)
                if (cv != null) return cv
            }
        } catch (t: Throwable) {
            // ادامه با روش داخلی
        }
        var bmp = src
        if (config.flattenLighting) {
            val flat = flattenLighting(bmp)
            if (flat !== bmp) bmp.recycle()
            bmp = flat
        }
        val enhanced = Enhance.improve(bmp, if (config.sharpen) 0.6f else 0f)
        if (enhanced !== bmp) bmp.recycle()
        bmp = enhanced
        return bmp
    }

    fun scan(src: Bitmap, config: Config = Config()): Bitmap {
        var bmp = crop(src, detect(src).quad, config.maxSide)
        val enhanced = enhance(bmp, config)
        if (enhanced !== bmp) bmp.recycle()
        bmp = enhanced
        return bmp
    }

    // ---------- لایه ۱: ماسک کاغذ ----------

    private fun paperQuad(src: Bitmap, workMax: Int = 520): Pair<Quad, Float>? {
        val scale = workMax.toFloat() / max(src.width, src.height)
        val w = max(48, (src.width * scale).toInt())
        val h = max(48, (src.height * scale).toInt())
        val small = Bitmap.createScaledBitmap(src, w, h, true)
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        if (small !== src) small.recycle()

        val mask = BooleanArray(w * h)
        for (i in px.indices) {
            val c = px[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val mx = max(r, max(g, b))
            val mn = min(r, min(g, b))
            val chroma = mx - mn
            val brightness = (r + g + b) / 3
            mask[i] = brightness > 105 && chroma < 45
        }

        val component = centerComponent(mask, w, h) ?: return null
        val ratio = component.count { it }.toFloat() / (w * h)
        if (ratio < 0.12f || ratio > 0.995f) return null

        val closed = dilate(component, w, h, 3)
        val quad = quadFromMask(closed, w, h, src.width, src.height) ?: return null
        val area = quad.area() / (src.width.toFloat() * src.height)
        if (area < 0.15f || area > 0.995f) return null
        return quad to if (area > 0.5f) 0.8f else 0.7f
    }

    private fun centerComponent(mask: BooleanArray, w: Int, h: Int): BooleanArray? {
        val labels = IntArray(w * h) { -1 }
        val cx = w / 2
        val cy = h / 2
        var centerLabel = -1
        var label = 0
        val queue = IntArray(w * h)
        for (start in mask.indices) {
            if (!mask[start] || labels[start] >= 0) continue
            var head = 0
            var tail = 0
            queue[tail++] = start
            labels[start] = label
            var touchesCenter = false
            while (head < tail) {
                val idx = queue[head++]
                val x = idx % w
                val y = idx / w
                if (x == cx && y == cy) touchesCenter = true
                if (x > 0 && mask[idx - 1] && labels[idx - 1] < 0) {
                    labels[idx - 1] = label
                    queue[tail++] = idx - 1
                }
                if (x < w - 1 && mask[idx + 1] && labels[idx + 1] < 0) {
                    labels[idx + 1] = label
                    queue[tail++] = idx + 1
                }
                if (y > 0 && mask[idx - w] && labels[idx - w] < 0) {
                    labels[idx - w] = label
                    queue[tail++] = idx - w
                }
                if (y < h - 1 && mask[idx + w] && labels[idx + w] < 0) {
                    labels[idx + w] = label
                    queue[tail++] = idx + w
                }
            }
            if (touchesCenter) centerLabel = label
            label++
        }
        if (centerLabel < 0) return null
        return BooleanArray(w * h) { labels[it] == centerLabel }
    }

    private fun dilate(mask: BooleanArray, w: Int, h: Int, radius: Int): BooleanArray {
        val out = BooleanArray(mask.size)
        for (y in 0 until h) {
            for (x in 0 until w) {
                if (!mask[y * w + x]) continue
                for (dy in -radius..radius) {
                    val yy = y + dy
                    if (yy !in 0 until h) continue
                    for (dx in -radius..radius) {
                        val xx = x + dx
                        if (xx !in 0 until w) continue
                        out[yy * w + xx] = true
                    }
                }
            }
        }
        return out
    }

    // ---------- لایه ۳: رشد از مرکز ----------

    private fun centerGrowQuad(src: Bitmap, workMax: Int = 520): Detection {
        val scale = workMax.toFloat() / max(src.width, src.height)
        val w = max(48, (src.width * scale).toInt())
        val h = max(48, (src.height * scale).toInt())
        val small = Bitmap.createScaledBitmap(src, w, h, true)
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        if (small !== src) small.recycle()

        val center = centerColor(px, w, h)
        val mask = BooleanArray(w * h)
        val seeds = listOf(
            (h / 2) * w + (w / 2),
            (h / 2 - h / 10) * w + (w / 2 - w / 10),
            (h / 2 + h / 10) * w + (w / 2 + w / 10),
            (h / 2 - h / 10) * w + (w / 2 + w / 10),
            (h / 2 + h / 10) * w + (w / 2 - w / 10)
        )
        for (seed in seeds) {
            if (seed !in mask.indices) continue
            val m = grow(px, w, h, center, seed)
            for (i in mask.indices) if (m[i]) mask[i] = true
        }
        val component = centerComponent(mask, w, h)
        val chosen = component ?: return Detection(null, 0f, center)
        val closed = dilate(chosen, w, h, 2)
        val quad = quadFromMask(closed, w, h, src.width, src.height)
            ?: return Detection(null, 0f, center)
        val area = quad.area() / (src.width.toFloat() * src.height)
        if (area < 0.1f || area > 0.995f) return Detection(null, 0f, center)
        return Detection(quad, if (area > 0.2f) 0.85f else 0.4f, center)
    }

    private fun centerColor(px: IntArray, w: Int, h: Int): Int {
        val cx = w / 2
        val cy = h / 2
        var r = 0L
        var g = 0L
        var b = 0L
        var n = 0L
        for (y in (cy - 6).coerceAtLeast(0)..(cy + 6).coerceAtMost(h - 1)) {
            for (x in (cx - 6).coerceAtLeast(0)..(cx + 6).coerceAtMost(w - 1)) {
                val c = px[y * w + x]
                r += (c shr 16) and 0xFF
                g += (c shr 8) and 0xFF
                b += c and 0xFF
                n++
            }
        }
        return Color.rgb((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
    }

    private fun grow(px: IntArray, w: Int, h: Int, center: Int, seed: Int): BooleanArray {
        val cr = (center shr 16) and 0xFF
        val cg = (center shr 8) and 0xFF
        val cb = center and 0xFF
        val mask = BooleanArray(w * h)
        val visited = BooleanArray(w * h)
        val queue = IntArray(w * h)
        var head = 0
        var tail = 0
        queue[tail++] = seed
        visited[seed] = true
        val tolerance = 150
        while (head < tail) {
            val index = queue[head++]
            val x = index % w
            val y = index / w
            val c = px[index]
            val diff = abs(((c shr 16) and 0xFF) - cr) +
                abs(((c shr 8) and 0xFF) - cg) +
                abs((c and 0xFF) - cb)
            if (diff > tolerance) continue
            mask[index] = true
            if (x > 0 && !visited[index - 1]) {
                visited[index - 1] = true
                queue[tail++] = index - 1
            }
            if (x < w - 1 && !visited[index + 1]) {
                visited[index + 1] = true
                queue[tail++] = index + 1
            }
            if (y > 0 && !visited[index - w]) {
                visited[index - w] = true
                queue[tail++] = index - w
            }
            if (y < h - 1 && !visited[index + w]) {
                visited[index + w] = true
                queue[tail++] = index + w
            }
        }
        return mask
    }

    // ---------- گوشه‌ها ----------

    private fun quadFromMask(mask: BooleanArray, w: Int, h: Int, srcW: Int, srcH: Int): Quad? {
        var minY = -1
        var maxY = -1
        var minX = -1
        var maxX = -1
        for (y in 0 until h) {
            var lo = -1
            var hi = -1
            for (x in 0 until w) {
                if (mask[y * w + x]) {
                    if (lo < 0) lo = x
                    hi = x
                }
            }
            if (lo >= 0) {
                if (minY < 0) minY = y
                maxY = y
                if (minX < 0 || lo < minX) minX = lo
                if (hi > maxX) maxX = hi
            }
        }
        if (minY < 0 || minX < 0 || maxY - minY < h * 0.2f) return null

        val leftPts = ArrayList<PointF>()
        val rightPts = ArrayList<PointF>()
        val topPts = ArrayList<PointF>()
        val bottomPts = ArrayList<PointF>()
        val spanX = (maxX - minX).toFloat()
        val spanY = (maxY - minY).toFloat()

        val step = max(1, (spanY / 140f).toInt())
        for (y in minY..maxY step step) {
            var lo = -1
            var hi = -1
            for (x in minX..maxX) {
                if (mask[y * w + x]) {
                    if (lo < 0) lo = x
                    hi = x
                }
            }
            if (lo >= 0 && hi - lo > spanX * 0.4f) {
                leftPts.add(PointF(lo.toFloat(), y.toFloat()))
                rightPts.add(PointF(hi.toFloat(), y.toFloat()))
            }
        }
        val colStep = max(1, (spanX / 140f).toInt())
        for (x in minX..maxX step colStep) {
            var top = -1
            var bottom = -1
            for (y in minY..maxY) {
                if (mask[y * w + x]) {
                    if (top < 0) top = y
                    bottom = y
                }
            }
            if (top >= 0 && bottom - top > spanY * 0.4f) {
                topPts.add(PointF(x.toFloat(), top.toFloat()))
                bottomPts.add(PointF(x.toFloat(), bottom.toFloat()))
            }
        }
        if (leftPts.size < 6 || topPts.size < 6) return null

        val left = ransacLine(leftPts, true) ?: return null
        val right = ransacLine(rightPts, true) ?: return null
        val top = ransacLine(topPts, false) ?: return null
        val bottom = ransacLine(bottomPts, false) ?: return null

        val tl = intersect(left, top) ?: return null
        val tr = intersect(right, top) ?: return null
        val br = intersect(right, bottom) ?: return null
        val bl = intersect(left, bottom) ?: return null

        val sx = srcW.toFloat() / w
        val sy = srcH.toFloat() / h
        val points = listOf(tl, tr, br, bl).map {
            PointF(
                (it.x * sx).coerceIn(0f, srcW - 1f),
                (it.y * sy).coerceIn(0f, srcH - 1f)
            )
        }
        return orderQuad(points)
    }

    private data class Line(val a: Float, val b: Float)

    private fun ransacLine(points: List<PointF>, vertical: Boolean): Line? {
        if (points.size < 4) return null
        val random = kotlin.random.Random(points.size * 31L + if (vertical) 1 else 2)
        var bestA = 0f
        var bestB = 0f
        var bestInliers = -1
        repeat(120) {
            val i1 = random.nextInt(points.size)
            var i2 = random.nextInt(points.size)
            if (i2 == i1) i2 = (i2 + 1) % points.size
            val p1 = points[i1]
            val p2 = points[i2]
            val dx = if (vertical) p2.y - p1.y else p2.x - p1.x
            if (abs(dx) < 1e-3f) return@repeat
            val a = if (vertical) (p2.x - p1.x) / dx else (p2.y - p1.y) / dx
            val b = if (vertical) p1.x - a * p1.y else p1.y - a * p1.x
            var inliers = 0
            for (p in points) {
                val predicted = if (vertical) a * p.y + b else a * p.x + b
                val actual = if (vertical) p.x else p.y
                if (abs(predicted - actual) < 3f) inliers++
            }
            if (inliers > bestInliers) {
                bestInliers = inliers
                bestA = a
                bestB = b
            }
        }
        if (bestInliers < points.size / 3) return null
        return Line(bestA, bestB)
    }

    private fun intersect(vertical: Line, horizontal: Line): PointF? {
        val den = 1f - vertical.a * horizontal.a
        if (abs(den) < 1e-4f) return null
        val x = (vertical.a * horizontal.b + vertical.b) / den
        val y = horizontal.a * x + horizontal.b
        if (!x.isFinite() || !y.isFinite()) return null
        return PointF(x, y)
    }

    private fun orderQuad(points: List<PointF>): Quad? {
        if (points.size != 4) return null
        val cx = points.sumOf { it.x.toDouble() }.toFloat() / 4f
        val cy = points.sumOf { it.y.toDouble() }.toFloat() / 4f
        val sorted = points.sortedBy { atan2((it.y - cy).toDouble(), (it.x - cx).toDouble()) }
        var idx = 0
        var best = Float.MAX_VALUE
        sorted.forEachIndexed { i, p ->
            if (p.x + p.y < best) {
                best = p.x + p.y
                idx = i
            }
        }
        val rot = List(4) { sorted[(idx + it) % 4] }
        val area2 = (rot[0].x * rot[1].y - rot[1].x * rot[0].y) +
            (rot[1].x * rot[2].y - rot[2].x * rot[1].y) +
            (rot[2].x * rot[3].y - rot[3].x * rot[2].y) +
            (rot[3].x * rot[0].y - rot[0].x * rot[3].y)
        val ordered = if (area2 < 0f) listOf(rot[0], rot[3], rot[2], rot[1]) else rot
        return Quad(ordered[0], ordered[1], ordered[2], ordered[3])
    }

    // ---------- روشنایی ----------

    private fun flattenLighting(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        if (w < 32 || h < 32) return src

        val cols = 24
        val rows = max(8, (cols * h.toFloat() / w).toInt())
        val small = Bitmap.createScaledBitmap(src, cols, rows, true)
        val smallPx = IntArray(cols * rows)
        small.getPixels(smallPx, 0, cols, 0, 0, cols, rows)
        if (small !== src) small.recycle()

        var estimate = FloatArray(cols * rows) { i ->
            val c = smallPx[i]
            (0.299f * ((c shr 16) and 0xFF) + 0.587f * ((c shr 8) and 0xFF) + 0.114f * (c and 0xFF))
        }
        repeat(2) {
            val next = FloatArray(estimate.size)
            for (y in 0 until rows) {
                for (x in 0 until cols) {
                    var sum = 0f
                    var count = 0
                    for (dy in -1..1) {
                        val yy = (y + dy).coerceIn(0, rows - 1)
                        for (dx in -1..1) {
                            val xx = (x + dx).coerceIn(0, cols - 1)
                            sum += estimate[yy * cols + xx]
                            count++
                        }
                    }
                    next[y * cols + x] = sum / count
                }
            }
            estimate = next
        }

        val median = estimate.sorted().let { it[it.size / 2] }.coerceAtLeast(40f)
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        for (y in 0 until h) {
            val fy = y.toFloat() / h * (rows - 1)
            val y0 = fy.toInt().coerceIn(0, rows - 1)
            val y1 = (y0 + 1).coerceAtMost(rows - 1)
            val ty = fy - y0
            for (x in 0 until w) {
                val fx = x.toFloat() / w * (cols - 1)
                val x0 = fx.toInt().coerceIn(0, cols - 1)
                val x1 = (x0 + 1).coerceAtMost(cols - 1)
                val tx = fx - x0
                val bgTop = estimate[y0 * cols + x0] * (1 - tx) + estimate[y0 * cols + x1] * tx
                val bgBottom = estimate[y1 * cols + x0] * (1 - tx) + estimate[y1 * cols + x1] * tx
                val bg = bgTop * (1 - ty) + bgBottom * ty
                val k = (median / bg.coerceAtLeast(20f)).coerceIn(0.6f, 1.8f)
                val c = px[y * w + x]
                val r = (((c shr 16) and 0xFF) * k).roundToInt().coerceIn(0, 255)
                val g = (((c shr 8) and 0xFF) * k).roundToInt().coerceIn(0, 255)
                val b = ((c and 0xFF) * k).roundToInt().coerceIn(0, 255)
                px[y * w + x] = Color.rgb(r, g, b)
            }
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }
}
