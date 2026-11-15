package com.experience.clock.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.experience.clock.R
import com.experience.clock.world.WorldClocks
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickDialog(hour: Int, minute: Int, onPick: (Int, Int) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val state = rememberTimePickerState(hour, minute, android.text.format.DateFormat.is24HourFormat(context))
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onPick(state.hour, state.minute); onDismiss() }) { Text(stringResource(android.R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
        text = { TimePicker(state) },
    )
}

/** Searchable list of cities derived from IANA zone ids. */
@Composable
fun ZonePickerDialog(exclude: Set<String>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val all = remember { WorldClocks.allZones() }
    var query by remember { mutableStateOf("") }
    val shown = remember(query, exclude) { WorldClocks.search(all, query).filter { it.id !in exclude } }
    val now = remember { Instant.now() }
    val local = remember { ZoneId.systemDefault() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_city)) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
        text = {
            Column(Modifier.fillMaxHeight(0.75f)) {
                OutlinedTextField(query, { query = it }, singleLine = true, leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    placeholder = { Text(stringResource(R.string.search_city)) }, modifier = Modifier.fillMaxWidth())
                LazyColumn {
                    items(shown, key = { it.id }) { z ->
                        val diff = WorldClocks.formatDifference(WorldClocks.differenceMinutes(ZoneId.of(z.id), local, now))
                        Column(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button) { onPick(z.id); onDismiss() }.padding(vertical = 8.dp)) {
                            Text(z.city, style = MaterialTheme.typography.titleMedium)
                            Text(z.region + " · " + stringResource(R.string.hours_diff, diff), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
    )
}
