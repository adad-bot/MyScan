package ir.scanfa.app.scan

import android.content.Context
import android.graphics.Bitmap
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * شبکهٔ کوچک بهبود سند (PixelNet): برای هر پیکسل با استفاده از رنگ و پس‌زمینهٔ محو،
 * ضریب تصحیح روشنایی/رنگ را پیش‌بینی می‌کند (حذف سایه و سفیدکردن کاغذ).
 * مدل داخل اپ و کاملاً آفلاین اجرا می‌شود.
 */
object PixelEnhancer {

    private const val ASSET = "models/pixelnet.onnx"
    private var session: OrtSession? = null
    private var env: OrtEnvironment? = null

    fun available(context: Context): Boolean = load(context) != null

    @Synchronized
    private fun load(context: Context): OrtSession? {
        session?.let { return it }
        return try {
            val environment = OrtEnvironment.getEnvironment()
            val bytes = context.assets.open(ASSET).use { it.readBytes() }
            val created = environment.createSession(bytes, OrtSession.SessionOptions())
            env = environment
            session = created
            created
        } catch (t: Throwable) {
            android.util.Log.e("PixelEnhancer", "model load failed: " + t.message)
            null
        }
    }

    fun enhance(context: Context, src: Bitmap): Bitmap? {
        val current = load(context) ?: return null
        if (!OpenCvScanner.isReady()) return null
        return try {
            val fw = src.width
            val fh = src.height
            val longSide = max(fw, fh)
            val scale = (600.0 / longSide).coerceAtMost(1.0)
            val sw = max(64, (fw * scale).toInt())
            val sh = max(64, (fh * scale).toInt())

            val small = Bitmap.createScaledBitmap(src, sw, sh, true)
            val smallPx = IntArray(sw * sh)
            small.getPixels(smallPx, 0, sw, 0, 0, sw, sh)

            // پس‌زمینهٔ محو
            val smallMat = Mat()
            Utils.bitmapToMat(small, smallMat)
            val blurred = Mat()
            val sigma = max(6.0, 0.035 * max(sw, sh))
            Imgproc.GaussianBlur(smallMat, blurred, Size(0.0, 0.0), sigma)
            val bgBitmap = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(blurred, bgBitmap)
            val bgPx = IntArray(sw * sh)
            bgBitmap.getPixels(bgPx, 0, sw, 0, 0, sw, sh)
            smallMat.release()
            blurred.release()
            if (small !== src) small.recycle()
            bgBitmap.recycle()

            val n = sw * sh
            val features = FloatArray(n * 7)
            for (i in 0 until n) {
                val c = smallPx[i]
                val b = bgPx[i]
                val r = ((c shr 16) and 0xFF) / 255f
                val g = ((c shr 8) and 0xFF) / 255f
                val bl = (c and 0xFF) / 255f
                val br = ((b shr 16) and 0xFF) / 255f
                val bgc = ((b shr 8) and 0xFF) / 255f
                val bb = (b and 0xFF) / 255f
                val lum = (r + g + bl) / 3f
                val o = i * 7
                features[o] = r
                features[o + 1] = g
                features[o + 2] = bl
                features[o + 3] = br
                features[o + 4] = bgc
                features[o + 5] = bb
                features[o + 6] = lum
            }

            val environment = env ?: return null
            val tensor = OnnxTensor.createTensor(environment, FloatBuffer.wrap(features), longArrayOf(n.toLong(), 7))
            val result = current.run(mapOf("features" to tensor))
            val gains = (result[0] as OnnxTensor).floatBuffer
            val gainFloats = FloatArray(n * 3)
            gains.get(gainFloats)
            tensor.close()
            result.close()

            val fullPx = IntArray(fw * fh)
            src.getPixels(fullPx, 0, fw, 0, 0, fw, fh)
            val out = IntArray(fullPx.size)
            for (y in 0 until fh) {
                val sy = (y.toFloat() / fh * sh).toInt().coerceIn(0, sh - 1)
                for (x in 0 until fw) {
                    val sx = (x.toFloat() / fw * sw).toInt().coerceIn(0, sw - 1)
                    val si = sy * sw + sx
                    val c = fullPx[y * fw + x]
                    val r = (((c shr 16) and 0xFF) * gainFloats[si * 3]).roundToInt().coerceIn(0, 255)
                    val g = (((c shr 8) and 0xFF) * gainFloats[si * 3 + 1]).roundToInt().coerceIn(0, 255)
                    val b = ((c and 0xFF) * gainFloats[si * 3 + 2]).roundToInt().coerceIn(0, 255)
                    out[y * fw + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }
            }

            val bitmap = Bitmap.createBitmap(fw, fh, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(out, 0, fw, 0, 0, fw, fh)
            bitmap
        } catch (t: Throwable) {
            android.util.Log.e("PixelEnhancer", "enhance failed: " + t.message)
            null
        }
    }
}
