package ir.scanfa.app.scan

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

/**
 * اسکنر سند گوگل (ML Kit Document Scanner):
 * تشخیص لبه، برش، صاف‌کردن خمیدگی و بهبود تصویر روی دستگاه.
 * به سرویس‌های گوگل پلی نیاز دارد.
 */
object MlKitScanner {

    fun available(context: Context): Boolean {
        return try {
            GoogleApiAvailability.getInstance()
                .isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
        } catch (t: Throwable) {
            false
        }
    }

    fun start(
        activity: Activity,
        onReady: (IntentSender) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val options = GmsDocumentScannerOptions.Builder()
                .setGalleryImportAllowed(true)
                .setPageLimit(20)
                .setResultFormats(
                    GmsDocumentScannerOptions.RESULT_FORMAT_JPEG,
                    GmsDocumentScannerOptions.RESULT_FORMAT_PDF
                )
                .build()
            val client = GmsDocumentScanning.getClient(options)
            client.getStartScanIntent(activity)
                .addOnSuccessListener { sender -> onReady(sender) }
                .addOnFailureListener { e -> onError(e.message ?: "اسکنر گوگل در دسترس نیست") }
        } catch (t: Throwable) {
            onError("سرویس اسکن گوگل روی این دستگاه نیست")
        }
    }

    fun resultPages(data: Intent): List<Uri> {
        return try {
            val result = GmsDocumentScanningResult.fromActivityResultIntent(data) ?: return emptyList()
            result.pages?.mapNotNull { it.imageUri } ?: emptyList()
        } catch (t: Throwable) {
            emptyList()
        }
    }
}
