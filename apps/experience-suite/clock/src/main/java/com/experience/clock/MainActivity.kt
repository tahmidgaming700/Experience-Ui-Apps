package com.experience.clock

import android.content.Intent
import android.os.Bundle
import android.provider.AlarmClock
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.experience.clock.ui.AlarmsScreen
import com.experience.clock.ui.ClockScreen
import com.experience.clock.ui.StopwatchScreen
import com.experience.clock.ui.TimerScreen
import com.experience.design.ExperienceActivity
import com.experience.design.components.WidthClass
import com.experience.design.components.currentWidthClass

enum class Tab(val label: Int, val icon: ImageVector) {
    ALARMS(R.string.tab_alarms, Icons.Outlined.Alarm),
    CLOCK(R.string.tab_clock, Icons.Outlined.AccessTime),
    TIMER(R.string.tab_timer, Icons.Outlined.HourglassEmpty),
    STOPWATCH(R.string.tab_stopwatch, Icons.Outlined.Timer),
}

/** Tabs: alarms, clock, timer, stopwatch. Bottom navigation bar on phones, navigation rail on tablets. */
class MainActivity : ExperienceActivity() {
    companion object {
        const val ACTION_SHOW_ALARMS_INTERNAL = "com.experience.clock.action.SHOW_ALARMS"
        const val ACTION_SHOW_TIMERS_INTERNAL = "com.experience.clock.action.SHOW_TIMERS"
        const val ACTION_SHOW_STOPWATCH_INTERNAL = "com.experience.clock.action.SHOW_STOPWATCH"
        const val EXTRA_HIGHLIGHT_ALARM = "highlight_alarm"
        const val EXTRA_NEW_ALARM = "new_alarm"
    }

    private var tab by mutableStateOf(Tab.ALARMS)
    private var highlight by mutableStateOf<Long?>(null)
    private var createNew by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifications.ensure(this)
        tab = savedInstanceState?.getString("tab")?.let { runCatching { Tab.valueOf(it) }.getOrNull() } ?: Tab.ALARMS
        if (savedInstanceState == null) handle(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("tab", tab.name)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        when (intent?.action) {
            AlarmClock.ACTION_SHOW_ALARMS, ACTION_SHOW_ALARMS_INTERNAL -> {
                tab = Tab.ALARMS
                highlight = intent.getLongExtra(EXTRA_HIGHLIGHT_ALARM, -1L).takeIf { it > 0 }
                createNew = intent.getBooleanExtra(EXTRA_NEW_ALARM, false)
            }
            AlarmClock.ACTION_SHOW_TIMERS, ACTION_SHOW_TIMERS_INTERNAL -> tab = Tab.TIMER
            ACTION_SHOW_STOPWATCH_INTERNAL -> tab = Tab.STOPWATCH
        }
    }

    @Composable
    override fun Content() {
        val wide = currentWidthClass() != WidthClass.COMPACT
        val bottomBar: @Composable () -> Unit = if (wide) ({}) else ({
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t, onClick = { tab = t },
                        icon = { Icon(t.icon, null) }, label = { Text(stringResource(t.label)) },
                    )
                }
            }
        })
        val screen = @Composable {
            when (tab) {
                Tab.ALARMS -> AlarmsScreen(bottomBar, highlight, createNew) { highlight = null; createNew = false }
                Tab.CLOCK -> ClockScreen(bottomBar, wide)
                Tab.TIMER -> TimerScreen(bottomBar)
                Tab.STOPWATCH -> StopwatchScreen(bottomBar, wide)
            }
        }
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
                ) {
                    Spacer(Modifier.height(24.dp))
                    Tab.entries.forEach { t ->
                        NavigationRailItem(
                            selected = tab == t, onClick = { tab = t },
                            icon = { Icon(t.icon, null) }, label = { Text(stringResource(t.label)) },
                        )
                    }
                }
                Box(Modifier.weight(1f)) { screen() }
            }
        } else {
            screen()
        }
    }
}
