package ir.scanfa.app.image

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * لبه‌یابی Canny: خاکستری، بلور گاوسی، گرادیان سوبل،
 * سرکوب غیربیشینه و هیسترزیس.
 */
object EdgeDetector {

    data class Edges(val mask: BooleanArray, val width: Int, val height: Int)

    fun canny(src: Bitmap, workMax: Int = 640): Edges {
        val scale = workMax.toFloat() / max(src.width, src.height)
        val w = max(64, (src.width * scale).toInt())
        val h = max(64, (src.height * scale).toInt())
        val small = Bitmap.createScaledBitmap(src, w, h, true)
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        if (small !== src) small.recycle()

        val gray = FloatArray(w * h) { i ->
            val c = px[i]
            0.299f * ((c shr 16) and 0xFF) + 0.587f * ((c shr 8) and 0xFF) + 0.114f * (c and 0xFF)
        }
        val smooth = gaussian(gray, w, h)

        val mag = FloatArray(w * h)
        val dir = FloatArray(w * h)
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val i = y * w + x
                val gx = (smooth[i - w + 1] + 2 * smooth[i + 1] + smooth[i + w + 1]) -
                    (smooth[i - w - 1] + 2 * smooth[i - 1] + smooth[i + w - 1])
                val gy = (smooth[i + w - 1] + 2 * smooth[i + w] + smooth[i + w + 1]) -
                    (smooth[i - w - 1] + 2 * smooth[i - w] + smooth[i - w + 1])
                mag[i] = hypot(gx.toDouble(), gy.toDouble()).toFloat()
                dir[i] = atan2(gy.toDouble(), gx.toDouble()).toFloat()
            }
        }

        // سرکوب غیربیشینه
        val thin = FloatArray(w * h)
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val i = y * w + x
                val m = mag[i]
                if (m <= 0f) continue
                val angle = (dir[i] * 180f / Math.PI.toFloat() + 180f) % 180f
                val (dx1, dy1, dx2, dy2) = when {
                    angle < 22.5f || angle >= 157.5f -> intArrayOf(1, 0, -1, 0)
                    angle < 67.5f -> intArrayOf(1, 1, -1, -1)
                    angle < 112.5f -> intArrayOf(0, 1, 0, -1)
                    else -> intArrayOf(-1, 1, 1, -1)
                }
                val a = mag[(y + dy1) * w + (x + dx1)]
                val b = mag[(y + dy2) * w + (x + dx2)]
                if (m >= a && m >= b) thin[i] = m
            }
        }

        // آستانه‌ها بر اساس صدک
        val sorted = thin.filter { it > 0f }.sorted()
        val high = if (sorted.isEmpty()) 0f else sorted[(sorted.size * 0.93f).toInt().coerceIn(0, sorted.size - 1)]
        val low = high * 0.45f
        val strong = BooleanArray(w * h) { thin[it] >= high && high > 0f }
        val weak = BooleanArray(w * h) { thin[it] >= low }

        // هیسترزیس
        val out = BooleanArray(w * h)
        val stack = IntArray(w * h)
        var top = 0
        for (i in strong.indices) if (strong[i]) {
            out[i] = true
            stack[top++] = i
        }
        while (top > 0) {
            val index = stack[--top]
            val x = index % w
            val y = index / w
            for (dy in -1..1) {
                for (dx in -1..1) {
                    val xx = x + dx
                    val yy = y + dy
                    if (xx !in 0 until w || yy !in 0 until h) continue
                    val n = yy * w + xx
                    if (!out[n] && weak[n]) {
                        out[n] = true
                        stack[top++] = n
                    }
                }
            }
        }
        return Edges(out, w, h)
    }

    private fun gaussian(src: FloatArray, w: Int, h: Int): FloatArray {
        val kernel = floatArrayOf(1f, 4f, 6f, 4f, 1f)
        val sum = 16f
        val tmp = FloatArray(src.size)
        for (y in 0 until h) {
            for (x in 0 until w) {
                var acc = 0f
                for (k in -2..2) {
                    val xx = (x + k).coerceIn(0, w - 1)
                    acc += src[y * w + xx] * kernel[k + 2]
                }
                tmp[y * w + x] = acc / sum
            }
        }
        val out = FloatArray(src.size)
        for (y in 0 until h) {
            for (x in 0 until w) {
                var acc = 0f
                for (k in -2..2) {
                    val yy = (y + k).coerceIn(0, h - 1)
                    acc += tmp[yy * w + x] * kernel[k + 2]
                }
                out[y * w + x] = acc / sum
            }
        }
        return out
    }
}

/**
 * تشخیص خط با تبدیل هاف و ساخت بزرگ‌ترین چهارضلعی از خطوط غالب.
 */
object HoughQuad {

    data class Line(val theta: Int, val rho: Int)

    fun findQuad(edges: EdgeDetector.Edges, srcW: Int, srcH: Int): Quad? {
        val w = edges.width
        val h = edges.height
        if (w < 32 || h < 32) return null

        val diag = hypot(w.toDouble(), h.toDouble()).toInt() + 2
        val rhoCount = diag * 2 + 1
        val thetaCount = 180
        val acc = IntArray(rhoCount * thetaCount)

        val cosT = FloatArray(thetaCount) { kotlin.math.cos(it * Math.PI / 180.0).toFloat() }
        val sinT = FloatArray(thetaCount) { kotlin.math.sin(it * Math.PI / 180.0).toFloat() }

        for (y in 0 until h) {
            for (x in 0 until w) {
                if (!edges.mask[y * w + x]) continue
                for (t in 0 until thetaCount) {
                    val rho = (x * cosT[t] + y * sinT[t]).roundToInt()
                    acc[(rho + diag) * thetaCount + t]++
                }
            }
        }

        // انتخاب قله‌های خط
        val peaks = ArrayList<Line>()
        val minVotes = max(18, (w + h) / 6)
        repeat(14) {
            var best = 0
            var bestRho = 0
            var bestTheta = 0
            for (r in 0 until rhoCount) {
                for (t in 0 until thetaCount) {
                    val v = acc[r * thetaCount + t]
                    if (v > best) {
                        best = v
                        bestRho = r - diag
                        bestTheta = t
                    }
                }
            }
            if (best < minVotes) return@repeat
            peaks.add(Line(bestTheta, bestRho))
            // پاک‌کردن همسایگی
            for (dr in -14..14) {
                for (dt in -6..6) {
                    val rr = (bestRho + diag + dr)
                    val tt = (bestTheta + dt + thetaCount) % thetaCount
                    if (rr in 0 until rhoCount) acc[rr * thetaCount + tt] = 0
                }
            }
            // معادل‌های زاویهٔ مکمل
            val mirrored = (bestTheta + 180) % thetaCount
            for (dr in -14..14) {
                val rr = (bestRho + diag + dr)
                if (rr in 0 until rhoCount) acc[rr * thetaCount + mirrored] = 0
            }
        }
        if (peaks.size < 4) return null

        val verticals = peaks.filter { it.theta in 0..35 || it.theta in 145..179 }
        val horizontals = peaks.filter { it.theta in 55..125 }
        if (horizontals.size < 2 || verticals.size < 2) return null

        var bestQuad: Quad? = null
        var bestArea = 0f
        for (hi in horizontals.indices) {
            for (hj in hi + 1 until horizontals.size) {
                for (vi in verticals.indices) {
                    for (vj in vi + 1 until verticals.size) {
                        val corners = ArrayList<android.graphics.PointF>()
                        val h1 = horizontals[hi]
                        val h2 = horizontals[hj]
                        val v1 = verticals[vi]
                        val v2 = verticals[vj]
                        val p1 = intersect(h1, v1)
                        val p2 = intersect(h1, v2)
                        val p3 = intersect(h2, v1)
                        val p4 = intersect(h2, v2)
                        if (p1 == null || p2 == null || p3 == null || p4 == null) continue
                        val quad = order(
                            listOf(
                                android.graphics.PointF(p1.first, p1.second),
                                android.graphics.PointF(p2.first, p2.second),
                                android.graphics.PointF(p3.first, p3.second),
                                android.graphics.PointF(p4.first, p4.second)
                            )
                        ) ?: continue
                        val area = quad.area()
                        val ratio = area / (w.toFloat() * h)
                        if (ratio < 0.2f || ratio > 0.99f) continue
                        if (area > bestArea) {
                            bestArea = area
                            bestQuad = quad
                        }
                    }
                }
            }
        }
        val quad = bestQuad ?: return null

        val sx = srcW.toFloat() / w
        val sy = srcH.toFloat() / h
        val scaled = Quad(
            android.graphics.PointF(quad.tl.x * sx, quad.tl.y * sy),
            android.graphics.PointF(quad.tr.x * sx, quad.tr.y * sy),
            android.graphics.PointF(quad.br.x * sx, quad.br.y * sy),
            android.graphics.PointF(quad.bl.x * sx, quad.bl.y * sy)
        )
        return scaled
    }

    private fun intersect(h: Line, v: Line): Pair<Float, Float>? {
        val t1 = h.theta * Math.PI / 180.0
        val t2 = v.theta * Math.PI / 180.0
        val a1 = kotlin.math.cos(t1)
        val b1 = kotlin.math.sin(t1)
        val a2 = kotlin.math.cos(t2)
        val b2 = kotlin.math.sin(t2)
        val det = a1 * b2 - a2 * b1
        if (abs(det) < 1e-6) return null
        val x = ((h.rho * b2 - v.rho * b1) / det).toFloat()
        val y = ((a1 * v.rho - a2 * h.rho) / det).toFloat()
        if (!x.isFinite() || !y.isFinite()) return null
        return x to y
    }

    private fun order(points: List<android.graphics.PointF>): Quad? {
        if (points.size != 4) return null
        val cx = points.sumOf { it.x.toDouble() }.toFloat() / 4f
        val cy = points.sumOf { it.y.toDouble() }.toFloat() / 4f
        val sorted = points.sortedBy { atan2((it.y - cy).toDouble(), (it.x - cx).toDouble()) }
        var idx = 0
        var best = Float.MAX_VALUE
        sorted.forEachIndexed { i, p -> if (p.x + p.y < best) { best = p.x + p.y; idx = i } }
        val rot = List(4) { sorted[(idx + it) % 4] }
        val area2 = (rot[0].x * rot[1].y - rot[1].x * rot[0].y) +
            (rot[1].x * rot[2].y - rot[2].x * rot[1].y) +
            (rot[2].x * rot[3].y - rot[3].x * rot[2].y) +
            (rot[3].x * rot[0].y - rot[0].x * rot[3].y)
        val ordered = if (area2 < 0f) listOf(rot[0], rot[3], rot[2], rot[1]) else rot
        return Quad(ordered[0], ordered[1], ordered[2], ordered[3])
    }
}
