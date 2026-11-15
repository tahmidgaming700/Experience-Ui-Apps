package com.experience.calendar.ui

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.experience.calendar.OpenEvent
import com.experience.calendar.R
import com.experience.calendar.logic.RecurrencePreset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailSheet(
    open: OpenEvent,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: (occurrenceOnly: Boolean) -> Unit,
) {
    val context = LocalContext.current
    val d = open.details.draft
    var confirm by remember { mutableStateOf(false) }
    val begin = open.instanceBegin ?: d.startMillis()
    val end = open.instanceEnd ?: d.endMillis()
    val flags = if (d.allDay) DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_UTC
    else DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_SHOW_TIME
    val recurring = open.details.rrule != null

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ColorDot(calendarColor(open.details.color), 16)
                Spacer(Modifier.width(12.dp))
                Text(d.title.ifBlank { stringResource(R.string.untitled_event) }, style = MaterialTheme.typography.headlineSmall)
            }
            Spacer(Modifier.height(16.dp))
            DetailLine(Icons.Outlined.Schedule, DateUtils.formatDateRange(context, begin, end, flags) +
                if (d.allDay) " · " + stringResource(R.string.all_day) else "")
            if (recurring) DetailLine(Icons.Outlined.Repeat, if (d.preset == RecurrencePreset.CUSTOM) stringResource(R.string.repeat_custom) else presetLabel(d.preset, d.startDate))
            if (d.location.isNotBlank()) DetailLine(Icons.Outlined.Place, d.location)
            if (d.reminders.isNotEmpty()) DetailLine(Icons.Outlined.Notifications, d.reminders.sorted().map { reminderLabel(it) }.joinToString(", "))
            DetailLine(Icons.Outlined.CalendarMonth, open.details.calendarName)
            if (d.description.isNotBlank()) DetailLine(Icons.Outlined.Notes, d.description)
            if (open.details.canModify) {
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = onEdit, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Edit, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.edit))
                    }
                    OutlinedButton(onClick = { confirm = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Delete, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.delete))
                    }
                }
                if (recurring) {
                    Text(stringResource(R.string.edit_applies_to_series), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.delete_event_q)) },
            text = { if (recurring) Text(stringResource(R.string.delete_recurring_body)) },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    if (recurring && open.instanceBegin != null) {
                        TextButton(onClick = { confirm = false; onDelete(true) }) { Text(stringResource(R.string.delete_this_event)) }
                    }
                    TextButton(onClick = { confirm = false; onDelete(false) }) {
                        Text(stringResource(if (recurring) R.string.delete_all_events else R.string.delete))
                    }
                }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }
}

@Composable
private fun DetailLine(icon: ImageVector, text: String) {
    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}
