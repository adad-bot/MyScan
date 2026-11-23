package ir.scanfa.app.ui

import android.media.MediaPlayer
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.scanfa.app.R
import kotlinx.coroutines.delay

/**
 * لوگوی زندهٔ MyScan: حلقهٔ چرخان، خط اسکن متحرک و نبض ملایم.
 * با لمس، گل رز سه ثانیه‌ای پخش می‌شود.
 */
@Composable
fun MyScanLogo(
    size: Dp = 58.dp,
    onClick: () -> Unit = {}
) {
    val transition = rememberInfiniteTransition(label = "logo")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing)),
        label = "sweep"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing)),
        label = "spin"
    )

    Box(
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                scaleX = pulse
                scaleY = pulse
            }
            .clip(CircleShape)
            .pressClick { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = this.size.minDimension / 2f
            val center = this.center

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF12897E), Color(0xFF0B4B43)),
                    center = center,
                    radius = radius
                ),
                radius = radius
            )
            rotate(spin) {
                drawArc(
                    color = Color(0xFF9BE8C8).copy(alpha = 0.85f),
                    startAngle = 0f,
                    sweepAngle = 80f,
                    useCenter = false,
                    topLeft = Offset(radius * 0.10f, radius * 0.10f),
                    size = Size(radius * 1.80f, radius * 1.80f),
                    style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            val pageW = radius * 0.92f
            val pageH = radius * 1.12f
            val left = center.x - pageW / 2f
            val top = center.y - pageH / 2f
            drawRoundRect(
                color = Color(0xFFF6FFFB),
                topLeft = Offset(left, top),
                size = Size(pageW, pageH),
                cornerRadius = CornerRadius(radius * 0.12f)
            )
            val lineX = left + pageW * 0.18f
            val lineRight = left + pageW * 0.82f
            drawLine(
                Color(0xFF0F766E).copy(alpha = 0.75f),
                Offset(lineX, top + pageH * 0.28f),
                Offset(lineRight, top + pageH * 0.28f),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                Color(0xFF0F766E).copy(alpha = 0.55f),
                Offset(lineX, top + pageH * 0.45f),
                Offset(left + pageW * 0.62f, top + pageH * 0.45f),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                Color(0xFF0F766E).copy(alpha = 0.55f),
                Offset(lineX, top + pageH * 0.62f),
                Offset(left + pageW * 0.72f, top + pageH * 0.62f),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )

            clipRect(left, top, left + pageW, top + pageH) {
                val scanY = top + pageH * sweep
                drawLine(
                    Color(0xFF2FD3B4).copy(alpha = 0.9f),
                    Offset(left, scanY),
                    Offset(left + pageW, scanY),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(Color(0xFF2FD3B4).copy(alpha = 0.18f), Color.Transparent),
                        startY = scanY - pageH * 0.18f,
                        endY = scanY
                    ),
                    topLeft = Offset(left, scanY - pageH * 0.18f),
                    size = Size(pageW, pageH * 0.18f)
                )
            }
        }
    }
}

/** جشن کوچک: گل رز سه ثانیه‌ای با یک ملودی کوتاه. */
@Composable
fun RoseOverlay(onDone: () -> Unit) {
    val context = LocalContext.current
    var player by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(Unit) {
        player = try {
            MediaPlayer.create(context, R.raw.rose)?.apply {
                setVolume(0.9f, 0.9f)
                start()
            }
        } catch (t: Throwable) {
            null
        }
        onDispose {
            try {
                player?.stop()
                player?.release()
            } catch (t: Throwable) {
                // نادیده‌گرفتنی
            }
            player = null
        }
    }

    LaunchedEffect(Unit) {
        delay(3000)
        onDone()
    }

    val transition = rememberInfiniteTransition(label = "rose")
    val turn by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing)),
        label = "turn"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .pressClick { onDone() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Canvas(Modifier.size(230.dp)) {
                val c = center
                val r = size.minDimension / 2f
                rotate(turn) {
                    for (i in 0 until 8) {
                        rotate(i * 45f, pivot = c) {
                            drawOval(
                                brush = Brush.radialGradient(
                                    listOf(Color(0xFFFF6B9A), Color(0xFFC2185B)),
                                    center = Offset(c.x, c.y - r * 0.42f),
                                    radius = r * 0.8f
                                ),
                                topLeft = Offset(c.x - r * 0.30f, c.y - r * 0.92f),
                                size = Size(r * 0.60f, r * 0.92f)
                            )
                        }
                    }
                    drawCircle(Color(0xFFFFD1E0), radius = r * 0.20f, center = c)
                    drawCircle(Color(0xFFE91E63).copy(alpha = 0.5f), radius = r * 0.10f, center = c)
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(
                "گل رز برای علی دادمان",
                color = Color(0xFFFFE4EE),
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}
