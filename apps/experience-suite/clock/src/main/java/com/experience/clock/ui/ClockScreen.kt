package com.experience.clock.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddLocationAlt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.experience.clock.ClockSettings
import com.experience.clock.R
import com.experience.clock.world.WorldClocks
import com.experience.design.components.ExperienceScaffold
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Current time, ticking on whole seconds. */
@Composable
fun rememberNow(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000 - System.currentTimeMillis() % 1000)
            now = System.currentTimeMillis()
        }
    }
    return now
}

@Composable
fun ClockScreen(bottomBar: @Composable () -> Unit, wide: Boolean) {
    val context = LocalContext.current
    val settings = remember { ClockSettings(context) }
    var cities by remember { mutableStateOf(settings.worldClocks) }
    var picking by rememberSaveable { mutableStateOf(false) }
    val now = rememberNow()
    val zone = ZoneId.systemDefault()

    ExperienceScaffold(
        title = stringResource(R.string.tab_clock),
        bottomBar = bottomBar,
        maxContentWidth = 1200.dp,
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { picking = true }, icon = { Icon(Icons.Outlined.AddLocationAlt, null) },
                text = { Text(stringResource(R.string.add_city)) })
        },
    ) { padding ->
        val hero = @Composable { m: Modifier -> LocalClock(now, zone, m) }
        val list = @Composable { m: Modifier ->
            LazyColumn(m, contentPadding = PaddingValues(bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!wide) item { hero(Modifier.fillMaxWidth()) }
                items(cities, key = { it }) { id ->
                    WorldClockRow(id, now, zone, Modifier.animateItem()) {
                        cities = cities - id
                        settings.worldClocks = cities
                    }
                }
            }
        }
        if (wide) {
            Row(Modifier.padding(padding).padding(horizontal = 16.dp).fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                hero(Modifier.weight(1f))
                list(Modifier.weight(1f))
            }
        } else {
            list(Modifier.padding(padding).padding(horizontal = 16.dp).fillMaxSize())
        }
    }

    if (picking) ZonePickerDialog(cities.toSet() + zone.id, onPick = { id ->
        cities = cities + id
        settings.worldClocks = cities
    }, onDismiss = { picking = false })
}

@Composable
private fun LocalClock(now: Long, zone: ZoneId, modifier: Modifier) {
    val context = LocalContext.current
    val t = Instant.ofEpochMilli(now).atZone(zone)
    val (time, ampm) = formatClock(context, t.hour, t.minute)
    val date = t.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.getDefault()))
    Column(modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        AnalogClock(t, Modifier.widthIn(max = 300.dp).fillMaxWidth(0.75f).aspectRatio(1f)
            .clearAndSetSemantics { contentDescription = "$time $ampm" })
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 16.dp)) {
            Text(time, style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.SemiBold))
            Text(":%02d".format(t.second), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp, start = 2.dp))
            if (ampm.isNotEmpty()) Text(" $ampm", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))
        }
        Text(date, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(WorldClocks.cityName(zone.id), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
    }
}

/** Expressive analog face: tonal disc, hour dots, rounded hands, accent second hand. */
@Composable
fun AnalogClock(time: ZonedDateTime, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    Canvas(modifier) {
        val r = size.minDimension / 2
        val c = center
        drawCircle(scheme.surfaceContainerHigh, r)
        for (i in 0 until 12) {
            val a = i * PI / 6
            val p = Offset(c.x + (r * 0.86f) * sin(a).toFloat(), c.y - (r * 0.86f) * cos(a).toFloat())
            drawCircle(if (i % 3 == 0) scheme.primary else scheme.outline, if (i % 3 == 0) r * 0.045f else r * 0.025f, p)
        }
        fun hand(fraction: Double, length: Float, width: Float, color: androidx.compose.ui.graphics.Color) {
            val a = fraction * 2 * PI
            drawLine(color, c, Offset(c.x + length * sin(a).toFloat(), c.y - length * cos(a).toFloat()), width, StrokeCap.Round)
        }
        val s = time.second
        val m = time.minute + s / 60.0
        val h = (time.hour % 12) + m / 60.0
        hand(h / 12, r * 0.5f, r * 0.09f, scheme.primary)
        hand(m / 60, r * 0.72f, r * 0.06f, scheme.onSurface)
        hand(s / 60.0, r * 0.8f, r * 0.02f, scheme.tertiary)
        drawCircle(scheme.tertiary, r * 0.05f, c)
    }
}

@Composable
private fun WorldClockRow(id: String, now: Long, local: ZoneId, modifier: Modifier, onRemove: () -> Unit) {
    val context = LocalContext.current
    val zone = runCatching { ZoneId.of(id) }.getOrNull() ?: return
    val instant = Instant.ofEpochMilli(now)
    val t = instant.atZone(zone)
    val (time, ampm) = formatClock(context, t.hour, t.minute)
    val diff = WorldClocks.differenceMinutes(zone, local, instant)
    val day = when (WorldClocks.dayDifference(zone, local, instant)) {
        -1 -> stringResource(R.string.yesterday)
        1 -> stringResource(R.string.tomorrow)
        else -> stringResource(R.string.today)
    }
    val diffText = if (diff == 0) stringResource(R.string.same_time) else stringResource(R.string.hours_diff, WorldClocks.formatDifference(diff))
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 20.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(WorldClocks.cityName(id), style = MaterialTheme.typography.titleLarge)
                Text("$day, $diffText", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(time, style = MaterialTheme.typography.headlineLarge)
            if (ampm.isNotEmpty()) Text(" $ampm", style = MaterialTheme.typography.titleSmall)
            IconButton(onClick = onRemove) { Icon(Icons.Outlined.Close, stringResource(R.string.remove_city, WorldClocks.cityName(id))) }
        }
    }
}
