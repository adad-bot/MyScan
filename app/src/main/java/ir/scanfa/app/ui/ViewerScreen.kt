package ir.scanfa.app.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import ir.scanfa.app.vm.ScannerViewModel
import java.io.File

/** نمایش تمام‌صفحهٔ صفحات سند با بزرگ‌نمایی و ورق‌زدن. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ViewerScreen(vm: ScannerViewModel, nav: NavHostController, docId: String) {
    val doc = remember(docId) { vm.load(docId) }
    val pages = doc?.pages ?: emptyList()

    if (pages.isEmpty()) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Text("صفحه‌ای برای نمایش نیست", color = Color.White)
        }
        return
    }

    val pager = rememberPagerState { pages.size }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { index ->
            ZoomablePage(File(pages[index].filePath()))
        }

        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.55f)) {
                Box(Modifier.pressClick { nav.popBackStack() }.padding(10.dp)) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "بازگشت", tint = Color.White)
                }
            }
            Box(Modifier.weight(1f))
            Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.55f)) {
                Text(
                    "${ir.scanfa.app.util.DateFa.fa(pager.currentPage + 1)} از ${ir.scanfa.app.util.DateFa.fa(pages.size)}",
                    color = Color.White,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun ZoomablePage(file: File) {
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, file) {
        value = runCatching {
            val options = BitmapFactory.Options().apply {
                inSampleSize = 2
            }
            BitmapFactory.decodeFile(file.absolutePath, options)
        }.getOrNull()
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        bitmap?.let { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "صفحهٔ سند",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offsetX
                        translationY = offsetY
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            if (scale > 1f) {
                                offsetX += pan.x
                                offsetY += pan.y
                            } else {
                                offsetX = 0f
                                offsetY = 0f
                            }
                        }
                    }
            )
        }
    }
}
