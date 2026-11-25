package ir.scanfa.app.ui

import android.app.Activity
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import ir.scanfa.app.scan.MlKitScanner
import ir.scanfa.app.scan.UvDocDewarper
import java.io.File
import java.io.FileOutputStream
import ir.scanfa.app.vm.ScannerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** ورود سند از گالری، فایل PDF یا دوربین. */
@Composable
fun ImportScreen(vm: ScannerViewModel, nav: NavHostController) {
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    val mlLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val data = res.data
            val uris = if (data != null) MlKitScanner.resultPages(data) else emptyList()
            if (uris.isEmpty()) {
                vm.postMessage("نتیجه‌ای از اسکنر گوگل نرسید.")
            } else {
                scope.launch {
                    val id = vm.createDocument("", null)
                    var count = 0
                    for (uri in uris) {
                        if (vm.addScannedPage(id, uri) != null) count++
                    }
                    if (count > 0) {
                        vm.postMessage("${ir.scanfa.app.util.DateFa.fa(count)} صفحه اضافه شد.")
                        nav.navigate("doc/$id")
                    } else {
                        vm.postMessage("خواندن صفحات ناموفق بود.")
                    }
                }
            }
        }
    }

    fun startMlKit() {
        val activity = context as? Activity
        if (activity == null || !MlKitScanner.available(context)) {
            vm.postMessage("سرویس اسکن گوگل روی این دستگاه نیست؛ از «تصویر از گالری» یا «دوربین» استفاده کن.")
            return
        }
        MlKitScanner.start(
            activity,
            onReady = { sender -> mlLauncher.launch(IntentSenderRequest.Builder(sender).build()) },
            onError = { msg -> vm.postMessage(msg) }
        )
    }

    val aiPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) {
            vm.postMessage("تصویری انتخاب نشد.")
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val decoded = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val options = BitmapFactory.Options().apply { inSampleSize = 2 }
                        BitmapFactory.decodeStream(stream, null, options)
                    }
                } catch (t: Throwable) {
                    null
                }
            }
            if (decoded == null) {
                vm.postMessage("خواندن تصویر ناموفق بود.")
                return@launch
            }
            vm.postMessage("در حال صاف‌کردن با هوش مصنوعی…")
            val dewarped = withContext(Dispatchers.IO) { UvDocDewarper.dewarp(context, decoded) }
            val refined = withContext(Dispatchers.IO) {
                val base = dewarped ?: return@withContext null
                ir.scanfa.app.scan.FlowDewarper.dewarp(context, base, 0.3f)
            }
            val processed = withContext(Dispatchers.IO) {
                val source = refined ?: dewarped ?: decoded
                val cropped = ir.scanfa.app.scan.OpenCvScanner.cropToPaper(source)
                val ready = cropped ?: source
                if (cropped != null && cropped !== source) source.recycle()
                ir.scanfa.app.scan.DocumentScanEngine.enhance(ready)
            }
            if (dewarped == null) vm.postMessage("مدل آفلاین در دسترس نبود؛ برش معمولی اعمال شد.")
            val file = File(context.cacheDir, "ai_scan_	${System.currentTimeMillis()}.png")
            try {
                FileOutputStream(file).use { out ->
                    processed.compress(android.graphics.Bitmap.CompressFormat.PNG, 95, out)
                }
            } catch (t: Throwable) {
                vm.postMessage("ذخیرهٔ نتیجه ناموفق بود.")
                return@launch
            }
            val id = vm.createDocument("", null)
            val page = vm.addScannedPage(id, android.net.Uri.fromFile(file))
            if (page != null) {
                vm.postMessage("صفحه با هوش مصنوعی آفلاین اضافه شد.")
                nav.navigate("editor/$id/${page.id}")
            } else {
                vm.postMessage("افزودن صفحه ناموفق بود.")
                nav.navigate("doc/$id")
            }
            processed.recycle()
            if (dewarped != null) dewarped.recycle()
            decoded.recycle()
        }
    }

    fun startAiScan() {
        aiPicker.launch(arrayOf("image/*"))
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) {
            vm.postMessage("تصویری انتخاب نشد.")
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val id = vm.createDocument("", null)
            val page = vm.addPageFromUri(id, uri)
            if (page != null) {
                nav.navigate("editor/$id/${page.id}")
            } else {
                vm.postMessage("خواندن تصویر ناموفق بود؛ فایل دیگری امتحان کن.")
                nav.navigate("doc/$id")
            }
        }
    }

    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) {
            vm.postMessage("فایلی انتخاب نشد.")
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val id = vm.createDocument("", null)
            val count = vm.importPdf(id, uri)
            if (count > 0) {
                vm.postMessage("${ir.scanfa.app.util.DateFa.fa(count)} صفحه از PDF اضافه شد.")
                nav.navigate("doc/$id")
            } else {
                vm.postMessage("این PDF خوانده نشد.")
                nav.navigate("doc/$id")
            }
        }
    }

    Scaffold(topBar = { ScreenBar("وارد کردن سند", onBack = { nav.popBackStack() }) }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(18.dp)
        ) {
            Text("سند تازه", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "از گالری، فایل PDF یا دوربین وارد کن؛ بعد در ویرایشگر قاب و فیلتر را تنظیم کن.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))

            ImportTile(
                icon = Icons.Filled.AutoAwesome,
                title = "اسکن هوشمند (گوگل)",
                subtitle = "تشخیص، برش، صاف‌کردن و بهبود خودکار — نیازمند سرویس گوگل",
            ) { startMlKit() }

            ImportTile(
                icon = Icons.Filled.AutoFixHigh,
                title = "اسکن هوشمند (آفلاین)",
                subtitle = "صاف‌کردن خمیدگی با مدل هوش مصنوعی داخل اپ — بدون اینترنت",
            ) { startAiScan() }

            ImportTile(
                icon = Icons.Filled.PhotoLibrary,
                title = "تصویر از گالری",
                subtitle = "JPG، PNG و تصاویر گوشی",
            ) { imagePicker.launch(arrayOf("image/*")) }

            ImportTile(
                icon = Icons.Filled.PictureAsPdf,
                title = "فایل PDF",
                subtitle = "هر صفحه PDF به صفحهٔ سند تبدیل می‌شود",
            ) { pdfPicker.launch(arrayOf("application/pdf")) }

            ImportTile(
                icon = Icons.Filled.CameraAlt,
                title = "عکس با دوربین",
                subtitle = "تشخیص خودکار قاب یا تنظیم دستی",
            ) {
                val id = vm.createDocument("", null)
                nav.navigate("camera/$id")
            }

            Spacer(Modifier.height(14.dp))
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    "اگر فایل باز نشد، مطمئن شو فایل روی گوشی ذخیره شده و دسترسی فایل‌ها را رد نکرده‌ای.",
                    modifier = Modifier.padding(14.dp),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun ImportTile(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).pressClick { onClick() },
        shape = RoundedCornerShape(22.dp),
        color = scheme.surface,
        shadowElevation = 2.dp
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(52.dp)
                    .then(Modifier)
                    .padding(0.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(shape = RoundedCornerShape(16.dp), color = scheme.primaryContainer) {
                    Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = scheme.onPrimaryContainer)
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, fontSize = 12.sp, color = scheme.onSurfaceVariant)
            }
            Text("›", fontSize = 24.sp, color = scheme.outline)
        }
    }
}
