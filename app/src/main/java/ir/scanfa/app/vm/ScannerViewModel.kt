package ir.scanfa.app.vm

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import ir.scanfa.app.data.DocStore
import ir.scanfa.app.data.ExportFormat
import ir.scanfa.app.data.FilterKind
import ir.scanfa.app.data.Page
import ir.scanfa.app.data.PaperSize
import ir.scanfa.app.data.Prefs
import ir.scanfa.app.data.ScannerDoc
import ir.scanfa.app.export.ExportPaths
import ir.scanfa.app.export.Exporters
import ir.scanfa.app.image.DocDetector
import ir.scanfa.app.image.Filters
import ir.scanfa.app.image.Img
import ir.scanfa.app.image.PdfImport
import ir.scanfa.app.image.Quad
import ir.scanfa.app.image.Warp
import ir.scanfa.app.ocr.OcrEngine
import ir.scanfa.app.util.DateFa
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** وضعیت و عملیات اصلی اپ؛ پردازش‌های سنگین روی دیسپچر پیش‌فرض اجرا می‌شوند. */
class ScannerViewModel(app: Application) : AndroidViewModel(app) {

    val prefs = Prefs(app)

    private val _docs = MutableStateFlow<List<ScannerDoc>>(emptyList())
    val docs: StateFlow<List<ScannerDoc>> = _docs.asStateFlow()

    private val _busy = MutableStateFlow<String?>(null)
    val busy: StateFlow<String?> = _busy.asStateFlow()

    private val _progress = MutableStateFlow<Pair<Int, Int>?>(null)
    val progress: StateFlow<Pair<Int, Int>?> = _progress.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _themeMode = MutableStateFlow("system")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    init {
        DocStore.init(app)
        _themeMode.value = prefs.themeMode
        refresh()
    }

    fun setThemeMode(mode: String) {
        prefs.themeMode = mode
        _themeMode.value = mode
    }

    fun refresh() {
        _docs.value = DocStore.list()
    }

    fun load(id: String): ScannerDoc? = DocStore.load(id)

    fun takeMessage() {
        _message.value = null
    }

    fun postMessage(text: String) {
        _message.value = text
    }

    fun createDocument(title: String, templateId: String?): String {
        val id = UUID.randomUUID().toString().replace("-", "").take(12)
        val now = System.currentTimeMillis()
        val doc = ScannerDoc(
            id = id,
            title = title.trim().ifBlank { "سند ${DateFa.faText(DateFa.short(now))}" },
            createdAt = now,
            updatedAt = now,
            templateId = templateId,
            paperSize = prefs.defaultPaper,
            sizeCapKb = prefs.defaultCapKb
        )
        DocStore.save(doc)
        refresh()
        return id
    }

    suspend fun addPageFromUri(docId: String, uri: Uri): Page? = withContext(Dispatchers.IO) {
        val tmp = File(getApplication<Application>().cacheDir, "import_${System.currentTimeMillis()}.jpg")
        val copied = try {
            getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                tmp.outputStream().use { output -> input.copyTo(output) }
            } != null
        } catch (t: Throwable) {
            false
        }
        if (!copied) return@withContext null
        val page = createPageFromFile(docId, tmp)
        tmp.delete()
        page
    }

    suspend fun addPageFromFile(docId: String, file: File): Page? = withContext(Dispatchers.IO) {
        createPageFromFile(docId, file)
    }

    suspend fun addPageFromBitmap(docId: String, bitmap: Bitmap): Page? = withContext(Dispatchers.IO) {
        val target = DocStore.newImageFile(docId, "orig")
        if (!Img.saveJpeg(bitmap, target, 92)) return@withContext null
        val doc = DocStore.load(docId) ?: return@withContext null
        val crop = if (prefs.autoDetect) {
            DocDetector.detect(bitmap).toNormalized(bitmap.width, bitmap.height)
        } else {
            null
        }
        val page = Page(
            id = UUID.randomUUID().toString().take(10),
            originalPath = target.absolutePath,
            crop = crop
        )
        doc.pages.add(page)
        DocStore.save(doc)
        refresh()
        page
    }

    private fun createPageFromFile(docId: String, source: File): Page? {
        val doc = DocStore.load(docId) ?: return null
        val target = DocStore.newImageFile(docId, "orig")
        try {
            source.copyTo(target, overwrite = true)
        } catch (t: Throwable) {
            return null
        }
        if (target.length() == 0L) {
            target.delete()
            return null
        }
        val bmp = Img.decode(target, 2400)
        val crop = if (prefs.autoDetect && bmp != null) {
            DocDetector.detect(bmp).toNormalized(bmp.width, bmp.height)
        } else {
            null
        }
        bmp?.recycle()
        val page = Page(
            id = UUID.randomUUID().toString().take(10),
            originalPath = target.absolutePath,
            crop = crop
        )
        doc.pages.add(page)
        DocStore.save(doc)
        refresh()
        return page
    }

    suspend fun importPdf(docId: String, uri: Uri): Int = withContext(Dispatchers.IO) {
        val bitmaps = PdfImport.renderPages(getApplication(), uri, 1800)
        var added = 0
        for (bmp in bitmaps) {
            if (addPageFromBitmap(docId, bmp) != null) added++
            bmp.recycle()
        }
        refresh()
        added
    }

    /** پیش‌نمایش کم‌حجم برای ویرایشگر بدون ذخیره روی دیسک. */
    suspend fun previewPage(page: Page, crop: List<Float>?, filter: FilterKind, maxDim: Int = 900): Bitmap? =
        withContext(Dispatchers.Default) {
            val src = Img.decode(File(page.originalPath), maxDim) ?: return@withContext null
            val quad = if (crop != null && crop.size == 8) {
                Quad.fromNormalized(crop, src.width, src.height)
            } else {
                DocDetector.detect(src)
            }
            var bmp = Warp.warp(src, quad, maxDim)
            if (bmp !== src) src.recycle()
            val filtered = Filters.apply(bmp, filter)
            if (filtered !== bmp) bmp.recycle()
            bmp = Img.rotate(filtered, page.rotation)
            bmp
        }

    /** اعمال قاب و فیلتر با کیفیت کامل و ذخیرهٔ فایل پردازش‌شده. */
    suspend fun saveProcessed(docId: String, pageId: String, crop: List<Float>?, filter: FilterKind): Page? =
        withContext(Dispatchers.Default) {
            val doc = DocStore.load(docId) ?: return@withContext null
            val index = doc.pages.indexOfFirst { it.id == pageId }
            if (index < 0) return@withContext null
            val page = doc.pages[index]
            val src = Img.decode(File(page.originalPath), 2200) ?: return@withContext null
            val quad = if (crop != null && crop.size == 8) {
                Quad.fromNormalized(crop, src.width, src.height)
            } else {
                DocDetector.detect(src)
            }
            val normalized = crop ?: quad.toNormalized(src.width, src.height)
            var bmp = Warp.warp(src, quad, 2200)
            if (bmp !== src) src.recycle()
            val filtered = Filters.apply(bmp, filter)
            if (filtered !== bmp) bmp.recycle()
            bmp = Img.rotate(filtered, page.rotation)
            val outFile = DocStore.newImageFile(docId, "proc")
            val saved = Img.saveJpeg(bmp, outFile, prefs.jpegQuality)
            bmp.recycle()
            if (!saved) return@withContext null
            page.processedPath?.let { old ->
                if (old != outFile.absolutePath) File(old).delete()
            }
            val updated = page.copy(
                processedPath = outFile.absolutePath,
                filter = filter.id,
                crop = normalized
            )
            doc.pages[index] = updated
            DocStore.save(doc)
            refresh()
            updated
        }

    suspend fun runOcr(docId: String, pageIds: List<String>, language: String): String =
        withContext(Dispatchers.Default) {
            val doc = DocStore.load(docId) ?: return@withContext "سند پیدا نشد."
            val targets = doc.pages.filter { pageIds.contains(it.id) }
            if (targets.isEmpty()) return@withContext "صفحه‌ای برای متن‌خوانی انتخاب نشده است."
            _busy.value = "متن‌خوانی"
            var ok = 0
            var failed = 0
            targets.forEachIndexed { i, page ->
                _progress.value = (i + 1) to targets.size
                val src = Img.decode(File(page.filePath()), 2000)
                if (src == null) {
                    failed++
                    return@forEachIndexed
                }
                val text = OcrEngine.recognize(getApplication(), src, language)
                src.recycle()
                if (text == null) {
                    failed++
                } else {
                    val idx = doc.pages.indexOfFirst { it.id == page.id }
                    if (idx >= 0) {
                        doc.pages[idx] = doc.pages[idx].copy(ocrText = text, ocrDone = true)
                        ok++
                    }
                }
            }
            DocStore.save(doc)
            refresh()
            _busy.value = null
            _progress.value = null
            when {
                ok == 0 -> "متن‌خوانی ناموفق بود. فایل زبان و کیفیت تصویر را بررسی کنید."
                failed > 0 -> "متن‌خوانی ${DateFa.fa(ok)} صفحه انجام شد و ${DateFa.fa(failed)} صفحه ناموفق بود."
                else -> "متن‌خوانی ${DateFa.fa(ok)} صفحه انجام شد."
            }
        }

    /** اعمال یک فیلتر روی چند صفحه (از تصویر اصل با همان قاب ذخیره‌شده). */
    suspend fun applyFilter(docId: String, pageIds: List<String>, filter: FilterKind): Int =
        withContext(Dispatchers.Default) {
            val doc = DocStore.load(docId) ?: return@withContext 0
            var done = 0
            for (id in pageIds) {
                val page = doc.page(id) ?: continue
                if (saveProcessed(docId, id, page.crop, filter) != null) done++
            }
            refresh()
            done
        }

    fun saveText(docId: String, pageId: String, text: String) {
        val doc = DocStore.load(docId) ?: return
        val idx = doc.pages.indexOfFirst { it.id == pageId }
        if (idx < 0) return
        doc.pages[idx] = doc.pages[idx].copy(ocrEdited = text, ocrDone = true)
        DocStore.save(doc)
        refresh()
    }

    suspend fun rotatePages(docId: String, pageIds: List<String>, delta: Int) = withContext(Dispatchers.Default) {
        val doc = DocStore.load(docId) ?: return@withContext
        for (id in pageIds) {
            val idx = doc.pages.indexOfFirst { it.id == id }
            if (idx < 0) continue
            val rotated = doc.pages[idx].copy(rotation = (doc.pages[idx].rotation + delta + 360) % 360)
            doc.pages[idx] = rotated
        }
        DocStore.save(doc)
        refresh()
        for (id in pageIds) {
            val page = DocStore.load(docId)?.page(id) ?: continue
            if (page.processedPath != null) {
                saveProcessed(docId, id, page.crop, FilterKind.of(page.filter))
            }
        }
        refresh()
        Unit
    }

    fun deletePages(docId: String, pageIds: List<String>) {
        val doc = DocStore.load(docId) ?: return
        val removed = doc.pages.filter { pageIds.contains(it.id) }
        doc.pages.removeAll { pageIds.contains(it.id) }
        DocStore.save(doc)
        for (page in removed) {
            page.processedPath?.let { File(it).delete() }
            if (!prefs.keepOriginal) File(page.originalPath).delete()
        }
        refresh()
    }

    fun movePage(docId: String, from: Int, to: Int) {
        val doc = DocStore.load(docId) ?: return
        if (from !in doc.pages.indices || to !in doc.pages.indices) return
        val page = doc.pages.removeAt(from)
        doc.pages.add(to, page)
        DocStore.save(doc)
        refresh()
    }

    fun assignSlot(docId: String, pageId: String, slotId: String?) {
        val doc = DocStore.load(docId) ?: return
        doc.pages = doc.pages.map { page ->
            when {
                page.id == pageId -> page.copy(slotId = slotId)
                slotId != null && page.slotId == slotId -> page.copy(slotId = null)
                else -> page
            }
        }.toMutableList()
        DocStore.save(doc)
        refresh()
    }

    fun rename(docId: String, title: String) {
        val doc = DocStore.load(docId) ?: return
        doc.title = title.trim().ifBlank { doc.title }
        DocStore.save(doc)
        refresh()
    }

    fun deleteDocument(docId: String) {
        DocStore.delete(docId)
        refresh()
    }

    suspend fun export(
        docId: String,
        pageIds: List<String>?,
        format: ExportFormat,
        paper: PaperSize,
        capKb: Int?
    ): Exporters.Result? = withContext(Dispatchers.Default) {
        val doc = DocStore.load(docId) ?: return@withContext null
        val pages = if (pageIds == null) doc.pages.toList() else doc.pages.filter { pageIds.contains(it.id) }
        if (pages.isEmpty()) return@withContext Exporters.Result(emptyList(), 0, false, "صفحه‌ای انتخاب نشده است.")
        _busy.value = "ساخت خروجی"
        _progress.value = 0 to pages.size
        val result = Exporters.export(doc, pages, format.id, exportDir(), paper, capKb) { done, total ->
            _progress.value = done to total
        }
        _busy.value = null
        _progress.value = null
        refresh()
        result
    }

    fun exportDir(): File = ExportPaths.exportsDir(getApplication<Application>().filesDir)

    fun clearExports(): Int {
        val dir = exportDir()
        val files = dir.listFiles() ?: return 0
        var count = 0
        for (f in files) {
            if (f.isFile && f.delete()) count++
        }
        return count
    }

    override fun onCleared() {
        OcrEngine.release()
        super.onCleared()
    }
}
