package ir.scanfa.app.ui

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import ir.scanfa.app.data.ExportFormat
import ir.scanfa.app.data.PaperSize
import ir.scanfa.app.data.ScannerDoc
import ir.scanfa.app.export.ExportPaths
import ir.scanfa.app.export.Exporters
import ir.scanfa.app.export.ShareUtil
import ir.scanfa.app.util.DateFa
import ir.scanfa.app.vm.ScannerViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(vm: ScannerViewModel, nav: NavHostController, docId: String) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val doc: ScannerDoc? = remember(docId) { vm.load(docId) }

    var format by remember { mutableStateOf(ExportFormat.PDF_IMAGE) }
    var paper by remember { mutableStateOf(PaperSize.of(doc?.paperSize)) }
    var capKb by remember { mutableStateOf<Int?>(doc?.sizeCapKb ?: 1024) }
    var showCustom by remember { mutableStateOf(false) }
    var customText by remember { mutableStateOf("1500") }
    var generating by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Exporters.Result?>(null) }

    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val r = result
        if (uri != null && r != null && r.files.isNotEmpty()) {
            val ok = ShareUtil.copyTo(context, r.files.first(), uri)
            vm.postMessage(if (ok) "فایل در محل انتخابی ذخیره شد." else "ذخیره ناموفق بود.")
        }
    }

    fun generate() {
        scope.launch {
            generating = true
            result = vm.export(docId, null, format, paper, capKb)
            generating = false
        }
    }

    Scaffold(topBar = { ScreenBar("خروجی سند", onBack = { nav.popBackStack() }) }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("قالب خروجی", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    for (option in ExportFormat.entries) {
                        SelectionRow(
                            selected = format == option,
                            title = option.label,
                            subtitle = describe(option),
                            onClick = {
                                format = option
                                result = null
                            }
                        )
                    }
                }
            }

            Card(shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("اندازهٔ کاغذ", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (p in PaperSize.entries) {
                            FilterChip(
                                selected = paper == p,
                                onClick = { paper = p },
                                label = { Text(p.label) }
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("سقف حجم فایل", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CapChip("۵۰۰ کیلوبایت", capKb == 500) { capKb = 500 }
                        CapChip("۱ مگابایت", capKb == 1024) { capKb = 1024 }
                        CapChip("۲ مگابایت", capKb == 2048) { capKb = 2048 }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CapChip("بدون سقف", capKb == null) { capKb = null }
                        CapChip("مقدار دلخواه", false) { showCustom = true }
                    }
                    HintText(
                        if (capKb == null) "بدون سقف: کیفیت کامل تصویر حفظ می‌شود."
                        else "اگر با حفظ خوانایی به سقف نرسیم، اپ پیشنهاد تفکیک فایل می‌دهد."
                    )
                }
            }

            Button(
                onClick = { generate() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !generating
            ) {
                Text(if (generating) "در حال ساخت…" else "ساخت خروجی")
            }

            val r = result
            if (r != null) {
                Card(shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            if (r.files.isEmpty()) "خروجی ساخته نشد" else "خروجی آماده است",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(r.message, style = MaterialTheme.typography.bodyMedium)
                        if (r.files.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "حجم: ${ExportPaths.humanSize(r.totalBytes)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                r.files.first().name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { ShareUtil.shareMany(context, r.files, format.mime) }) {
                                    Icon(Icons.Filled.Share, contentDescription = null)
                                    Spacer(Modifier.height(0.dp))
                                    Text("ارسال")
                                }
                                TextButton(onClick = { saver.launch(r.files.first().name) }) {
                                    Text("ذخیره در پوشه")
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider()
            HintText(
                "خروجی‌ها در پوشهٔ داخلی اپ ساخته می‌شوند و پس از ساخت، با «ارسال» به هر برنامه‌ای (واتساپ، تلگرام، ایمیل، مرورگر) فرستاده یا در محل انتخابی ذخیره می‌شوند."
            )
        }
    }

    if (showCustom) {
        AlertDialog(
            onDismissRequest = { showCustom = false },
            title = { Text("سقف حجم (کیلوبایت)") },
            text = {
                OutlinedTextField(
                    value = customText,
                    onValueChange = { customText = it.filter { ch -> ch.isDigit() } },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    capKb = customText.toIntOrNull()?.coerceIn(80, 20000) ?: 1024
                    showCustom = false
                }) { Text("ثبت") }
            },
            dismissButton = { TextButton(onClick = { showCustom = false }) { Text("انصراف") } }
        )
    }
}

@Composable
private fun SelectionRow(
    selected: Boolean,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(Modifier.padding(end = 8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CapChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

private fun describe(format: ExportFormat): String = when (format) {
    ExportFormat.PDF_IMAGE -> "تصویر هر صفحه با ترتیب، بدون متن قابل انتخاب"
    ExportFormat.PDF_SEARCHABLE -> "تصویر + لایهٔ متن برای جست‌وجو و انتخاب"
    ExportFormat.DOCX -> "فایل Word راست‌به‌چپ با تصویر و متن editable"
    ExportFormat.JPG -> "هر صفحه یک فایل تصویری مستقل"
    ExportFormat.TXT -> "متن خام همهٔ صفحات در یک فایل"
}
