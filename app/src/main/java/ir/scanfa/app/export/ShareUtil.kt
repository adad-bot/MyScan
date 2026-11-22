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

    fun mimeFor(ext: String): String = when (ext.lowercase()) {
        "pdf" -> "application/pdf"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "jpg", "jpeg" -> "image/jpeg"
        "txt" -> "text/plain"
        else -> "application/octet-stream"
    }
}
