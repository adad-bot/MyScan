package ir.scanfa.app.export

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * سازندهٔ فایل Word (DOCX) با جهت راست‌به‌چپ.
 *
 * به‌جای کتابخانهٔ سنگین، بستهٔ OOXML حداقلی ساخته می‌شود: سند، استایل‌ها،
 * روابط و تصاویر. Word و LibreOffice این بسته را باز می‌کنند.
 */
class DocxBuilder {

    private class Picture(val id: String, val bytes: ByteArray, val cx: Long, val cy: Long, val fileName: String)

    private val body = StringBuilder()
    private val pictures = ArrayList<Picture>()
    private val rels = StringBuilder()
    private var picSeq = 0

    companion object {
        private const val EMU_PER_PX = 9525L
        private const val MAX_WIDTH_EMU = 6120000L // 17 سانتی‌متر
        private const val FONT = "Tahoma"
    }

    fun heading(text: String, level: Int = 1): DocxBuilder {
        val size = if (level <= 1) 34 else 28
        body.append("<w:p><w:pPr><w:bidi/><w:spacing w:before=\"240\" w:after=\"120\"/>")
            .append("<w:jc w:val=\"right\"/><w:outlineLvl w:val=\"${(level - 1).coerceIn(0, 8)}\"/></w:pPr>")
            .append(run(text, bold = true, size = size, rtl = true))
            .append("</w:p>")
        return this
    }

    fun paragraph(text: String, bold: Boolean = false, size: Int = 24, align: String = "right"): DocxBuilder {
        body.append("<w:p><w:pPr><w:bidi/><w:spacing w:after=\"100\" w:line=\"300\" w:lineRule=\"auto\"/><w:jc w:val=\"$align\"/></w:pPr>")
        for (line in text.split('\n')) {
            body.append(run(line, bold = bold, size = size, rtl = true))
            if (line != text.split('\n').last()) body.append("<w:r><w:br/></w:r>")
        }
        body.append("</w:p>")
        return this
    }

    fun bullet(text: String): DocxBuilder {
        body.append("<w:p><w:pPr><w:bidi/><w:spacing w:after=\"60\"/>")
            .append("<w:ind w:right=\"420\" w:left=\"0\"/><w:jc w:val=\"right\"/></w:pPr>")
            .append(run("• $text", bold = false, size = 24, rtl = true))
            .append("</w:p>")
        return this
    }

    fun image(jpeg: ByteArray, widthPx: Int, heightPx: Int): DocxBuilder {
        if (jpeg.isEmpty() || widthPx <= 0 || heightPx <= 0) return this
        picSeq++
        var cx = widthPx.toLong() * EMU_PER_PX
        var cy = heightPx.toLong() * EMU_PER_PX
        if (cx > MAX_WIDTH_EMU) {
            val k = MAX_WIDTH_EMU.toDouble() / cx.toDouble()
            cx = MAX_WIDTH_EMU
            cy = (cy * k).toLong()
        }
        val name = "image$picSeq.jpeg"
        val rid = "rIdImg$picSeq"
        pictures.add(Picture(rid, jpeg, cx, cy, name))
        rels.append("<Relationship Id=\"$rid\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"media/$name\"/>")

        body.append("<w:p><w:pPr><w:bidi/><w:spacing w:after=\"120\"/><w:jc w:val=\"center\"/></w:pPr>")
            .append("<w:r><w:drawing><wp:inline distT=\"0\" distB=\"0\" distL=\"0\" distR=\"0\">")
            .append("<wp:extent cx=\"$cx\" cy=\"$cy\"/>")
            .append("<wp:docPr id=\"${100 + picSeq}\" name=\"Picture $picSeq\"/>")
            .append("<wp:cNvGraphicFramePr><a:graphicFrameLocks xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" noChangeAspect=\"1\"/></wp:cNvGraphicFramePr>")
            .append("<a:graphic xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\">")
            .append("<a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">")
            .append("<pic:pic xmlns:pic=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">")
            .append("<pic:nvPicPr><pic:cNvPr id=\"${100 + picSeq}\" name=\"$name\"/><pic:cNvPicPr/></pic:nvPicPr>")
            .append("<pic:blipFill><a:blip r:embed=\"$rid\"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>")
            .append("<pic:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"$cx\" cy=\"$cy\"/></a:xfrm>")
            .append("<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></pic:spPr>")
            .append("</pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>")
        return this
    }

    fun pageBreak(): DocxBuilder {
        body.append("<w:p><w:r><w:br w:type=\"page\"/></w:r></w:p>")
        return this
    }

    fun build(out: OutputStream) {
        val document = buildDocumentXml()
        ZipOutputStream(out).use { zip ->
            put(zip, "[Content_Types].xml", contentTypes())
            put(zip, "_rels/.rels", rootRels())
            put(zip, "word/document.xml", document)
            put(zip, "word/styles.xml", styles())
            put(zip, "word/_rels/document.xml.rels", documentRels())
            pictures.forEachIndexed { i, p ->
                val entry = ZipEntry("word/media/${p.fileName}")
                zip.putNextEntry(entry)
                zip.write(p.bytes)
                zip.closeEntry()
                if (i == pictures.size - 1) zip.flush()
            }
        }
    }

    private fun put(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun run(text: String, bold: Boolean, size: Int, rtl: Boolean): String {
        val sb = StringBuilder()
        sb.append("<w:r><w:rPr>")
        sb.append("<w:rFonts w:ascii=\"$FONT\" w:hAnsi=\"$FONT\" w:cs=\"$FONT\"/>")
        if (bold) sb.append("<w:b/><w:bCs/>")
        sb.append("<w:sz w:val=\"$size\"/><w:szCs w:val=\"$size\"/>")
        if (rtl) sb.append("<w:rtl/>")
        sb.append("</w:rPr><w:t xml:space=\"preserve\">").append(escape(text)).append("</w:t></w:r>")
        return sb.toString()
    }

    private fun escape(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    private fun buildDocumentXml(): String {
        val sect = "<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/>" +
            "<w:pgMar w:top=\"1134\" w:right=\"1134\" w:bottom=\"1134\" w:left=\"1134\" w:header=\"708\" w:footer=\"708\" w:gutter=\"0\"/>" +
            "<w:cols w:space=\"708\"/><w:bidi/><w:docGrid w:linePitch=\"360\"/></w:sectPr>"
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\" " +
            "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" " +
            "xmlns:wp=\"http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing\" " +
            "xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" " +
            "xmlns:pic=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">" +
            "<w:body>" + body.toString() + sect + "</w:body></w:document>"
    }

    private fun contentTypes(): String = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
        "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
        "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
        "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
        "<Default Extension=\"jpeg\" ContentType=\"image/jpeg\"/>" +
        "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>" +
        "<Override PartName=\"/word/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml\"/>" +
        "</Types>"

    private fun rootRels(): String = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
        "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
        "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>" +
        "</Relationships>"

    private fun documentRels(): String = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
        "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
        "<Relationship Id=\"rIdStyles\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>" +
        rels.toString() +
        "</Relationships>"

    private fun styles(): String = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
        "<w:styles xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">" +
        "<w:docDefaults><w:rPrDefault><w:rPr>" +
        "<w:rFonts w:ascii=\"$FONT\" w:hAnsi=\"$FONT\" w:cs=\"$FONT\"/>" +
        "<w:sz w:val=\"24\"/><w:szCs w:val=\"24\"/><w:lang w:val=\"en-US\" w:bidi=\"fa-IR\"/>" +
        "</w:rPr></w:rPrDefault><w:pPrDefault><w:pPr><w:bidi/><w:jc w:val=\"right\"/></w:pPr></w:pPrDefault></w:docDefaults>" +
        "<w:style w:type=\"paragraph\" w:default=\"1\" w:styleId=\"Normal\"><w:name w:val=\"Normal\"/><w:qFormat/></w:style>" +
        "</w:styles>"
}
