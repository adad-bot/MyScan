package ir.scanfa.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import ir.scanfa.app.util.Calendars
import ir.scanfa.app.util.Occasions
import ir.scanfa.app.vm.ScannerViewModel
import kotlin.math.ceil

@Composable
fun CalendarScreen(vm: ScannerViewModel, nav: NavHostController) {
    val lockGuard by vm.lockGuard.collectAsState()
    val today = remember { Calendars.todayJdn() }
    var selected by remember { mutableLongStateOf(today) }
    val startJ = remember { Calendars.d2j(today) }
    var viewYear by remember { mutableIntStateOf(startJ.jy) }
    var viewMonth by remember { mutableIntStateOf(startJ.jm) }

    fun shiftMonth(delta: Int) {
        var total = viewYear * 12 + (viewMonth - 1) + delta
        viewYear = Math.floorDiv(total, 12)
        viewMonth = Math.floorMod(total, 12) + 1
    }

    Scaffold(topBar = {
        AppHeader(
            onLogoClick = { nav.navigate("about") },
            onSettings = { nav.navigate("settings") },
            onLock = { vm.toggleLockGuard() }
        )
    }) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(16.dp)
        ) {
            DateHeroCard(jdn = selected, showSeason = vm.prefs.seasonEffects)
            Spacer(Modifier.height(16.dp))
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { shiftMonth(-1) }) {
                            Icon(Icons.Filled.ChevronRight, contentDescription = "ماه قبل")
                        }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            val firstJdn = Calendars.j2d(viewYear, viewMonth, 1)
                            val lastJdn = Calendars.j2d(
                                viewYear,
                                viewMonth,
                                Calendars.jalaliMonthLength(viewYear, viewMonth)
                            )
                            val g1 = Calendars.d2g(firstJdn)
                            val g2 = Calendars.d2g(lastJdn)
                            val h1 = Calendars.toHijri(firstJdn)
                            val h2 = Calendars.toHijri(lastJdn)
                            val gRange = if (g1.second == g2.second) {
                                Calendars.gregorianMonths[(g1.second - 1).coerceIn(0, 11)] + " " + g2.first
                            } else {
                                Calendars.gregorianShort[(g1.second - 1).coerceIn(0, 11)] + " – " +
                                    Calendars.gregorianShort[(g2.second - 1).coerceIn(0, 11)] + " " + g2.first
                            }
                            val hRange = if (h1.hm == h2.hm) {
                                Calendars.hijriMonths[(h1.hm - 1).coerceIn(0, 11)] + " " + Calendars.fa(h2.hy)
                            } else {
                                Calendars.hijriMonths[(h1.hm - 1).coerceIn(0, 11)] + " – " +
                                    Calendars.hijriMonths[(h2.hm - 1).coerceIn(0, 11)] + " " + Calendars.fa(h2.hy)
                            }
                            Text(
                                "${Calendars.jalaliMonths[viewMonth - 1]} ${Calendars.fa(viewYear)}",
                                fontSize = 23.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                gRange,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                hRange,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { shiftMonth(1) }) {
                            Icon(Icons.Filled.ChevronLeft, contentDescription = "ماه بعد")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row {
                        Calendars.weekdays.forEach { day ->
                            Text(
                                day.take(3),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    MonthGrid(
                        viewYear = viewYear,
                        viewMonth = viewMonth,
                        selected = selected,
                        today = today
                    ) { selected = it }
                    Spacer(Modifier.height(8.dp))
                }
            }
            Spacer(Modifier.height(14.dp))
            val events = Occasions.forJdn(selected)
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("مناسبت‌های این روز", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    if (events.isEmpty()) {
                        Text(
                            "مناسبت ثبت‌شده‌ای برای این روز نیست.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        events.forEach { event ->
                            Text("• $event", style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Text(
                "بر پایهٔ تقویم شمسی؛ معادل میلادی و قمری برای هر روز نمایش داده می‌شود.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        }
    }
}

@Composable
private fun MonthGrid(viewYear: Int, viewMonth: Int, selected: Long, today: Long, onPick: (Long) -> Unit) {
    val first = Calendars.j2d(viewYear, viewMonth, 1)
    val offset = Calendars.weekdayIndex(first)
    val length = Calendars.jalaliMonthLength(viewYear, viewMonth)
    val cells = offset + length
    val rows = ceil(cells / 7.0).toInt()

    Column {
        for (row in 0 until rows) {
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                for (col in 0 until 7) {
                    val index = row * 7 + col
                    val day = index - offset + 1
                    if (day in 1..length) {
                        val jdn = Calendars.j2d(viewYear, viewMonth, day)
                        DayCell(
                            jdn = jdn,
                            selected = jdn == selected,
                            today = jdn == today,
                            modifier = Modifier.weight(1f)
                        ) { onPick(jdn) }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(jdn: Long, selected: Boolean, today: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val j = Calendars.d2j(jdn)
    val g = Calendars.d2g(jdn)
    val h = Calendars.toHijri(jdn)
    val previousHijri = Calendars.toHijri(jdn - 1)
    val previousGregorian = Calendars.d2g(jdn - 1)
    val isFriday = Calendars.weekdayIndex(jdn) == 6

    Column(
        modifier = modifier
            .padding(2.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
            .background(if (selected) scheme.primary else scheme.surface)
            .then(
                if (today && !selected) Modifier.border(1.dp, scheme.primary, androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                else Modifier
            )
            .pressClick { onClick() }
            .padding(vertical = 6.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            Calendars.fa(j.jd),
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
            color = when {
                selected -> scheme.onPrimary
                isFriday -> scheme.error
                else -> scheme.onSurface
            }
        )

        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                Calendars.fa(h.hd),
                fontSize = 9.sp,
                color = if (selected) scheme.onPrimary.copy(alpha = 0.85f) else scheme.onSurfaceVariant
            )
            Text(
                "  ",
                fontSize = 9.sp,
                color = if (selected) scheme.onPrimary else scheme.onSurfaceVariant
            )
            Text(
                g.third.toString(),
                fontSize = 9.sp,
                color = if (selected) scheme.onPrimary.copy(alpha = 0.85f) else scheme.onSurfaceVariant
            )
        }

        val gregChanged = g.second != previousGregorian.second
        val hijriChanged = h.hm != previousHijri.hm
        if (gregChanged || hijriChanged) {
            Row(horizontalArrangement = Arrangement.Center) {
                if (gregChanged) {
                    Text(
                        Calendars.gregorianShort[(g.second - 1).coerceIn(0, 11)],
                        fontSize = 8.sp,
                        color = if (selected) scheme.onPrimary.copy(alpha = 0.8f) else scheme.primary
                    )
                }
                if (gregChanged && hijriChanged) {
                    Text(" ", fontSize = 8.sp)
                }
                if (hijriChanged) {
                    Text(
                        Calendars.hijriMonths[(h.hm - 1).coerceIn(0, 11)],
                        fontSize = 8.sp,
                        color = if (selected) scheme.onPrimary.copy(alpha = 0.8f) else scheme.primary,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

