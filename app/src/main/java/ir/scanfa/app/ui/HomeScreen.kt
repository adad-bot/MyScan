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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PhotoLibrary
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
    val lockGuard by vm.lockGuard.collectAsState()
    val scope = rememberCoroutineScope()
    var petalTrigger by remember { mutableIntStateOf(0) }
    var lastLogoTap by remember { mutableLongStateOf(0L) }
    val view = androidx.compose.ui.platform.LocalView.current
    var logoAnchor by remember { mutableStateOf(0.87f to 0.09f) }

    fun startScan() {
        val id = vm.createDocument("", null)
        nav.navigate("camera/$id")
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            HomeBottomBar(
                onHome = {},
                onTools = { nav.navigate("tools") },
                onScan = { startScan() },
                onImport = { nav.navigate("import") },
                onDocs = { nav.navigate("docs") }
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                item {
                    AppHeader(
                        onLogoClick = {
                            val now = System.currentTimeMillis()
                            if (now - lastLogoTap < 400L) nav.navigate("about") else petalTrigger += 1
                            lastLogoTap = now
                        },
                        onSettings = { nav.navigate("settings") },
                        onLock = { vm.toggleLockGuard() },
                        lockGuard = lockGuard,
                        onLogoPositioned = { x, y ->
                            if (view.width > 0 && view.height > 0) {
                                logoAnchor = x / view.width to y / view.height
                            }
                        }
                    )
                }
                item {
                    DateHeroCard(
                        jdn = remember { Calendars.todayJdn() },
                        showSeason = vm.prefs.seasonEffects,
                        onClick = { nav.navigate("calendar") }
                    )
                }
                item { SectionHeader("سندهای اخیر", docs.size) }
                if (docs.isEmpty()) {
                    item { EmptyDocs(onScan = { startScan() }) }
                } else {
                    items(docs, key = { it.id }) { doc ->
                        DocCard(doc) { nav.navigate("doc/${doc.id}") }
                    }
                }
            }

            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .fillMaxHeight()
                    .width(120.dp)
            ) {
                if (petalTrigger > 0) {
                    PetalRain(
                        trigger = petalTrigger,
                        modifier = Modifier.fillMaxSize(),
                        anchorX = 0.5f,
                        anchorY = 0.10f,
                        onDone = { petalTrigger = 0 }
                    )
                }
            }
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
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                    .padding(horizontal = 10.dp, vertical = 3.dp)
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
                "از دکمهٔ دوربین پایین شروع کنید یا عکس و PDF وارد کنید.",
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
    onTools: () -> Unit,
    onScan: () -> Unit,
    onImport: () -> Unit,
    onDocs: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Surface(color = scheme.primaryContainer, shadowElevation = 10.dp) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(scheme.primary.copy(alpha = 0.25f))
            )
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
            BottomItem(Icons.Filled.Home, "خانه", true, Modifier.weight(1f), onHome)
            BottomItem(Icons.Filled.Widgets, "ابزارها", false, Modifier.weight(1f), onTools)
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Surface(
                    modifier = Modifier.size(64.dp).pressClick { onScan() },
                    shape = CircleShape,
                    color = scheme.primary,
                    shadowElevation = 6.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.CameraAlt,
                            contentDescription = "اسکن",
                            tint = scheme.onPrimary,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
            }
            BottomItem(Icons.Filled.Description, "اسناد", false, Modifier.weight(1f), onDocs)
            BottomItem(Icons.Filled.AddCircle, "وارد کردن", false, Modifier.weight(1f), onImport)
            }
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
    val tint = if (selected) scheme.primary else scheme.onPrimaryContainer
    Column(
        modifier = modifier.pressClick { onClick() }.padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(if (selected) 30.dp else 28.dp)
        )
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = tint
        )
    }
}
