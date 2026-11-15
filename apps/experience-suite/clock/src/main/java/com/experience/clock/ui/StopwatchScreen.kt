package com.experience.clock.ui

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.TrendingDown
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.experience.clock.R
import com.experience.clock.stopwatch.StopwatchRepository
import com.experience.design.components.ExperienceScaffold
import com.experience.design.components.GroupedRow
import com.experience.design.theme.ExperienceShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun StopwatchScreen(bottomBar: @Composable () -> Unit, wide: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { launch(Dispatchers.IO) { StopwatchRepository.ensureLoaded(context) } }
    val sw by StopwatchRepository.state.collectAsState()
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(sw.running) { while (sw.running) withFrameMillis { now = SystemClock.elapsedRealtime() } }
    val elapsed = sw.elapsed(if (sw.running) now else SystemClock.elapsedRealtime())

    fun act(f: (com.experience.clock.stopwatch.Stopwatch, Long) -> com.experience.clock.stopwatch.Stopwatch) {
        scope.launch(Dispatchers.IO) { StopwatchRepository.update(context, f) }
    }

    ExperienceScaffold(title = stringResource(R.string.tab_stopwatch), bottomBar = bottomBar, maxContentWidth = 1200.dp) { padding ->
        val dial = @Composable { m: Modifier ->
            Column(m, horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.widthIn(max = 320.dp).fillMaxWidth(0.8f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                    val track = MaterialTheme.colorScheme.surfaceContainerHigh
                    val accent = MaterialTheme.colorScheme.primary
                    Canvas(Modifier.fillMaxSize()) {
                        val stroke = 12.dp.toPx()
                        drawCircle(track, size.minDimension / 2 - stroke / 2, style = Stroke(stroke))
                        val f = (elapsed % 60_000) / 60_000f
                        drawArc(accent, -90f, 360f * f, false, style = Stroke(stroke, cap = StrokeCap.Round))
                        val a = f * 2 * PI
                        val r = size.minDimension / 2 - stroke / 2
                        drawCircle(accent, stroke * 0.9f, Offset(center.x + r * sin(a).toFloat(), center.y - r * cos(a).toFloat()))
                    }
                    Text(formatStopwatch(elapsed), style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.SemiBold))
                }
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (!sw.isReset) {
                        FilledTonalButton(
                            onClick = { if (sw.running) act { s, t -> s.lap(t) } else act { s, _ -> s.reset() } },
                            shape = ExperienceShape.pill, modifier = Modifier.size(width = 128.dp, height = 72.dp),
                        ) {
                            Icon(if (sw.running) Icons.Outlined.Flag else Icons.Outlined.Replay, null)
                            Spacer(Modifier.size(8.dp))
                            Text(stringResource(if (sw.running) R.string.lap else R.string.reset))
                        }
                    }
                    Button(
                        onClick = { if (sw.running) act { s, t -> s.pause(t) } else act { s, t -> s.start(t) } },
                        shape = ExperienceShape.pill, modifier = Modifier.size(width = 160.dp, height = 72.dp),
                    ) {
                        Icon(if (sw.running) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, null)
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(if (sw.running) R.string.pause else if (sw.isReset) R.string.start else R.string.resume))
                    }
                }
            }
        }
        val laps = @Composable { m: Modifier ->
            val durations = sw.lapDurations()
            val marks = sw.bestAndWorst()
            LazyColumn(m, contentPadding = PaddingValues(bottom = 32.dp)) {
                if (!wide) item { dial(Modifier.fillMaxWidth().padding(vertical = 16.dp)) }
                val count = durations.size
                itemsIndexed(durations.reversed(), key = { i, _ -> count - i }) { i, d ->
                    val index = count - 1 - i
                    val tag = when (marks) {
                        null -> null
                        else -> if (index == marks.first) R.string.fastest else if (index == marks.second) R.string.slowest else null
                    }
                    GroupedRow(
                        title = stringResource(R.string.lap_n, index + 1) + "  ·  " + formatStopwatch(d),
                        summary = stringResource(R.string.total_time, formatStopwatch(sw.laps[index])) + (tag?.let { "  ·  " + stringResource(it) } ?: ""),
                        index = i, count = count,
                        trailing = {
                            if (tag != null) Icon(
                                if (tag == R.string.fastest) Icons.Outlined.TrendingDown else Icons.Outlined.TrendingUp, null,
                                tint = if (tag == R.string.fastest) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            )
                        },
                    )
                }
            }
        }
        if (wide) {
            Row(Modifier.padding(padding).padding(horizontal = 16.dp).fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                dial(Modifier.weight(1f).padding(top = 24.dp))
                laps(Modifier.weight(1f))
            }
        } else {
            laps(Modifier.padding(padding).padding(horizontal = 16.dp).fillMaxSize())
        }
    }
}
