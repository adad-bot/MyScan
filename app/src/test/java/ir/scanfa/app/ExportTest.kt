package ir.scanfa.app

import ir.scanfa.app.export.DocxBuilder
import ir.scanfa.app.export.PdfImageWriter
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

/** آزمون خروجی‌های DOCX و PDF در محیط JVM (بدون شبیه‌ساز). */
class ExportTest {

    private fun jpeg(): ByteArray {
        val stream = javaClass.classLoader?.getResourceAsStream("test.jpg")
            ?: error("test.jpg not found on test classpath")
        return stream.use { it.readBytes() }
    }

    @Test
    fun docxHasRtlPersianContent() {
        val dir = File("build/test-artifacts")
        dir.mkdirs()
        val file = File(dir, "sample.docx")

        val builder = DocxBuilder()
        builder.heading("گزارش آزمون", 1)
        builder.paragraph("این یک متن فارسی راست‌به‌چپ است.")
        builder.image(jpeg(), 240, 320)
        file.outputStream().use { builder.build(it) }

        assertTrue("docx should not be empty", file.length() > 1000)
        ZipFile(file).use { zip ->
            val entry = zip.getEntry("word/document.xml")
            assertTrue("document.xml must exist", entry != null)
            val xml = zip.getInputStream(entry).readBytes().toString(Charsets.UTF_8)
            assertTrue(xml.contains("گزارش آزمون"))
            assertTrue(xml.contains("<w:bidi/>"))
            assertTrue(xml.contains("image1.jpeg"))
            assertTrue(zip.getEntry("word/styles.xml") != null)
        }
    }

    @Test
    fun pdfHasValidStructure() {
        val dir = File("build/test-artifacts")
        dir.mkdirs()
        val file = File(dir, "sample.pdf")

        val writer = PdfImageWriter().paper(595, 842)
        writer.addImage(jpeg(), 240, 320)
        writer.addImage(jpeg(), 240, 320)
        file.outputStream().use { writer.writeTo(it) }

        val text = file.readBytes().toString(Charsets.ISO_8859_1)
        assertTrue(text.startsWith("%PDF-1.4"))
        assertTrue(text.contains("/Type /Catalog"))
        assertTrue(text.contains("/Type /Pages"))
        assertTrue(text.contains("/DCTDecode"))
        assertTrue(text.contains("startxref"))
        assertTrue(text.endsWith("%%EOF\n"))
        assertTrue(file.length() > 2000)
    }
}
