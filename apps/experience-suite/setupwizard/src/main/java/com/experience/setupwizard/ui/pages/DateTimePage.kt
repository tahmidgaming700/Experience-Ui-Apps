package com.experience.setupwizard.ui.pages

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.experience.design.components.GroupedRow
import com.experience.design.components.SectionHeader
import com.experience.design.components.SwitchRow
import com.experience.setupwizard.SetupViewModel
import com.experience.setupwizard.model.TimeZoneCatalog
import com.experience.setupwizard.model.ZoneOption
import com.experience.setupwizard.ui.ChoiceRow
import com.experience.setupwizard.ui.PageLayout
import com.experience.setupwizard.ui.SearchField
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.TimeZone

@Composable
fun DateTimePage(vm: SetupViewModel) {
    val context = LocalContext.current
    val s by vm.systemState.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var pickZone by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) { now = System.currentTimeMillis(); delay(1000) }
    }
    val zone = TimeZone.getTimeZone(s.timeZoneId)
    val dateText = DateFormat.getLongDateFormat(context).apply { timeZone = zone }.format(now)
    val timeText = DateFormat.getTimeFormat(context).apply { timeZone = zone }.format(now)

    PageLayout(Icons.Outlined.Schedule, "Date & time", "Use the network-provided time, or set it yourself.") {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = com.experience.design.theme.ExperienceShape.hero) {
                Column(Modifier.padding(24.dp)) {
                    Text(timeText, style = MaterialTheme.typography.displayMedium)
                    Text(dateText, style = MaterialTheme.typography.titleMedium)
                }
            }
            SectionHeader("Automatic")
            SwitchRow("Set time automatically", s.autoTime, vm::setAutoTime, 0, 2, summary = "Use the time provided by the network")
            SwitchRow("Set time zone automatically", s.autoTimeZone, vm::setAutoTimeZone, 1, 2, summary = "Use the mobile network's time zone")
            val manualRows = buildList {
                if (!s.autoTimeZone) add("zone")
                if (!s.autoTime) { add("date"); add("time") }
            }
            if (manualRows.isNotEmpty()) SectionHeader("Manual")
            manualRows.forEachIndexed { i, row ->
                when (row) {
                    "zone" -> GroupedRow(
                        "Time zone", i, manualRows.size,
                        summary = "${zone.getDisplayName(zone.inDaylightTime(java.util.Date(now)), TimeZone.LONG)} (${TimeZoneCatalog.formatOffset(zone.getOffset(now))})",
                        icon = Icons.Outlined.Public, onClick = { pickZone = true },
                    )
                    "date" -> GroupedRow("Date", i, manualRows.size, summary = dateText, icon = Icons.Outlined.CalendarMonth, onClick = {
                        val c = Calendar.getInstance(zone).apply { timeInMillis = now }
                        DatePickerDialog(context, { _, y, m, d ->
                            vm.setTime(Calendar.getInstance(zone).apply { timeInMillis = System.currentTimeMillis(); set(y, m, d) }.timeInMillis)
                        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
                    })
                    "time" -> GroupedRow("Time", i, manualRows.size, summary = timeText, icon = Icons.Outlined.Schedule, onClick = {
                        val c = Calendar.getInstance(zone).apply { timeInMillis = now }
                        TimePickerDialog(context, { _, h, min ->
                            vm.setTime(Calendar.getInstance(zone).apply {
                                timeInMillis = System.currentTimeMillis()
                                set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, min); set(Calendar.SECOND, 0)
                            }.timeInMillis)
                        }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), DateFormat.is24HourFormat(context)).show()
                    })
                }
            }
        }
    }
    if (pickZone) {
        val region by vm.region.collectAsState()
        TimeZonePicker(region, s.timeZoneId, onDismiss = { pickZone = false }) { id ->
            vm.setTimeZone(id)
            pickZone = false
        }
    }
}

/** Full-screen searchable time-zone list; zones of the chosen region come first. */
@Composable
private fun TimeZonePicker(region: String, current: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val all = remember { TimeZoneCatalog.zones() }
    val suggested: List<ZoneOption> = remember(region, all) {
        val ids = runCatching { android.icu.util.TimeZone.getAvailableIDs(region).toSet() }.getOrDefault(emptySet())
        all.filter { it.id in ids }
    }
    val items = remember(query, all, suggested) {
        if (query.isBlank()) suggested + all.filter { it !in suggested } else TimeZoneCatalog.filter(all, query)
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(16.dp)) {
                Text("Time zone", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(8.dp))
                SearchField(query, { query = it }, "Search city or GMT offset")
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    itemsIndexed(items, key = { _, z -> z.id }) { i, z ->
                        ChoiceRow(z.city, z.id == current, i, items.size, summary = "${z.area} · ${z.offsetLabel}") { onPick(z.id) }
                    }
                }
            }
        }
    }
}
