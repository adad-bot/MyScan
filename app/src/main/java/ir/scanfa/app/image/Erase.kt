package ir.scanfa.app.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

/** پاک‌کردن هوشمند: نقاط لمس‌شده روی تصویر با رنگ سفید پوشانده می‌شوند. */
object Erase {

    /** @param strokes سه‌تایی‌های (x, y, radius) نرمال‌شده بین ۰ و ۱. */
    fun apply(src: Bitmap, strokes: List<Float>, color: Int = Color.WHITE): Bitmap {
        if (strokes.size < 3) return src
        val mutable = src.copy(Bitmap.Config.ARGB_8888, true) ?: return src
        val canvas = Canvas(mutable)
        val paint = Paint().apply {
            this.color = color
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        var i = 0
        while (i + 2 < strokes.size) {
            val x = strokes[i] * mutable.width
            val y = strokes[i + 1] * mutable.height
            val radius = (strokes[i + 2] * mutable.width).coerceIn(2f, mutable.width / 4f)
            canvas.drawCircle(x, y, radius, paint)
            i += 3
        }
        if (mutable !== src) src.recycle()
        return mutable
    }
}
