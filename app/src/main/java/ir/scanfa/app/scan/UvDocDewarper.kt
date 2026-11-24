package ir.scanfa.app.scan

import android.content.Context
import android.graphics.Bitmap
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * صاف‌کردن خمیدگی سند با مدل هوش مصنوعی UVDoc (ONNX، کاملاً آفلاین).
 * ورودی RGB نرمال‌شده و خروجی تصویر رکتوفای‌شده است.
 */
object UvDocDewarper {

    private const val MODEL_ASSET = "models/uvdoc.onnx"
    private const val INPUT_NAME = "image"

    private var session: OrtSession? = null
    private var env: OrtEnvironment? = null
    private var loading = false

    fun available(context: Context): Boolean = load(context) != null

    @Synchronized
    private fun load(context: Context): OrtSession? {
        session?.let { return it }
        if (loading) return null
        loading = true
        return try {
            val environment = OrtEnvironment.getEnvironment()
            val bytes = context.assets.open(MODEL_ASSET).use { it.readBytes() }
            val options = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(2)
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            }
            val created = environment.createSession(bytes, options)
            env = environment
            session = created
            created
        } catch (t: Throwable) {
            null
        } finally {
            loading = false
        }
    }

    /** تصویر را صاف می‌کند؛ در صورت خطا null برمی‌گرداند. */
    fun dewarp(context: Context, src: Bitmap, maxSide: Int = 1024): Bitmap? {
        val current = load(context) ?: return null
        return try {
            var scale = 1.0f
            val longSide = max(src.width, src.height)
            if (longSide > maxSide) scale = maxSide.toFloat() / longSide
            var w = ((src.width * scale) / 32f).roundToInt() * 32
            var h = ((src.height * scale) / 32f).roundToInt() * 32
            w = w.coerceIn(128, maxSide)
            h = h.coerceIn(128, maxSide)

            val resized = Bitmap.createScaledBitmap(src, w, h, true)
            val pixels = IntArray(w * h)
            resized.getPixels(pixels, 0, w, 0, 0, w, h)
            if (resized !== src) resized.recycle()

            val plane = w * h
            val data = FloatArray(3 * plane)
            val mean = floatArrayOf(0.485f, 0.456f, 0.406f)
            val std = floatArrayOf(0.229f, 0.224f, 0.225f)
            for (i in pixels.indices) {
                val c = pixels[i]
                val r = ((c shr 16) and 0xFF) / 255f
                val g = ((c shr 8) and 0xFF) / 255f
                val b = (c and 0xFF) / 255f
                data[i] = (r - mean[0]) / std[0]
                data[plane + i] = (g - mean[1]) / std[1]
                data[2 * plane + i] = (b - mean[2]) / std[2]
            }

            val environment = env ?: return null
            val tensor = OnnxTensor.createTensor(
                environment,
                FloatBuffer.wrap(data),
                longArrayOf(1, 3, h.toLong(), w.toLong())
            )
            val output = current.run(mapOf(INPUT_NAME to tensor))
            val resultTensor = output[0] as OnnxTensor
            val buffer = resultTensor.floatBuffer
            val outPlane = w * h
            val outPixels = IntArray(outPlane)
            val rgb = FloatArray(3 * outPlane)
            buffer.get(rgb)
            for (i in 0 until outPlane) {
                val r = (rgb[i].coerceIn(0f, 1f) * 255f).roundToInt()
                val g = (rgb[outPlane + i].coerceIn(0f, 1f) * 255f).roundToInt()
                val b = (rgb[2 * outPlane + i].coerceIn(0f, 1f) * 255f).roundToInt()
                outPixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
            tensor.close()
            output.close()

            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(outPixels, 0, w, 0, 0, w, h)
            bitmap
        } catch (t: Throwable) {
            null
        }
    }
}
