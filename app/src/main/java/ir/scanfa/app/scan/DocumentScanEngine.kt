package ir.scanfa.app.scan

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
import ir.scanfa.app.image.Enhance
import ir.scanfa.app.image.Quad
import ir.scanfa.app.image.SmartScan
import ir.scanfa.app.image.Warp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * موتور اسکن سند (Document Scan Engine)
 *
 * یک موتور مستقل و قابل استفادهٔ مجدد در برنامه‌های دیگر:
 *  ۱) تشخیص سند با «سازگاری پیکسلی» از مرکز تصویر به سمت لبه‌ها (Region Growing)
 *  ۲) قاب‌بندی هوشمند و برش خودکار
 *  ۳) اصلاح کجی/خمیدگی با برازش خطوط لبه
 *  ۴) یکسان‌سازی روشنایی (سایه و نقاط پرنور به‌صورت محلی اصلاح می‌شوند)
 *  ۵) تیزکردن رنگ و متن برای حس اسکن واقعی
 */
object DocumentScanEngine {

    data class Config(
        val maxSide: Int = 2200,
        val flattenLighting: Boolean = true,
        val sharpen: Boolean = true,
        val colorBoost: Float = 1.25f
    )

    data class Detection(
        val quad: Quad?,
        val confidence: Float,
        val centerColor: Int
    )

    /** تشخیص قاب سند از روی رنگ مرکز و رشد به سمت لبه‌ها. */
    fun detect(src: Bitmap, workMax: Int = 560): Detection {
        // مسیر اصلی: لبه‌یابی Canny + تبدیل هاف (مثل اسکنرهای حرفه‌ای)
        try {
            val edges = ir.scanfa.app.image.EdgeDetector.canny(src, 640)
            val hough = ir.scanfa.app.image.HoughQuad.findQuad(edges, src.width, src.height)
            if (hough != null) {
                return Detection(hough, 0.95f, Color.WHITE)
            }
        } catch (t: Throwable) {
            // به روش رنگ‌محور برمی‌گردیم
        }
        return try {
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
                (h / 2 - h / 12) * w + (w / 2 - w / 12),
                (h / 2 + h / 12) * w + (w / 2 + w / 12),
                (h / 2 - h / 12) * w + (w / 2 + w / 12),
                (h / 2 + h / 12) * w + (w / 2 - w / 12)
            )
            for (seed in seeds) {
                if (seed !in mask.indices) continue
                val m = growFrom(px, w, h, center, seed)
                for (i in mask.indices) if (m[i]) mask[i] = true
            }
            val filled = closeMask(mask, w, h)

            val quad = quadFromMask(filled, w, h, src.width, src.height)
            if (quad == null) return Detection(null, 0f, center)

            val area = quad.area() / (src.width.toFloat() * src.height)
            val confidence = when {
                area < 0.10f -> 0.15f
                area > 0.99f -> 0.3f
                area < 0.18f -> 0.55f
                else -> 0.92f
            }
            Detection(quad, confidence, center)
        } catch (t: Throwable) {
            Detection(null, 0f, Color.WHITE)
        }
    }

    /** برش و صاف‌کردن سند؛ اگر قاب نداشته باشیم خودش تشخیص می‌دهد. */
    fun crop(src: Bitmap, quad: Quad?, maxSide: Int = 2200): Bitmap {
        val q = quad ?: detect(src).quad ?: Quad.inset(src.width, src.height, 0.02f)
        var bmp = Warp.warp(src, q, maxSide)
        val trimmed = SmartScan.trimEdges(bmp)
        if (trimmed !== bmp) {
            bmp.recycle()
            bmp = trimmed
        }
        return bmp
    }

    /** یکسان‌سازی روشنایی + کنتراست + رنگ + تیزکردن. */
    fun enhance(src: Bitmap, config: Config = Config()): Bitmap {
        var bmp = src
        if (config.flattenLighting) {
            val flat = flattenLighting(bmp)
            if (flat !== bmp) bmp.recycle()
            bmp = flat
        }
        val enhanced = Enhance.improve(bmp, if (config.sharpen) 0.75f else 0f)
        if (enhanced !== bmp) bmp.recycle()
        bmp = enhanced
        return bmp
    }

    /** خط لولهٔ کامل: تشخیص، برش، صاف‌سازی و بهبود. */
    fun scan(src: Bitmap, config: Config = Config()): Bitmap {
        val detection = detect(src)
        var bmp = crop(src, detection.quad, config.maxSide)
        val enhanced = enhance(bmp, config)
        if (enhanced !== bmp) bmp.recycle()
        bmp = enhanced
        return bmp
    }

    // ---------- تشخیص ----------

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

    /** رشد ناحیه از مرکز: پیکسل‌های سازگار با رنگ مرکز جزو سند می‌شوند. */
    private fun growFrom(px: IntArray, w: Int, h: Int, center: Int, startSeed: Int): BooleanArray {
        val cr = (center shr 16) and 0xFF
        val cg = (center shr 8) and 0xFF
        val cb = center and 0xFF
        val mask = BooleanArray(w * h)
        val visited = BooleanArray(w * h)
        val queue = IntArray(w * h)
        var head = 0
        var tail = 0

        val start = startSeed
        queue[tail++] = start
        visited[start] = true

        while (head < tail) {
            val index = queue[head++]
            val x = index % w
            val y = index / w
            val c = px[index]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val diff = abs(r - cr) + abs(g - cg) + abs(b - cb)
            // تحمل بالا برای متن/سایه های داخل سند
            val tolerance = 150
            if (diff <= tolerance) {
                mask[index] = true
                if (x > 0) push(queue, visited, index - 1, tail).also { tail = it }
                if (x < w - 1) push(queue, visited, index + 1, tail).also { tail = it }
                if (y > 0) push(queue, visited, index - w, tail).also { tail = it }
                if (y < h - 1) push(queue, visited, index + w, tail).also { tail = it }
            }
        }
        return mask
    }

    private fun push(queue: IntArray, visited: BooleanArray, next: Int, tail: Int): Int {
        if (visited[next]) return tail
        visited[next] = true
        queue[tail] = next
        return tail + 1
    }

    private fun closeMask(mask: BooleanArray, w: Int, h: Int): BooleanArray {
        val dilated = BooleanArray(mask.size)
        for (y in 0 until h) {
            for (x in 0 until w) {
                if (!mask[y * w + x]) continue
                for (dy in -2..2) {
                    val yy = y + dy
                    if (yy !in 0 until h) continue
                    for (dx in -2..2) {
                        val xx = x + dx
                        if (xx !in 0 until w) continue
                        dilated[yy * w + xx] = true
                    }
                }
            }
        }
        val eroded = BooleanArray(mask.size)
        for (y in 2 until h - 2) {
            for (x in 2 until w - 2) {
                if (dilated[y * w + x]) {
                    var all = true
                    for (dy in -1..1) {
                        for (dx in -1..1) {
                            if (!dilated[(y + dy) * w + (x + dx)]) {
                                all = false
                                break
                            }
                        }
                        if (!all) break
                    }
                    eroded[y * w + x] = all
                }
            }
        }
        return eroded
    }

    private fun quadFromMask(mask: BooleanArray, w: Int, h: Int, srcW: Int, srcH: Int): Quad? {
        val leftPts = ArrayList<PointF>()
        val rightPts = ArrayList<PointF>()
        val topPts = ArrayList<PointF>()
        val bottomPts = ArrayList<PointF>()
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
                if (hi - lo > w * 0.3f) {
                    leftPts.add(PointF(lo.toFloat(), y.toFloat()))
                    rightPts.add(PointF(hi.toFloat(), y.toFloat()))
                }
            }
        }
        if (minY < 0 || minX < 0) return null
        for (x in minX..maxX) {
            var top = -1
            var bottom = -1
            for (y in minY..maxY) {
                if (mask[y * w + x]) {
                    if (top < 0) top = y
                    bottom = y
                }
            }
            if (top >= 0 && bottom - top > h * 0.3f) {
                topPts.add(PointF(x.toFloat(), top.toFloat()))
                bottomPts.add(PointF(x.toFloat(), bottom.toFloat()))
            }
        }
        if (leftPts.size < 5 || topPts.size < 5) return null

        val left = fitVertical(leftPts) ?: return null
        val right = fitVertical(rightPts) ?: return null
        val top = fitHorizontal(topPts) ?: return null
        val bottom = fitHorizontal(bottomPts) ?: return null

        val tl0 = intersect(left, top) ?: return null
        val tr0 = intersect(right, top) ?: return null
        val br0 = intersect(right, bottom) ?: return null
        val bl0 = intersect(left, bottom) ?: return null

        // مقیاس از فضای کاری به تصویر اصلی
        val sx = srcW.toFloat() / w
        val sy = srcH.toFloat() / h
        val pts = listOf(
            PointF(tl0.x * sx, tl0.y * sy),
            PointF(tr0.x * sx, tr0.y * sy),
            PointF(br0.x * sx, br0.y * sy),
            PointF(bl0.x * sx, bl0.y * sy)
        ).map { p ->
            PointF(
                p.x.coerceIn(0f, srcW - 1f),
                p.y.coerceIn(0f, srcH - 1f)
            )
        }
        return orderQuad(pts)
    }

    /** مرتب‌سازی گوشه‌ها: شروع از بالا-چپ و چرخش در جهت ساعت‌گرد. */
    private fun orderQuad(points: List<PointF>): Quad? {
        if (points.size != 4) return null
        val cx = points.sumOf { it.x.toDouble() }.toFloat() / 4f
        val cy = points.sumOf { it.y.toDouble() }.toFloat() / 4f
        val sorted = points.sortedBy { kotlin.math.atan2(it.y - cy, it.x - cx) }
        var startIndex = 0
        var best = Float.MAX_VALUE
        sorted.forEachIndexed { i, p ->
            val s = p.x + p.y
            if (s < best) {
                best = s
                startIndex = i
            }
        }
        val rotated = List(4) { sorted[(startIndex + it) % 4] }
        // اطمینان از جهت ساعت‌گرد
        val area = (rotated[0].x * rotated[1].y - rotated[1].x * rotated[0].y) +
            (rotated[1].x * rotated[2].y - rotated[2].x * rotated[1].y) +
            (rotated[2].x * rotated[3].y - rotated[3].x * rotated[2].y) +
            (rotated[3].x * rotated[0].y - rotated[0].x * rotated[3].y)
        val ordered = if (area < 0f) listOf(rotated[0], rotated[3], rotated[2], rotated[1]) else rotated
        return Quad(ordered[0], ordered[1], ordered[2], ordered[3])
    }

    private data class Line(val a: Float, val b: Float)

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
        return Line(a, b)
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
        return Line(a, b)
    }

    /** خط عمودی: x = a*y + b ، خط افقی: y = a*x + b */
    private fun intersect(vertical: Line, horizontal: Line): PointF? {
        val den = 1f - vertical.a * horizontal.a
        if (abs(den) < 1e-4f) return null
        val x = (vertical.a * horizontal.b + vertical.b) / den
        val y = horizontal.a * x + horizontal.b
        return PointF(x, y)
    }

    // ---------- یکسان‌سازی روشنایی ----------

    /** برآورد نور پس‌زمینه و تقسیم پیکسل‌ها بر آن؛ سایه و نور موضعی اصلاح می‌شود. */
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

        val lum = FloatArray(cols * rows) { i ->
            val c = smallPx[i]
            (0.299f * ((c shr 16) and 0xFF) + 0.587f * ((c shr 8) and 0xFF) + 0.114f * (c and 0xFF))
        }
        // میانگین‌گیری چند پاس برای برآورد نور زمینه
        var estimate = lum
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
                val top = estimate[y0 * cols + x0] * (1 - tx) + estimate[y0 * cols + x1] * tx
                val bottom = estimate[y1 * cols + x0] * (1 - tx) + estimate[y1 * cols + x1] * tx
                val bg = top * (1 - ty) + bottom * ty

                val k = (median / bg.coerceAtLeast(20f)).coerceIn(0.55f, 1.9f)
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
