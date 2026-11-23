package ir.scanfa.app.ui

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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

/** فهرست همهٔ اسناد با تصویر، جست‌وجو و فیلترهای تاریخ، تعداد صفحه و حجم. */
@Composable
fun DocsScreen(vm: ScannerViewModel, nav: NavHostController) {
    val docs by vm.docs.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var sortByDate by rememberSaveable { mutableStateOf(true) }
    var minPages by rememberSaveable { mutableIntStateOf(0) }
    var rangeDays by rememberSaveable { mutableIntStateOf(0) }

    val filtered = remember(docs, query, sortByDate, minPages, rangeDays) {
        val now = System.currentTimeMillis()
        val rangeMs = rangeDays.toLong() * 24L * 60L * 60L * 1000L
        val base = docs.filter { doc ->
            val textMatch = query.isBlank() ||
                doc.title.contains(query, ignoreCase = true) ||
                doc.pages.any { it.text.contains(query, ignoreCase = true) }
            val pagesMatch = doc.pages.size >= minPages
            val dateMatch = rangeDays == 0 || (now - doc.updatedAt) <= rangeMs
            textMatch && pagesMatch && dateMatch
        }
        if (sortByDate) base.sortedByDescending { it.updatedAt } else base.sortedBy { it.title }
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
                Modifier.fillMaxWidth().padding(horizontal = 14.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(selected = sortByDate, onClick = { sortByDate = true }, label = { Text("بر اساس تاریخ") })
                FilterChip(selected = !sortByDate, onClick = { sortByDate = false }, label = { Text("بر اساس نام") })
                FilterChip(selected = rangeDays == 0, onClick = { rangeDays = 0 }, label = { Text("همه زمان‌ها") })
                FilterChip(selected = rangeDays == 1, onClick = { rangeDays = 1 }, label = { Text("امروز") })
                FilterChip(selected = rangeDays == 7, onClick = { rangeDays = 7 }, label = { Text("۷ روز") })
                FilterChip(selected = rangeDays == 30, onClick = { rangeDays = 30 }, label = { Text("۳۰ روز") })
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(selected = minPages == 0, onClick = { minPages = 0 }, label = { Text("هر تعداد صفحه") })
                FilterChip(selected = minPages == 1, onClick = { minPages = 1 }, label = { Text("۱+ صفحه") })
                FilterChip(selected = minPages == 5, onClick = { minPages = 5 }, label = { Text("۵+ صفحه") })
                FilterChip(selected = minPages == 10, onClick = { minPages = 10 }, label = { Text("۱۰+ صفحه") })
            }

            Text(
                "${DateFa.fa(filtered.size)} سند",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            )

            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("سندی پیدا نشد.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(vertical = 6.dp)) {
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
    val sizeKb = remember(doc) {
        doc.pages.sumOf { runCatching { File(it.filePath()).length() }.getOrDefault(0L) } / 1024L
    }
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
                    "${DateFa.fa(doc.pages.size)} صفحه • ${DateFa.short(doc.updatedAt)} • ${DateFa.fa(sizeKb.toInt())} کیلوبایت",
                    fontSize = 11.sp,
                    color = scheme.onSurfaceVariant
                )
            }
        }
    }
}
