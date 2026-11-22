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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import ir.scanfa.app.data.Templates
import ir.scanfa.app.vm.ScannerViewModel

@Composable
fun TemplatesScreen(vm: ScannerViewModel, nav: NavHostController) {
    Scaffold(topBar = { ScreenBar("الگوهای مدارک", onBack = { nav.popBackStack() }) }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "الگو ترتیب و کامل‌بودن صفحات را کنترل می‌کند؛ قبل از خروجی، ناقص بودن مشخص می‌شود.",
                style = MaterialTheme.typography.bodyMedium
            )
            for (template in Templates.all) {
                Card(shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Text(template.title, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            template.note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (template.slots.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            for (slot in template.slots) {
                                Text("• ${slot.title}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                val id = vm.createDocument("", template.id)
                                nav.navigate("camera/$id")
                            }) { Text("ساخت سند و اسکن") }
                            Button(onClick = {
                                val id = vm.createDocument("", template.id)
                                nav.navigate("doc/$id")
                            }) { Text("باز کردن") }
                        }
                    }
                }
            }
        }
    }
}
