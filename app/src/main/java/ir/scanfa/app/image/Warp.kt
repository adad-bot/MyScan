package ir.scanfa.app.image

import android.graphics.Bitmap
import android.graphics.PointF
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** اصلاح پرسپکتیو: نگاشت چهار گوشهٔ سند به یک مستطیل صاف. */
object Warp {

    fun warp(src: Bitmap, quad: Quad, maxSide: Int = 2400, bg: Int = 0xFFFFFFFF.toInt()): Bitmap {
        val top = hypot((quad.tr.x - quad.tl.x).toDouble(), (quad.tr.y - quad.tl.y).toDouble())
        val bottom = hypot((quad.br.x - quad.bl.x).toDouble(), (quad.br.y - quad.bl.y).toDouble())
        val left = hypot((quad.bl.x - quad.tl.x).toDouble(), (quad.bl.y - quad.tl.y).toDouble())
        val right = hypot((quad.br.x - quad.tr.x).toDouble(), (quad.br.y - quad.tr.y).toDouble())

        var outW = ((top + bottom) / 2.0).toInt().coerceAtLeast(32)
        var outH = ((left + right) / 2.0).toInt().coerceAtLeast(32)
        val longEdge = max(outW, outH)
        if (longEdge > maxSide) {
            val s = maxSide.toFloat() / longEdge
            outW = (outW * s).toInt().coerceAtLeast(32)
            outH = (outH * s).toInt().coerceAtLeast(32)
        }

        val rect = listOf(
            PointF(0f, 0f),
            PointF(outW.toFloat(), 0f),
            PointF(outW.toFloat(), outH.toFloat()),
            PointF(0f, outH.toFloat())
        )
        val h = homography(rect, quad.points()) ?: return copyOf(src)
        if (!h.all { it.isFinite() }) return copyOf(src)

        val srcPixels = IntArray(src.width * src.height)
        src.getPixels(srcPixels, 0, src.width, 0, 0, src.width, src.height)
        val out = IntArray(outW * outH)

        for (y in 0 until outH) {
            val dy = y.toDouble()
            for (x in 0 until outW) {
                val den = h[6] * x + h[7] * dy + h[8]
                if (abs(den) < 1e-9) {
                    out[y * outW + x] = bg
                    continue
                }
                val u = (h[0] * x + h[1] * dy + h[2]) / den
                val v = (h[3] * x + h[4] * dy + h[5]) / den
                out[y * outW + x] = sample(srcPixels, src.width, src.height, u, v, bg)
            }
        }
        return Bitmap.createBitmap(out, outW, outH, Bitmap.Config.ARGB_8888)
    }

    private fun copyOf(src: Bitmap): Bitmap = src.copy(Bitmap.Config.ARGB_8888, false)

    private fun sample(px: IntArray, w: Int, h: Int, x: Double, y: Double, bg: Int): Int {
        if (x.isNaN() || y.isNaN()) return bg
        val cx = x.coerceIn(0.0, (w - 1.001).toDouble())
        val cy = y.coerceIn(0.0, (h - 1.001).toDouble())
        val x0 = floor(cx).toInt()
        val y0 = floor(cy).toInt()
        val fx = (cx - x0).toFloat()
        val fy = (cy - y0).toFloat()
        val x1 = min(x0 + 1, w - 1)
        val y1 = min(y0 + 1, h - 1)

        val c00 = px[y0 * w + x0]
        val c10 = px[y0 * w + x1]
        val c01 = px[y1 * w + x0]
        val c11 = px[y1 * w + x1]

        fun blend(a: Int, b: Int, t: Float, shift: Int): Int {
            val va = (a shr shift) and 0xFF
            val vb = (b shr shift) and 0xFF
            return (va + (vb - va) * t).toInt()
        }

        val r0 = blend(c00, c10, fx, 16).toFloat()
        val g0 = blend(c00, c10, fx, 8).toFloat()
        val b0 = blend(c00, c10, fx, 0).toFloat()
        val r1 = blend(c01, c11, fx, 16).toFloat()
        val g1 = blend(c01, c11, fx, 8).toFloat()
        val b1 = blend(c01, c11, fx, 0).toFloat()

        val r = (r0 + (r1 - r0) * fy).toInt().coerceIn(0, 255)
        val g = (g0 + (g1 - g0) * fy).toInt().coerceIn(0, 255)
        val b = (b0 + (b1 - b0) * fy).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    /** نگاشت هشت‌پارامتری از چهار نقطه به چهار نقطه؛ h8 = ۱. */
    private fun homography(from: List<PointF>, to: List<PointF>): DoubleArray? {
        if (from.size != 4 || to.size != 4) return null
        val a = Array(8) { DoubleArray(9) }
        for (i in 0 until 4) {
            val x = from[i].x.toDouble()
            val y = from[i].y.toDouble()
            val u = to[i].x.toDouble()
            val v = to[i].y.toDouble()
            val r1 = i * 2
            val r2 = r1 + 1
            a[r1][0] = x
            a[r1][1] = y
            a[r1][2] = 1.0
            a[r1][6] = -x * u
            a[r1][7] = -y * u
            a[r1][8] = u

            a[r2][3] = x
            a[r2][4] = y
            a[r2][5] = 1.0
            a[r2][6] = -x * v
            a[r2][7] = -y * v
            a[r2][8] = v
        }

        for (col in 0 until 8) {
            var pivot = col
            for (r in col until 8) {
                if (abs(a[r][col]) > abs(a[pivot][col])) pivot = r
            }
            if (abs(a[pivot][col]) < 1e-9) return null
            val tmp = a[col]
            a[col] = a[pivot]
            a[pivot] = tmp

            val d = a[col][col]
            for (c in col until 9) a[col][c] /= d
            for (r in 0 until 8) {
                if (r == col) continue
                val f = a[r][col]
                if (abs(f) > 1e-12) {
                    for (c in col until 9) a[r][c] -= f * a[col][c]
                }
            }
        }

        val h = DoubleArray(9)
        for (i in 0 until 8) h[i] = a[i][8]
        h[8] = 1.0
        return h
    }
}
