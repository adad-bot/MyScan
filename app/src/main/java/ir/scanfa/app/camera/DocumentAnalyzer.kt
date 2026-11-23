package ir.scanfa.app.camera

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import ir.scanfa.app.image.DocDetector
import ir.scanfa.app.image.Img

/**
 * تحلیل زندهٔ فریم دوربین: قاب سند را پیدا می‌کند و مختصات نرمال‌شده (۰..۱)
 * به‌همراه مساحت نسبی و نسبت تصویر را برمی‌گرداند.
 */
class DocumentAnalyzer(
    private val minIntervalMs: Long = 220L,
    private val onDetected: (quad: List<Float>, area: Float, aspect: Float) -> Unit
) : ImageAnalysis.Analyzer {

    @Volatile
    var enabled: Boolean = true

    private var lastRun = 0L
    private var lastNorm: List<Float>? = null

    override fun analyze(image: ImageProxy) {
        val now = SystemClock.elapsedRealtime()
        if (!enabled || now - lastRun < minIntervalMs) {
            image.close()
            return
        }
        lastRun = now

        var bmp: Bitmap? = null
        var upright: Bitmap? = null
        var small: Bitmap? = null
        try {
            bmp = image.toBitmap()
            val rotation = image.imageInfo.rotationDegrees
            upright = if (bmp != null && rotation != 0) Img.rotate(bmp, rotation) else bmp
            if (upright != null) {
                small = Img.scaleWithin(upright, 460)
                val quad = DocDetector.detect(small)
                val area = (quad.area() / (small.width.toFloat() * small.height)).coerceIn(0f, 1f)
                val normalized = quad.toNormalized(small.width, small.height)
                val previous = lastNorm
                val smoothed = if (previous != null && previous.size == normalized.size) {
                    List(normalized.size) { i -> previous[i] * 0.5f + normalized[i] * 0.5f }
                } else {
                    normalized
                }
                lastNorm = smoothed
                onDetected(smoothed, area, small.width.toFloat() / small.height.toFloat())
            }
        } catch (t: Throwable) {
            // فریم بعدی
        } finally {
            if (small != null && small !== upright) small.recycle()
            if (upright != null && upright !== bmp) upright.recycle()
            bmp?.recycle()
            image.close()
        }
    }
}
