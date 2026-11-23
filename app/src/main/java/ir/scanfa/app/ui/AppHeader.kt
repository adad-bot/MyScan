package ir.scanfa.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** سرصفحهٔ مشترک صفحهٔ اصلی و تقویم: لوگو، نام، تنظیمات و قفل. */
@Composable
fun AppHeader(
    onLogoClick: () -> Unit,
    onSettings: () -> Unit,
    onLock: () -> Unit,
    onLogoPositioned: ((Float, Float) -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.onGloballyPositioned { coords ->
                val rect = coords.boundsInRoot()
                onLogoPositioned?.invoke(rect.center.x, rect.center.y)
            }
        ) {
            MyScanLogo(size = 54.dp, onClick = onLogoClick)
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text("MyScan", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = scheme.onSurface)
            Text("علی دادمان", fontSize = 14.sp, color = scheme.primary)
        }
        Spacer(Modifier.weight(1f))
        HeaderIconButton(Icons.Filled.Lock, "قفل", 46.dp, 22.dp, onLock)
        Spacer(Modifier.width(8.dp))
        HeaderIconButton(Icons.Filled.Settings, "تنظیمات", 54.dp, 28.dp, onSettings)
    }
}

@Composable
private fun HeaderIconButton(
    icon: ImageVector,
    label: String,
    size: Dp,
    iconSize: Dp,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.size(size).pressClick { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}
