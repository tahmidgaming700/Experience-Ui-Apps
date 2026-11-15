package com.experience.calendar.ui

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.experience.calendar.R
import com.experience.calendar.data.CalendarInfo
import com.experience.calendar.logic.EventDraft
import com.experience.calendar.logic.Recurrence
import com.experience.calendar.logic.RecurrenceEnd
import com.experience.calendar.logic.RecurrencePreset
import com.experience.calendar.logic.TimeZones
import com.experience.design.components.ExperienceScaffold
import com.experience.design.components.GroupedRow
import com.experience.design.components.SectionHeader
import com.experience.design.components.SwitchRow
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

private enum class Pick { START_DATE, START_TIME, END_DATE, END_TIME, ZONE }

private val REMINDER_CHOICES = listOf(0, 5, 10, 15, 30, 60, 120, 1440, 2880, 10080)

@Composable
fun presetLabel(preset: RecurrencePreset, start: LocalDate): String {
    val dow = start.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
    return when (preset) {
        RecurrencePreset.NONE -> stringResource(R.string.repeat_none)
        RecurrencePreset.DAILY -> stringResource(R.string.repeat_daily)
        RecurrencePreset.WEEKDAYS -> stringResource(R.string.repeat_weekdays)
        RecurrencePreset.WEEKLY -> stringResource(R.string.repeat_weekly, dow)
        RecurrencePreset.MONTHLY_BY_DATE -> stringResource(R.string.repeat_monthly_date, start.dayOfMonth)
        RecurrencePreset.MONTHLY_BY_WEEKDAY -> {
            val ord = Recurrence.weekdayOrdinal(start)
            val ordText = if (ord == -1) stringResource(R.string.ordinal_last) else stringResource(
                listOf(R.string.ordinal_1, R.string.ordinal_2, R.string.ordinal_3, R.string.ordinal_4)[ord - 1]
            )
            stringResource(R.string.repeat_monthly_weekday, ordText, dow)
        }
        RecurrencePreset.YEARLY -> stringResource(R.string.repeat_yearly, mediumDate(start))
        RecurrencePreset.CUSTOM -> stringResource(R.string.repeat_custom)
    }
}

@Composable
fun reminderLabel(minutes: Int): String = when {
    minutes == 0 -> stringResource(R.string.reminder_at_start)
    minutes % 10080 == 0 -> pluralStringResource(R.plurals.reminder_weeks, minutes / 10080, minutes / 10080)
    minutes % 1440 == 0 -> pluralStringResource(R.plurals.reminder_days, minutes / 1440, minutes / 1440)
    minutes % 60 == 0 -> pluralStringResource(R.plurals.reminder_hours, minutes / 60, minutes / 60)
    else -> pluralStringResource(R.plurals.reminder_minutes, minutes, minutes)
}

/** Create/edit form. Edits to a repeating event apply to the whole series. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EventEditor(
    initial: EventDraft,
    calendars: List<CalendarInfo>,
    onSave: (EventDraft) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    var d by remember(initial) { mutableStateOf(initial) }
    var pick by remember { mutableStateOf<Pick?>(null) }
    var calMenu by remember { mutableStateOf(false) }
    var repeatMenu by remember { mutableStateOf(false) }
    var endMenu by remember { mutableStateOf(false) }
    var reminderMenu by remember { mutableStateOf(false) }
    val writable = calendars.filter { it.writable }

    /** Moving the start keeps the duration; moving the end before the start is rejected by validation. */
    fun moveStart(date: LocalDate = d.startDate, time: LocalTime = d.startTime) {
        val oldStart = LocalDateTime.of(d.startDate, d.startTime)
        val oldEnd = LocalDateTime.of(d.endDate, d.endTime)
        val length = Duration.between(oldStart, oldEnd).let { if (it.isNegative) Duration.ofHours(1) else it }
        val newStart = LocalDateTime.of(date, time)
        val newEnd = newStart.plus(length)
        d = d.copy(startDate = date, startTime = time, endDate = newEnd.toLocalDate(), endTime = newEnd.toLocalTime())
    }

    ExperienceScaffold(
        title = stringResource(if (initial.id == 0L) R.string.new_event else R.string.edit_event),
        onBack = onCancel,
        actions = {
            Button(onClick = { onSave(d) }, enabled = d.isValid, modifier = Modifier.padding(end = 8.dp)) { Text(stringResource(R.string.save)) }
        },
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            OutlinedTextField(d.title, { d = d.copy(title = it) }, label = { Text(stringResource(R.string.field_title)) },
                singleLine = true, modifier = Modifier.fillMaxWidth())

            SectionHeader(stringResource(R.string.section_when))
            SwitchRow(stringResource(R.string.all_day), d.allDay, { d = d.copy(allDay = it) }, index = 0, count = if (d.allDay) 3 else 4,
                icon = Icons.Outlined.Schedule)
            GroupedRow(
                title = stringResource(R.string.starts), summary = dateTimeText(context, d.startDate, d.startTime, d.allDay),
                index = 1, count = if (d.allDay) 3 else 4, icon = Icons.Outlined.Event,
                trailing = {
                    Row {
                        TextButton(onClick = { pick = Pick.START_DATE }) { Text(stringResource(R.string.change_date)) }
                        if (!d.allDay) TextButton(onClick = { pick = Pick.START_TIME }) { Text(stringResource(R.string.change_time)) }
                    }
                },
            )
            GroupedRow(
                title = stringResource(R.string.ends), summary = dateTimeText(context, d.endDate, d.endTime, d.allDay),
                index = 2, count = if (d.allDay) 3 else 4, icon = Icons.Outlined.Event,
                trailing = {
                    Row {
                        TextButton(onClick = { pick = Pick.END_DATE }) { Text(stringResource(R.string.change_date)) }
                        if (!d.allDay) TextButton(onClick = { pick = Pick.END_TIME }) { Text(stringResource(R.string.change_time)) }
                    }
                },
            )
            if (!d.allDay) {
                val zone = runCatching { ZoneId.of(d.timeZone) }.getOrDefault(ZoneId.systemDefault())
                val offset = zone.rules.getOffset(java.time.Instant.now()).totalSeconds
                GroupedRow(
                    title = stringResource(R.string.time_zone),
                    summary = TimeZones.cityName(d.timeZone) + " · " + TimeZones.offsetLabel(offset),
                    index = 3, count = 4, icon = Icons.Outlined.Public, onClick = { pick = Pick.ZONE },
                )
            }
            if (!d.isValid) {
                Text(stringResource(R.string.error_end_before_start), color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(8.dp))
            }

            SectionHeader(stringResource(R.string.section_repeat))
            Box {
                GroupedRow(
                    title = presetLabel(d.preset, d.startDate), index = 0, count = if (d.preset == RecurrencePreset.NONE) 1 else 2,
                    icon = Icons.Outlined.Repeat, onClick = { repeatMenu = true },
                )
                DropdownMenu(repeatMenu, { repeatMenu = false }) {
                    RecurrencePreset.entries.filter { it != RecurrencePreset.CUSTOM || d.customRrule != null }.forEach { p ->
                        DropdownMenuItem(text = { Text(presetLabel(p, d.startDate)) }, onClick = { d = d.copy(preset = p); repeatMenu = false })
                    }
                }
            }
            if (d.preset != RecurrencePreset.NONE && d.preset != RecurrencePreset.CUSTOM) {
                Box {
                    val endText = when (val e = d.recurrenceEnd) {
                        RecurrenceEnd.Never -> stringResource(R.string.repeat_forever)
                        is RecurrenceEnd.Count -> pluralStringResource(R.plurals.repeat_count, e.times, e.times)
                        is RecurrenceEnd.Until -> stringResource(R.string.repeat_until, mediumDate(e.date))
                    }
                    GroupedRow(title = stringResource(R.string.repeat_ends), summary = endText, index = 1, count = 2, onClick = { endMenu = true })
                    DropdownMenu(endMenu, { endMenu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.repeat_forever)) }, onClick = { d = d.copy(recurrenceEnd = RecurrenceEnd.Never); endMenu = false })
                        listOf(5, 10, 20).forEach { n ->
                            DropdownMenuItem(text = { Text(pluralStringResource(R.plurals.repeat_count, n, n)) },
                                onClick = { d = d.copy(recurrenceEnd = RecurrenceEnd.Count(n)); endMenu = false })
                        }
                        listOf(1L, 3L, 6L, 12L).forEach { months ->
                            val until = d.startDate.plusMonths(months)
                            DropdownMenuItem(text = { Text(stringResource(R.string.repeat_until, mediumDate(until))) },
                                onClick = { d = d.copy(recurrenceEnd = RecurrenceEnd.Until(until)); endMenu = false })
                        }
                    }
                }
            }

            SectionHeader(stringResource(R.string.section_details))
            OutlinedTextField(d.location, { d = d.copy(location = it) }, label = { Text(stringResource(R.string.field_location)) },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(d.description, { d = d.copy(description = it) }, label = { Text(stringResource(R.string.field_description)) },
                minLines = 3, modifier = Modifier.fillMaxWidth())

            SectionHeader(stringResource(R.string.section_calendar))
            Box {
                val cal = calendars.firstOrNull { it.id == d.calendarId }
                GroupedRow(
                    title = cal?.name ?: stringResource(R.string.choose_calendar),
                    summary = cal?.accountName,
                    index = 0, count = 1,
                    onClick = if (initial.id == 0L) ({ calMenu = true }) else null,
                    trailing = { cal?.let { ColorDot(calendarColor(it.color), 16) } },
                )
                DropdownMenu(calMenu, { calMenu = false }) {
                    writable.forEach { c ->
                        DropdownMenuItem(
                            text = { Text(c.name) }, leadingIcon = { ColorDot(calendarColor(c.color), 14) },
                            onClick = { d = d.copy(calendarId = c.id); calMenu = false },
                        )
                    }
                }
            }

            SectionHeader(stringResource(R.string.section_reminders))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                d.reminders.sorted().forEach { m ->
                    InputChip(
                        selected = false, onClick = { d = d.copy(reminders = d.reminders - m) },
                        label = { Text(reminderLabel(m)) },
                        leadingIcon = { Icon(Icons.Outlined.Notifications, null) },
                        trailingIcon = { Icon(Icons.Outlined.Close, stringResource(R.string.remove_reminder)) },
                    )
                }
                Box {
                    AssistChip(onClick = { reminderMenu = true }, label = { Text(stringResource(R.string.add_reminder)) },
                        leadingIcon = { Icon(Icons.Outlined.Add, null) })
                    DropdownMenu(reminderMenu, { reminderMenu = false }) {
                        REMINDER_CHOICES.filter { it !in d.reminders }.forEach { m ->
                            DropdownMenuItem(text = { Text(reminderLabel(m)) }, onClick = { d = d.copy(reminders = d.reminders + m); reminderMenu = false })
                        }
                    }
                }
            }
            Spacer(Modifier.height(48.dp))
        }
    }

    when (pick) {
        Pick.START_DATE -> DatePickDialog(d.startDate, { moveStart(date = it) }, { pick = null })
        Pick.START_TIME -> TimePickDialog(d.startTime, { moveStart(time = it) }, { pick = null })
        Pick.END_DATE -> DatePickDialog(d.endDate, { d = d.copy(endDate = it) }, { pick = null })
        Pick.END_TIME -> TimePickDialog(d.endTime, { d = d.copy(endTime = it) }, { pick = null })
        Pick.ZONE -> TimeZoneDialog(d.timeZone, { d = d.copy(timeZone = it) }, { pick = null })
        null -> Unit
    }
}

private fun dateTimeText(context: android.content.Context, date: LocalDate, time: LocalTime, allDay: Boolean): String {
    val dateText = mediumDate(date)
    if (allDay) return dateText
    val millis = date.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    return dateText + " · " + DateUtils.formatDateTime(context, millis, DateUtils.FORMAT_SHOW_TIME)
}
