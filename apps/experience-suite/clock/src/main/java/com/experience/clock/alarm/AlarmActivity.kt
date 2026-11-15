package com.experience.clock.alarm

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.AlarmOff
import androidx.compose.material.icons.outlined.Snooze
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.experience.clock.R
import com.experience.design.theme.ExperienceShape
import com.experience.design.theme.ExperienceTheme

/**
 * Full-screen ringing UI, shown over the lock screen and turning the screen on
 * (launched by the alarm notification's full-screen intent).
 */
class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent { ExperienceTheme { Ringing() } }
    }

    private fun send(action: String) {
        startService(AlarmService.intent(this, action))
        finish()
    }

    @Composable
    private fun Ringing() {
        val alarm by AlarmService.ringing.collectAsState()
        LaunchedEffect(alarm) { if (alarm == null) { kotlinx.coroutines.delay(1500); if (AlarmService.ringing.value == null) finish() } }
        val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
            1f, 1.12f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "scale",
        )
        Surface(color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Spacer(Modifier.height(32.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(shape = ExperienceShape.hero, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(112.dp).scale(pulse)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Alarm, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                    Spacer(Modifier.height(32.dp))
                    val a = alarm
                    Text(
                        if (a != null) AlarmService.formatTime(this@AlarmActivity, a.hour, a.minute) else "",
                        style = MaterialTheme.typography.displayLarge.copy(fontSize = 88.sp, fontWeight = FontWeight.SemiBold),
                    )
                    Text(
                        a?.label?.takeIf { it.isNotBlank() } ?: stringResource(R.string.alarm),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    FilledTonalButton(
                        onClick = { send(AlarmService.ACTION_SNOOZE) },
                        shape = ExperienceShape.pill,
                        modifier = Modifier.weight(1f).height(88.dp),
                    ) {
                        Icon(Icons.Outlined.Snooze, null)
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.snooze), style = MaterialTheme.typography.titleLarge)
                    }
                    Button(
                        onClick = { send(AlarmService.ACTION_DISMISS) },
                        shape = ExperienceShape.pill,
                        modifier = Modifier.weight(1f).height(88.dp),
                    ) {
                        Icon(Icons.Outlined.AlarmOff, null)
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.dismiss), style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }
    }
}
