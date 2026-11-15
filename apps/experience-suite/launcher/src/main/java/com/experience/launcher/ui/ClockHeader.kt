package com.experience.launcher.ui

import android.app.AlarmManager
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Current time in millis, refreshed every minute and on time / zone / alarm changes. */
@Composable
private fun rememberClockState(): Pair<Long, Long?> {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var nextAlarm by remember { mutableStateOf(nextAlarm(context)) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(context, owner) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                now = System.currentTimeMillis()
                nextAlarm = nextAlarm(context)
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED)
        }
        context.registerReceiver(receiver, filter)
        val observer = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) {
                now = System.currentTimeMillis(); nextAlarm = nextAlarm(context)
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            context.unregisterReceiver(receiver)
            owner.lifecycle.removeObserver(observer)
        }
    }
    return now to nextAlarm
}

private fun nextAlarm(context: Context): Long? =
    context.getSystemService(AlarmManager::class.java)?.nextAlarmClock?.triggerTime

private fun pattern(skeleton: String) = DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton)

private fun Context.tryStart(intent: Intent) {
    try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
    } catch (_: SecurityException) {
    }
}

/**
 * Glanceable header: an oversized clock, the full date and the next alarm. The clock
 * opens the alarms of the clock app, the date opens today in the calendar.
 */
@Composable
fun ClockHeader(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val (now, alarm) = rememberClockState()
    val is24 = DateFormat.is24HourFormat(context)
    val time = SimpleDateFormat(pattern(if (is24) "Hm" else "hm"), Locale.getDefault()).format(Date(now))
    val date = SimpleDateFormat(pattern("EEEEMMMMd"), Locale.getDefault()).format(Date(now))
    val compactHeight = LocalConfiguration.current.screenHeightDp < 520
    val shape = RoundedCornerShape(24.dp)

    Column(modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(
            time,
            style = wallpaperTextStyle(MaterialTheme.typography.displayLarge).copy(
                fontSize = if (compactHeight) 56.sp else 88.sp,
                lineHeight = if (compactHeight) 60.sp else 92.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = (-2).sp,
            ),
            modifier = Modifier
                .clip(shape)
                .clickable(role = Role.Button, onClickLabel = "Open alarms") {
                    context.tryStart(Intent(AlarmClock.ACTION_SHOW_ALARMS))
                }
                .padding(horizontal = 4.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                date,
                style = wallpaperTextStyle(MaterialTheme.typography.titleLarge),
                modifier = Modifier
                    .clip(shape)
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button, onClickLabel = "Open calendar") {
                        val uri = ContentUris.appendId(CalendarContract.CONTENT_URI.buildUpon().appendPath("time"), System.currentTimeMillis()).build()
                        context.tryStart(Intent(Intent.ACTION_VIEW, uri))
                    }
                    .padding(horizontal = 4.dp, vertical = 10.dp),
            )
            if (alarm != null) {
                Spacer(Modifier.width(12.dp))
                Icon(Icons.Outlined.Alarm, contentDescription = "Next alarm", tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                val alarmText = SimpleDateFormat(pattern(if (is24) "EEEHm" else "EEEhma"), Locale.getDefault()).format(Date(alarm))
                Text(alarmText, style = wallpaperTextStyle(MaterialTheme.typography.titleSmall))
            }
        }
    }
}
