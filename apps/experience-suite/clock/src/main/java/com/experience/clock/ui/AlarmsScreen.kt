package com.experience.clock.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Snooze
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.experience.clock.ClockSettings
import com.experience.clock.R
import com.experience.clock.alarm.Alarm
import com.experience.clock.alarm.AlarmRepository
import com.experience.clock.alarm.NextAlarm
import com.experience.design.components.EmptyState
import com.experience.design.components.ExperienceScaffold
import com.experience.design.components.GroupedRow
import com.experience.design.components.SwitchRow
import com.experience.design.theme.ExperienceMotion
import com.experience.design.theme.ExperienceShape
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.Duration
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Calendar
import java.util.Locale

private fun CoroutineScope.io(block: () -> Unit) = launch(Dispatchers.IO) { block() }

/** Locale-ordered week (Sunday first in the US, Monday first elsewhere). */
fun localeWeek(): List<DayOfWeek> {
    val first = DayOfWeek.of(((Calendar.getInstance().firstDayOfWeek + 5) % 7) + 1)
    return List(7) { first.plus(it.toLong()) }
}

@Composable
fun daysSummary(alarm: Alarm): String = when (alarm.days) {
    0 -> {
        val next = NextAlarm.nextOccurrence(alarm.hour, alarm.minute, 0, ZonedDateTime.now())
        stringResource(if (next.toLocalDate() == ZonedDateTime.now().toLocalDate()) R.string.today else R.string.tomorrow)
    }
    Alarm.EVERY_DAY -> stringResource(R.string.every_day)
    Alarm.WEEKDAYS -> stringResource(R.string.weekdays)
    Alarm.daysOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) -> stringResource(R.string.weekends)
    else -> localeWeek().filter { alarm.repeatsOn(it) }.joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
}

@Composable
fun AlarmsScreen(bottomBar: @Composable () -> Unit, highlight: Long?, createNew: Boolean, onConsumed: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val alarms by AlarmRepository.alarms.collectAsState()
    var expanded by rememberSaveable { mutableLongStateOf(-1L) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var settings by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { AlarmRepository.load(context) } }
    LaunchedEffect(highlight, createNew) {
        if (highlight != null) expanded = highlight
        if (createNew) adding = true
        if (highlight != null || createNew) onConsumed()
    }
    // Re-render the "rings in …" summary every minute.
    var tick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); tick = System.currentTimeMillis() } }

    ExperienceScaffold(
        title = stringResource(R.string.tab_alarms),
        bottomBar = bottomBar,
        actions = { IconButton(onClick = { settings = true }) { Icon(Icons.Outlined.Settings, stringResource(R.string.settings)) } },
        floatingActionButton = {
            FloatingActionButton(onClick = { adding = true }, shape = ExperienceShape.hero) { Icon(Icons.Outlined.Add, stringResource(R.string.add_alarm)) }
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "next") { NextAlarmLine(alarms, tick) }
            if (alarms.isEmpty()) item(key = "empty") {
                EmptyState(Icons.Outlined.Alarm, stringResource(R.string.no_alarms), stringResource(R.string.no_alarms_body))
            }
            items(alarms, key = { it.id }) { alarm ->
                AlarmCard(
                    alarm = alarm,
                    expanded = expanded == alarm.id,
                    onExpand = { expanded = if (expanded == alarm.id) -1 else alarm.id },
                    onChange = { updated -> scope.io { AlarmRepository.update(context, updated) } },
                    onToggle = { on -> scope.io { AlarmRepository.setEnabled(context, alarm, on) } },
                    onDelete = { scope.io { AlarmRepository.delete(context, alarm.id) } },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }

    if (adding) {
        TimePickDialog(7, 0, onPick = { h, m ->
            scope.launch {
                val created = withContext(Dispatchers.IO) {
                    AlarmRepository.add(context, Alarm(hour = h, minute = m, snoozeMinutes = ClockSettings(context).defaultSnoozeMinutes))
                }
                expanded = created.id
            }
        }, onDismiss = { adding = false })
    }
    if (settings) SettingsDialog { settings = false }
}

@Composable
private fun NextAlarmLine(alarms: List<Alarm>, tick: Long) {
    val now = ZonedDateTime.now()
    val soonest = remember(alarms, tick) { NextAlarm.soonest(alarms, now) }
    val text = if (soonest == null) stringResource(R.string.no_upcoming_alarm) else {
        val mins = Duration.between(now, soonest.second).toMinutes() + 1
        val h = (mins / 60).toInt()
        val m = (mins % 60).toInt()
        when {
            h == 0 -> pluralStringResource(R.plurals.rings_in_minutes, m, m)
            h < 24 -> stringResource(R.string.rings_in_hours, h, m)
            else -> stringResource(R.string.rings_in_days, h / 24, h % 24)
        }
    }
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(4.dp))
}

@Composable
private fun AlarmCard(
    alarm: Alarm,
    expanded: Boolean,
    onExpand: () -> Unit,
    onChange: (Alarm) -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val bg by animateColorAsState(if (alarm.enabled) scheme.primaryContainer else scheme.surfaceContainer, ExperienceMotion.defaultEffects(), label = "alarmBg")
    val fg = if (alarm.enabled) scheme.onPrimaryContainer else scheme.onSurfaceVariant
    var pickTime by remember { mutableStateOf(false) }
    var editLabel by remember { mutableStateOf(false) }
    val ringtonePicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            @Suppress("DEPRECATION")
            val uri = r.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            val default = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            onChange(alarm.copy(ringtone = when (uri) { null -> ""; default -> null; else -> uri.toString() }))
        }
    }
    val time = formatClock(context, alarm.hour, alarm.minute)

    Surface(shape = MaterialTheme.shapes.extraLarge, color = bg, contentColor = fg, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.animateContentSize(ExperienceMotion.defaultSpatial()).padding(horizontal = 20.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.clickable(onClickLabel = stringResource(R.string.change_time)) { pickTime = true }) {
                        Text(time.first, style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.SemiBold))
                        if (time.second.isNotEmpty()) Text(" " + time.second, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    Text(
                        listOf(alarm.label, daysSummary(alarm)).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (alarm.snoozedUntil != null && alarm.snoozedUntil > System.currentTimeMillis()) {
                        val cal = Calendar.getInstance().apply { timeInMillis = alarm.snoozedUntil }
                        val t = formatClock(context, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
                        Text(stringResource(R.string.snoozed_until, (t.first + " " + t.second).trim()), style = MaterialTheme.typography.labelLarge)
                    }
                }
                Switch(
                    checked = alarm.enabled, onCheckedChange = onToggle,
                    modifier = Modifier.semantics { contentDescription = context.getString(R.string.alarm_switch, time.first + " " + time.second) },
                )
            }
            AnimatedVisibility(expanded) {
                Column(Modifier.padding(top = 12.dp)) {
                    DayToggles(alarm.days) { onChange(alarm.copy(days = it, skipUntil = null)) }
                    Spacer(Modifier.height(12.dp))
                    GroupedRow(stringResource(R.string.label), 0, 4, summary = alarm.label.ifBlank { stringResource(R.string.no_label) },
                        icon = Icons.Outlined.Label, onClick = { editLabel = true })
                    GroupedRow(stringResource(R.string.sound), 1, 4, summary = ringtoneTitle(context, alarm.ringtone), icon = Icons.Outlined.MusicNote,
                        onClick = { ringtonePicker.launch(ringtonePickerIntent(context, alarm.ringtone)) })
                    SwitchRow(stringResource(R.string.vibrate), alarm.vibrate, { onChange(alarm.copy(vibrate = it)) }, 2, 4, icon = Icons.Outlined.Vibration)
                    SnoozeRow(alarm.snoozeMinutes) { onChange(alarm.copy(snoozeMinutes = it)) }
                    TextButton(onClick = onDelete, modifier = Modifier.padding(top = 4.dp)) {
                        Icon(Icons.Outlined.Delete, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.delete))
                    }
                }
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                IconButton(onClick = onExpand) {
                    Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        stringResource(if (expanded) R.string.collapse else R.string.expand))
                }
            }
        }
    }

    if (pickTime) TimePickDialog(alarm.hour, alarm.minute, { h, m ->
        onChange(alarm.copy(hour = h, minute = m, enabled = true, snoozedUntil = null, skipUntil = null))
    }, { pickTime = false })
    if (editLabel) {
        var text by remember { mutableStateOf(alarm.label) }
        AlertDialog(
            onDismissRequest = { editLabel = false },
            title = { Text(stringResource(R.string.label)) },
            text = { OutlinedTextField(text, { text = it.take(60) }, singleLine = true) },
            confirmButton = { TextButton(onClick = { onChange(alarm.copy(label = text.trim())); editLabel = false }) { Text(stringResource(android.R.string.ok)) } },
            dismissButton = { TextButton(onClick = { editLabel = false }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }
}

@Composable
private fun SnoozeRow(minutes: Int, onPick: (Int) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        GroupedRow(stringResource(R.string.snooze_length), 3, 4, summary = pluralStringResource(R.plurals.minutes, minutes, minutes),
            icon = Icons.Outlined.Snooze, onClick = { menu = true })
        DropdownMenu(menu, { menu = false }) {
            listOf(1, 5, 10, 15, 20, 30).forEach { m ->
                DropdownMenuItem(text = { Text(pluralStringResource(R.plurals.minutes, m, m)) }, onClick = { onPick(m); menu = false })
            }
        }
    }
}

/** Seven round toggles; each states its day and whether it is on (not colour alone). */
@Composable
private fun DayToggles(days: Int, onChange: (Int) -> Unit) {
    val on = stringResource(R.string.on)
    val off = stringResource(R.string.off)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        localeWeek().forEach { d ->
            val selected = days and Alarm.dayBit(d) != 0
            val scheme = MaterialTheme.colorScheme
            Surface(
                shape = CircleShape,
                color = if (selected) scheme.primary else scheme.surface.copy(alpha = 0.6f),
                contentColor = if (selected) scheme.onPrimary else scheme.onSurface,
                modifier = Modifier.size(44.dp).clickable(role = Role.Checkbox) { onChange(days xor Alarm.dayBit(d)) }
                    .semantics {
                        contentDescription = d.getDisplayName(TextStyle.FULL, Locale.getDefault())
                        stateDescription = if (selected) on else off
                    },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(d.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal))
                }
            }
        }
    }
}

/** (time, am/pm) respecting the 12/24-hour setting. */
fun formatClock(context: Context, hour: Int, minute: Int): Pair<String, String> {
    if (android.text.format.DateFormat.is24HourFormat(context)) return "%02d:%02d".format(hour, minute) to ""
    val h12 = if (hour % 12 == 0) 12 else hour % 12
    val cal = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, hour) }
    val ampm = java.text.DateFormatSymbols.getInstance().amPmStrings[cal.get(Calendar.AM_PM)]
    return "%d:%02d".format(h12, minute) to ampm
}

fun ringtonePickerIntent(context: Context, current: String?): Intent =
    Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
        .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
        .putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
        .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, context.getString(R.string.sound))
        .putExtra(
            RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
            when (current) { null -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM); "" -> null; else -> Uri.parse(current) },
        )

@Composable
fun ringtoneTitle(context: Context, ringtone: String?): String {
    val silent = stringResource(R.string.silent)
    val def = stringResource(R.string.default_sound)
    var title by remember(ringtone) { mutableStateOf(if (ringtone == null) def else if (ringtone == "") silent else "…") }
    LaunchedEffect(ringtone) {
        if (!ringtone.isNullOrEmpty()) {
            title = withContext(Dispatchers.IO) {
                runCatching { RingtoneManager.getRingtone(context, Uri.parse(ringtone))?.getTitle(context) }.getOrNull() ?: def
            }
        }
    }
    return title
}

@Composable
private fun SettingsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { ClockSettings(context) }
    var silence by remember { mutableStateOf(settings.autoSilenceMinutes) }
    var snooze by remember { mutableStateOf(settings.defaultSnoozeMinutes) }
    var ramp by remember { mutableStateOf(settings.volumeRampSeconds) }
    var timerSound by remember { mutableStateOf(settings.timerRingtone) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            @Suppress("DEPRECATION")
            val uri = r.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            val v = when (uri) { null -> ""; RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) -> null; else -> uri.toString() }
            timerSound = v
            settings.timerRingtone = v
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings)) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
        text = {
            Column {
                ChoiceRow(stringResource(R.string.auto_silence), listOf(1, 5, 10, 15, 20, 30, 0), silence,
                    { if (it == 0) context.getString(R.string.never) else context.resources.getQuantityString(R.plurals.minutes, it, it) }, 0, 4) {
                    silence = it; settings.autoSilenceMinutes = it
                }
                ChoiceRow(stringResource(R.string.default_snooze), listOf(1, 5, 10, 15, 20, 30), snooze,
                    { context.resources.getQuantityString(R.plurals.minutes, it, it) }, 1, 4) {
                    snooze = it; settings.defaultSnoozeMinutes = it
                }
                ChoiceRow(stringResource(R.string.volume_ramp), listOf(0, 15, 30, 60), ramp,
                    { if (it == 0) context.getString(R.string.off) else context.resources.getQuantityString(R.plurals.seconds, it, it) }, 2, 4) {
                    ramp = it; settings.volumeRampSeconds = it
                }
                GroupedRow(stringResource(R.string.timer_sound), 3, 4, summary = ringtoneTitle(context, timerSound),
                    onClick = { picker.launch(ringtonePickerIntent(context, timerSound)) })
            }
        },
    )
}

@Composable
private fun ChoiceRow(title: String, options: List<Int>, value: Int, label: (Int) -> String, index: Int, count: Int, onPick: (Int) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        GroupedRow(title, index, count, summary = label(value), onClick = { menu = true })
        DropdownMenu(menu, { menu = false }) {
            options.forEach { o -> DropdownMenuItem(text = { Text(label(o)) }, onClick = { onPick(o); menu = false }) }
        }
    }
}
