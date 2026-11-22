package ir.scanfa.app.export

import android.graphics.BitmapFactory
import java.io.File

/** مکان خروجی‌ها و کمک‌های مسیر فایل. */
object ExportPaths {

    fun exportsDir(root: File): File {
        val dir = File(root, "exports")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun safeName(input: String, fallback: String = "سند"): String {
        val cleaned = input.trim()
            .replace(Regex("[\\\\/:*?\"<>|]"), "-")
            .replace(Regex("\\s+"), "_")
            .take(48)
        return cleaned.ifBlank { fallback }
    }

    fun uniqueFile(dir: File, base: String, ext: String): File {
        var file = File(dir, "$base.$ext")
        var i = 2
        while (file.exists()) {
            file = File(dir, "${base}_$i.$ext")
            i++
        }
        return file
    }

    fun jpegSize(file: File): IntArray? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        if (opts.outWidth <= 0) return null
        return intArrayOf(opts.outWidth, opts.outHeight)
    }

    fun humanSize(bytes: Long): String = when {
        bytes >= 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1048576.0)
        bytes >= 1024 -> String.format(java.util.Locale.US, "%d KB", bytes / 1024)
        else -> "$bytes B"
    }
}
