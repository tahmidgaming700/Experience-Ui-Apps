package com.experience.calendar.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.experience.calendar.CalendarViewModel
import com.experience.calendar.R
import com.experience.calendar.data.EventInstance
import com.experience.calendar.logic.EventDays
import com.experience.calendar.logic.MonthGrid
import com.experience.design.components.EmptyState
import com.experience.design.theme.ExperienceMotion
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

private const val PAGE_COUNT = 2400
private const val ANCHOR_PAGE = PAGE_COUNT / 2

/** Groups occurrences by every local day they cover. */
fun groupByDay(events: List<EventInstance>, zone: java.time.ZoneId): Map<LocalDate, List<EventInstance>> {
    val map = HashMap<LocalDate, MutableList<EventInstance>>()
    for (e in events) {
        val span = EventDays.span(e.allDay, e.begin, e.end, e.startDay, e.endDay, zone)
        var d = span.start
        var guard = 0
        while (!d.isAfter(span.endInclusive) && guard++ < 400) {
            map.getOrPut(d) { ArrayList() } += e
            d = d.plusDays(1)
        }
    }
    // All-day first, then by start time.
    return map.mapValues { (_, v) -> v.sortedWith(compareBy({ !it.allDay }, { it.begin })) }
}

/** Swipeable month grid with event dots, plus the selected day's events (side by side on wide screens). */
@Composable
fun MonthView(vm: CalendarViewModel, wide: Boolean, onEvent: (EventInstance) -> Unit) {
    val anchor = remember { YearMonth.now() }
    val firstDow = remember { WeekFields.of(Locale.getDefault()).firstDayOfWeek }
    val selectedMonth = YearMonth.from(vm.selectedDate)
    val pager = rememberPagerState(initialPage = MonthGrid.pageForMonth(anchor, ANCHOR_PAGE, selectedMonth)) { PAGE_COUNT }

    // Selection (Today button, intents) drives the pager...
    LaunchedEffect(selectedMonth) {
        val target = MonthGrid.pageForMonth(anchor, ANCHOR_PAGE, selectedMonth).coerceIn(0, PAGE_COUNT - 1)
        if (target != pager.currentPage) pager.animateScrollToPage(target)
    }
    // ...and swiping moves the selection into the new month.
    LaunchedEffect(pager.settledPage) {
        val month = MonthGrid.monthForPage(anchor, ANCHOR_PAGE, pager.settledPage)
        if (YearMonth.from(vm.selectedDate) != month) {
            vm.selectedDate = if (month == YearMonth.now()) LocalDate.now() else month.atDay(1)
        }
    }

    // Events for the selected day.
    var dayEvents by remember { mutableStateOf(emptyList<EventInstance>()) }
    LaunchedEffect(vm.selectedDate, vm.dataVersion) {
        dayEvents = groupByDay(vm.instances(vm.selectedDate, vm.selectedDate.plusDays(1)), vm.zone)[vm.selectedDate].orEmpty()
    }

    val grid = @Composable { modifier: Modifier ->
        HorizontalPager(state = pager, modifier = modifier, beyondViewportPageCount = 1, verticalAlignment = Alignment.Top) { page ->
            MonthPage(vm, MonthGrid.monthForPage(anchor, ANCHOR_PAGE, page), firstDow, if (wide) 1.3f else 0.9f)
        }
    }
    val list = @Composable { modifier: Modifier ->
        LazyColumn(modifier.padding(horizontal = 16.dp)) {
            item {
                Text(longDate(vm.selectedDate), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 12.dp))
            }
            if (dayEvents.isEmpty()) {
                item { EmptyState(Icons.Outlined.EventAvailable, stringResource(R.string.no_events), stringResource(R.string.no_events_body)) }
            }
            items(dayEvents, key = { "${it.eventId}-${it.begin}" }) { EventRow(it, onClick = { onEvent(it) }) }
            item { Spacer(Modifier.height(96.dp)) }
        }
    }
    if (wide) {
        Row(Modifier.fillMaxSize()) {
            grid(Modifier.weight(0.6f))
            list(Modifier.weight(0.4f).fillMaxSize())
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            grid(Modifier.fillMaxWidth())
            list(Modifier.weight(1f).fillMaxWidth())
        }
    }
}

@Composable
private fun MonthPage(vm: CalendarViewModel, month: YearMonth, firstDow: DayOfWeek, cellAspect: Float) {
    val days = remember(month, firstDow) { MonthGrid.days(month, firstDow) }
    var byDay by remember { mutableStateOf(emptyMap<LocalDate, List<EventInstance>>()) }
    LaunchedEffect(month, vm.dataVersion) {
        byDay = groupByDay(vm.instances(days.first(), days.last().plusDays(1)), vm.zone)
    }
    val today = LocalDate.now()
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        Row(Modifier.fillMaxWidth()) {
            MonthGrid.weekdays(firstDow).forEach { dow ->
                Text(
                    dow.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.weight(1f).padding(vertical = 6.dp)
                        .semantics { contentDescription = dow.getDisplayName(TextStyle.FULL, Locale.getDefault()) },
                )
            }
        }
        days.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    DayCell(
                        day = day,
                        inMonth = YearMonth.from(day) == month,
                        isToday = day == today,
                        selected = day == vm.selectedDate,
                        events = byDay[day].orEmpty(),
                        onClick = { vm.selectedDate = day },
                        aspect = cellAspect,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: LocalDate, inMonth: Boolean, isToday: Boolean, selected: Boolean,
    events: List<EventInstance>, onClick: () -> Unit, aspect: Float, modifier: Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val bg by animateColorAsState(
        when { selected -> scheme.primary; isToday -> scheme.primaryContainer; else -> Color.Transparent },
        ExperienceMotion.fastEffects(), label = "cellBg",
    )
    val fg = when { selected -> scheme.onPrimary; isToday -> scheme.onPrimaryContainer; inMonth -> scheme.onSurface; else -> scheme.onSurfaceVariant.copy(alpha = 0.6f) }
    val todayLabel = stringResource(R.string.today)
    val countLabel = pluralStringResource(R.plurals.event_count, events.size, events.size)
    val description = buildString {
        append(mediumDate(day))
        if (isToday) append(", ").append(todayLabel)
        append(", ").append(countLabel)
    }
    Box(
        modifier
            .aspectRatio(aspect)
            .heightIn(min = 48.dp)
            .padding(2.dp)
            .clickable(onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = description
                this.selected = selected
                role = Role.Button
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 2.dp)) {
            Surface(shape = CircleShape, color = bg, modifier = Modifier.size(34.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        day.dayOfMonth.toString(),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = if (isToday || selected) FontWeight.Bold else FontWeight.Normal),
                        color = fg,
                    )
                }
            }
            Spacer(Modifier.height(3.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                events.distinctBy { it.color }.take(3).forEach { ColorDot(calendarColor(it.color), 6) }
                if (events.size > 3) Text("+", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            }
        }
    }
}
