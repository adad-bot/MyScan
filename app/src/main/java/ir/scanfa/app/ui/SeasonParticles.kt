package ir.scanfa.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * بارش آرام و تزئینی داخل کارت تاریخ؛
 * پاییز برگ، زمستان برف، بهار گل و تابستان ستاره/نور.
 */
@Composable
fun SeasonParticles(month: Int, modifier: Modifier = Modifier) {
    val emojis = when (month) {
        1, 2, 3 -> listOf("🌸", "🌺", "🌼", "❀")
        4, 5, 6 -> listOf("☀️", "✨", "🌤", "☀")
        7, 8, 9 -> listOf("🍂", "🍁", "🍃")
        else -> listOf("❄️", "❅", "❄")
    }
    val count = when (month) {
        7, 8, 9 -> 12
        10, 11, 12 -> 14
        else -> 10
    }

    val transition = rememberInfiniteTransition(label = "seasonParticles")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(16000, easing = LinearEasing)),
        label = "seasonT"
    )

    BoxWithConstraints(modifier) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        repeat(count) { i ->
            val seed = ((i * 37) % 100) / 100f
            val local = ((t + seed) % 1f)
            val x = width * (0.05f + seed * 0.9f) + (width * 0.05f) * kotlin.math.sin((t + seed) * 6.28f)
            val y = height * local
            val size = 11f + seed * 6f
            Text(
                text = emojis[i % emojis.size],
                fontSize = size.sp,
                modifier = Modifier
                    .offset { IntOffset(x.roundToInt(), (y - 14.dp.value * 2).roundToInt()) }
                    .graphicsLayer {
                        rotationZ = t * 360f * (0.3f + seed)
                        alpha = (0.85f - local * 0.15f).coerceIn(0.4f, 2f)
                    }
            )
        }
    }
}
