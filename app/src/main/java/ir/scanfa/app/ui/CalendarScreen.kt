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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import ir.scanfa.app.util.Calendars
import ir.scanfa.app.util.Occasions
import ir.scanfa.app.vm.ScannerViewModel
import kotlin.math.ceil

@Composable
fun CalendarScreen(vm: ScannerViewModel, nav: NavHostController) {
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

    Scaffold(topBar = { ScreenBar("تقویم", onBack = { nav.popBackStack() }) }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(16.dp)
        ) {
            SelectedDateCard(selected)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (offset in -3..3) {
                    DayChip(
                        jdn = today + offset,
                        selected = (today + offset) == selected,
                        isToday = offset == 0,
                        modifier = Modifier.weight(1f)
                    ) { selected = today + offset }
                }
            }
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
                            Text(
                                "${Calendars.jalaliMonths[viewMonth - 1]} ${Calendars.fa(viewYear)}",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                Calendars.hijriText(Calendars.j2d(viewYear, viewMonth, 15)),
                                style = MaterialTheme.typography.labelSmall,
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = {
                            selected = today
                            val tj = Calendars.d2j(today)
                            viewYear = tj.jy
                            viewMonth = tj.jm
                        }) { Text("امروز") }
                        Spacer(Modifier.weight(1f))
                        Text(
                            Calendars.jalaliShort(selected),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
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

@Composable
private fun SelectedDateCard(jdn: Long) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                Calendars.weekdayName(jdn),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
            Text(
                Calendars.jalaliText(jdn),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "میلادی: ${Calendars.gregorianText(jdn)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
            )
            Text(
                "قمری: ${Calendars.hijriText(jdn)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
            )
        }
    }
}

@Composable
private fun DayChip(jdn: Long, selected: Boolean, isToday: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val j = Calendars.d2j(jdn)
    Surface(
        modifier = modifier.pressClick { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = if (selected) scheme.primary else scheme.surface,
        border = if (isToday && !selected) androidx.compose.foundation.BorderStroke(1.5.dp, scheme.primary) else null
    ) {
        Column(
            Modifier.padding(vertical = 8.dp, horizontal = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                Calendars.weekdays[Calendars.weekdayIndex(jdn)].take(1),
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) scheme.onPrimary else scheme.onSurfaceVariant
            )
            Text(
                Calendars.fa(j.jd),
                style = MaterialTheme.typography.titleMedium,
                color = if (selected) scheme.onPrimary else scheme.onSurface
            )
            Text(
                Calendars.fa(Calendars.d2g(jdn).third),
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) scheme.onPrimary.copy(alpha = 0.8f) else scheme.onSurfaceVariant
            )
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
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                for (col in 0 until 7) {
                    val index = row * 7 + col
                    val day = index - offset + 1
                    if (day in 1..length) {
                        val jdn = Calendars.j2d(viewYear, viewMonth, day)
                        DayCell(
                            day = day,
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
private fun DayCell(day: Int, selected: Boolean, today: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(modifier.padding(2.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(36.dp)
                .background(
                    if (selected) scheme.primary else if (today) scheme.primaryContainer else scheme.surface,
                    CircleShape
                )
                .then(
                    if (today && !selected) Modifier.border(1.dp, scheme.primary, CircleShape) else Modifier
                )
                .pressClick { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                Calendars.fa(day),
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    selected -> scheme.onPrimary
                    today -> scheme.onPrimaryContainer
                    else -> scheme.onSurface
                }
            )
        }
    }
}
