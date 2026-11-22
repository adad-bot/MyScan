package ir.scanfa.app.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import ir.scanfa.app.data.Page
import ir.scanfa.app.data.PaperSize
import ir.scanfa.app.data.ScannerDoc
import ir.scanfa.app.image.Img
import ir.scanfa.app.util.DateFa
import java.io.File
import java.io.FileOutputStream
import kotlin.math.sqrt

/** تولید خروجی در قالب‌های PDF تصویری، PDF قابل جست‌وجو، DOCX، JPG و TXT. */
object Exporters {

    data class Result(
        val files: List<File>,
        val totalBytes: Long,
        val underCap: Boolean,
        val message: String
    )

    private class Prepared(val jpeg: ByteArray, val width: Int, val height: Int)

    fun export(
        doc: ScannerDoc,
        pages: List<Page>,
        format: String,
        outDir: File,
        paper: PaperSize,
        capKb: Int?,
        onStep: (Int, Int) -> Unit
    ): Result {
        if (pages.isEmpty()) {
            return Result(emptyList(), 0, false, "صفحه‌ای برای خروجی انتخاب نشده است.")
        }
        val base = ExportPaths.safeName(doc.title)
        return when (format) {
            "pdf_search" -> searchablePdf(pages, outDir, base, paper, capKb, onStep)
            "docx" -> docx(doc, pages, outDir, base, onStep)
            "jpg" -> images(pages, outDir, base, onStep)
            "txt" -> txt(doc, pages, outDir, base)
            else -> imagePdf(pages, outDir, base, paper, capKb, onStep)
        }
    }

    // ---------- PDF تصویری ----------

    private fun imagePdf(
        pages: List<Page>,
        outDir: File,
        base: String,
        paper: PaperSize,
        capKb: Int?,
        onStep: (Int, Int) -> Unit
    ): Result {
        val prepared = ArrayList<Prepared>()
        pages.forEachIndexed { index, page ->
            val bmp = Img.decode(File(page.filePath()), 2400) ?: return@forEachIndexed
            prepared.add(Prepared(Img.encodeJpeg(bmp, 90), bmp.width, bmp.height))
            bmp.recycle()
            onStep(index + 1, pages.size)
        }
        if (prepared.isEmpty()) return Result(emptyList(), 0, false, "تصویر سالمی برای خروجی پیدا نشد.")

        var scale = 1f
        var quality = 88
        var bestScale = 1f
        var bestQuality = 88
        var bestBytes = Long.MAX_VALUE
        var underCap = capKb == null
        var attempt = 0

        while (attempt < 6) {
            attempt++
            val bytes = writeImagePdf(prepared, paper, scale, quality)
            if (bytes <= 0) break
            if (bytes < bestBytes) {
                bestBytes = bytes
                bestScale = scale
                bestQuality = quality
            }
            val cap = capKb
            if (cap == null || bytes <= cap.toLong() * 1024) {
                underCap = true
                break
            }
            val ratio = (cap.toDouble() * 1024 / bytes).coerceIn(0.15, 0.95)
            scale *= (sqrt(ratio).toFloat() * 0.97f)
            quality = (quality - 8).coerceAtLeast(28)
            if (scale < 0.30f && quality <= 28) break
        }

        val outFile = ExportPaths.uniqueFile(outDir, base, "pdf")
        val writer = PdfImageWriter().paper(paper.widthPt, paper.heightPt)
        for (p in prepared) {
            val targetW = (p.width * bestScale).toInt().coerceAtLeast(96)
            val targetH = (p.height * bestScale).toInt().coerceAtLeast(96)
            val decoded = Img.decodeBytes(p.jpeg)
            if (decoded == null) {
                writer.addImage(p.jpeg, p.width, p.height)
                continue
            }
            val scaled = if (targetW < p.width || targetH < p.height) {
                Bitmap.createScaledBitmap(decoded, targetW, targetH, true)
            } else {
                decoded
            }
            writer.addImage(Img.encodeJpeg(scaled, bestQuality), scaled.width, scaled.height)
            if (scaled !== decoded) scaled.recycle()
            decoded.recycle()
        }
        val ok = try {
            FileOutputStream(outFile).use { writer.writeTo(it) }
            true
        } catch (t: Throwable) {
            false
        }
        if (!ok) return Result(emptyList(), 0, false, "ساخت PDF ناموفق بود (فضای ذخیره‌سازی را بررسی کنید).")

        val size = outFile.length()
        val message = buildString {
            append("PDF تصویری با ${DateFa.fa(pages.size)} صفحه ساخته شد: ${ExportPaths.humanSize(size)}")
            if (capKb != null) {
                if (underCap) append(" — زیر سقف ${capKb} کیلوبایت.")
                else append(" — رسیدن به سقف ${capKb} کیلوبایت بدون افت خوانایی ممکن نشد؛ فایل را به چند بخش تقسیم کنید.")
            }
        }
        return Result(listOf(outFile), size, underCap, message)
    }

    private fun writeImagePdf(prepared: List<Prepared>, paper: PaperSize, scale: Float, quality: Int): Long {
        val tmp = File.createTempFile("scan_pdf", ".part")
        var total = 0L
        try {
            val writer = PdfImageWriter().paper(paper.widthPt, paper.heightPt)
            for (p in prepared) {
                val targetW = (p.width * scale).toInt().coerceAtLeast(96)
                val targetH = (p.height * scale).toInt().coerceAtLeast(96)
                val decoded = Img.decodeBytes(p.jpeg) ?: continue
                val scaled = if (targetW < p.width || targetH < p.height) {
                    Bitmap.createScaledBitmap(decoded, targetW, targetH, true)
                } else {
                    decoded
                }
                writer.addImage(Img.encodeJpeg(scaled, quality), scaled.width, scaled.height)
                if (scaled !== decoded) scaled.recycle()
                decoded.recycle()
            }
            FileOutputStream(tmp).use { writer.writeTo(it) }
            total = tmp.length()
        } catch (t: Throwable) {
            total = 0
        } finally {
            tmp.delete()
        }
        return total
    }

    // ---------- PDF قابل جست‌وجو ----------

    private fun searchablePdf(
        pages: List<Page>,
        outDir: File,
        base: String,
        paper: PaperSize,
        capKb: Int?,
        onStep: (Int, Int) -> Unit
    ): Result {
        val outFile = ExportPaths.uniqueFile(outDir, base, "pdf")
        var scale = 1f
        var bestScale = 1f
        var bestBytes = Long.MAX_VALUE
        var underCap = capKb == null
        var attempt = 0

        while (attempt < 5) {
            attempt++
            val ok = renderSearchable(pages, outFile, paper, scale) { index -> onStep(index, pages.size) }
            if (!ok) {
                return Result(emptyList(), 0, false, "ساخت PDF قابل جست‌وجو ناموفق بود.")
            }
            val size = outFile.length()
            if (size < bestBytes) {
                bestBytes = size
                bestScale = scale
            }
            val cap = capKb
            if (cap == null || size <= cap.toLong() * 1024) {
                underCap = true
                break
            }
            val ratio = (cap.toDouble() * 1024 / size).coerceIn(0.2, 0.95)
            scale *= (sqrt(ratio).toFloat() * 0.96f)
            if (scale < 0.3f) break
        }
        if (bestScale < 1f) {
            renderSearchable(pages, outFile, paper, bestScale) { }
        }

        val size = outFile.length()
        val message = buildString {
            append("PDF قابل جست‌وجو ساخته شد: ${ExportPaths.humanSize(size)}")
            if (capKb != null && !underCap) append(" — سقف ${capKb} کیلوبایت رعایت نشد.")
        }
        return Result(listOf(outFile), size, underCap, message)
    }

    private fun renderSearchable(pages: List<Page>, outFile: File, paper: PaperSize, scale: Float, onPage: (Int) -> Unit): Boolean {
        val document = PdfDocument()
        return try {
            pages.forEachIndexed { index, page ->
                val bmp = Img.decode(File(page.filePath()), 2000) ?: return@forEachIndexed
                val targetW = (bmp.width * scale).toInt().coerceAtLeast(96)
                val targetH = (bmp.height * scale).toInt().coerceAtLeast(96)
                val scaled = if (targetW < bmp.width) Bitmap.createScaledBitmap(bmp, targetW, targetH, true) else bmp

                val pageW: Int
                val pageH: Int
                if (paper == PaperSize.AUTO) {
                    pageW = 595
                    pageH = (595f * scaled.height / scaled.width).toInt().coerceIn(200, 2400)
                } else {
                    pageW = paper.widthPt
                    pageH = paper.heightPt
                }
                val info = PdfDocument.PageInfo.Builder(pageW, pageH, index + 1).create()
                val pdfPage = document.startPage(info)
                try {
                    pdfPage.canvas.drawColor(Color.WHITE)
                    val margin = 12f
                    val availW = pageW - margin * 2
                    val availH = pageH - margin * 2
                    val ratio = scaled.width.toFloat() / scaled.height.toFloat()
                    var drawW = availW
                    var drawH = drawW / ratio
                    if (drawH > availH) {
                        drawH = availH
                        drawW = drawH * ratio
                    }
                    val left = (pageW - drawW) / 2f
                    val top = (pageH - drawH) / 2f
                    val area = RectF(left, top, left + drawW, top + drawH)
                    pdfPage.canvas.drawBitmap(scaled, null, area, Paint(Paint.FILTER_BITMAP_FLAG))
                    drawInvisibleText(pdfPage.canvas, page.text, area)
                } finally {
                    document.finishPage(pdfPage)
                }
                if (scaled !== bmp) scaled.recycle()
                bmp.recycle()
                onPage(index + 1)
            }
            FileOutputStream(outFile).use { document.writeTo(it) }
            true
        } catch (t: Throwable) {
            false
        } finally {
            try {
                document.close()
            } catch (t: Throwable) {
                // نادیده‌گرفتنی
            }
        }
    }

    /** لایهٔ متن نامرئی برای جست‌وجو؛ مکان تقریبی بر اساس تعداد سطرها. */
    private fun drawInvisibleText(canvas: Canvas, text: String, area: RectF) {
        if (text.isBlank()) return
        val lines = text.split('\n').filter { it.isNotBlank() }
        if (lines.isEmpty()) return
        val paint = Paint().apply {
            isAntiAlias = true
            color = 0x01FFFFFF
            textSize = (area.width() / 34f).coerceIn(6f, 24f)
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            textAlign = Paint.Align.RIGHT
        }
        val lineHeight = paint.textSize * 1.55f
        var y = area.top + paint.textSize * 1.3f
        var count = 0
        for (line in lines) {
            if (y > area.bottom - paint.textSize || count > 320) break
            canvas.drawText(line, area.right - 4f, y, paint)
            y += lineHeight
            count++
        }
    }

    // ---------- Word ----------

    private fun docx(doc: ScannerDoc, pages: List<Page>, outDir: File, base: String, onStep: (Int, Int) -> Unit): Result {
        val builder = DocxBuilder()
        builder.heading(doc.title, 1)
        builder.paragraph(
            "تاریخ: ${DateFa.short(doc.createdAt)}   |   تعداد صفحات: ${DateFa.fa(pages.size)}",
            size = 22
        )

        val template = doc.template()
        if (template != null && template.slots.isNotEmpty()) {
            builder.heading("فهرست مدارک", 2)
            for (slot in template.slots) {
                val pageIndex = pages.indexOfFirst { it.slotId == slot.id }
                val mark = if (pageIndex >= 0) "✓" else "✗"
                val where = if (pageIndex >= 0) " — صفحهٔ ${DateFa.fa(pageIndex + 1)}" else " — ثبت نشده"
                builder.bullet("$mark ${slot.title}$where")
            }
        }

        pages.forEachIndexed { index, page ->
            val slot = template?.let { t ->
                t.slots.firstOrNull { it.id == page.slotId }?.title
            }
            builder.heading("صفحهٔ ${DateFa.fa(index + 1)}" + if (slot != null) " — $slot" else "", 2)
            val bmp = Img.decode(File(page.filePath()), 1500)
            if (bmp != null) {
                builder.image(Img.encodeJpeg(bmp, 80), bmp.width, bmp.height)
                bmp.recycle()
            }
            val text = page.text
            if (text.isNotBlank()) {
                builder.paragraph("متن استخراج‌شده:", bold = true, size = 24)
                builder.paragraph(text, size = 24)
            } else {
                builder.paragraph("متن این صفحه استخراج نشده است.", size = 22)
            }
            if (index < pages.size - 1) builder.pageBreak()
            onStep(index + 1, pages.size)
        }

        builder.paragraph("این فایل با اپلیکیشن «اسکنر سند» ساخته شد.", size = 20, align = "center")
        val outFile = ExportPaths.uniqueFile(outDir, base, "docx")
        val ok = try {
            FileOutputStream(outFile).use { builder.build(it) }
            true
        } catch (t: Throwable) {
            false
        }
        if (!ok) return Result(emptyList(), 0, false, "ساخت فایل Word ناموفق بود.")
        return Result(listOf(outFile), outFile.length(), true, "فایل Word ساخته شد: ${ExportPaths.humanSize(outFile.length())}")
    }

    // ---------- تصویر و متن ----------

    private fun images(pages: List<Page>, outDir: File, base: String, onStep: (Int, Int) -> Unit): Result {
        val files = ArrayList<File>()
        pages.forEachIndexed { index, page ->
            val src = File(page.filePath())
            if (!src.exists()) return@forEachIndexed
            val target = ExportPaths.uniqueFile(outDir, "${base}_${index + 1}", "jpg")
            try {
                src.copyTo(target, overwrite = true)
                files.add(target)
            } catch (t: Throwable) {
                // فایل بعدی
            }
            onStep(index + 1, pages.size)
        }
        if (files.isEmpty()) return Result(emptyList(), 0, false, "تصویری برای خروجی پیدا نشد.")
        val total = files.sumOf { it.length() }
        return Result(files, total, true, "${DateFa.fa(files.size)} فایل JPG ساخته شد (${ExportPaths.humanSize(total)}).")
    }

    private fun txt(doc: ScannerDoc, pages: List<Page>, outDir: File, base: String): Result {
        val outFile = ExportPaths.uniqueFile(outDir, base, "txt")
        val sb = StringBuilder()
        sb.append(doc.title).append('\n')
        sb.append("تاریخ: ").append(DateFa.short(doc.createdAt)).append('\n')
        sb.append("تعداد صفحات: ").append(pages.size).append("\n\n")
        pages.forEachIndexed { index, page ->
            sb.append("===== صفحهٔ ").append(index + 1).append(" =====\n")
            sb.append(page.text.ifBlank { "— بدون متن —" }).append("\n\n")
        }
        return try {
            outFile.writeText(sb.toString(), Charsets.UTF_8)
            Result(listOf(outFile), outFile.length(), true, "فایل متنی ساخته شد: ${ExportPaths.humanSize(outFile.length())}")
        } catch (t: Throwable) {
            Result(emptyList(), 0, false, "ذخیرهٔ فایل متنی ناموفق بود.")
        }
    }
}
