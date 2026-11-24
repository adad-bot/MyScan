package ir.scanfa.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.scanfa.app.util.Calendars

/**
 * کارت تاریخ اصلی (الگو گرفته از MyC): دایرهٔ سفید با روز شمسی،
 * سمت راست ماه/میلادی/روز و سمت چپ روز هفته/قمری،
 * با بارش آرام برگ/برف/گل داخل خود کارت.
 */
@Composable
fun DateHeroCard(
    jdn: Long,
    showSeason: Boolean,
    onClick: (() -> Unit)? = null
) {
    val j = Calendars.d2j(jdn)
    val h = Calendars.toHijri(jdn)
    val g = Calendars.d2g(jdn)
    val scheme = MaterialTheme.colorScheme

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .then(if (onClick != null) Modifier.pressClick { onClick() } else Modifier),
        shape = RoundedCornerShape(30.dp),
        color = scheme.surface,
        shadowElevation = 3.dp
    ) {
        Box {
            if (showSeason) {
                SeasonParticles(
                    month = j.jm,
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(30.dp))
                )
            }

            Column(
                Modifier
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                scheme.primaryContainer.copy(alpha = 0.35f),
                                scheme.surface.copy(alpha = 0.55f)
                            )
                        )
                    )
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(
                        Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            Calendars.jalaliMonths[(j.jm - 1).coerceIn(0, 11)],
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold,
                            color = scheme.onSurface
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${Calendars.gregorianMonths[(g.second - 1).coerceIn(0, 11)]} ${g.first}",
                            fontSize = 13.sp,
                            color = scheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            g.third.toString(),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = scheme.primary
                        )
                    }

                    Box(
                        Modifier
                            .size(110.dp)
                            .background(scheme.surface, CircleShape)
                            .border(2.dp, scheme.primary.copy(alpha = 0.35f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "🌞",
                            modifier = Modifier.align(Alignment.TopEnd).padding(14.dp),
                            fontSize = 18.sp
                        )
                        Text(
                            "🍃",
                            modifier = Modifier.align(Alignment.BottomStart).padding(14.dp),
                            fontSize = 15.sp
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                Calendars.fa(j.jd),
                                fontSize = 40.sp,
                                fontWeight = FontWeight.Bold,
                                color = scheme.onSurface
                            )
                            Text(
                                Calendars.fa(j.jy),
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = scheme.onSurfaceVariant
                            )
                        }
                    }

                    Column(
                        Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            Calendars.weekdayName(jdn),
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = scheme.onSurface
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${Calendars.hijriMonths[(h.hm - 1).coerceIn(0, 11)]} ${Calendars.fa(h.hy)}",
                            fontSize = 13.sp,
                            color = scheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            Calendars.fa(h.hd),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = scheme.primary
                        )
                    }
                }
            }
        }
    }
}
