package ir.scanfa.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import ir.scanfa.app.data.FilterKind
import ir.scanfa.app.data.Page
import ir.scanfa.app.image.DocDetector
import ir.scanfa.app.image.Img
import ir.scanfa.app.vm.ScannerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.hypot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(vm: ScannerViewModel, nav: NavHostController, docId: String, pageId: String) {
    val scope = rememberCoroutineScope()
    var page by remember { mutableStateOf<Page?>(null) }
    var original by remember { mutableStateOf<Bitmap?>(null) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var corners by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var frameMode by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf(FilterKind.ORIGINAL) }
    var working by remember { mutableStateOf(false) }

    LaunchedEffect(pageId) {
        val loaded = vm.load(docId)?.page(pageId) ?: return@LaunchedEffect
        page = loaded
        filter = FilterKind.of(loaded.filter)
        val src = withContext(Dispatchers.IO) { Img.decode(File(loaded.originalPath), 1400) }
        original = src
        val existing = loaded.crop
        corners = if (existing != null && existing.size == 8) {
            existing.chunked(2).map { Offset(it[0], it[1]) }
        } else if (src != null) {
            DocDetector.detect(src).toNormalized(src.width, src.height)
                .chunked(2)
                .map { Offset(it[0], it[1]) }
        } else {
            emptyList()
        }
        preview = withContext(Dispatchers.IO) { Img.decode(File(loaded.filePath()), 1400) }
        frameMode = existing == null
    }

    fun cropList(): List<Float> = corners.flatMap { listOf(it.x, it.y) }

    fun regenerate() {
        val p = page ?: return
        scope.launch {
            working = true
            val bmp = vm.previewPage(p, cropList(), filter, 1000)
            if (bmp != null) preview = bmp
            working = false
        }
    }

    fun save() {
        val p = page ?: return
        scope.launch {
            working = true
            val updated = vm.saveProcessed(docId, pageId, cropList(), filter)
            working = false
            if (updated != null) {
                vm.postMessage("صفحه ذخیره شد.")
                nav.popBackStack()
            } else {
                vm.postMessage("ذخیرهٔ صفحه ناموفق بود.")
            }
        }
    }

    Scaffold(
        topBar = {
            ScreenBar(
                "ویرایش صفحه",
                onBack = { nav.popBackStack() },
                actions = {
                    IconButton(onClick = { save() }) {
                        Icon(Icons.Filled.Check, contentDescription = "ذخیره")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            val shown = if (frameMode) original else (preview ?: original)
            Box(Modifier.fillMaxWidth().weight(1f).padding(8.dp)) {
                if (shown != null) {
                    EditorCanvas(
                        bitmap = shown,
                        corners = if (frameMode) corners else emptyList(),
                        onMove = { index, pos ->
                            val list = corners.toMutableList()
                            if (index in list.indices) {
                                list[index] = pos
                                corners = list
                            }
                        }
                    )
                } else {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
                if (working) {
                    LinearProgressIndicator(Modifier.align(Alignment.TopCenter).fillMaxWidth())
                }
            }

            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterKind.entries.forEach { kind ->
                        FilterChip(
                            selected = filter == kind,
                            onClick = {
                                filter = kind
                                regenerate()
                            },
                            label = { Text(kind.label) }
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { frameMode = !frameMode }, modifier = Modifier.weight(1f)) {
                        Text(if (frameMode) "نمایش نتیجه" else "تنظیم قاب")
                    }
                    OutlinedButton(onClick = { regenerate() }, modifier = Modifier.weight(1f)) {
                        Text("پیش‌نمایش")
                    }
                }
                Spacer(Modifier.height(6.dp))
                Button(onClick = { save() }, modifier = Modifier.fillMaxWidth()) {
                    Text("ذخیرهٔ صفحه")
                }
                HintText(
                    if (frameMode) "گوشه‌های سند را با انگشت جابه‌جا کنید تا کل برگه داخل کادر قرار بگیرد."
                    else "فیلتر را انتخاب کنید؛ نتیجه در همین صفحه نمایش داده می‌شود."
                )
            }
        }
    }
}

@Composable
private fun EditorCanvas(
    bitmap: Bitmap,
    corners: List<Offset>,
    onMove: (Int, Offset) -> Unit
) {
    val latestCorners by rememberUpdatedState(corners)
    var dragIndex by remember { mutableIntStateOf(-1) }
    val threshold = with(LocalDensity.current) { 64.dp.toPx() }
    val showFrame = corners.size == 4

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(bitmap, showFrame) {
                if (!showFrame) return@pointerInput
                detectDragGestures(
                    onDragStart = { pos ->
                        val rect = fitRect(bitmap, size.width.toFloat(), size.height.toFloat())
                        var best = -1
                        var bestDistance = threshold
                        latestCorners.forEachIndexed { index, c ->
                            val p = Offset(rect.left + c.x * rect.width, rect.top + c.y * rect.height)
                            val d = hypot(p.x - pos.x, p.y - pos.y)
                            if (d < bestDistance) {
                                bestDistance = d
                                best = index
                            }
                        }
                        dragIndex = best
                    },
                    onDragEnd = { dragIndex = -1 },
                    onDragCancel = { dragIndex = -1 },
                    onDrag = { change, _ ->
                        if (dragIndex >= 0) {
                            change.consume()
                            val rect = fitRect(bitmap, size.width.toFloat(), size.height.toFloat())
                            if (rect.width > 0f && rect.height > 0f) {
                                val nx = ((change.position.x - rect.left) / rect.width).coerceIn(0f, 1f)
                                val ny = ((change.position.y - rect.top) / rect.height).coerceIn(0f, 1f)
                                onMove(dragIndex, Offset(nx, ny))
                            }
                        }
                    }
                )
            }
    ) {
        val rect = fitRect(bitmap, size.width, size.height)
        drawImage(
            image = bitmap.asImageBitmap(),
            dstOffset = IntOffset(rect.left.toInt(), rect.top.toInt()),
            dstSize = IntSize(rect.width.toInt(), rect.height.toInt())
        )
        if (showFrame) {
            val points = corners.map { c ->
                Offset(rect.left + c.x * rect.width, rect.top + c.y * rect.height)
            }
            val path = Path().apply {
                moveTo(points[0].x, points[0].y)
                lineTo(points[1].x, points[1].y)
                lineTo(points[2].x, points[2].y)
                lineTo(points[3].x, points[3].y)
                close()
            }
            drawPath(path, color = Color(0xFF2FBF8F), style = Stroke(width = 3.dp.toPx()))
            points.forEach { p ->
                drawCircle(Color.White, radius = 12.dp.toPx(), center = p)
                drawCircle(Color(0xFF14594A), radius = 8.dp.toPx(), center = p)
            }
        }
    }
}

private fun fitRect(bitmap: Bitmap, width: Float, height: Float): Rect {
    if (width <= 0f || height <= 0f) return Rect(0f, 0f, 1f, 1f)
    val scale = minOf(width / bitmap.width, height / bitmap.height)
    val w = bitmap.width * scale
    val h = bitmap.height * scale
    val left = (width - w) / 2f
    val top = (height - h) / 2f
    return Rect(left, top, left + w, top + h)
}
