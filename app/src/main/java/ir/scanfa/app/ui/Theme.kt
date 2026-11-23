package ir.scanfa.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** یک «پوسته» کامل: نام + رنگ مایه در حالت روشن و تیره. */
data class Skin(
    val id: String,
    val name: String,
    val primary: Long,
    val primaryContainer: Long,
    val onPrimaryContainer: Long,
    val tertiary: Long,
    val tertiaryContainer: Long,
    val darkPrimary: Long,
    val darkPrimaryContainer: Long
)

val Skins = listOf(
    Skin("emerald", "زمرد", 0xFF0F766E, 0xFFA7F3D0, 0xFF053B34, 0xFFD97706, 0xFFFEF3C7, 0xFF5EEAD4, 0xFF115E56),
    Skin("ocean", "اقیانوس", 0xFF1D4ED8, 0xFFBFDBFE, 0xFF172554, 0xFF0EA5E9, 0xFFE0F2FE, 0xFF93C5FD, 0xFF1E3A8A),
    Skin("amber", "کهربا", 0xFFB45309, 0xFFFDE68A, 0xFF451A03, 0xFF0F766E, 0xFFCCFBF1, 0xFFFCD34D, 0xFF78350F),
    Skin("violet", "ارغوان", 0xFF7C3AED, 0xFFDDD6FE, 0xFF2E1065, 0xFFDB2777, 0xFFFCE7F3, 0xFFC4B5FD, 0xFF4C1D95),
    Skin("graphite", "گرافیت", 0xFF334155, 0xFFCBD5E1, 0xFF0F172A, 0xFF0EA5A4, 0xFFCCFBF1, 0xFFCBD5E1, 0xFF1E293B),
    Skin("sunset", "غروب", 0xFFC2410C, 0xFFFED7AA, 0xFF431407, 0xFF7C3AED, 0xFFEDE9FE, 0xFFFDBA74, 0xFF7C2D12)
)

private fun lightFor(skin: Skin): ColorScheme = lightColorScheme(
    primary = Color(skin.primary),
    onPrimary = Color.White,
    primaryContainer = Color(skin.primaryContainer),
    onPrimaryContainer = Color(skin.onPrimaryContainer),
    secondary = Color(0xFF475569),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2E8F0),
    onSecondaryContainer = Color(0xFF1E293B),
    tertiary = Color(skin.tertiary),
    onTertiary = Color.White,
    tertiaryContainer = Color(skin.tertiaryContainer),
    onTertiaryContainer = Color(0xFF3B2500),
    background = Color(0xFFF7FAF6),
    onBackground = Color(0xFF141B17),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF141B17),
    surfaceVariant = Color(0xFFEDF2EC),
    onSurfaceVariant = Color(0xFF4A554D),
    outline = Color(0xFF9AA79E),
    outlineVariant = Color(0xFFD7E0D7),
    error = Color(0xFFDC2626),
    onError = Color.White
)

private fun darkFor(skin: Skin): ColorScheme = darkColorScheme(
    primary = Color(skin.darkPrimary),
    onPrimary = Color(0xFF00322B),
    primaryContainer = Color(skin.darkPrimaryContainer),
    onPrimaryContainer = Color(0xFFCCFBF1),
    secondary = Color(0xFF94A3B8),
    onSecondary = Color(0xFF0F172A),
    secondaryContainer = Color(0xFF1E293B),
    onSecondaryContainer = Color(0xFFE2E8F0),
    tertiary = Color(skin.darkPrimary),
    onTertiary = Color(0xFF3B2500),
    tertiaryContainer = Color(0xFF5B3F00),
    onTertiaryContainer = Color(0xFFFEF3C7),
    background = Color(0xFF0B120F),
    onBackground = Color(0xFFE6F0E9),
    surface = Color(0xFF111A15),
    onSurface = Color(0xFFE6F0E9),
    surfaceVariant = Color(0xFF1B2620),
    onSurfaceVariant = Color(0xFFB9C7BD),
    outline = Color(0xFF5E6E64),
    outlineVariant = Color(0xFF2B3A32),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A)
)

private val AppTypography = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun ScannerTheme(mode: String = "system", skinId: String = "emerald", content: @Composable () -> Unit) {
    val dark = when (mode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val skin = Skins.firstOrNull { it.id == skinId } ?: Skins.first()
    MaterialTheme(
        colorScheme = if (dark) darkFor(skin) else lightFor(skin),
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
