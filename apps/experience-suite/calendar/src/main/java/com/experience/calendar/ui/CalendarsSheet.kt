package com.experience.calendar.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.experience.calendar.R
import com.experience.calendar.data.CalendarInfo
import com.experience.design.components.GroupedRow
import com.experience.design.components.SectionHeader

/** Calendars grouped by account, each with its colour and a visibility checkbox. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarsSheet(calendars: List<CalendarInfo>, onToggle: (CalendarInfo, Boolean) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
            Text(stringResource(R.string.calendars), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 8.dp))
            if (calendars.isEmpty()) Text(stringResource(R.string.no_calendars), modifier = Modifier.padding(8.dp))
            calendars.groupBy { it.accountName }.forEach { (account, list) ->
                SectionHeader(account)
                list.forEachIndexed { i, cal ->
                    GroupedRow(
                        title = cal.name,
                        summary = if (cal.writable) null else stringResource(R.string.read_only),
                        index = i, count = list.size,
                        onClick = { onToggle(cal, !cal.visible) },
                        trailing = {
                            ColorDot(calendarColor(cal.color), 16)
                            Checkbox(checked = cal.visible, onCheckedChange = { onToggle(cal, it) })
                        },
                    )
                }
            }
        }
    }
}
