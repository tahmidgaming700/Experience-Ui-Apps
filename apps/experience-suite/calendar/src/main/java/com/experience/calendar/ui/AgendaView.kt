package com.experience.calendar.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.experience.calendar.CalendarViewModel
import com.experience.calendar.R
import com.experience.calendar.data.EventInstance
import com.experience.design.components.EmptyState
import java.time.LocalDate

/** Upcoming events grouped by day, starting at the selected date, extendable in 60-day steps. */
@Composable
fun AgendaView(vm: CalendarViewModel, onEvent: (EventInstance) -> Unit) {
    var windowDays by rememberSaveable { mutableIntStateOf(60) }
    val from = vm.selectedDate
    var grouped by remember { mutableStateOf<List<Pair<LocalDate, List<EventInstance>>>?>(null) }
    LaunchedEffect(from, windowDays, vm.dataVersion) {
        val events = vm.instances(from, from.plusDays(windowDays.toLong()))
        grouped = groupByDay(events, vm.zone).filterKeys { !it.isBefore(from) && it.isBefore(from.plusDays(windowDays.toLong())) }
            .toSortedMap().map { it.key to it.value }
    }
    val today = LocalDate.now()
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        val list = grouped
        if (list != null && list.isEmpty()) {
            item { EmptyState(Icons.Outlined.EventAvailable, stringResource(R.string.no_upcoming), pluralStringResource(R.plurals.no_upcoming_body, windowDays, windowDays)) }
        }
        list?.forEach { (day, events) ->
            item(key = "h$day") {
                val label = if (day == today) stringResource(R.string.today) + " · " + longDate(day) else longDate(day)
                Text(
                    label, style = MaterialTheme.typography.titleSmall,
                    color = if (day == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp, start = 4.dp).semantics { heading() },
                )
            }
            items(events, key = { "$day-${it.eventId}-${it.begin}" }) { EventRow(it, onClick = { onEvent(it) }) }
        }
        if (list != null) {
            item {
                FilledTonalButton(onClick = { windowDays += 60 }, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
                    Text(stringResource(R.string.load_more))
                }
                Spacer(Modifier.height(80.dp))
            }
        }
    }
}
