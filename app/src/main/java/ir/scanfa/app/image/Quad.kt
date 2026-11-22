package ir.scanfa.app.image

import android.graphics.PointF

/** چهار گوشهٔ سند روی تصویر؛ همیشه به ترتیب بالا-چپ، بالا-راست، پایین-راست، پایین-چپ. */
data class Quad(val tl: PointF, val tr: PointF, val br: PointF, val bl: PointF) {

    fun points(): List<PointF> = listOf(tl, tr, br, bl)

    fun toNormalized(w: Int, h: Int): List<Float> = listOf(
        (tl.x / w).coerceIn(0f, 1f), (tl.y / h).coerceIn(0f, 1f),
        (tr.x / w).coerceIn(0f, 1f), (tr.y / h).coerceIn(0f, 1f),
        (br.x / w).coerceIn(0f, 1f), (br.y / h).coerceIn(0f, 1f),
        (bl.x / w).coerceIn(0f, 1f), (bl.y / h).coerceIn(0f, 1f)
    )

    fun area(): Float {
        val p = points()
        var sum = 0f
        for (i in 0 until 4) {
            val a = p[i]
            val b = p[(i + 1) % 4]
            sum += a.x * b.y - b.x * a.y
        }
        return kotlin.math.abs(sum) / 2f
    }

    companion object {
        fun full(w: Float, h: Float): Quad = Quad(
            PointF(0f, 0f), PointF(w, 0f), PointF(w, h), PointF(0f, h)
        )

        fun inset(w: Int, h: Int, frac: Float = 0.03f): Quad = Quad(
            PointF(w * frac, h * frac),
            PointF(w * (1f - frac), h * frac),
            PointF(w * (1f - frac), h * (1f - frac)),
            PointF(w * frac, h * (1f - frac))
        )

        fun fromNormalized(v: List<Float>, w: Int, h: Int): Quad = Quad(
            PointF(v[0] * w, v[1] * h),
            PointF(v[2] * w, v[3] * h),
            PointF(v[4] * w, v[5] * h),
            PointF(v[6] * w, v[7] * h)
        )
    }
}
