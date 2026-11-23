package ir.scanfa.app.export

import android.net.Uri
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/** اشتراک‌گذاری و ذخیرهٔ فایل خروجی با مجوز موقت. */
object ShareUtil {

    fun share(ctx: Context, file: File, mime: String) {
        shareMany(ctx, listOf(file), mime)
    }

    fun shareMany(ctx: Context, files: List<File>, mime: String) {
        if (files.isEmpty()) return
        val uris = ArrayList<Uri>()
        for (f in files) {
            try {
                uris.add(FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f))
            } catch (t: Throwable) {
                // فایل خارج از مسیرهای مجاز
            }
        }
        if (uris.isEmpty()) return
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uris[0])
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = mime
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            }
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val chooser = Intent.createChooser(intent, "ارسال فایل")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            ctx.startActivity(chooser)
        } catch (t: Throwable) {
            // مقصد سازگاری پیدا نشد
        }
    }

    fun copyTo(ctx: Context, file: File, target: Uri): Boolean = try {
        ctx.contentResolver.openOutputStream(target)?.use { out ->
            file.inputStream().use { input -> input.copyTo(out) }
        } != null
    } catch (t: Throwable) {
        false
    }

    /** ذخیرهٔ فایل‌ها در پوشهٔ دانلودها؛ نام ذخیره‌شده‌ها را برمی‌گرداند. */
    fun saveToDownloads(ctx: Context, files: List<File>): List<String> {
        val saved = ArrayList<String>()
        for (file in files) {
            if (!file.exists()) continue
            val mime = mimeFor(file.extension)
            try {
                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    val values = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                        put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mime)
                        put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS + "/MyScan")
                    }
                    val uri = ctx.contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    if (uri != null) {
                        ctx.contentResolver.openOutputStream(uri)?.use { out ->
                            file.inputStream().use { input -> input.copyTo(out) }
                        }
                        saved.add(file.name)
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val dir = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "MyScan")
                    if (!dir.exists()) dir.mkdirs()
                    val target = File(dir, file.name)
                    file.copyTo(target, overwrite = true)
                    saved.add(target.absolutePath)
                }
            } catch (t: Throwable) {
                // فایل بعدی
            }
        }
        return saved
    }

    fun mimeFor(ext: String): String = when (ext.lowercase()) {
        "pdf" -> "application/pdf"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "jpg", "jpeg" -> "image/jpeg"
        "txt" -> "text/plain"
        else -> "application/octet-stream"
    }
}
