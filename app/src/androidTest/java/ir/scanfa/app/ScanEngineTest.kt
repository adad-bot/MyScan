package ir.scanfa.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ir.scanfa.app.scan.DocumentScanEngine
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/** آزمون واقعی موتور اسکن روی دو عکس نمونه. */
@RunWith(AndroidJUnit4::class)
class ScanEngineTest {

    @Test
    fun processSamples() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val outDir = File(context.getExternalFilesDir(null), "scan_test").apply { mkdirs() }

        val names = listOf("one.png", "two.png")
        var processed = 0
        for (name in names) {
            val src = try {
                instrumentation.context.assets.open("scan_test/" + name).use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            } catch (t: Throwable) {
                null
            } ?: continue

            val detection = DocumentScanEngine.detect(src)
            val overlay = src.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(overlay)
            val paint = Paint().apply {
                color = Color.GREEN
                style = Paint.Style.STROKE
                strokeWidth = 8f
            }
            detection.quad?.let { q ->
                val path = android.graphics.Path().apply {
                    moveTo(q.tl.x, q.tl.y)
                    lineTo(q.tr.x, q.tr.y)
                    lineTo(q.br.x, q.br.y)
                    lineTo(q.bl.x, q.bl.y)
                    close()
                }
                canvas.drawPath(path, paint)
            }
            save(overlay, File(outDir, "${name}_quad.png"))
            overlay.recycle()

            val scanned = DocumentScanEngine.scan(src)
            save(scanned, File(outDir, "${name}_out.png"))
            scanned.recycle()
            src.recycle()
            processed++
            android.util.Log.i(
                "ScanEngineTest",
                "$name quad=${detection.quad} conf=${detection.confidence}"
            )
        }
        assertTrue("هیچ نمونه‌ای پردازش نشد", processed > 0)
    }

    private fun save(bitmap: Bitmap, file: File) {
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }
}
