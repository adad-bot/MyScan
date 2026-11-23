package ir.scanfa.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
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
import ir.scanfa.app.vm.ScannerViewModel
import kotlinx.coroutines.launch

/** ورود سند از گالری، فایل PDF یا دوربین. */
@Composable
fun ImportScreen(vm: ScannerViewModel, nav: NavHostController) {
    val scope = rememberCoroutineScope()

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
