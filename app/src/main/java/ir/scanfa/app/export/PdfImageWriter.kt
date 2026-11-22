package ir.scanfa.app.export

import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.Locale

/**
 * نویسندهٔ سبک PDF فقط برای تصویر (بدون وابستگی به کتابخانهٔ خارجی).
 *
 * هر صفحه یک JPEG خام را با فیلتر DCTDecode در فایل جاسازی می‌کند؛ بنابراین
 * حجم خروجی دقیقاً تابع کیفیت و اندازهٔ همان JPEG است و کنترل سقف حجم ساده می‌شود.
 */
class PdfImageWriter {

    private class ImgPage(val jpeg: ByteArray, val width: Int, val height: Int)

    private val pages = ArrayList<ImgPage>()
    private var paperWidth = 595
    private var paperHeight = 842
    private var autoSize = false

    /** اندازهٔ کاغذ به نقطه؛ اگر auto باشد، ارتفاع از نسبت تصویر محاسبه می‌شود. */
    fun paper(widthPt: Int, heightPt: Int): PdfImageWriter {
        if (widthPt > 0 && heightPt > 0) {
            paperWidth = widthPt
            paperHeight = heightPt
            autoSize = false
        } else {
            autoSize = true
        }
        return this
    }

    fun addImage(jpeg: ByteArray, width: Int, height: Int): PdfImageWriter {
        if (width > 0 && height > 0 && jpeg.isNotEmpty()) {
            pages.add(ImgPage(jpeg, width, height))
        }
        return this
    }

    fun pageCount(): Int = pages.size

    fun writeTo(out: OutputStream) {
        val buf = ByteArrayOutputStream(1 shl 20)
        fun w(s: String) = buf.write(s.toByteArray(Charsets.ISO_8859_1))
        fun wb(b: ByteArray) = buf.write(b)

        val count = pages.size
        val maxObj = 2 + count * 3
        val offsets = LongArray(maxObj + 1)

        w("%PDF-1.4\n")
        wb(byteArrayOf(0x25, 0xE2.toByte(), 0xE3.toByte(), 0xCF.toByte(), 0xD3.toByte(), 0x0A))

        fun obj(n: Int, body: () -> Unit) {
            offsets[n] = buf.size().toLong()
            w("$n 0 obj\n")
            body()
            w("\nendobj\n")
        }

        obj(1) { w("<< /Type /Catalog /Pages 2 0 R >>") }

        val kids = (0 until count).joinToString(" ") { "${3 + it * 3} 0 R" }
        obj(2) { w("<< /Type /Pages /Count $count /Kids [$kids] >>") }

        pages.forEachIndexed { i, p ->
            val pageObj = 3 + i * 3
            val contentObj = pageObj + 1
            val imgObj = pageObj + 2

            val pageW: Float
            val pageH: Float
            if (autoSize) {
                pageW = 595f
                pageH = (595f * p.height / p.width).coerceIn(200f, 2400f)
            } else {
                pageW = paperWidth.toFloat()
                pageH = paperHeight.toFloat()
            }

            val margin = 0.03f
            val availW = pageW * (1 - margin * 2)
            val availH = pageH * (1 - margin * 2)
            val ratio = p.width.toFloat() / p.height.toFloat()
            var drawW = availW
            var drawH = drawW / ratio
            if (drawH > availH) {
                drawH = availH
                drawW = drawH * ratio
            }
            val x = (pageW - drawW) / 2f
            val y = (pageH - drawH) / 2f

            obj(pageObj) {
                w(
                    "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${fmt(pageW)} ${fmt(pageH)}] " +
                        "/Resources << /XObject << /Im0 $imgObj 0 R >> >> /Contents $contentObj 0 R >>"
                )
            }

            val content = "q ${fmt(drawW)} 0 0 ${fmt(drawH)} ${fmt(x)} ${fmt(y)} cm /Im0 Do Q"
            val contentBytes = content.toByteArray(Charsets.ISO_8859_1)
            obj(contentObj) {
                w("<< /Length ${contentBytes.size} >>\nstream\n")
                wb(contentBytes)
                w("\nendstream")
            }

            obj(imgObj) {
                w(
                    "<< /Type /XObject /Subtype /Image /Width ${p.width} /Height ${p.height} " +
                        "/ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${p.jpeg.size} >>\nstream\n"
                )
                wb(p.jpeg)
                w("\nendstream")
            }
        }

        val xrefPos = buf.size().toLong()
        w("xref\n0 ${maxObj + 1}\n")
        w("0000000000 65535 f \n")
        for (n in 1..maxObj) {
            w(String.format(Locale.US, "%010d 00000 n \n", offsets[n]))
        }
        w("trailer\n<< /Size ${maxObj + 1} /Root 1 0 R >>\nstartxref\n$xrefPos\n%%EOF\n")

        buf.writeTo(out)
    }

    private fun fmt(v: Float): String = String.format(Locale.US, "%.2f", v)
}
