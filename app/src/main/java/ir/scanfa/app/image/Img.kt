package ir.scanfa.app.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.LruCache
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

/** بارگذاری، چرخش و ذخیرهٔ تصویر با کنترل حافظه. */
object Img {

    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun decode(file: File, maxDim: Int = 2400, respectExif: Boolean = true): Bitmap? {
        if (!file.exists() || file.length() == 0L) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        val longEdge = max(bounds.outWidth, bounds.outHeight)
        while (longEdge / (sample * 2) >= maxDim) sample *= 2

        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return null
        var result = decoded

        if (respectExif) {
            val degrees = exifRotation(file)
            if (degrees != 0) {
                val rotated = rotate(decoded, degrees)
                if (rotated !== decoded) decoded.recycle()
                result = rotated
            }
        }

        val scale = maxDim.toFloat() / max(result.width, result.height)
        if (scale < 1f) {
            val scaled = scaleTo(result, (result.width * scale).toInt().coerceAtLeast(1), (result.height * scale).toInt().coerceAtLeast(1))
            if (scaled !== result) result.recycle()
            result = scaled
        }
        return result
    }

    fun decodeBytes(bytes: ByteArray): Bitmap? =
        try {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (t: Throwable) {
            null
        }

    fun rotate(src: Bitmap, degrees: Int): Bitmap {
        val d = ((degrees % 360) + 360) % 360
        if (d == 0) return src
        val matrix = Matrix().apply { postRotate(d.toFloat()) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
    }

    fun scaleTo(src: Bitmap, w: Int, h: Int): Bitmap =
        if (src.width == w && src.height == h) src else Bitmap.createScaledBitmap(src, w, h, true)

    fun scaleWithin(src: Bitmap, maxDim: Int): Bitmap {
        val longEdge = max(src.width, src.height)
        if (longEdge <= maxDim) return src
        val scale = maxDim.toFloat() / longEdge
        return scaleTo(src, (src.width * scale).toInt().coerceAtLeast(1), (src.height * scale).toInt().coerceAtLeast(1))
    }

    fun saveJpeg(bitmap: Bitmap, file: File, quality: Int = 85): Boolean {
        file.parentFile?.mkdirs()
        return try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(20, 100), out)
            }
            file.length() > 0
        } catch (t: Throwable) {
            false
        }
    }

    fun encodeJpeg(bitmap: Bitmap, quality: Int = 85): ByteArray {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(20, 100), out)
        return out.toByteArray()
    }

    fun thumb(file: File, size: Int = 320): Bitmap? {
        val key = "${file.absolutePath}@${file.lastModified()}#$size"
        cache.get(key)?.let { return it }
        val bmp = decode(file, size, respectExif = true) ?: return null
        cache.put(key, bmp)
        return bmp
    }

    fun clearCache() = cache.evictAll()

    private fun exifRotation(file: File): Int = try {
        val exif = ExifInterface(file.absolutePath)
        when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    } catch (t: Throwable) {
        0
    }
}
