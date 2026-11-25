package ir.scanfa.app.scan

import android.content.Context
import android.graphics.Bitmap
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * شبکهٔ سفارشی پیش‌بینی میدان جابه‌جایی برای صاف‌کردن باقی‌ماندهٔ خمیدگی.
 * flow در رزولوشن کوچک محاسبه و با ضریب k روی تصویر کامل اعمال می‌شود.
 */
object FlowDewarper {

    private const val ASSET = "models/flow.onnx"
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
            android.util.Log.e("FlowDewarper", "load failed: " + t.message)
            null
        }
    }

    fun dewarp(context: Context, src: Bitmap, k: Float = 0.3f): Bitmap? {
        val current = load(context) ?: return null
        if (!OpenCvScanner.isReady()) return null
        return try {
            val fw = src.width
            val fh = src.height
            val wMat = Mat()
            Utils.bitmapToMat(src, wMat)
            val rgb = Mat()
            Imgproc.cvtColor(wMat, rgb, Imgproc.COLOR_RGBA2RGB)
            wMat.release()

            val scale = (768.0 / max(fw, fh)).coerceAtMost(1.0)
            val sw = max(128, ((fw * scale) / 32).roundToInt() * 32)
            val sh = max(128, ((fh * scale) / 32).roundToInt() * 32)
            val small = Mat()
            Imgproc.resize(rgb, small, Size(sw.toDouble(), sh.toDouble()))

            val plane = sw * sh
            val bytes = ByteArray(plane * 3)
            small.get(0, 0, bytes)
            val data = FloatArray(plane * 3)
            for (i in 0 until plane) {
                data[i] = (bytes[i * 3].toInt() and 0xFF) / 255f
                data[plane + i] = (bytes[i * 3 + 1].toInt() and 0xFF) / 255f
                data[2 * plane + i] = (bytes[i * 3 + 2].toInt() and 0xFF) / 255f
            }
            small.release()

            val environment = env ?: return null
            val tensor = OnnxTensor.createTensor(
                environment,
                FloatBuffer.wrap(data),
                longArrayOf(1, 3, sh.toLong(), sw.toLong())
            )
            val result = current.run(mapOf("input" to tensor))
            val flowTensor = result[0] as OnnxTensor
            val flowBuf = flowTensor.floatBuffer
            val flow = FloatArray(plane * 2)
            flowBuf.get(flow)
            tensor.close()
            result.close()

            // ساخت نقشهٔ کامل با بزرگ‌نمایی میدان
            val mapX = Mat(fh, fw, CvType.CV_32FC1)
            val mapY = Mat(fh, fw, CvType.CV_32FC1)
            val mx = FloatArray(fw * fh)
            val my = FloatArray(fw * fh)
            val kx = k * fw.toFloat() / sw
            val ky = k * fh.toFloat() / sh
            for (y in 0 until fh) {
                val sy = (y.toFloat() / fh * sh).toInt().coerceIn(0, sh - 1)
                for (x in 0 until fw) {
                    val sx = (x.toFloat() / fw * sw).toInt().coerceIn(0, sw - 1)
                    val si = sy * sw + sx
                    mx[y * fw + x] = x + flow[si] * kx
                    my[y * fw + x] = y + flow[plane + si] * ky
                }
            }
            mapX.put(0, 0, mx)
            mapY.put(0, 0, my)

            val out = Mat()
            Imgproc.remap(rgb, out, mapX, mapY, Imgproc.INTER_CUBIC, org.opencv.core.Core.BORDER_REPLICATE)
            mapX.release()
            mapY.release()
            rgb.release()

            val rgba = Mat()
            Imgproc.cvtColor(out, rgba, Imgproc.COLOR_RGB2RGBA)
            out.release()
            val bitmap = Bitmap.createBitmap(fw, fh, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(rgba, bitmap)
            rgba.release()
            bitmap
        } catch (t: Throwable) {
            android.util.Log.e("FlowDewarper", "dewarp failed: " + t.message)
            null
        }
    }
}
