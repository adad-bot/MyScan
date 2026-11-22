package ir.scanfa.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import ir.scanfa.app.util.DateFa
import ir.scanfa.app.vm.ScannerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: ScannerViewModel, nav: NavHostController) {
    var language by remember { mutableStateOf(vm.prefs.ocrLanguage) }
    var quality by remember { mutableStateOf(vm.prefs.jpegQuality) }
    var maxSize by remember { mutableStateOf(vm.prefs.maxPageSize) }
    var cap by remember { mutableStateOf(vm.prefs.defaultCapKb) }
    var keepOriginal by remember { mutableStateOf(vm.prefs.keepOriginal) }
    var autoDetect by remember { mutableStateOf(vm.prefs.autoDetect) }

    Scaffold(topBar = { ScreenBar("تنظیمات", onBack = { nav.popBackStack() }) }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("زبان متن‌خوانی", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LanguageChip("فارسی", "fas", language) {
                            language = it
                            vm.prefs.ocrLanguage = it
                        }
                        LanguageChip("انگلیسی", "eng", language) {
                            language = it
                            vm.prefs.ocrLanguage = it
                        }
                        LanguageChip("هر دو", "fas+eng", language) {
                            language = it
                            vm.prefs.ocrLanguage = it
                        }
                    }
                    HintText("فایل زبان فارسی (fas) داخل اپ است و پردازش روی دستگاه انجام می‌شود.")
                }
            }

            Card(shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("کیفیت تصویر خروجی: ${DateFa.fa(quality)}", style = MaterialTheme.typography.titleSmall)
                    Slider(
                        value = quality.toFloat(),
                        onValueChange = { v ->
                            quality = v.toInt()
                            vm.prefs.jpegQuality = quality
                        },
                        valueRange = 60f..95f
                    )
                    Text("بیشترین اندازهٔ صفحه: ${DateFa.fa(maxSize)} پیکسل", style = MaterialTheme.typography.titleSmall)
                    Slider(
                        value = maxSize.toFloat(),
                        onValueChange = { v ->
                            maxSize = (v.toInt() / 100) * 100
                            vm.prefs.maxPageSize = maxSize
                        },
                        valueRange = 1200f..3000f
                    )
                    HintText("اندازهٔ بزرگ‌تر، متن ریز را خواناتر می‌کند اما حجم و زمان پردازش را بالا می‌برد.")
                }
            }

            Card(shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("سقف حجم پیش‌فرض", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (option in listOf(500, 1024, 2048)) {
                            FilterChip(
                                selected = cap == option,
                                onClick = {
                                    cap = option
                                    vm.prefs.defaultCapKb = option
                                },
                                label = { Text(if (option < 1024) "${DateFa.fa(option)} کیلوبایت" else "${DateFa.fa(option / 1024)} مگابایت") }
                            )
                        }
                    }
                }
            }

            Card(shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(12.dp)) {
                    SwitchRow("حفظ تصویر اصل", "تصویر اصلی بعد از پردازش نگه داشته می‌شود.", keepOriginal) {
                        keepOriginal = it
                        vm.prefs.keepOriginal = it
                    }
                    SwitchRow("تشخیص خودکار قاب", "قاب سند هنگام ثبت خودکار پیدا می‌شود.", autoDetect) {
                        autoDetect = it
                        vm.prefs.autoDetect = it
                    }
                }
            }

            Card(shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("فضای ذخیره‌سازی", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = {
                        val count = vm.clearExports()
                        vm.postMessage("${DateFa.fa(count)} فایل خروجی پاک شد.")
                    }) { Text("پاک‌کردن فایل‌های خروجی") }
                    HintText("سندها و تصاویر اصلی حذف نمی‌شوند؛ فقط خروجی‌های ساخته‌شده پاک می‌شوند.")
                }
            }

            HintText("اسکنر سند — نسخهٔ ۱.۰ | پردازش محلی، بدون نیاز به حساب کاربری")
        }
    }
}

@Composable
private fun LanguageChip(label: String, value: String, current: String, onSelect: (String) -> Unit) {
    FilterChip(selected = current == value, onClick = { onSelect(value) }, label = { Text(label) })
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
