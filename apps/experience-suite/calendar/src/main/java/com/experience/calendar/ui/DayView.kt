package com.experience.calendar.ui

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.experience.calendar.CalendarViewModel
import com.experience.calendar.R
import com.experience.calendar.data.EventInstance
import com.experience.calendar.logic.DayLayout
import com.experience.calendar.logic.EventDays
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.delay

private const val DAY_PAGES = 20000
private const val DAY_ANCHOR = DAY_PAGES / 2
private val HOUR_HEIGHT = 64.dp
private val GUTTER = 56.dp

/** Swipeable day timeline: all-day chips on top, hour grid with positioned event blocks. */
@Composable
fun DayView(vm: CalendarViewModel, onEvent: (EventInstance) -> Unit, onCreateAt: (LocalDate, LocalTime) -> Unit) {
    val anchor = remember { LocalDate.now() }
    fun pageOf(d: LocalDate) = (DAY_ANCHOR + ChronoUnit.DAYS.between(anchor, d)).toInt().coerceIn(0, DAY_PAGES - 1)
    val pager = rememberPagerState(initialPage = pageOf(vm.selectedDate)) { DAY_PAGES }
    LaunchedEffect(vm.selectedDate) {
        val t = pageOf(vm.selectedDate)
        if (t != pager.currentPage) pager.animateScrollToPage(t)
    }
    LaunchedEffect(pager.settledPage) {
        val d = anchor.plusDays((pager.settledPage - DAY_ANCHOR).toLong())
        if (d != vm.selectedDate) vm.selectedDate = d
    }
    HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1, verticalAlignment = Alignment.Top) { page ->
        DayPage(vm, anchor.plusDays((page - DAY_ANCHOR).toLong()), onEvent, onCreateAt)
    }
}

@Composable
private fun DayPage(vm: CalendarViewModel, date: LocalDate, onEvent: (EventInstance) -> Unit, onCreateAt: (LocalDate, LocalTime) -> Unit) {
    val context = LocalContext.current
    var events by remember { mutableStateOf(emptyList<EventInstance>()) }
    LaunchedEffect(date, vm.dataVersion) {
        events = groupByDay(vm.instances(date, date.plusDays(1)), vm.zone)[date].orEmpty()
    }
    val (dayStart, dayEnd) = remember(date) { EventDays.dayBounds(date, vm.zone) }
    val allDay = events.filter { it.allDay }
    val timed = events.filter { !it.allDay }
    val placed = remember(timed, dayStart) {
        DayLayout.layout(timed, { maxOf(it.begin, dayStart) }, { minOf(it.end, dayEnd) })
    }
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    LaunchedEffect(date) {
        val hour = if (date == LocalDate.now()) (LocalTime.now().hour - 1).coerceAtLeast(0) else 8
        scroll.scrollTo(with(density) { (HOUR_HEIGHT * hour).roundToPx() })
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            longDate(date), style = MaterialTheme.typography.titleMedium,
            color = if (date == LocalDate.now()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (allDay.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = GUTTER, end = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                allDay.forEach { e ->
                    Surface(shape = RoundedCornerShape(12.dp), color = calendarColor(e.color), contentColor = Color.White,
                        modifier = Modifier.clickable(role = Role.Button) { onEvent(e) }) {
                        Text(e.title.ifBlank { stringResource(R.string.untitled_event) }, maxLines = 1,
                            style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp))
                    }
                }
            }
        }
        HorizontalDivider()
        BoxWithConstraints(Modifier.fillMaxSize().verticalScroll(scroll)) {
            val laneWidth = maxWidth - GUTTER - 8.dp
            Box(Modifier.height(HOUR_HEIGHT * 24).fillMaxWidth()) {
                // Hour lines and labels; tapping an hour creates an event there.
                for (h in 0 until 24) {
                    val label = DateUtils.formatDateTime(context, date.atTime(h, 0).atZone(vm.zone).toInstant().toEpochMilli(), DateUtils.FORMAT_SHOW_TIME)
                    val newAt = stringResource(R.string.new_event_at, label)
                    Row(
                        Modifier.offset(y = HOUR_HEIGHT * h).height(HOUR_HEIGHT).fillMaxWidth()
                            .clickable(onClickLabel = newAt) { onCreateAt(date, LocalTime.of(h, 0)) }
                            .semantics { contentDescription = newAt },
                    ) {
                        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(GUTTER).padding(start = 8.dp, top = 2.dp))
                        HorizontalDivider(Modifier.padding(top = 0.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
                // Event blocks.
                placed.forEach { p ->
                    val top = HOUR_HEIGHT * ((p.start - dayStart) / 3_600_000f)
                    val height = (HOUR_HEIGHT * ((p.end - p.start) / 3_600_000f)).coerceAtLeast(24.dp)
                    val w = laneWidth / p.columns
                    val e = p.item
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = calendarColor(e.color),
                        contentColor = Color.White,
                        modifier = Modifier
                            .offset(x = GUTTER + w * p.column, y = top)
                            .width(w - 2.dp)
                            .height(height - 2.dp)
                            .clickable(role = Role.Button) { onEvent(e) },
                    ) {
                        Column(Modifier.padding(horizontal = 6.dp, vertical = 3.dp)) {
                            Text(e.title.ifBlank { stringResource(R.string.untitled_event) }, style = MaterialTheme.typography.labelLarge,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (height > 40.dp) Text(timeRange(context, e), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                    }
                }
                // Current time indicator.
                if (date == LocalDate.now()) {
                    val now by produceState(System.currentTimeMillis()) { while (true) { delay(60_000); value = System.currentTimeMillis() } }
                    val y = HOUR_HEIGHT * ((now - dayStart) / 3_600_000f)
                    Box(Modifier.offset(x = GUTTER - 4.dp, y = y - 1.dp).width(laneWidth + 4.dp).height(2.dp).background(MaterialTheme.colorScheme.error))
                }
            }
        }
    }
}
