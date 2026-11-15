package com.experience.clock.ui

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.experience.clock.R
import com.experience.clock.timer.Timer
import com.experience.clock.timer.TimerRepository
import com.experience.design.components.ExperienceScaffold
import com.experience.design.theme.ExperienceShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun TimerScreen(bottomBar: @Composable () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { launch(Dispatchers.IO) { TimerRepository.ensureLoaded(context) } }
    val timers by TimerRepository.timers.collectAsState()
    var entering by rememberSaveable { mutableStateOf(false) }
    var digits by rememberSaveable { mutableStateOf("") }
    val showEntry = entering || timers.isEmpty()

    // Tick while something is counting.
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val ticking = timers.any { it.state == Timer.State.RUNNING || it.state == Timer.State.EXPIRED }
    LaunchedEffect(ticking) { while (ticking) { now = SystemClock.elapsedRealtime(); delay(200) } }

    fun io(block: () -> Unit) { scope.launch(Dispatchers.IO) { block() } }

    ExperienceScaffold(
        title = stringResource(R.string.tab_timer),
        bottomBar = bottomBar,
        floatingActionButton = {
            if (showEntry) {
                if (TimerInput.toMillis(digits) > 0) ExtendedFloatingActionButton(
                    onClick = {
                        val ms = TimerInput.toMillis(digits)
                        io { TimerRepository.add(context, ms) }
                        digits = ""; entering = false
                    },
                    icon = { Icon(Icons.Outlined.PlayArrow, null) },
                    text = { Text(stringResource(R.string.start)) },
                )
            } else {
                ExtendedFloatingActionButton(onClick = { entering = true }, icon = { Icon(Icons.Outlined.Add, null) },
                    text = { Text(stringResource(R.string.add_timer)) })
            }
        },
    ) { padding ->
        if (showEntry) {
            Column(Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                TimerEntry(digits, onDigit = { digits = TimerInput.push(digits, it) }, onBackspace = { digits = TimerInput.pop(digits) })
                if (timers.isNotEmpty()) TextButton(onClick = { entering = false; digits = "" }) { Text(stringResource(android.R.string.cancel)) }
            }
        } else {
            LazyColumn(
                Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(timers, key = { it.id }) { t ->
                    TimerCard(
                        t, now, Modifier.animateItem(),
                        onStartPause = { io { if (t.state == Timer.State.RUNNING) TimerRepository.pause(context, t.id) else TimerRepository.start(context, t.id) } },
                        onAddMinute = { io { TimerRepository.addMinute(context, t.id) } },
                        onReset = { io { TimerRepository.reset(context, t.id) } },
                        onDelete = { io { TimerRepository.delete(context, t.id) } },
                    )
                }
            }
        }
    }
}

@Composable
private fun TimerEntry(digits: String, onDigit: (String) -> Unit, onBackspace: () -> Unit) {
    val (h, m, s) = TimerInput.fields(digits)
    val hasInput = digits.isNotEmpty()
    val color = if (hasInput) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val label = stringResource(R.string.timer_entry_description, h, m, s)
    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(vertical = 16.dp).semantics { contentDescription = label }) {
        listOf(h to "h", m to "m", s to "s").forEach { (v, unit) ->
            Text("%02d".format(v), style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.SemiBold), color = color)
            Text(unit, style = MaterialTheme.typography.titleLarge, color = color, modifier = Modifier.padding(bottom = 10.dp, end = 10.dp))
        }
    }
    Column(Modifier.widthIn(max = 360.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("00", "0", "⌫")).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { k ->
                    Surface(
                        onClick = { if (k == "⌫") onBackspace() else onDigit(k) },
                        shape = ExperienceShape.pill,
                        color = if (k == "⌫") MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.weight(1f).heightIn(min = 64.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.height(64.dp)) {
                            if (k == "⌫") Icon(Icons.AutoMirrored.Outlined.Backspace, stringResource(R.string.backspace))
                            else Text(k, style = MaterialTheme.typography.headlineMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimerCard(
    t: Timer, now: Long, modifier: Modifier,
    onStartPause: () -> Unit, onAddMinute: () -> Unit, onReset: () -> Unit, onDelete: () -> Unit,
) {
    val remaining = t.remaining(now)
    val expired = t.state == Timer.State.EXPIRED
    val scheme = MaterialTheme.colorScheme
    val progress = if (t.lengthMs > 0) (remaining.coerceAtLeast(0).toFloat() / maxOf(t.lengthMs, remaining)).coerceIn(0f, 1f) else 0f
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = if (expired) scheme.errorContainer else scheme.surfaceContainer,
        contentColor = if (expired) scheme.onErrorContainer else scheme.onSurface,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(120.dp), contentAlignment = Alignment.Center) {
                val track = scheme.surfaceContainerHighest
                val arc = if (expired) scheme.error else scheme.primary
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
                    drawArc(track, 0f, 360f, false, style = stroke)
                    drawArc(arc, -90f, 360f * progress, false, style = stroke)
                }
                Text(formatDuration(remaining), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold))
            }
            Spacer(Modifier.size(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    when (t.state) {
                        Timer.State.EXPIRED -> stringResource(R.string.times_up)
                        Timer.State.PAUSED -> stringResource(R.string.paused)
                        else -> t.label.ifBlank { stringResource(R.string.timer_name, formatDuration(t.lengthMs)) }
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (expired) {
                        FilledIconButton(onClick = onReset) { Icon(Icons.Outlined.Stop, stringResource(R.string.stop)) }
                    } else {
                        FilledIconButton(onClick = onStartPause) {
                            Icon(if (t.state == Timer.State.RUNNING) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                                stringResource(if (t.state == Timer.State.RUNNING) R.string.pause else R.string.start))
                        }
                    }
                    FilledTonalButton(onClick = onAddMinute) { Text(stringResource(R.string.add_minute)) }
                    if (t.state == Timer.State.PAUSED) FilledTonalIconButton(onClick = onReset) { Icon(Icons.Outlined.Replay, stringResource(R.string.reset)) }
                    IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, stringResource(R.string.delete_timer)) }
                }
            }
        }
    }
}
