package com.experience.calendar.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CalendarViewDay
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.experience.calendar.CalendarViewModel
import com.experience.calendar.R
import com.experience.calendar.ViewMode
import com.experience.calendar.logic.EventDraft
import com.experience.design.components.ExperienceScaffold
import com.experience.design.components.WidthClass
import com.experience.design.components.currentWidthClass
import com.experience.design.theme.ExperienceMotion
import java.time.LocalDate

@Composable
fun CalendarApp(vm: CalendarViewModel) {
    val wide = currentWidthClass() != WidthClass.COMPACT
    AnimatedContent(
        targetState = vm.editing,
        contentKey = { it != null },
        transitionSpec = { (fadeIn(ExperienceMotion.defaultEffects()) + slideInVertically(ExperienceMotion.defaultSpatial()) { it / 8 }) togetherWith fadeOut(ExperienceMotion.fastEffects()) },
        label = "editor",
    ) { draft ->
        if (draft != null) {
            androidx.activity.compose.BackHandler { vm.editing = null }
            EventEditor(draft, vm.calendars, onSave = { vm.save(it) }, onCancel = { vm.editing = null })
        } else {
            Browser(vm, wide)
        }
    }

    vm.openEvent?.let { open ->
        EventDetailSheet(
            open,
            onDismiss = { vm.openEvent = null },
            onEdit = { vm.edit(open.details.draft.id) },
            onDelete = { occurrenceOnly -> vm.delete(open.details.draft.id, if (occurrenceOnly) open.instanceBegin else null) },
        )
    }
    if (vm.showCalendars) CalendarsSheet(vm.calendars, vm::setVisible) { vm.showCalendars = false }
    vm.error?.let { err ->
        AlertDialog(
            onDismissRequest = { vm.error = null },
            text = { Text(stringResource(err)) },
            confirmButton = { TextButton(onClick = { vm.error = null }) { Text(stringResource(android.R.string.ok)) } },
        )
    }
}

@Composable
private fun Browser(vm: CalendarViewModel, wide: Boolean) {
    val title = when (vm.mode) {
        ViewMode.MONTH, ViewMode.DAY -> monthTitle(vm.selectedDate)
        ViewMode.AGENDA -> stringResource(R.string.view_agenda)
    }
    ExperienceScaffold(
        title = title,
        maxContentWidth = 1400.dp,
        actions = {
            IconButton(onClick = { vm.selectedDate = LocalDate.now() }) { Icon(Icons.Outlined.Today, stringResource(R.string.go_to_today)) }
            IconButton(onClick = { vm.showCalendars = true }) { Icon(Icons.Outlined.Layers, stringResource(R.string.calendars)) }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { vm.newEvent() },
                icon = { Icon(Icons.Outlined.Add, null) },
                text = { Text(stringResource(R.string.new_event)) },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                val modes = listOf(
                    Triple(ViewMode.MONTH, R.string.view_month, Icons.Outlined.CalendarMonth),
                    Triple(ViewMode.AGENDA, R.string.view_agenda, Icons.Outlined.ViewAgenda),
                    Triple(ViewMode.DAY, R.string.view_day, Icons.Outlined.CalendarViewDay),
                )
                modes.forEachIndexed { i, (mode, label, icon) ->
                    SegmentedButton(
                        selected = vm.mode == mode,
                        onClick = { vm.mode = mode },
                        shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                        icon = { SegmentedButtonDefaults.Icon(vm.mode == mode) { Icon(icon, null, Modifier.padding(0.dp)) } },
                        label = { Text(stringResource(label)) },
                    )
                }
            }
            val onEvent = { e: com.experience.calendar.data.EventInstance -> vm.open(e.eventId, e.begin, e.end) }
            when (vm.mode) {
                ViewMode.MONTH -> MonthView(vm, wide, onEvent)
                ViewMode.AGENDA -> AgendaView(vm, onEvent)
                ViewMode.DAY -> DayView(vm, onEvent) { date, time ->
                    val start = date.atTime(time)
                    vm.newEvent(
                        EventDraft.newAt(date, vm.zone).copy(
                            startDate = date, startTime = time,
                            endDate = start.plusHours(1).toLocalDate(), endTime = start.plusHours(1).toLocalTime(),
                        )
                    )
                }
            }
        }
    }
}
