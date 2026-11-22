package ir.scanfa.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.NavHostController
import ir.scanfa.app.camera.DocumentAnalyzer
import ir.scanfa.app.util.DateFa
import ir.scanfa.app.vm.ScannerViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.min

@Composable
fun CameraScreen(vm: ScannerViewModel, nav: NavHostController, docId: String) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val view = LocalView.current

    DisposableEffect(Unit) {
        val window = (view.context as? android.app.Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
    }
    LaunchedEffect(Unit) {
        if (!granted) permissionLauncher.launch(Manifest.permission.CAMERA)
        if (vm.load(docId) == null) nav.popBackStack()
    }

    if (!granted) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("برای اسکن، دسترسی دوربین لازم است.", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) { Text("اجازهٔ دسترسی") }
            TextButton(onClick = { nav.popBackStack() }) { Text("بازگشت") }
        }
        return
    }

    var flashOn by remember { mutableStateOf(false) }
    var autoCapture by remember { mutableStateOf(vm.prefs.autoCapture) }
    var shots by remember { mutableIntStateOf(0) }
    var capturing by remember { mutableStateOf(false) }
    var liveQuad by remember { mutableStateOf<List<Float>?>(null) }
    var frameAspect by remember { mutableFloatStateOf(1.5f) }
    var hits by remember { mutableIntStateOf(0) }
    var target by remember { mutableStateOf<List<Float>?>(null) }

    val camera = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_CAPTURE or CameraController.IMAGE_ANALYSIS)
        }
    }

    val analyzer = remember {
        DocumentAnalyzer { quad, area, aspect ->
            frameAspect = aspect
            if (area < 0.16f) {
                liveQuad = null
                hits = 0
                target = null
            } else {
                liveQuad = quad
                val previous = target
                if (previous != null && similar(previous, quad)) {
                    hits = (hits + 1).coerceAtMost(20)
                } else {
                    target = quad
                    hits = 1
                }
            }
        }
    }

    val executor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(camera) {
        try {
            camera.setImageAnalysisBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            camera.setImageAnalysisAnalyzer(executor, analyzer)
        } catch (t: Throwable) {
            // تحلیل زنده در دسترس نیست
        }
        try {
            camera.bindToLifecycle(lifecycleOwner)
        } catch (t: Throwable) {
            // دوربین در دسترس نیست
        }
        onDispose {
            try {
                camera.clearImageAnalysisAnalyzer()
                camera.unbind()
            } catch (t: Throwable) {
                // نادیده‌گرفتنی
            }
            try {
                executor.shutdown()
            } catch (t: Throwable) {
                // نادیده‌گرفتنی
            }
        }
    }

    LaunchedEffect(flashOn) {
        try {
            camera.imageCaptureFlashMode =
                if (flashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
        } catch (t: Throwable) {
            // نادیده‌گرفتنی
        }
    }

    fun capture() {
        if (capturing) return
        capturing = true
        val file = File(context.cacheDir, "capture_${System.currentTimeMillis()}.jpg")
        val options = ImageCapture.OutputFileOptions.Builder(file).build()
        try {
            camera.takePicture(
                options,
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(results: ImageCapture.OutputFileResults) {
                        scope.launch {
                            val page = vm.addPageFromFile(docId, file)
                            file.delete()
                            capturing = false
                            hits = 0
                            if (page != null) {
                                shots += 1
                                nav.navigate("editor/$docId/${page.id}")
                            } else {
                                vm.postMessage("ثبت تصویر ناموفق بود.")
                            }
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        capturing = false
                        hits = 0
                        vm.postMessage("خطای دوربین: ${exception.message}")
                    }
                }
            )
        } catch (t: Throwable) {
            capturing = false
            vm.postMessage("دوربین آمادهٔ عکس‌برداری نیست.")
        }
    }

    LaunchedEffect(hits, autoCapture, capturing) {
        if (autoCapture && !capturing && hits >= 5) {
            hits = 0
            capture()
        }
    }

    val quad = liveQuad
    val stable = hits >= 3

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    this.controller = camera
                    scaleType = PreviewView.ScaleType.FIT_CENTER
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (quad != null) {
            DocumentOverlay(
                quad = quad,
                aspect = frameAspect,
                stable = stable,
                modifier = Modifier.fillMaxSize()
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 12.dp, end = 12.dp, top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { nav.popBackStack() }) {
                    Icon(Icons.Filled.Close, contentDescription = "بستن", tint = Color.White)
                }
                Text("${DateFa.fa(shots)} صفحه", color = Color.White)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { flashOn = !flashOn }) {
                    Icon(
                        if (flashOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                        contentDescription = "فلش",
                        tint = if (flashOn) Color(0xFFFBBF24) else Color.White
                    )
                }
                IconButton(onClick = {
                    autoCapture = !autoCapture
                    vm.prefs.autoCapture = autoCapture
                }) {
                    Icon(
                        Icons.Filled.AutoMode,
                        contentDescription = "ثبت خودکار",
                        tint = if (autoCapture) Color(0xFF5EEAD4) else Color.White.copy(alpha = 0.5f)
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 40.dp, start = 20.dp, end = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            StatusPill(quad != null, stable, autoCapture)
            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { nav.navigate("doc/$docId") }) {
                    Text("پایان", color = Color.White, style = MaterialTheme.typography.titleMedium)
                }

                Box(contentAlignment = Alignment.Center) {
                    if (autoCapture) {
                        CircularProgressIndicator(
                            progress = (hits / 5f).coerceIn(0f, 1f),
                            modifier = Modifier.size(92.dp),
                            color = Color(0xFF5EEAD4),
                            trackColor = Color.White.copy(alpha = 0.25f),
                            strokeWidth = 4.dp
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .border(4.dp, Color.White, CircleShape)
                            .padding(7.dp)
                            .background(Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (capturing) {
                            CircularProgressIndicator(Modifier.size(26.dp), color = Color(0xFF0F766E))
                        } else {
                            IconButton(onClick = { capture() }) {
                                Icon(Icons.Filled.Check, contentDescription = "عکس‌برداری", tint = Color(0xFF0F766E))
                            }
                        }
                    }
                }

                Text("ثبت خودکار", color = if (autoCapture) Color(0xFF5EEAD4) else Color.White.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun StatusPill(hasQuad: Boolean, stable: Boolean, auto: Boolean) {
    val label = when {
        !hasQuad -> "کادر سند را داخل تصویر بگیرید"
        stable && auto -> "سند شناسایی شد — ثابت بمانید"
        stable -> "سند شناسایی شد"
        else -> "در حال تنظیم قاب…"
    }
    val dot = when {
        !hasQuad -> Color(0xFFF87171)
        stable -> Color(0xFF5EEAD4)
        else -> Color(0xFFFBBF24)
    }
    val pulse = rememberInfiniteTransition(label = "pulse")
    val alpha by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulseAlpha"
    )

    Surface(color = Color.Black.copy(alpha = 0.45f), shape = RoundedCornerShape(30.dp)) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(9.dp)
                    .background(dot.copy(alpha = if (hasQuad) alpha else 1f), CircleShape)
            )
            Spacer(Modifier.size(8.dp))
            Text(label, color = Color.White, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun DocumentOverlay(quad: List<Float>, aspect: Float, stable: Boolean, modifier: Modifier) {
    val animated = List(8) { index ->
        animateFloatAsState(
            targetValue = quad.getOrElse(index) { 0f },
            animationSpec = tween(160),
            label = "q$index"
        ).value
    }

    Canvas(modifier) {
        val vw = size.width
        val vh = size.height
        if (vw <= 0f || vh <= 0f || aspect <= 0f) return@Canvas
        val scale = min(vw / aspect, vh)
        val drawW = aspect * scale
        val drawH = scale
        val left = (vw - drawW) / 2f
        val top = (vh - drawH) / 2f
        val rect = Rect(left, top, left + drawW, top + drawH)

        val points = (0 until 4).map { i ->
            Offset(
                rect.left + animated[i * 2] * rect.width,
                rect.top + animated[i * 2 + 1] * rect.height
            )
        }
        val path = Path().apply {
            moveTo(points[0].x, points[0].y)
            lineTo(points[1].x, points[1].y)
            lineTo(points[2].x, points[2].y)
            lineTo(points[3].x, points[3].y)
            close()
        }
        val color = if (stable) Color(0xFF5EEAD4) else Color(0xFFFBBF24)
        drawPath(path, color = color.copy(alpha = 0.22f))
        drawPath(path, color = color, style = Stroke(width = 2.dp.toPx()))
        drawPath(path, color = color.copy(alpha = 0.45f), style = Stroke(width = 9.dp.toPx()))
        points.forEach { p ->
            drawCircle(Color.White, radius = 7.dp.toPx(), center = p)
            drawCircle(color, radius = 4.dp.toPx(), center = p)
        }
        // گوشه‌های راهنما
        val len = 22.dp.toPx()
        val w = 3.dp.toPx()
        drawLine(color, Offset(rect.left, rect.top + len), Offset(rect.left, rect.top), w, StrokeCap.Round)
        drawLine(color, Offset(rect.left, rect.top), Offset(rect.left + len, rect.top), w, StrokeCap.Round)
        drawLine(color, Offset(rect.right - len, rect.top), Offset(rect.right, rect.top), w, StrokeCap.Round)
        drawLine(color, Offset(rect.right, rect.top), Offset(rect.right, rect.top + len), w, StrokeCap.Round)
    }
}

private fun similar(a: List<Float>, b: List<Float>): Boolean {
    if (a.size != 8 || b.size != 8) return false
    var maxShift = 0f
    for (i in 0 until 8) {
        val d = abs(a[i] - b[i])
        if (d > maxShift) maxShift = d
    }
    return maxShift < 0.05f
}
