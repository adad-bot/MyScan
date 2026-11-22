package ir.scanfa.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import ir.scanfa.app.data.Page
import ir.scanfa.app.vm.ScannerViewModel
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrScreen(vm: ScannerViewModel, nav: NavHostController, docId: String, pageId: String) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var page by remember { mutableStateOf<Page?>(vm.load(docId)?.page(pageId)) }
    var text by remember { mutableStateOf(page?.text ?: "") }
    var running by remember { mutableStateOf(false) }

    LaunchedEffect(pageId) {
        val loaded = vm.load(docId)?.page(pageId)
        page = loaded
        if (text.isBlank()) text = loaded?.text ?: ""
    }

    Scaffold(
        topBar = {
            ScreenBar(
                "متن صفحه",
                onBack = { nav.popBackStack() },
                actions = {
                    IconButton(onClick = {
                        vm.saveText(docId, pageId, text)
                        vm.postMessage("متن ذخیره شد.")
                        nav.popBackStack()
                    }) {
                        Icon(Icons.Filled.Check, contentDescription = "ذخیرهٔ متن")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(12.dp)
        ) {
            Card(Modifier.fillMaxWidth().height(190.dp)) {
                FileImage(
                    file = File(page?.filePath() ?: ""),
                    modifier = Modifier.fillMaxSize(),
                    size = 700,
                    contentDescription = "تصویر صفحه",
                    contentScale = ContentScale.Fit
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        scope.launch {
                            running = true
                            val result = vm.runOcr(docId, listOf(pageId), vm.prefs.ocrLanguage)
                            page = vm.load(docId)?.page(pageId)
                            page?.text?.let { if (it.isNotBlank()) text = it }
                            vm.postMessage(result)
                            running = false
                        }
                    },
                    enabled = !running
                ) {
                    Text(if (running) "در حال متن‌خوانی…" else "اجرای متن‌خوانی")
                }
                TextButton(onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("متن سند", text))
                    vm.postMessage("متن کپی شد.")
                }) { Text("کپی") }
                TextButton(onClick = { text = "" }) { Text("پاک کردن") }
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp),
                label = { Text("متن قابل ویرایش") },
                textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Rtl)
            )
            HintText("اصلاح متن در همین کادر، هم در Word و هم در PDF قابل جست‌وجو استفاده می‌شود.")
        }
    }
}
