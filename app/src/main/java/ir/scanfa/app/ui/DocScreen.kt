package ir.scanfa.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import ir.scanfa.app.data.FilterKind
import ir.scanfa.app.data.Page
import ir.scanfa.app.data.ScannerDoc
import ir.scanfa.app.util.DateFa
import ir.scanfa.app.vm.ScannerViewModel
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocScreen(vm: ScannerViewModel, nav: NavHostController, docId: String) {
    val scope = rememberCoroutineScope()
    val docs by vm.docs.collectAsState()
    var doc by remember { mutableStateOf<ScannerDoc?>(vm.load(docId)) }
    val selected = remember { mutableStateListOf<String>() }
    var showAdd by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showFilter by remember { mutableStateOf(false) }
    var showNote by remember { mutableStateOf(false) }
    var noteText by remember { mutableStateOf("") }
    var slotDialog by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    var addToStart by remember { mutableStateOf(false) }

    fun reload() {
        doc = vm.load(docId)
    }

    LaunchedEffect(docs) { reload() }

    val galleryPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val page = vm.addPageFromUri(docId, uri, if (addToStart) 0 else null)
                reload()
                if (page != null) nav.navigate("editor/$docId/${page.id}")
            }
        }
    }

    fun targetIds(): List<String> {
        val d = doc ?: return emptyList()
        val all = d.pages.map { it.id }
        return if (selected.isEmpty()) all else all.filter { selected.contains(it) }
    }

    fun rotate() {
        val ids = targetIds()
        if (ids.isEmpty()) return
        scope.launch {
            vm.rotatePages(docId, ids, 90)
            reload()
            vm.postMessage("چرخش اعمال شد.")
        }
    }

    fun runOcr() {
        val ids = targetIds()
        if (ids.isEmpty()) {
            vm.postMessage("صفحه‌ای وجود ندارد.")
            return
        }
        scope.launch {
            val result = vm.runOcr(docId, ids, vm.prefs.ocrLanguage)
            reload()
            vm.postMessage(result)
            if (result.contains("انجام شد")) {
                nav.navigate("ocr/$docId/${ids.first()}")
            }
        }
    }

    Scaffold(
        topBar = {
            ScreenBar(
                doc?.title ?: "سند",
                onBack = { nav.popBackStack() },
                actions = {
                    IconButton(onClick = {
                        name = doc?.title ?: ""
                        showRename = true
                    }) {
                        Icon(Icons.Filled.Edit, contentDescription = "تغییر نام")
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "بیشتر")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text("حذف سند") }, onClick = {
                                menuOpen = false
                                showDelete = true
                            })
                            DropdownMenuItem(text = { Text("تنظیمات") }, onClick = {
                                menuOpen = false
                                nav.navigate("settings")
                            })
                        }
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp, modifier = Modifier.navigationBarsPadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    BarAction(Icons.Filled.Add, "افزودن") { showAdd = true }
                    BarAction(Icons.Filled.RotateRight, "چرخش") { rotate() }
                    BarAction(Icons.Filled.EditNote, "یادداشت") { showNote = true }
                    BarAction(Icons.Filled.FilterAlt, "فیلتر") { showFilter = true }
                    BarAction(Icons.Filled.TextFields, "متن") { runOcr() }
                    BarAction(Icons.Filled.PictureAsPdf, "خروجی") { nav.navigate("export/$docId") }
                }
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            val template = doc?.template()
            if (template != null && template.slots.isNotEmpty()) {
                Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(template.title, style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(4.dp))
                        for (slot in template.slots) {
                            val pageIndex = doc?.pages?.indexOfFirst { it.slotId == slot.id } ?: -1
                            Row(
                                Modifier.fillMaxWidth().clickable { slotDialog = slot.id }.padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(if (pageIndex >= 0) "✓" else "✗", color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    slot.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    if (pageIndex >= 0) "صفحهٔ ${DateFa.fa(pageIndex + 1)}" else "انتخاب صفحه",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            val pages = doc?.pages ?: emptyList()
            if (pages.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("صفحه‌ای ثبت نشده است", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Text("با دکمهٔ افزودن، عکس بگیرید یا از گالری وارد کنید.")
                    }
                }
            } else {
                LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                    itemsIndexed(pages, key = { _, p -> p.id }) { index, page ->
                        PageRow(
                            page = page,
                            index = index,
                            checked = selected.contains(page.id),
                            slotTitle = template?.let { t -> t.slots.firstOrNull { it.id == page.slotId }?.title },
                            onCheck = {
                                if (selected.contains(page.id)) selected.remove(page.id) else selected.add(page.id)
                            },
                            onOpen = { nav.navigate("editor/$docId/${page.id}") },
                            onText = { nav.navigate("ocr/$docId/${page.id}") }
                        )
                    }
                    item { Spacer(Modifier.height(12.dp)) }
                }
            }

            if (selected.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${DateFa.fa(selected.size)} انتخاب", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.weight(1f))
                    if (selected.size == 1) {
                        val idx = doc?.pages?.indexOfFirst { it.id == selected.first() } ?: -1
                        if (idx > 0) {
                            TextButton(onClick = {
                                vm.movePage(docId, idx, 0)
                                selected.clear()
                                reload()
                            }) { Text("به ابتدا", style = MaterialTheme.typography.labelSmall) }
                        }
                        val last = (doc?.pages?.size ?: 1) - 1
                        if (idx in 0 until last) {
                            TextButton(onClick = {
                                vm.movePage(docId, idx, last)
                                selected.clear()
                                reload()
                            }) { Text("به انتها", style = MaterialTheme.typography.labelSmall) }
                        }
                    }
                    TextButton(onClick = {
                        vm.deletePages(docId, selected.toList())
                        selected.clear()
                        reload()
                    }) { Text("حذف", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall) }
                    TextButton(onClick = { selected.clear() }) { Text("لغو", style = MaterialTheme.typography.labelSmall) }
                }
            }
        }
    }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("افزودن صفحه") },
            text = {
                Column {
                    TextButton(onClick = {
                        showAdd = false
                        nav.navigate("camera/$docId")
                    }) { Text("عکس‌برداری با دوربین") }
                    TextButton(onClick = {
                        showAdd = false
                        galleryPicker.launch(arrayOf("image/*"))
                    }) { Text("انتخاب عکس از گالری") }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = addToStart, onCheckedChange = { addToStart = it })
                        Text("افزودن به ابتدای سند", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAdd = false }) { Text("بستن") }
            }
        )
    }

    if (showRename) {
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text("نام سند") },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.rename(docId, name)
                    showRename = false
                    reload()
                }) { Text("ذخیره") }
            },
            dismissButton = {
                TextButton(onClick = { showRename = false }) { Text("انصراف") }
            }
        )
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("حذف سند") },
            text = { Text("این سند و تصاویرش حذف می‌شوند. ادامه می‌دهید؟") },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false
                    vm.deleteDocument(docId)
                    nav.popBackStack()
                }) { Text("حذف") }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text("انصراف") }
            }
        )
    }

    if (showFilter) {
        AlertDialog(
            onDismissRequest = { showFilter = false },
            title = { Text("اعمال فیلتر") },
            text = {
                Column {
                    for (kind in FilterKind.entries) {
                        TextButton(onClick = {
                            showFilter = false
                            val ids = targetIds()
                            scope.launch {
                                vm.applyFilter(docId, ids, kind)
                                reload()
                                vm.postMessage("فیلتر «${kind.label}» روی ${DateFa.fa(ids.size)} صفحه اعمال شد.")
                            }
                        }) { Text(kind.label) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFilter = false }) { Text("بستن") }
            }
        )
    }

    val activeSlot = slotDialog
    if (activeSlot != null) {
        AlertDialog(
            onDismissRequest = { slotDialog = null },
            title = { Text("انتخاب صفحه") },
            text = {
                Column(Modifier.heightIn(max = 320.dp)) {
                    LazyColumn {
                        itemsIndexed(doc?.pages ?: emptyList<Page>()) { index, page ->
                            TextButton(onClick = {
                                vm.assignSlot(docId, page.id, activeSlot)
                                slotDialog = null
                                reload()
                            }) { Text("صفحهٔ ${DateFa.fa(index + 1)}") }
                        }
                    }
                    TextButton(onClick = {
                        val current = doc?.pages?.firstOrNull { it.slotId == activeSlot }
                        if (current != null) vm.assignSlot(docId, current.id, null)
                        slotDialog = null
                        reload()
                    }) { Text("حذف تخصیص") }
                }
            },
            confirmButton = {
                TextButton(onClick = { slotDialog = null }) { Text("بستن") }
            }
        )
    }


    if (showNote) {
        AlertDialog(
            onDismissRequest = { showNote = false },
            title = { Text("یادداشت صفحه") },
            text = {
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                    minLines = 5,
                    label = { Text("متن یادداشت") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val text = noteText
                    showNote = false
                    noteText = ""
                    scope.launch {
                        val page = vm.addNotePage(docId, text)
                        reload()
                        if (page != null) {
                            vm.postMessage("صفحهٔ یادداشت اضافه شد.")
                            nav.navigate("editor/$docId/${page.id}")
                        } else {
                            vm.postMessage("متن یادداشت خالی بود.")
                        }
                    }
                }) { Text("افزودن") }
            },
            dismissButton = {
                TextButton(onClick = { showNote = false }) { Text("انصراف") }
            }
        )
    }
}

@Composable
private fun BarAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier.clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun PageRow(
    page: Page,
    index: Int,
    checked: Boolean,
    slotTitle: String?,
    onCheck: () -> Unit,
    onOpen: () -> Unit,
    onText: () -> Unit
) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = checked, onCheckedChange = { onCheck() })
            FileImage(
                file = File(page.filePath()),
                modifier = Modifier.size(64.dp),
                size = 200,
                contentDescription = "صفحهٔ ${index + 1}"
            )
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f).clickable(onClick = onOpen)) {
                Text("صفحهٔ ${DateFa.fa(index + 1)}", style = MaterialTheme.typography.titleSmall)
                Text(
                    when {
                        slotTitle != null -> slotTitle
                        page.ocrDone -> "متن استخراج شده"
                        else -> "بدون متن"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onText) {
                Icon(Icons.Filled.TextFields, contentDescription = "متن صفحه")
            }
        }
    }

}
