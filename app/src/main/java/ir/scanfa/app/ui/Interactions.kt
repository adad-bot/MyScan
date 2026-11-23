package ir.scanfa.app.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/** امکان خاموش‌کردن بازخورد لرزشی از تنظیمات. */
val LocalHapticsEnabled = staticCompositionLocalOf { true }

/** اگر روشن باشد، منوها با دو کلیک فعال می‌شوند. */
val LocalDoubleTapEnabled = staticCompositionLocalOf { true }

/**
 * کلیک «زنده»: فشردن دکمه آن را کمی جمع می‌کند، موج لمسی اجرا می‌شود
 * و بازخورد لرزشی بسیار کوتاه می‌دهد.
 */
@Composable
fun Modifier.pressClick(
    enabled: Boolean = true,
    requireDoubleTap: Boolean? = null,
    onClick: () -> Unit
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.955f else 1f,
        animationSpec = spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioMediumBouncy
        ),
        label = "pressScale"
    )
    val doubleTapRequired = requireDoubleTap ?: LocalDoubleTapEnabled.current
    var lastTap by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0L) }
    val haptic = LocalHapticFeedback.current
    val hapticsEnabled = LocalHapticsEnabled.current

    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            enabled = enabled,
            interactionSource = interaction,
            indication = LocalIndication.current,
            onClick = {
                if (hapticsEnabled) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                if (doubleTapRequired) {
                    val now = System.currentTimeMillis()
                    if (now - lastTap in 1..450L) {
                        lastTap = 0L
                        onClick()
                    } else {
                        lastTap = now
                    }
                } else {
                    onClick()
                }
            }
        )
}
