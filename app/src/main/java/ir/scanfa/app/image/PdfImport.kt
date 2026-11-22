package ir.scanfa.app.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import kotlin.math.max

/** ورود PDF: هر صفحه به یک تصویر تبدیل می‌شود. */
object PdfImport {

    fun renderPages(ctx: Context, uri: Uri, maxDim: Int = 1800): List<Bitmap> {
        val tmp = File(ctx.cacheDir, "import_${System.currentTimeMillis()}.pdf")
        ctx.contentResolver.openInputStream(uri)?.use { input ->
            tmp.outputStream().use { output -> input.copyTo(output) }
        } ?: return emptyList()

        val result = ArrayList<Bitmap>()
        try {
            ParcelFileDescriptor.open(tmp, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { renderer ->
                    for (i in 0 until renderer.pageCount) {
                        renderer.openPage(i).use { page ->
                            val pw = page.width
                            val ph = page.height
                            val scale = maxDim.toFloat() / max(pw, ph).toFloat()
                            val bw = (pw * scale).toInt().coerceAtLeast(32)
                            val bh = (ph * scale).toInt().coerceAtLeast(32)
                            val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888).apply {
                                eraseColor(Color.WHITE)
                            }
                            val matrix = Matrix().apply { setScale(bw.toFloat() / pw, bh.toFloat() / ph) }
                            page.render(bmp, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            result.add(bmp)
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            // فایل معیوب یا رمزدار: هرچه رندر شده برگردانده می‌شود
        } finally {
            tmp.delete()
        }
        return result
    }
}
