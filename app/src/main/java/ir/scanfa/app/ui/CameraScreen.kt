package ir.scanfa.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import ir.scanfa.app.util.DateFa
import ir.scanfa.app.vm.ScannerViewModel
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun CameraScreen(vm: ScannerViewModel, nav: NavHostController, docId: String) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

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
    var shots by remember { mutableIntStateOf(0) }
    var capturing by remember { mutableStateOf(false) }

    val camera = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
        }
    }

    DisposableEffect(camera) {
        try {
            camera.bindToLifecycle(lifecycleOwner)
        } catch (t: Throwable) {
            // دوربین در دسترس نیست
        }
        onDispose {
            try {
                camera.unbind()
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

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    this.controller = camera
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        FrameOverlay(Modifier.fillMaxSize())

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 32.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { nav.popBackStack() }) {
                    Icon(Icons.Filled.Close, contentDescription = "بستن", tint = Color.White)
                }
                Text("${DateFa.fa(shots)} صفحه", color = Color.White)
            }
            IconButton(onClick = { flashOn = !flashOn }) {
                Icon(
                    if (flashOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                    contentDescription = "فلش",
                    tint = Color.White
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 36.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("کادر سند را در تمام صفحه قرار دهید", color = Color.White)
            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { nav.navigate("doc/$docId") }) {
                    Text("پایان", color = Color.White, style = MaterialTheme.typography.titleMedium)
                }

                Box(
                    modifier = Modifier
                        .size(78.dp)
                        .border(4.dp, Color.White, CircleShape)
                        .padding(8.dp)
                        .background(Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (capturing) {
                        CircularProgressIndicator(Modifier.size(28.dp), color = Color(0xFF14594A))
                    } else {
                        IconButton(onClick = {
                            if (!capturing) {
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
                                                vm.postMessage("خطای دوربین: ${exception.message}")
                                            }
                                        }
                                    )
                                } catch (t: Throwable) {
                                    capturing = false
                                    vm.postMessage("دوربین آمادهٔ عکس‌برداری نیست.")
                                }
                            }
                        }) {
                            Icon(Icons.Filled.Check, contentDescription = "عکس‌برداری", tint = Color(0xFF14594A))
                        }
                    }
                }

                Text("پایان و بازبینی", color = Color.Transparent, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun FrameOverlay(modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val left = w * 0.07f
        val right = w * 0.93f
        val top = h * 0.16f
        val bottom = h * 0.84f
        val len = minOf(w, h) * 0.09f
        val strokeWidth = 3.dp.toPx()
        val color = Color(0xFF9BE8C8)

        drawLine(color, Offset(left, top + len), Offset(left, top), strokeWidth = strokeWidth, cap = StrokeCap.Round)
        drawLine(color, Offset(left, top), Offset(left + len, top), strokeWidth = strokeWidth, cap = StrokeCap.Round)
        drawLine(color, Offset(right - len, top), Offset(right, top), strokeWidth = strokeWidth, cap = StrokeCap.Round)
        drawLine(color, Offset(right, top), Offset(right, top + len), strokeWidth = strokeWidth, cap = StrokeCap.Round)
        drawLine(color, Offset(right, bottom - len), Offset(right, bottom), strokeWidth = strokeWidth, cap = StrokeCap.Round)
        drawLine(color, Offset(right - len, bottom), Offset(right, bottom), strokeWidth = strokeWidth, cap = StrokeCap.Round)
        drawLine(color, Offset(left, bottom - len), Offset(left, bottom), strokeWidth = strokeWidth, cap = StrokeCap.Round)
        drawLine(color, Offset(left, bottom), Offset(left + len, bottom), strokeWidth = strokeWidth, cap = StrokeCap.Round)
    }
}
