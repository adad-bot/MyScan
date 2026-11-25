package ir.scanfa.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ir.scanfa.app.scan.DocumentScanEngine
import ir.scanfa.app.scan.UvDocDewarper
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
        ir.scanfa.app.AppContext.ctx = context
        val outDir = File(context.getExternalFilesDir(null), "scan_test").apply { mkdirs() }

        val names = listOf("one.png", "two.png", "three.jpg")
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

            // همان مسیر خودِ برنامه: برش + فیلتر + بهبود
            val cropped = DocumentScanEngine.crop(src, detection.quad, 2200)
            val filtered = ir.scanfa.app.image.Filters.apply(cropped, ir.scanfa.app.data.FilterKind.ENHANCE)
            val appResult = DocumentScanEngine.enhance(
                filtered,
                DocumentScanEngine.Config(maxSide = 2200)
            )
            save(appResult, File(outDir, "${name}_app.png"))
            appResult.recycle()
            if (filtered !== cropped) cropped.recycle()
            filtered.recycle()
            src.recycle()
            processed++
            android.util.Log.i(
                "ScanEngineTest",
                "$name quad=${detection.quad} conf=${detection.confidence}"
            )
        }
        assertTrue("هیچ نمونه‌ای پردازش نشد", processed > 0)
    }

    @Test
    fun dewarpSample() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        ir.scanfa.app.AppContext.ctx = context
        val outDir = File(context.getExternalFilesDir(null), "scan_test").apply { mkdirs() }
        val src = instrumentation.context.assets.open("scan_test/three.jpg").use { stream ->
            BitmapFactory.decodeStream(stream)
        }
        val dewarped = UvDocDewarper.dewarp(context, src) ?: error("UVDoc model failed")
        save(dewarped, File(outDir, "three_uvdoc.png"))
        val cropped = ir.scanfa.app.scan.OpenCvScanner.cropToPaper(dewarped) ?: dewarped
        val enhanced = DocumentScanEngine.enhance(cropped)
        save(enhanced, File(outDir, "three_uvdoc_final.png"))
        enhanced.recycle()
        dewarped.recycle()
        if (cropped !== dewarped) cropped.recycle()
        src.recycle()
    }

    private fun save(bitmap: Bitmap, file: File) {
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }
}
