package ir.scanfa.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
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
    var haptics by remember { mutableStateOf(vm.prefs.hapticsEnabled) }
    var theme by remember { mutableStateOf(vm.prefs.themeMode) }

    Scaffold(topBar = { ScreenBar("تنظیمات", onBack = { nav.popBackStack() }) }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SettingCard("ظاهر برنامه") {
                Text(
                    "تم دلخواهت را انتخاب کن؛ تغییر فوری اعمال می‌شود.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ThemeOption("روشن", Icons.Filled.LightMode, theme == "light", Modifier.weight(1f)) {
                        theme = "light"
                        vm.setThemeMode("light")
                    }
                    ThemeOption("تیره", Icons.Filled.DarkMode, theme == "dark", Modifier.weight(1f)) {
                        theme = "dark"
                        vm.setThemeMode("dark")
                    }
                    ThemeOption("سیستم", Icons.Filled.BrightnessAuto, theme == "system", Modifier.weight(1f)) {
                        theme = "system"
                        vm.setThemeMode("system")
                    }
                }
            }

            SettingCard("متن‌خوانی فارسی") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LanguageChip("فارسی", "fas", language) { language = it; vm.prefs.ocrLanguage = it }
                    LanguageChip("انگلیسی", "eng", language) { language = it; vm.prefs.ocrLanguage = it }
                    LanguageChip("هر دو", "fas+eng", language) { language = it; vm.prefs.ocrLanguage = it }
                }
                Spacer(Modifier.height(6.dp))
                HintText("فایل زبان داخل اپ است و پردازش روی دستگاه انجام می‌شود.")
            }

            SettingCard("بازخورد لمسی") {
                SwitchRow(
                    "لرزش کوتاه دکمه‌ها",
                    "با هر لمس، یک لرزش بسیار کوتاه حس می‌شود.",
                    haptics
                ) {
                    haptics = it
                    vm.setHaptics(it)
                }
            }

            SettingCard("دوربین و اسکن") {
                SwitchRow(
                    "تشخیص خودکار قاب",
                    "گوشه‌های سند هنگام ورود عکس پیدا و اصلاح می‌شود.",
                    autoDetect
                ) {
                    autoDetect = it
                    vm.prefs.autoDetect = it
                }
                Text(
                    "ثبت خودکار را در همان صفحهٔ دوربین، بالای دکمهٔ عکس‌برداری تیک بزنید.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SettingCard("کیفیت خروجی") {
                Text(
                    "کیفیت تصویر: ${DateFa.fa(quality)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = quality.toFloat(),
                    onValueChange = {
                        quality = it.toInt()
                        vm.prefs.jpegQuality = quality
                    },
                    valueRange = 60f..95f
                )
                Text(
                    "بیشترین اندازهٔ صفحه: ${DateFa.fa(maxSize)} پیکسل",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = maxSize.toFloat(),
                    onValueChange = {
                        maxSize = (it.toInt() / 100) * 100
                        vm.prefs.maxPageSize = maxSize
                    },
                    valueRange = 1200f..3000f
                )
            }

            SettingCard("سقف حجم پیش‌فرض") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (option in listOf(500, 1024, 2048)) {
                        FilterChip(
                            selected = cap == option,
                            onClick = {
                                cap = option
                                vm.prefs.defaultCapKb = option
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            label = {
                                Text(
                                    if (option < 1024) "${DateFa.fa(option)} کیلوبایت"
                                    else "${DateFa.fa(option / 1024)} مگابایت"
                                )
                            }
                        )
                    }
                }
            }

            SettingCard("حافظه") {
                SwitchRow(
                    "حفظ تصویر اصل",
                    "تصویر اصلی بعد از پردازش نگه داشته می‌شود.",
                    keepOriginal
                ) {
                    keepOriginal = it
                    vm.prefs.keepOriginal = it
                }
                TextButton(onClick = {
                    val count = vm.clearExports()
                    vm.postMessage("${DateFa.fa(count)} فایل خروجی پاک شد.")
                }) { Text("پاک‌کردن فایل‌های خروجی") }
            }

            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(46.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.DocumentScanner, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "MyScan",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            "علی دادمان",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                        Text(
                            "نسخهٔ ۱.۰ — پردازش محلی و بدون حساب",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun SettingCard(title: String, content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun ThemeOption(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.pressClick { onClick() },
        shape = RoundedCornerShape(18.dp),
        color = if (selected) scheme.primary else scheme.surfaceVariant,
        border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, scheme.outlineVariant)
    ) {
        Column(
            Modifier.padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) scheme.onPrimary else scheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) scheme.onPrimary else scheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LanguageChip(label: String, value: String, current: String, onSelect: (String) -> Unit) {
    FilterChip(
        selected = current == value,
        onClick = { onSelect(value) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        label = { Text(label) }
    )
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
