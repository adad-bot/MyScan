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
            val lumArr = FloatArray(n)
            for (i in 0 until n) {
                val c = smallPx[i]
                val r = ((c shr 16) and 0xFF) / 255f
                val g = ((c shr 8) and 0xFF) / 255f
                val b = (c and 0xFF) / 255f
                lumArr[i] = (r + g + b) / 3f
            }
            // انحراف معیار محلی با تصویر انتگرالی (پنجرهٔ ۹×۹)
            val iw = sw + 1
            val sum = DoubleArray(iw * (sh + 1))
            val sum2 = DoubleArray(iw * (sh + 1))
            for (y in 0 until sh) {
                for (x in 0 until sw) {
                    val v = lumArr[y * sw + x].toDouble()
                    sum[(y + 1) * iw + x + 1] = v + sum[y * iw + x + 1] + sum[(y + 1) * iw + x] - sum[y * iw + x]
                    sum2[(y + 1) * iw + x + 1] = v * v + sum2[y * iw + x + 1] + sum2[(y + 1) * iw + x] - sum2[y * iw + x]
                }
            }
            val stdArr = FloatArray(n)
            val r9 = 4
            for (y in 0 until sh) {
                val y0 = (y - r9).coerceAtLeast(0)
                val y1 = (y + r9).coerceAtMost(sh - 1)
                for (x in 0 until sw) {
                    val x0 = (x - r9).coerceAtLeast(0)
                    val x1 = (x + r9).coerceAtMost(sw - 1)
                    val cnt = ((x1 - x0 + 1) * (y1 - y0 + 1)).toDouble()
                    val s1 = sum[(y1 + 1) * iw + x1 + 1] - sum[y0 * iw + x1 + 1] - sum[(y1 + 1) * iw + x0] + sum[y0 * iw + x0]
                    val s2 = sum2[(y1 + 1) * iw + x1 + 1] - sum2[y0 * iw + x1 + 1] - sum2[(y1 + 1) * iw + x0] + sum2[y0 * iw + x0]
                    val mean = s1 / cnt
                    val variance = (s2 / cnt - mean * mean).coerceAtLeast(0.0)
                    stdArr[y * sw + x] = kotlin.math.sqrt(variance).toFloat()
                }
            }

            val features = FloatArray(n * 8)
            for (i in 0 until n) {
                val c = smallPx[i]
                val b = bgPx[i]
                val r = ((c shr 16) and 0xFF) / 255f
                val g = ((c shr 8) and 0xFF) / 255f
                val bl = (c and 0xFF) / 255f
                val br = ((b shr 16) and 0xFF) / 255f
                val bgc = ((b shr 8) and 0xFF) / 255f
                val bb = (b and 0xFF) / 255f
                val o = i * 8
                features[o] = r
                features[o + 1] = g
                features[o + 2] = bl
                features[o + 3] = br
                features[o + 4] = bgc
                features[o + 5] = bb
                features[o + 6] = lumArr[i]
                features[o + 7] = stdArr[i]
            }

            val environment = env ?: return null
            val tensor = OnnxTensor.createTensor(environment, FloatBuffer.wrap(features), longArrayOf(n.toLong(), 8))
            val result = current.run(mapOf("features" to tensor))
            val gains = (result[0] as OnnxTensor).floatBuffer
            val gainFloats = FloatArray(n * 3)
            gains.get(gainFloats)
            tensor.close()
            result.close()

            // کالیبراسیون تُن مطابق مرجع اسکنر: آلفا=۰٫۸ و گاما=۰٫۸۵
            val gamma = 0.85
            val lut = IntArray(256) { i ->
                (255.0 * Math.pow(i / 255.0, gamma)).toInt().coerceIn(0, 255)
            }
            val alpha = 0.8f
            for (i in 0 until n * 3) {
                gainFloats[i] = 1f + alpha * (gainFloats[i] - 1f)
            }

            val fullPx = IntArray(fw * fh)
            src.getPixels(fullPx, 0, fw, 0, 0, fw, fh)
            val out = IntArray(fullPx.size)
            for (y in 0 until fh) {
                val sy = (y.toFloat() / fh * sh).toInt().coerceIn(0, sh - 1)
                for (x in 0 until fw) {
                    val sx = (x.toFloat() / fw * sw).toInt().coerceIn(0, sw - 1)
                    val si = sy * sw + sx
                    val c = fullPx[y * fw + x]
                    val r = lut[(((c shr 16) and 0xFF) * gainFloats[si * 3]).roundToInt().coerceIn(0, 255)]
                    val g = lut[(((c shr 8) and 0xFF) * gainFloats[si * 3 + 1]).roundToInt().coerceIn(0, 255)]
                    val b = lut[((c and 0xFF) * gainFloats[si * 3 + 2]).roundToInt().coerceIn(0, 255)]
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
