package ir.scanfa.app.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.graphics.Typeface

/** صفحهٔ یادداشت را به یک تصویر سفید A4 با متن راست‌به‌چپ تبدیل می‌کند. */
object NoteRenderer {

    fun render(text: String, width: Int = 1240, height: Int = 1754): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val paint = TextPaint().apply {
            color = Color.rgb(24, 28, 25)
            textSize = width * 0.034f
            isAntiAlias = true
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }

        val margin = width * 0.075f
        val layoutWidth = (width - margin * 2).toInt().coerceAtLeast(100)
        val layout = StaticLayout.Builder
            .obtain(text, 0, text.length, paint, layoutWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setTextDirection(TextDirectionHeuristics.RTL)
            .setLineSpacing(0f, 1.45f)
            .setIncludePad(true)
            .build()

        canvas.save()
        canvas.translate(margin, margin * 0.9f)
        layout.draw(canvas)
        canvas.restore()
        return bitmap
    }
}
