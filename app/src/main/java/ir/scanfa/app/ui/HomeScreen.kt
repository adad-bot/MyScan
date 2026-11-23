package ir.scanfa.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import ir.scanfa.app.data.ScannerDoc
import ir.scanfa.app.util.Calendars
import ir.scanfa.app.util.DateFa
import ir.scanfa.app.vm.ScannerViewModel
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun HomeScreen(vm: ScannerViewModel, nav: NavHostController) {
    val docs by vm.docs.collectAsState()
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val id = vm.createDocument("", null)
                val page = vm.addPageFromUri(id, uri)
                if (page != null) {
                    nav.navigate("editor/$id/${page.id}")
                } else {
                    vm.postMessage("ورود تصویر ناموفق بود.")
                    nav.navigate("doc/$id")
                }
            }
        }
    }

    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val id = vm.createDocument("", null)
                val count = vm.importPdf(id, uri)
                vm.postMessage(
                    if (count > 0) "${DateFa.fa(count)} صفحه از PDF وارد شد."
                    else "PDF قابل خواندن نبود."
                )
                nav.navigate("doc/$id")
            }
        }
    }

    fun startScan() {
        val id = vm.createDocument("", null)
        nav.navigate("camera/$id")
    }

    var petalTrigger by remember { mutableIntStateOf(0) }
    var lastLogoTap by remember { mutableLongStateOf(0L) }

    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(scheme.background).navigationBarsPadding(),
        contentPadding = PaddingValues(bottom = 28.dp)
    ) {
        item {
            HomeHeader(
                dark = dark,
                onSettings = { nav.navigate("settings") },
                onLogoClick = {
                    val now = System.currentTimeMillis()
                    if (now - lastLogoTap < 400L) {
                        nav.navigate("about")
                    } else {
                        petalTrigger += 1
                    }
                    lastLogoTap = now
                },
                onScan = { startScan() },
                petalTrigger = petalTrigger,
                onPetalDone = { petalTrigger = 0 }
            )
        }
        item { DateBar(onClick = { nav.navigate("calendar") }) }
        item {
            QuickActions(
                onGallery = { imagePicker.launch("image/*") },
                onPdf = { pdfPicker.launch(arrayOf("application/pdf")) },
                onTools = { nav.navigate("tools") }
            )
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("سندهای اخیر", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(8.dp))
                if (docs.isNotEmpty()) {
                    Surface(
                        color = scheme.primaryContainer,
                        shape = CircleShape
                    ) {
                        Text(
                            DateFa.fa(docs.size),
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                if (docs.isNotEmpty()) {
                    Text(
                        "آخرین فعالیت",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant
                    )
                }
            }
        }
        if (docs.isEmpty()) {
            item { EmptyState(onScan = { startScan() }) }
        } else {
            items(docs, key = { it.id }) { doc ->
                DocCard(doc) { nav.navigate("doc/${doc.id}") }
            }
        }
    }

        if (petalTrigger > 0) {
            PetalRain(
                trigger = petalTrigger,
                modifier = Modifier.fillMaxSize().zIndex(2f),
                onDone = { petalTrigger = 0 }
            )
        }
    }
}

@Composable
private fun HomeHeader(
    dark: Boolean,
    onSettings: () -> Unit,
    onLogoClick: () -> Unit,
    onScan: () -> Unit,
    petalTrigger: Int,
    onPetalDone: () -> Unit
) {
    val start = if (dark) Color(0xFF10362F) else Color(0xFF0F766E)
    val end = if (dark) Color(0xFF0A1D19) else Color(0xFF0B4B43)

    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 30.dp, bottomEnd = 30.dp))
            .background(Brush.verticalGradient(listOf(start, end)))
    ) {
        Column(Modifier.statusBarsPadding().padding(horizontal = 20.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MyScanLogo(size = 64.dp, onClick = onLogoClick)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "MyScan",
                        color = Color(0xFFF3FFFA),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "علی دادمان",
                        color = Color(0xFFD8F3E8),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                IconButton(onClick = onSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "تنظیمات", tint = Color(0xFFEAFBF4))
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "اسکن تمیز و سریع اسناد",
                color = Color(0xFFEAFBF4),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = Color(0xFF9BE8C8),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "قاب سند خودکار تشخیص داده می‌شود",
                    color = Color(0xFFB9E6D6),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(Modifier.height(18.dp))
            val cardColor = if (dark) MaterialTheme.colorScheme.surface else Color.White
            val titleColor = if (dark) MaterialTheme.colorScheme.onSurface else Color(0xFF0B3B34)
            val subColor = if (dark) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFF4B6A62)
            val iconBg = if (dark) MaterialTheme.colorScheme.primaryContainer else Color(0xFF0F766E)
            val iconTint = if (dark) MaterialTheme.colorScheme.onPrimaryContainer else Color.White
            val chevronColor = if (dark) MaterialTheme.colorScheme.primary else Color(0xFF0F766E)
            Surface(
                color = cardColor,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth().pressClick { onScan() }
            ) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(iconBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.CameraAlt, contentDescription = null, tint = iconTint)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "اسکن سند جدید",
                            style = MaterialTheme.typography.titleMedium,
                            color = titleColor
                        )
                        Text(
                            "دوربین را باز کن، بقیه‌اش با MyScan",
                            style = MaterialTheme.typography.bodySmall,
                            color = subColor
                        )
                    }
                    Icon(Icons.Filled.ChevronLeft, contentDescription = null, tint = chevronColor)
                }
            }
            Spacer(Modifier.height(18.dp))
        }

    }
}

@Composable
private fun QuickActions(onGallery: () -> Unit, onPdf: () -> Unit, onTools: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        QuickAction("گالری", "از عکس", Icons.Filled.PhotoLibrary, Modifier.weight(1f), onGallery)
        QuickAction("PDF", "چند صفحه", Icons.Filled.Description, Modifier.weight(1f), onPdf)
        QuickAction("ابزارها", "افزونه‌ها", Icons.Filled.Widgets, Modifier.weight(1f), onTools)
    }
}

@Composable
private fun QuickAction(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.pressClick { onClick() },
        shape = RoundedCornerShape(18.dp),
        color = scheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp
    ) {
        Column(Modifier.padding(vertical = 14.dp, horizontal = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(scheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = scheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DocCard(doc: ScannerDoc, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp).pressClick { onClick() },
        shape = RoundedCornerShape(20.dp),
        color = scheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            val thumb = doc.pages.firstOrNull()?.filePath()?.let { File(it) }
            FileImage(
                file = thumb,
                modifier = Modifier.size(68.dp).clip(RoundedCornerShape(16.dp)),
                size = 220,
                contentDescription = doc.title
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    doc.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = scheme.primaryContainer, shape = CircleShape) {
                        Text(
                            "${DateFa.fa(doc.pages.size)} صفحه",
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        DateFa.short(doc.updatedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant
                    )
                }
                val template = doc.template()
                if (template != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        template.title,
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.primary
                    )
                }
            }
            Icon(Icons.Filled.ChevronLeft, contentDescription = null, tint = scheme.outline)
        }
    }
}

@Composable
private fun EmptyState(onScan: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        color = scheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, scheme.outlineVariant)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 34.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier.size(64.dp).clip(CircleShape).background(scheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.DocumentScanner,
                    contentDescription = null,
                    tint = scheme.onPrimaryContainer,
                    modifier = Modifier.size(34.dp)
                )
            }
            Spacer(Modifier.height(14.dp))
            Text("هنوز سندی نساخته‌اید", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                "با «اسکن سند جدید» شروع کنید یا عکس و PDF وارد کنید.",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Surface(
                color = scheme.primary,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.pressClick { onScan() }
            ) {
                Text(
                    "شروع اسکن",
                    color = scheme.onPrimary,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun DateBar(onClick: () -> Unit) {
    val today = remember { Calendars.todayJdn() }
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .pressClick { onClick() },
        shape = RoundedCornerShape(18.dp),
        color = scheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, scheme.outlineVariant)
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.CalendarMonth,
                contentDescription = "تقویم",
                tint = scheme.primary
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "${Calendars.weekdayName(today)} ${Calendars.jalaliText(today)}",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    "میلادی: ${Calendars.gregorianText(today)}  •  قمری: ${Calendars.hijriText(today)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant
                )
            }
            Icon(Icons.Filled.ChevronLeft, contentDescription = null, tint = scheme.outline)
        }
    }
}
