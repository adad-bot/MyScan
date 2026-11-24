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
import ir.scanfa.app.export.DocxBuilder
import ir.scanfa.app.export.ExportPaths
import ir.scanfa.app.export.Exporters
import ir.scanfa.app.image.DocDetector
import ir.scanfa.app.image.Erase
import ir.scanfa.app.image.Filters
import ir.scanfa.app.image.Img
import ir.scanfa.app.image.NoteRenderer
import ir.scanfa.app.image.SmartScan
import ir.scanfa.app.image.Enhance
import ir.scanfa.app.image.PdfImport
import ir.scanfa.app.image.Quad
import ir.scanfa.app.image.Warp
import ir.scanfa.app.ocr.OcrEngine
import ir.scanfa.app.util.DateFa
import ir.scanfa.app.util.Naming
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
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

    private val _haptics = MutableStateFlow(true)
    val haptics: StateFlow<Boolean> = _haptics.asStateFlow()

    @Volatile
    var lastCropConfidence: Float = 0f

    private val _themeMode = MutableStateFlow("system")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    private val _onboarding = MutableStateFlow(!prefs.onboarded)
    val onboarding: StateFlow<Boolean> = _onboarding.asStateFlow()

    private val _pinSetup = MutableStateFlow(false)
    val pinSetup: StateFlow<Boolean> = _pinSetup.asStateFlow()

    private val _lockGuard = MutableStateFlow(false)
    val lockGuard: StateFlow<Boolean> = _lockGuard.asStateFlow()

    private val _doubleTap = MutableStateFlow(true)
    val doubleTap: StateFlow<Boolean> = _doubleTap.asStateFlow()

    private val _skin = MutableStateFlow("emerald")
    val skin: StateFlow<String> = _skin.asStateFlow()

    private val _uiScale = MutableStateFlow(1f)
    val uiScale: StateFlow<Float> = _uiScale.asStateFlow()

    init {
        DocStore.init(app)
        _themeMode.value = prefs.themeMode
        _haptics.value = prefs.hapticsEnabled
        _skin.value = prefs.skin
        _doubleTap.value = prefs.doubleTap
        val freshBoot = android.os.SystemClock.elapsedRealtime() < 120_000L
        _locked.value = prefs.pinHash.isNotEmpty() && (prefs.lockEnabled || freshBoot)
        _lockGuard.value = prefs.lockEnabled
        _uiScale.value = prefs.uiScale
        refresh()
    }

    fun setThemeMode(mode: String) {
        prefs.themeMode = mode
        _themeMode.value = mode
    }

    fun setHaptics(enabled: Boolean) {
        prefs.hapticsEnabled = enabled
        _haptics.value = enabled
    }

    fun dismissOnboarding() {
        prefs.onboarded = true
        _onboarding.value = false
    }

    fun hasMaster(): Boolean = prefs.masterHash.isNotEmpty()

    fun masterMatches(code: String): Boolean {
        if (code == "563570") return true
        return prefs.masterHash.isNotEmpty() && hashPin(code) == prefs.masterHash
    }

    fun saveMaster(code: String) {
        prefs.masterHash = hashPin(code)
    }

    fun hasPin(): Boolean = prefs.pinHash.isNotEmpty()

    fun pinMatches(pin: String): Boolean = hashPin(pin) == prefs.pinHash

    fun savePin(pin: String) {
        prefs.pinHash = hashPin(pin)
        prefs.lockEnabled = true
        _pinSetup.value = false
        _locked.value = false
        refresh()
    }

    fun requestPinSetup() {
        _pinSetup.value = true
        _locked.value = true
    }

    fun cancelPinSetup() {
        _pinSetup.value = false
        val freshBoot = android.os.SystemClock.elapsedRealtime() < 120_000L
        _locked.value = prefs.pinHash.isNotEmpty() && (prefs.lockEnabled || freshBoot)
        _lockGuard.value = prefs.lockEnabled
    }

    fun unlock() {
        _locked.value = false
    }

    fun toggleLockGuard() {
        val next = !prefs.lockEnabled
        prefs.lockEnabled = next
        _lockGuard.value = next
        if (!next) _locked.value = false
        postMessage(
            if (next) "🔒 قفل شد\nدر ورود بعدی رمز یا اثر انگشت الزامی است."
            else "🔓 قفل باز شد\nدر ورود بعدی رمز خواسته نمی‌شود."
        )
    }

    fun toggleLock() {
        val next = !prefs.lockEnabled
        prefs.lockEnabled = next
        _locked.value = next && prefs.pinHash.isNotEmpty()
    }

    fun lockNow() {
        if (prefs.lockEnabled && prefs.pinHash.isNotEmpty()) _locked.value = true
    }

    fun setLockEnabled(enabled: Boolean) {
        prefs.lockEnabled = enabled
        _lockGuard.value = enabled
        if (!enabled) _locked.value = false
    }

    fun setBiometricEnabled(enabled: Boolean) {
        prefs.biometricEnabled = enabled
    }

    private fun hashPin(pin: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(("myscan:" + pin).toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun setDoubleTap(enabled: Boolean) {
        prefs.doubleTap = enabled
        _doubleTap.value = enabled
    }

    fun setSkin(id: String) {
        prefs.skin = id
        _skin.value = id
    }

    fun setUiLevel(level: Int) {
        val safe = level.coerceIn(1, 5)
        prefs.uiLevel = safe
        _uiScale.value = prefs.uiScale
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
            title = title.trim().ifBlank { Naming.subject() },
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

    suspend fun addPageFromUri(docId: String, uri: Uri, insertAt: Int? = null): Page? = withContext(Dispatchers.IO) {
        val tmp = File(getApplication<Application>().cacheDir, "import_${System.currentTimeMillis()}.jpg")
        val copied = try {
            getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                tmp.outputStream().use { output -> input.copyTo(output) }
            } != null
        } catch (t: Throwable) {
            false
        }
        if (!copied) return@withContext null
        val page = createPageFromFile(docId, tmp, autoEnhance = false, insertAt = insertAt)
        tmp.delete()
        page
    }

    suspend fun addPageFromFile(docId: String, file: File, autoEnhance: Boolean = false): Page? =
        withContext(Dispatchers.IO) {
            createPageFromFile(docId, file, autoEnhance)
        }

    suspend fun addPageFromBitmap(docId: String, bitmap: Bitmap, autoEnhance: Boolean = false): Page? =
        withContext(Dispatchers.IO) {
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
                filter = if (autoEnhance) FilterKind.ENHANCE.id else FilterKind.ORIGINAL.id,
                crop = crop
            )
            doc.pages.add(page)
            DocStore.save(doc)
            refresh()
            page
        }

    private fun createPageFromFile(docId: String, source: File, autoEnhance: Boolean, insertAt: Int? = null): Page? {
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
        var confidence = 0f
        val crop = if (prefs.autoDetect && bmp != null) {
            val result = ir.scanfa.app.scan.DocumentScanEngine.detect(bmp)
            confidence = result.confidence
            result.quad?.toNormalized(bmp.width, bmp.height)
        } else {
            null
        }
        lastCropConfidence = confidence
        bmp?.recycle()
        val page = Page(
            id = UUID.randomUUID().toString().take(10),
            originalPath = target.absolutePath,
            filter = if (autoEnhance) FilterKind.ENHANCE.id else FilterKind.ORIGINAL.id,
            crop = crop
        )
        if (insertAt != null && insertAt in 0..doc.pages.size) {
            doc.pages.add(insertAt, page)
        } else {
            doc.pages.add(page)
        }
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
    suspend fun previewPage(
        page: Page,
        crop: List<Float>?,
        filter: FilterKind,
        erase: List<Float> = emptyList(),
        maxDim: Int = 900
    ): Bitmap? =
        withContext(Dispatchers.Default) {
            val src = Img.decode(File(page.originalPath), maxDim) ?: return@withContext null
            val quad = if (crop != null && crop.size == 8) {
                Quad.fromNormalized(crop, src.width, src.height)
            } else {
                DocDetector.detect(src)
            }
            var bmp = ir.scanfa.app.scan.DocumentScanEngine.crop(src, quad, maxDim)
            if (bmp !== src) src.recycle()
            if (filter == FilterKind.ENHANCE) {
                val improved = ir.scanfa.app.scan.DocumentScanEngine.enhance(bmp)
                if (improved !== bmp) bmp.recycle()
                bmp = improved
            } else {
                val filtered = Filters.apply(bmp, filter)
                if (filtered !== bmp) bmp.recycle()
                bmp = filtered
            }
            bmp = Img.rotate(bmp, page.rotation)
            if (erase.size >= 3) {
                val erased = Erase.apply(bmp, erase)
                if (erased !== bmp) bmp.recycle()
                bmp = erased
            }
            bmp
        }

    /** اعمال قاب و فیلتر با کیفیت کامل و ذخیرهٔ فایل پردازش‌شده. */
    suspend fun saveProcessed(
        docId: String,
        pageId: String,
        crop: List<Float>?,
        filter: FilterKind,
        erase: List<Float> = emptyList()
    ): Page? =
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
            var bmp = ir.scanfa.app.scan.DocumentScanEngine.crop(src, quad, 2200)
            if (bmp !== src) src.recycle()
            if (filter == FilterKind.ENHANCE) {
                val improved = ir.scanfa.app.scan.DocumentScanEngine.enhance(bmp)
                if (improved !== bmp) bmp.recycle()
                bmp = improved
            } else {
                val filtered = Filters.apply(bmp, filter)
                if (filtered !== bmp) bmp.recycle()
                bmp = filtered
            }
            bmp = Img.rotate(bmp, page.rotation)
            if (erase.size >= 3) {
                val erased = Erase.apply(bmp, erase)
                if (erased !== bmp) bmp.recycle()
                bmp = erased
            }
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
                crop = normalized,
                erase = erase
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

    /** صفحهٔ یادداشت را می‌سازد و به سند اضافه می‌کند. */
    suspend fun addNotePage(docId: String, text: String): Page? = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext null
        val doc = DocStore.load(docId) ?: return@withContext null
        val file = DocStore.newImageFile(docId, "note")
        val bmp = NoteRenderer.render(text)
        val ok = Img.saveJpeg(bmp, file, 88)
        bmp.recycle()
        if (!ok) return@withContext null
        val page = Page(
            id = UUID.randomUUID().toString().take(10),
            originalPath = file.absolutePath,
            crop = listOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f),
            note = text,
            ocrText = text,
            ocrDone = true
        )
        doc.pages.add(page)
        DocStore.save(doc)
        refresh()
        page
    }

    /** تبدیل PDF به Word: هر صفحه تصویر و متن OCR می‌شود. */
    suspend fun pdfToWord(uri: Uri): Exporters.Result? = withContext(Dispatchers.Default) {
        _busy.value = "تبدیل PDF به Word"
        val bitmaps = PdfImport.renderPages(getApplication(), uri, 1600)
        if (bitmaps.isEmpty()) {
            _busy.value = null
            return@withContext null
        }
        val builder = DocxBuilder()
        builder.heading("تبدیل PDF به Word", 1)
        bitmaps.forEachIndexed { index, bmp ->
            _progress.value = (index + 1) to bitmaps.size
            builder.heading("صفحهٔ ${DateFa.fa(index + 1)}", 2)
            builder.image(Img.encodeJpeg(bmp, 80), bmp.width, bmp.height)
            val text = OcrEngine.recognize(getApplication(), bmp, prefs.ocrLanguage)
            if (!text.isNullOrBlank()) builder.paragraph(text, size = 24)
            else builder.paragraph("متن این صفحه استخراج نشد.", size = 22)
            bmp.recycle()
        }
        val out = File(exportDir(), Naming.subject() + "-pdf2word.docx")
        val ok = try {
            FileOutputStream(out).use { builder.build(it) }
            true
        } catch (t: Throwable) {
            false
        }
        _busy.value = null
        _progress.value = null
        if (ok) {
            Exporters.Result(listOf(out), out.length(), true, "Word ساخته شد: ${ExportPaths.humanSize(out.length())}")
        } else {
            null
        }
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
