package ir.scanfa.app.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import ir.scanfa.app.data.ScannerDoc
import ir.scanfa.app.util.DateFa
import ir.scanfa.app.vm.ScannerViewModel
import java.io.File

/** فهرست همهٔ اسناد با تصویر، جست‌وجو و مرتب‌سازی بر اساس تاریخ یا نام. */
@Composable
fun DocsScreen(vm: ScannerViewModel, nav: NavHostController) {
    val docs by vm.docs.collectAsState()
    var query by remember { mutableStateOf("") }
    var byDate by remember { mutableStateOf(true) }

    val filtered = remember(docs, query, byDate) {
        val base = docs.filter { doc ->
            query.isBlank() ||
                doc.title.contains(query, ignoreCase = true) ||
                doc.pages.any { it.text.contains(query, ignoreCase = true) }
        }
        if (byDate) base.sortedByDescending { it.updatedAt } else base.sortedBy { it.title }
    }

    Scaffold(topBar = { ScreenBar("اسناد", onBack = { nav.popBackStack() }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().navigationBarsPadding()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("جست‌وجو در نام یا متن اسناد") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)
            )

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = byDate,
                    onClick = { byDate = true },
                    label = { Text("بر اساس تاریخ") }
                )
                FilterChip(
                    selected = !byDate,
                    onClick = { byDate = false },
                    label = { Text("بر اساس نام") }
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "${DateFa.fa(filtered.size)} سند",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("سندی پیدا نشد.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(filtered, key = { it.id }) { doc ->
                        DocListRow(doc) { nav.navigate("doc/${doc.id}") }
                    }
                }
            }
        }
    }
}

@Composable
private fun DocListRow(doc: ScannerDoc, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).pressClick { onClick() },
        shape = RoundedCornerShape(18.dp),
        color = scheme.surface,
        shadowElevation = 1.dp
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FileImage(
                file = doc.pages.firstOrNull()?.filePath()?.let { File(it) },
                modifier = Modifier.size(58.dp).clip(RoundedCornerShape(12.dp)),
                size = 200
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    doc.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${DateFa.fa(doc.pages.size)} صفحه • ${DateFa.short(doc.updatedAt)}",
                    fontSize = 11.sp,
                    color = scheme.onSurfaceVariant
                )
            }
        }
    }
}
