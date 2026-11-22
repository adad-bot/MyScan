package ir.scanfa.app.data

/** یک خانه از الگوی مدرک؛ مثلاً «روی مدرک هویتی». */
data class Slot(val id: String, val title: String)

/** الگوی آمادهٔ مدارک که ترتیب و کامل‌بودن صفحات را کنترل می‌کند. */
data class DocTemplate(
    val id: String,
    val title: String,
    val note: String,
    val slots: List<Slot>
)

/**
 * یک صفحهٔ سند.
 *
 * @param crop چهار گوشهٔ سند به‌صورت نرمال‌شده (۰ تا ۱) به ترتیب
 * بالا-چپ، بالا-راست، پایین-راست، پایین-چپ.
 */
data class Page(
    val id: String,
    val originalPath: String,
    val processedPath: String? = null,
    val filter: String = FilterKind.ORIGINAL.id,
    val rotation: Int = 0,
    val crop: List<Float>? = null,
    val ocrText: String? = null,
    val ocrEdited: String? = null,
    val slotId: String? = null,
    val ocrDone: Boolean = false
) {
    val text: String get() = (ocrEdited ?: ocrText ?: "").trim()

    fun filePath(): String = processedPath ?: originalPath

    fun withText(value: String): Page = copy(ocrEdited = value, ocrDone = true)
}

data class ScannerDoc(
    val id: String,
    var title: String,
    val createdAt: Long,
    var updatedAt: Long,
    var templateId: String? = null,
    var paperSize: String = PaperSize.A4.id,
    var sizeCapKb: Int = 1024,
    var pages: MutableList<Page> = mutableListOf()
) {
    fun page(pageId: String): Page? = pages.firstOrNull { it.id == pageId }

    fun template(): DocTemplate? = Templates.byId(templateId)

    fun slotPage(slotId: String): Page? = pages.firstOrNull { it.slotId == slotId }
}

enum class FilterKind(val id: String, val label: String) {
    ORIGINAL("original", "اصل"),
    ENHANCE("enhance", "بهبود"),
    SHADOW("shadow", "حذف سایه"),
    GRAY("gray", "خاکستری"),
    BW("bw", "سیاه‌وسفید");

    companion object {
        fun of(id: String?): FilterKind = entries.firstOrNull { it.id == id } ?: ORIGINAL
    }
}

enum class PaperSize(val id: String, val label: String, val widthPt: Int, val heightPt: Int) {
    A4("A4", "A4", 595, 842),
    LETTER("Letter", "Letter", 612, 792),
    AUTO("auto", "هم‌اندازه تصویر", 0, 0);

    companion object {
        fun of(id: String?): PaperSize = entries.firstOrNull { it.id == id } ?: A4
    }
}

enum class ExportFormat(val id: String, val label: String, val mime: String, val ext: String) {
    PDF_IMAGE("pdf_image", "PDF تصویری", "application/pdf", "pdf"),
    PDF_SEARCHABLE("pdf_search", "PDF قابل جست‌وجو", "application/pdf", "pdf"),
    DOCX("docx", "Word (DOCX)", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx"),
    JPG("jpg", "تصویر JPG", "image/jpeg", "jpg"),
    TXT("txt", "متن ساده (TXT)", "text/plain", "txt");

    companion object {
        fun of(id: String?): ExportFormat = entries.firstOrNull { it.id == id } ?: PDF_IMAGE
    }
}
