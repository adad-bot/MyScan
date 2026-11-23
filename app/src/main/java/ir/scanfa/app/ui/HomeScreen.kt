package ir.scanfa.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import ir.scanfa.app.data.ScannerDoc
import ir.scanfa.app.util.Calendars
import ir.scanfa.app.util.DateFa
import ir.scanfa.app.util.Occasions
import ir.scanfa.app.vm.ScannerViewModel
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun HomeScreen(vm: ScannerViewModel, nav: NavHostController) {
    val docs by vm.docs.collectAsState()
    val scope = rememberCoroutineScope()

    var petalTrigger by remember { mutableIntStateOf(0) }
    var lastLogoTap by remember { mutableLongStateOf(0L) }

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

    fun startScan() {
        val id = vm.createDocument("", null)
        nav.navigate("camera/$id")
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            HomeBottomBar(
                onHome = {},
                onCalendar = { nav.navigate("calendar") },
                onScan = { startScan() },
                onTools = { nav.navigate("tools") },
                onSettings = { nav.navigate("settings") }
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                item {
                    HomeTopBar(
                        onSettings = { nav.navigate("settings") },
                        onLock = { vm.lockNow() },
                        onLogoClick = {
                            val now = System.currentTimeMillis()
                            if (now - lastLogoTap < 400L) nav.navigate("about") else petalTrigger += 1
                            lastLogoTap = now
                        }
                    )
                }
                item { DateCard(onCalendar = { nav.navigate("calendar") }) }
                item {
                    ShortcutRow(
                        onCalendar = { nav.navigate("calendar") },
                        onTools = { nav.navigate("tools") },
                        onGallery = { imagePicker.launch("image/*") },
                        onPdf = { pdfPicker.launch(arrayOf("application/pdf")) }
                    )
                }
                item { ScanCard(onScan = { startScan() }) }
                item { SectionHeader("سندهای اخیر", docs.size) }
                if (docs.isEmpty()) {
                    item { EmptyDocs(onScan = { startScan() }) }
                } else {
                    items(docs, key = { it.id }) { doc ->
                        DocCard(doc) { nav.navigate("doc/${doc.id}") }
                    }
                }
            }

            if (petalTrigger > 0) {
                PetalRain(
                    trigger = petalTrigger,
                    modifier = Modifier.fillMaxSize(),
                    onDone = { petalTrigger = 0 }
                )
            }
        }
    }
}

@Composable
private fun HomeTopBar(
    onSettings: () -> Unit,
    onLock: () -> Unit,
    onLogoClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SquareIconButton(Icons.Filled.Settings, "تنظیمات", onSettings)
        Spacer(Modifier.width(8.dp))
        SquareIconButton(Icons.Filled.Lock, "قفل", onLock)
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text("MyScan", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = scheme.onSurface)
            Text("علی دادمان", fontSize = 14.sp, color = scheme.primary)
        }
        Spacer(Modifier.width(10.dp))
        MyScanLogo(size = 52.dp, onClick = onLogoClick)
    }
}

@Composable
private fun SquareIconButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.size(48.dp).pressClick { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DateCard(onCalendar: () -> Unit) {
    val today = remember { Calendars.todayJdn() }
    val j = remember(today) { Calendars.d2j(today) }
    val h = remember(today) { Calendars.toHijri(today) }
    val g = remember(today) { Calendars.d2g(today) }
    val scheme = MaterialTheme.colorScheme
    val events = remember(today) { Occasions.forJdn(today) }

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp).pressClick { onCalendar() },
        shape = RoundedCornerShape(28.dp),
        color = scheme.surface,
        shadowElevation = 3.dp
    ) {
        Column(
            Modifier
                .background(
                    Brush.verticalGradient(
                        listOf(scheme.primaryContainer.copy(alpha = 0.55f), scheme.surface)
                    )
                )
                .padding(horizontal = 14.dp, vertical = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(
                        Calendars.jalaliMonths[(j.jm - 1).coerceIn(0, 11)],
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${Calendars.gregorianMonths[(g.second - 1).coerceIn(0, 11)]} ${g.first}",
                        fontSize = 13.sp,
                        color = scheme.onSurfaceVariant
                    )
                    Text(
                        g.third.toString(),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = scheme.primary
                    )
                }

                Box(
                    Modifier
                        .size(100.dp)
                        .background(scheme.surface, CircleShape)
                        .border(1.dp, scheme.outlineVariant, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🌞", modifier = Modifier.align(Alignment.TopEnd).padding(10.dp), fontSize = 16.sp)
                    Text("🌿", modifier = Modifier.align(Alignment.BottomStart).padding(10.dp), fontSize = 14.sp)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            Calendars.fa(j.jd),
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Bold,
                            color = scheme.onSurface
                        )
                        Text(
                            Calendars.fa(j.jy),
                            fontSize = 13.sp,
                            color = scheme.onSurfaceVariant
                        )
                    }
                }

                Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                    Text(Calendars.weekdayName(today), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "${Calendars.hijriMonths[(h.hm - 1).coerceIn(0, 11)]} ${Calendars.fa(h.hy)}",
                        fontSize = 13.sp,
                        color = scheme.onSurfaceVariant
                    )
                    Text(
                        Calendars.fa(h.hd),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = scheme.primary
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (offset in -3..3) {
                    val jdn = today + offset
                    MiniDay(jdn, (today + offset) == today, Modifier.weight(1f))
                }
            }

            if (events.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = scheme.primaryContainer.copy(alpha = 0.5f)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🗓", fontSize = 14.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "مناسبت‌ها: " + events.joinToString("، "),
                            fontSize = 12.sp,
                            color = scheme.onPrimaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniDay(jdn: Long, isToday: Boolean, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val j = Calendars.d2j(jdn)
    val g = Calendars.d2g(jdn)
    val h = Calendars.toHijri(jdn)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (isToday) scheme.surface else scheme.surface.copy(alpha = 0.55f))
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            Calendars.weekdays[Calendars.weekdayIndex(jdn)].take(4),
            fontSize = 9.sp,
            color = scheme.onSurfaceVariant,
            maxLines = 1
        )
        Text(
            Calendars.fa(j.jd),
            fontSize = 15.sp,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
            color = if (isToday) scheme.primary else scheme.onSurface
        )
        Text("${g.third}", fontSize = 8.sp, color = scheme.onSurfaceVariant)
        Text("${Calendars.fa(h.hd)}", fontSize = 8.sp, color = scheme.onSurfaceVariant)
    }
}

@Composable
private fun ShortcutRow(
    onCalendar: () -> Unit,
    onTools: () -> Unit,
    onGallery: () -> Unit,
    onPdf: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Pill("تقویم و روزها", Modifier.weight(1f), onCalendar)
        Pill("افزونه‌ها", Modifier.weight(1f), onTools)
        Pill("گالری", Modifier.weight(1f), onGallery)
        Pill("PDF", Modifier.weight(1f), onPdf)
    }
}

@Composable
private fun Pill(text: String, modifier: Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.pressClick { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = scheme.surface
    ) {
        Text(
            text,
            modifier = Modifier.padding(vertical = 12.dp),
            textAlign = TextAlign.Center,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = scheme.onSurface
        )
    }
}

@Composable
private fun ScanCard(onScan: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp).pressClick { onScan() },
        shape = RoundedCornerShape(26.dp),
        color = scheme.primary,
        shadowElevation = 4.dp
    ) {
        Row(
            Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(44.dp).background(Color.White.copy(alpha = 0.18f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.CameraAlt, contentDescription = null, tint = Color.White)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("اسکن سند جدید", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text("تشخیص خودکار قاب و بهبود تصویر", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
            }
            Text("›", color = Color.White, fontSize = 26.sp)
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.width(8.dp))
        if (count > 0) {
            Text(
                DateFa.fa(count),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun DocCard(doc: ScannerDoc, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp).pressClick { onClick() },
        shape = RoundedCornerShape(20.dp),
        color = scheme.surface,
        shadowElevation = 1.dp
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            FileImage(
                file = doc.pages.firstOrNull()?.filePath()?.let { File(it) },
                modifier = Modifier.size(60.dp).clip(RoundedCornerShape(14.dp)),
                size = 200
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(doc.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                Text(
                    "${DateFa.fa(doc.pages.size)} صفحه • ${DateFa.short(doc.updatedAt)}",
                    fontSize = 11.sp,
                    color = scheme.onSurfaceVariant
                )
            }
            Text("›", color = scheme.outline, fontSize = 22.sp)
        }
    }
}

@Composable
private fun EmptyDocs(onScan: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
        shape = RoundedCornerShape(22.dp),
        color = scheme.surface
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 26.dp, horizontal = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("هنوز سندی نساخته‌اید", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                "با دکمهٔ اسکن شروع کنید یا عکس و PDF وارد کنید.",
                fontSize = 12.sp,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Surface(color = scheme.primary, shape = RoundedCornerShape(14.dp), modifier = Modifier.pressClick { onScan() }) {
                Text(
                    "شروع اسکن",
                    color = scheme.onPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                )
            }
        }
    }
}

@Composable
private fun HomeBottomBar(
    onHome: () -> Unit,
    onCalendar: () -> Unit,
    onScan: () -> Unit,
    onTools: () -> Unit,
    onSettings: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Surface(color = scheme.surface, shadowElevation = 8.dp) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomItem(Icons.Filled.Home, "خانه", true, Modifier.weight(1f), onHome)
            BottomItem(Icons.Filled.CalendarMonth, "تقویم", false, Modifier.weight(1f), onCalendar)
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Surface(
                    modifier = Modifier.size(54.dp).pressClick { onScan() },
                    shape = CircleShape,
                    color = scheme.primary,
                    shadowElevation = 6.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.CameraAlt, contentDescription = "اسکن", tint = scheme.onPrimary)
                    }
                }
            }
            BottomItem(Icons.Filled.Widgets, "ابزارها", false, Modifier.weight(1f), onTools)
            BottomItem(Icons.Filled.Settings, "تنظیمات", false, Modifier.weight(1f), onSettings)
        }
    }
}

@Composable
private fun BottomItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier.pressClick { onClick() }.padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (selected) scheme.primary else scheme.onSurfaceVariant.copy(alpha = 0.75f),
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            fontSize = 9.sp,
            color = if (selected) scheme.primary else scheme.onSurfaceVariant.copy(alpha = 0.75f)
        )
    }
}
