package ir.scanfa.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp

/** جلوهٔ فصلی آرام: بهار گل، تابستان نور، پاییز برگ، زمستان برف. */
@Composable
fun SeasonEffect(month: Int, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "season")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(14000, easing = LinearEasing)),
        label = "seasonT"
    )

    val season = when (month) {
        1, 2, 3 -> 0 // بهار
        4, 5, 6 -> 1 // تابستان
        7, 8, 9 -> 2 // پاییز
        else -> 3 // زمستان
    }

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        when (season) {
            0 -> repeat(14) { i ->
                val seed = (i * 37 % 100) / 100f
                val x = w * seed + (w * 0.03f) * kotlin.math.sin((t + seed) * 6.28f)
                val y = ((t * 1.3f + seed) % 1f) * h
                rotate(t * 300f + i * 20f, pivot = Offset(x, y)) {
                    drawOval(
                        color = if (i % 2 == 0) Color(0xFFFF9EC4).copy(alpha = 0.55f)
                        else Color(0xFFC4B5FD).copy(alpha = 0.5f),
                        topLeft = Offset(x, y),
                        size = Size(8.dp.toPx(), 12.dp.toPx())
                    )
                }
            }
            1 -> {
                // نور ملایم تابستان
                drawCircle(
                    color = Color(0xFFFFE08A).copy(alpha = 0.10f),
                    radius = w * 0.55f,
                    center = Offset(w * 0.85f, h * 0.05f)
                )
                repeat(10) { i ->
                    val seed = (i * 53 % 100) / 100f
                    val x = w * (0.2f + seed * 0.7f)
                    val y = h * ((t + seed) % 1f) * 0.9f
                    drawCircle(
                        color = Color(0xFFFFD166).copy(alpha = 0.25f),
                        radius = 2.dp.toPx(),
                        center = Offset(x, y)
                    )
                }
            }
            2 -> repeat(16) { i ->
                val seed = (i * 41 % 100) / 100f
                val x = w * seed + (w * 0.05f) * kotlin.math.sin((t + seed) * 6.28f)
                val y = ((t + seed) % 1f) * h
                rotate(t * 220f + i * 30f, pivot = Offset(x, y)) {
                    drawOval(
                        color = if (i % 3 == 0) Color(0xFFE11D48).copy(alpha = 0.8f)
                        else Color(0xFFF59E0B).copy(alpha = 0.85f),
                        topLeft = Offset(x, y),
                        size = Size(11.dp.toPx(), 7.dp.toPx())
                    )
                }
            }
            else -> repeat(30) { i ->
                val seed = (i * 29 % 100) / 100f
                val x = w * seed + (w * 0.04f) * kotlin.math.sin((t + seed) * 6.28f)
                val y = ((t * 1.1f + seed) % 1f) * h
                drawCircle(
                    color = Color.White.copy(alpha = 0.65f),
                    radius = (1.5f + seed * 1.6f).dp.toPx() / 2f,
                    center = Offset(x, y)
                )
            }
        }
    }
}
