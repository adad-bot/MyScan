package ir.scanfa.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WaterDrop
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import ir.scanfa.app.vm.ScannerViewModel
import kotlinx.coroutines.launch

/** افزونه‌ها: ابزارهای اسکن، تبدیل و ویرایش در یک صفحه. */
@Composable
fun ToolsScreen(vm: ScannerViewModel, nav: NavHostController) {
    val scope = rememberCoroutineScope()

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val id = vm.createDocument("", null)
                val page = vm.addPageFromUri(id, uri)
                if (page != null) nav.navigate("editor/$id/${page.id}") else nav.navigate("doc/$id")
            }
        }
    }

    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val id = vm.createDocument("", null)
                vm.importPdf(id, uri)
                nav.navigate("doc/$id")
            }
        }
    }

    fun soon(name: String) {
        vm.postMessage("«$name» در نسخهٔ بعد فعال می‌شود.")
    }

    Scaffold(topBar = { ScreenBar("افزونه‌ها", onBack = { nav.popBackStack() }) }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                "ابزارهای MyScan",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "هر ابزار را انتخاب کن؛ ابزارهای آماده همین حالا کار می‌کنند.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            SectionLabel("اسکن و ورود")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ToolCard("اسکن سند", Icons.Filled.CameraAlt, Modifier.weight(1f), enabled = true) {
                    val id = vm.createDocument("", null)
                    nav.navigate("camera/$id")
                }
                ToolCard("از گالری", Icons.Filled.PhotoLibrary, Modifier.weight(1f), enabled = true) {
                    imagePicker.launch("image/*")
                }
                ToolCard("از PDF", Icons.Filled.PictureAsPdf, Modifier.weight(1f), enabled = true) {
                    pdfPicker.launch(arrayOf("application/pdf"))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ToolCard("الگوهای مدارک", Icons.Filled.GridOn, Modifier.weight(1f), enabled = true) {
                    nav.navigate("templates")
                }
                ToolCard("اسکن کارت", Icons.Filled.DocumentScanner, Modifier.weight(1f), enabled = false) {
                    soon("اسکن کارت شناسایی")
                }
                ToolCard("اسکن کتاب", Icons.Filled.CloudUpload, Modifier.weight(1f), enabled = false) {
                    soon("اسکن کتاب")
                }
            }

            SectionLabel("ویرایش و بهبود")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ToolCard("پاک کردن هوشمند", Icons.Filled.AutoFixHigh, Modifier.weight(1f), enabled = true) {
                    vm.postMessage("صفحهٔ موردنظر را در «ویرایش صفحه» باز کن و گزینهٔ پاک کردن را انتخاب کن.")
                }
                ToolCard("مهر زمانی", Icons.Filled.Timer, Modifier.weight(1f), enabled = false) {
                    soon("مهر زمانی")
                }
                ToolCard("امضا", Icons.Filled.Draw, Modifier.weight(1f), enabled = false) {
                    soon("امضا")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ToolCard("واترمارک", Icons.Filled.WaterDrop, Modifier.weight(1f), enabled = false) {
                    soon("واترمارک")
                }
                ToolCard("پاک‌کردن پس‌زمینه", Icons.Filled.BorderColor, Modifier.weight(1f), enabled = false) {
                    soon("پاک‌کردن پس‌زمینه")
                }
            }

            SectionLabel("متن و خروجی")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ToolCard("استخراج متن", Icons.Filled.TextFields, Modifier.weight(1f), enabled = true) {
                    vm.postMessage("در صفحهٔ سند، دکمهٔ «متن» را بزن تا متن‌خوانی انجام شود.")
                }
                ToolCard("خروجی Word", Icons.Filled.Description, Modifier.weight(1f), enabled = true) {
                    vm.postMessage("در صفحهٔ سند، «خروجی» را بزن و قالب Word را انتخاب کن.")
                }
                ToolCard("PDF جست‌وجوپذیر", Icons.Filled.PictureAsPdf, Modifier.weight(1f), enabled = true) {
                    vm.postMessage("در صفحهٔ خروجی، «PDF قابل جست‌وجو» را انتخاب کن.")
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 20.dp, bottom = 10.dp)
    )
}

@Composable
private fun ToolCard(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.pressClick { onClick() },
        shape = RoundedCornerShape(20.dp),
        color = scheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, scheme.outlineVariant)
    ) {
        Column(
            Modifier.padding(vertical = 16.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .background(if (enabled) scheme.primaryContainer else scheme.surfaceVariant, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (enabled) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                color = if (enabled) scheme.onSurface else scheme.onSurfaceVariant
            )
            if (!enabled) {
                Spacer(Modifier.height(2.dp))
                Text("به‌زودی", style = MaterialTheme.typography.labelSmall, color = scheme.tertiary)
            }
        }
    }
}
