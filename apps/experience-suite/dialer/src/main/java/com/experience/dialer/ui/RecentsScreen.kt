package com.experience.dialer.ui

import android.Manifest
import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.experience.design.components.EmptyState
import com.experience.design.components.ExperienceScaffold
import com.experience.design.components.PermissionGate
import com.experience.design.components.SectionHeader
import com.experience.design.theme.ExperienceShape
import com.experience.dialer.DialerViewModel
import com.experience.dialer.data.CallGroup
import com.experience.dialer.data.CallKind
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun RecentsScreen(vm: DialerViewModel, bottomBar: @Composable () -> Unit) {
    var confirmClear by remember { mutableStateOf(false) }
    val groups by vm.callLog.collectAsStateWithLifecycle()
    ExperienceScaffold(
        title = "Recents",
        bottomBar = bottomBar,
        actions = {
            if (groups.isNotEmpty()) {
                IconButton(onClick = { confirmClear = true }) { Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear call history") }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            PermissionGate(
                listOf(Manifest.permission.READ_CALL_LOG, Manifest.permission.WRITE_CALL_LOG, Manifest.permission.READ_CONTACTS),
                "Call history",
                "Allow access to the call log to see and manage your recent calls.",
            ) {
                LaunchedEffect(Unit) { vm.refresh(); vm.markMissedRead() }
                RecentsList(vm, groups)
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear call history?") },
            text = { Text("All calls will be removed from the call log.") },
            confirmButton = { TextButton(onClick = { vm.clearCallLog(); confirmClear = false }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun RecentsList(vm: DialerViewModel, groups: List<CallGroup>) {
    val call = rememberCallAction()
    var missedOnly by rememberSaveable { mutableStateOf(false) }
    var details by remember { mutableStateOf<CallGroup?>(null) }
    val shown = remember(groups, missedOnly) { if (missedOnly) groups.filter { g -> g.types.any { CallKind.of(it) == CallKind.MISSED } } else groups }
    val zone = remember { ZoneId.systemDefault() }
    val sections = remember(shown) { shown.groupBy { Instant.ofEpochMilli(it.latest.dateMillis).atZone(zone).toLocalDate() } }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
        item { DialerRoleBanner() }
        item {
            Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !missedOnly, onClick = { missedOnly = false }, label = { Text("All") }, modifier = Modifier.heightIn(min = 48.dp))
                FilterChip(selected = missedOnly, onClick = { missedOnly = true }, label = { Text("Missed") }, modifier = Modifier.heightIn(min = 48.dp))
            }
        }
        if (shown.isEmpty()) {
            item { EmptyState(Icons.Filled.History, if (missedOnly) "No missed calls" else "No recent calls") }
        }
        sections.forEach { (day, items) ->
            item(key = "h$day") { SectionHeader(dayLabel(day)) }
            items(items.size, key = { items[it].latest.id }) { i ->
                CallGroupRow(vm, items[i], i, items.size, onOpen = { details = items[i] }, onCall = { call(items[i].latest.number) })
            }
        }
    }
    details?.let { g ->
        CallDetailsSheet(vm, g, onDismiss = { details = null }, onCall = { call(it) })
    }
}

private fun dayLabel(day: LocalDate): String {
    val today = LocalDate.now()
    return when (day) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    }
}

@Composable
private fun CallGroupRow(vm: DialerViewModel, group: CallGroup, index: Int, count: Int, onOpen: () -> Unit, onCall: () -> Unit) {
    val e = group.latest
    val kind = CallKind.of(e.type)
    val name = e.name ?: if (e.number.isBlank()) "Private number" else vm.formatter.format(e.number)
    val time = DateUtils.formatDateTime(null, e.dateMillis, DateUtils.FORMAT_SHOW_TIME)
    Surface(
        shape = ExperienceShape.grouped(index, count),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
    ) {
        Row(
            Modifier.clickable(role = Role.Button, onClickLabel = "Call details", onClick = onOpen).padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ContactAvatar(name, e.photoUri)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    name + if (group.count > 1) " (${group.count})" else "",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (kind == CallKind.MISSED && !e.isRead) FontWeight.Bold else null,
                    color = if (kind == CallKind.MISSED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(kind.icon, contentDescription = null, tint = kind.tint, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        listOfNotNull(kind.label, e.numberLabel.takeIf { e.name != null }, time).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (e.number.isNotBlank()) {
                IconButton(onClick = onCall, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.Call, contentDescription = "Call $name", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
