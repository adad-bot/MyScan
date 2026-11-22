package ir.scanfa.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val PaperColors = lightColorScheme(
    primary = Color(0xFF14594A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCBEDE0),
    onPrimaryContainer = Color(0xFF002019),
    secondary = Color(0xFF4B6357),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCDE9DA),
    onSecondaryContainer = Color(0xFF072018),
    background = Color(0xFFFAF8F2),
    onBackground = Color(0xFF1B1C19),
    surface = Color(0xFFFFFDF7),
    onSurface = Color(0xFF1B1C19),
    surfaceVariant = Color(0xFFECE9E1),
    onSurfaceVariant = Color(0xFF44483F),
    outline = Color(0xFF74796E),
    error = Color(0xFFB3261E),
    onError = Color.White
)

@Composable
fun ScannerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = PaperColors, typography = Typography(), content = content)
}
