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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import ir.scanfa.app.data.ScannerDoc
import ir.scanfa.app.util.DateFa
import ir.scanfa.app.vm.ScannerViewModel
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: ScannerViewModel, nav: NavHostController) {
    val docs by vm.docs.collectAsState()
    val scope = rememberCoroutineScope()

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

    Scaffold(
        topBar = {
            ScreenBar("اسکنر سند", actions = {
                IconButton(onClick = { nav.navigate("settings") }) {
                    Icon(Icons.Filled.Settings, contentDescription = "تنظیمات")
                }
            })
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionCard("اسکن", "دوربین", Icons.Filled.CameraAlt, Modifier.weight(1f)) {
                        val id = vm.createDocument("", null)
                        nav.navigate("camera/$id")
                    }
                    ActionCard("ورود عکس", "گالری", Icons.Filled.PhotoLibrary, Modifier.weight(1f)) {
                        imagePicker.launch("image/*")
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionCard("ورود PDF", "چند صفحه", Icons.Filled.Description, Modifier.weight(1f)) {
                        pdfPicker.launch(arrayOf("application/pdf"))
                    }
                    ActionCard("الگوها", "مدارک", Icons.Filled.Add, Modifier.weight(1f)) {
                        nav.navigate("templates")
                    }
                }
            }
            item { SectionTitle("سندهای اخیر") }
            if (docs.isEmpty()) {
                item { EmptyHome() }
            } else {
                items(docs, key = { it.id }) { doc ->
                    DocRow(doc) { nav.navigate("doc/${doc.id}") }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun ActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(16.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.height(10.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun DocRow(doc: ScannerDoc, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            val thumbFile = doc.pages.firstOrNull()?.filePath()?.let { File(it) }
            FileImage(
                file = thumbFile,
                modifier = Modifier
                    .size(64.dp)
                    .then(Modifier),
                size = 200
            )
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    doc.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${DateFa.fa(doc.pages.size)} صفحه • ${DateFa.short(doc.updatedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val template = doc.template()
                if (template != null) {
                    Text(template.title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun EmptyHome() {
    Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("هنوز سندی نساخته‌اید", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                "با دکمهٔ اسکن شروع کنید یا عکس و PDF وارد کنید.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
