package ir.scanfa.app.ui

import android.media.MediaPlayer
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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

/** نشان مربعی‌گوشهٔ MyScan به سبک MyC (حرف M + گوشه‌های اسکن). */
@Composable
fun AppMark(size: Dp = 48.dp, onClick: (() -> Unit)? = null) {
    val base = MaterialTheme.colorScheme.primary
    val deep = androidx.compose.ui.graphics.lerp(base, Color.Black, 0.28f)
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.30f))
            .background(Brush.verticalGradient(listOf(base, deep)))
            .then(if (onClick != null) Modifier.pressClick { onClick() } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val s = this.size.minDimension
            val stroke = s * 0.045f
            val pad = s * 0.20f
            val len = s * 0.14f
            val c = Color.White.copy(alpha = 0.9f)
            drawLine(c, Offset(pad, pad + len), Offset(pad, pad), stroke, StrokeCap.Round)
            drawLine(c, Offset(pad, pad), Offset(pad + len, pad), stroke, StrokeCap.Round)
            drawLine(c, Offset(s - pad - len, pad), Offset(s - pad, pad), stroke, StrokeCap.Round)
            drawLine(c, Offset(s - pad, pad), Offset(s - pad, pad + len), stroke, StrokeCap.Round)
        }
        Text(
            "M",
            color = Color.White,
            fontSize = (size.value * 0.46f).sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * بارش گل روی لوگو، به همان سبک MyC: ده‌ها گلبرگ ایموجی از مرکز لوگو
 * با پراکندگی افقی، تأخیر و اندازهٔ تصادفی به پایین می‌ریزند.
 */
@Composable
fun PetalRain(
    trigger: Int,
    modifier: Modifier = Modifier,
    anchorX: Float = 0.87f,
    anchorY: Float = 0.09f,
    onDone: () -> Unit = {}
) {
    if (trigger == 0) return
    val context = LocalContext.current
    val progress = remember(trigger) { Animatable(0f) }

    DisposableEffect(trigger) {
        val player = try {
            MediaPlayer.create(context, R.raw.rose)?.apply {
                setVolume(0.6f, 0.6f)
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
        }
    }

    LaunchedEffect(trigger) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(2600, easing = LinearEasing))
        onDone()
    }

    val petals = remember(trigger) {
        val emojis = listOf("\uD83C\uDF39", "\uD83C\uDF38", "\uD83C\uDF3A", "\uD83C\uDF43", "\u2740")
        List(46) { i ->
            Petal(
                emoji = emojis[i % emojis.size],
                dx = (Math.random() * 140 - 70).toFloat(),
                delay = (Math.random() * 0.35).toFloat(),
                size = (13 + Math.random() * 14).toFloat(),
                spin = (Math.random() * 2 - 1).toFloat()
            )
        }
    }

    BoxWithConstraints(modifier) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val startX = widthPx * anchorX
        val startY = heightPx * anchorY
        val t = progress.value

        petals.forEach { petal ->
            val local = ((t + petal.delay) % 1f)
            val density = LocalDensity.current
            val dxRatio = with(density) { petal.dx.dp.toPx() } / widthPx.coerceAtLeast(1f)
            val x = startX + dxRatio * widthPx * local
            val y = startY + (heightPx - startY + 40f) * local
            Text(
                petal.emoji,
                modifier = Modifier
                    .offset { androidx.compose.ui.unit.IntOffset(x.toInt(), y.toInt()) }
                    .graphicsLayer {
                        rotationZ = local * 360f * petal.spin
                        alpha = (1f - local * 0.25f).coerceIn(0f, 1f)
                    },
                fontSize = petal.size.sp
            )
        }
    }
}

private data class Petal(
    val emoji: String,
    val dx: Float,
    val delay: Float,
    val size: Float,
    val spin: Float
)
