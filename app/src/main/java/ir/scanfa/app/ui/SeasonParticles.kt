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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.roundToInt
import kotlin.random.Random

private data class SeasonItem(
    val emoji: String,
    val baseX: Float,
    val phase: Float,
    val speed: Float,
    val sway: Float,
    val swayFreq: Float,
    val size: Float,
    val spin: Float,
    val alpha: Float
)

/**
 * بارش واقعی و تصادفی برگ/برف/گل داخل کارت تاریخ؛
 * هر گلبرگ مسیر، سرعت، اندازه و چرخش جداگانه دارد.
 */
@Composable
fun SeasonParticles(month: Int, modifier: Modifier = Modifier) {
    val emojis = when (month) {
        1, 2, 3 -> listOf("🌸", "🌺", "🌼", "🌷")
        4, 5, 6 -> listOf("✨", "☀️", "🌤", "☀")
        7, 8, 9 -> listOf("🍂", "🍁", "🍃", "🍂")
        else -> listOf("❄️", "❅", "❄", "✻")
    }
    val count = when (month) {
        7, 8, 9 -> 18
        10, 11, 12 -> 22
        1, 2, 3 -> 14
        else -> 12
    }

    val items = remember(month) {
        val random = Random(month * 7919L + 13L)
        List(count) { index ->
            SeasonItem(
                emoji = emojis[index % emojis.size],
                baseX = random.nextFloat(),
                phase = random.nextFloat(),
                speed = 0.55f + random.nextFloat() * 0.9f,
                sway = 0.02f + random.nextFloat() * 0.09f,
                swayFreq = 0.6f + random.nextFloat() * 1.6f,
                size = 10f + random.nextFloat() * 12f,
                spin = (random.nextFloat() - 0.5f) * 2f,
                alpha = 0.55f + random.nextFloat() * 0.4f
            )
        }
    }

    val transition = rememberInfiniteTransition(label = "seasonParticles")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(18000, easing = LinearEasing)),
        label = "seasonT"
    )

    BoxWithConstraints(modifier) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()

        items.forEach { item ->
            val local = ((t * item.speed + item.phase) % 1f)
            val swayOffset = sin((t * item.swayFreq + item.phase) * 2f * PI).toFloat() * width * item.sway
            val wind = width * 0.04f * (local - 0.5f)
            val x = width * item.baseX + swayOffset + wind
            val y = height * local - 16f

            Text(
                text = item.emoji,
                fontSize = item.size.sp,
                modifier = Modifier
                    .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                    .graphicsLayer {
                        rotationZ = (item.phase * 360f) + local * 300f * item.spin
                        alpha = (item.alpha * (1f - local * 0.25f)).coerceIn(0.25f, 1f)
                    }
            )
        }
    }
}
