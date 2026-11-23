package ir.scanfa.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.random.Random

/**
 * ذخیره‌سازی سندها روی فضای اختصاصی اپ.
 *
 * ساختار: files/docs/<docId>/meta.json به همراه تصاویر همان پوشه.
 * نوشتن متادیتا اتمیک است تا قطع برنامه سند را خراب نکند.
 */
object DocStore {
    private lateinit var root: File

    fun init(ctx: Context) {
        root = File(ctx.filesDir, "docs")
        if (!root.exists()) root.mkdirs()
    }

    fun rootDir(): File = root

    fun docDir(id: String): File {
        val d = File(root, id)
        if (!d.exists()) d.mkdirs()
        return d
    }

    fun newImageFile(docId: String, tag: String): File {
        val name = "${tag}_${System.currentTimeMillis()}_${Random.nextInt(1000, 9999)}.jpg"
        return File(docDir(docId), name)
    }

    fun list(): List<ScannerDoc> {
        val dirs = root.listFiles() ?: return emptyList()
        return dirs.filter { it.isDirectory }
            .mapNotNull { readDoc(it) }
            .sortedByDescending { it.updatedAt }
    }

    fun load(id: String): ScannerDoc? = readDoc(File(root, id))

    fun save(doc: ScannerDoc) {
        doc.updatedAt = System.currentTimeMillis()
        val dir = docDir(doc.id)
        val tmp = File(dir, "meta.json.tmp")
        val target = File(dir, "meta.json")
        tmp.writeText(toJson(doc).toString(), Charsets.UTF_8)
        if (target.exists() && !target.delete()) {
            // آخرین تلاش: بازنویسی مستقیم
            target.writeText(tmp.readText(), Charsets.UTF_8)
            tmp.delete()
            return
        }
        if (!tmp.renameTo(target)) {
            target.writeText(tmp.readText(), Charsets.UTF_8)
            tmp.delete()
        }
    }

    fun delete(id: String) {
        File(root, id).deleteRecursively()
    }

    private fun readDoc(dir: File): ScannerDoc? {
        val meta = File(dir, "meta.json")
        if (!meta.exists()) return null
        return try {
            fromJson(JSONObject(meta.readText(Charsets.UTF_8)), dir.name)
        } catch (t: Throwable) {
            null
        }
    }

    private fun toJson(doc: ScannerDoc): JSONObject = JSONObject().apply {
        put("id", doc.id)
        put("title", doc.title)
        put("createdAt", doc.createdAt)
        put("updatedAt", doc.updatedAt)
        put("paper", doc.paperSize)
        put("capKb", doc.sizeCapKb)
        if (doc.templateId != null) put("templateId", doc.templateId)
        put("pages", JSONArray().apply { doc.pages.forEach { put(pageJson(it)) } })
    }

    private fun pageJson(p: Page): JSONObject = JSONObject().apply {
        put("id", p.id)
        put("original", p.originalPath)
        if (!p.processedPath.isNullOrBlank()) put("processed", p.processedPath)
        put("filter", p.filter)
        put("rotation", p.rotation)
        put("ocrDone", p.ocrDone)
        if (!p.note.isNullOrBlank()) put("note", p.note)
        if (!p.ocrText.isNullOrEmpty()) put("ocrText", p.ocrText)
        if (!p.ocrEdited.isNullOrEmpty()) put("ocrEdited", p.ocrEdited)
        if (!p.slotId.isNullOrBlank()) put("slot", p.slotId)
        if (p.erase.isNotEmpty()) {
            put("erase", JSONArray().apply { p.erase.forEach { put(it.toDouble()) } })
        }
        val crop = p.crop
        if (crop != null && crop.size == 8) {
            put("crop", JSONArray().apply { crop.forEach { put(it.toDouble()) } })
        }
    }

    private fun fromJson(obj: JSONObject, dirName: String): ScannerDoc {
        val pages = mutableListOf<Page>()
        val arr = obj.optJSONArray("pages")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val p = arr.optJSONObject(i) ?: continue
                pages.add(pageFromJson(p))
            }
        }
        return ScannerDoc(
            id = obj.optString("id", dirName),
            title = obj.optString("title", "سند"),
            createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
            updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
            templateId = obj.stringOrNull("templateId"),
            paperSize = obj.optString("paper", PaperSize.A4.id),
            sizeCapKb = obj.optInt("capKb", 1024),
            pages = pages
        )
    }

    private fun pageFromJson(p: JSONObject): Page {
        val cropArr = p.optJSONArray("crop")
        var crop: List<Float>? = null
        if (cropArr != null && cropArr.length() == 8) {
            val list = ArrayList<Float>(8)
            for (i in 0 until 8) list.add(cropArr.optDouble(i, 0.0).toFloat())
            crop = list
        }
        val eraseArr = p.optJSONArray("erase")
        val erase = ArrayList<Float>()
        if (eraseArr != null) {
            for (i in 0 until eraseArr.length()) erase.add(eraseArr.optDouble(i, 0.0).toFloat())
        }
        return Page(
            id = p.optString("id"),
            originalPath = p.optString("original"),
            processedPath = p.stringOrNull("processed"),
            filter = p.optString("filter", FilterKind.ORIGINAL.id),
            rotation = p.optInt("rotation", 0),
            crop = crop,
            erase = erase,
            note = p.stringOrNull("note"),
            ocrText = p.stringOrNull("ocrText"),
            ocrEdited = p.stringOrNull("ocrEdited"),
            slotId = p.stringOrNull("slot"),
            ocrDone = p.optBoolean("ocrDone", false)
        )
    }

    private fun JSONObject.stringOrNull(key: String): String? {
        if (!has(key) || isNull(key)) return null
        val s = optString(key, "")
        return s.ifBlank { null }
    }
}
