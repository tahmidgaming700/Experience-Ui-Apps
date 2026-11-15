package com.experience.setupwizard.ui.pages

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.telecom.TelecomManager
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.experience.design.system.SystemInfo
import com.experience.design.theme.ExperienceShape
import com.experience.setupwizard.ui.tryStart

/**
 * Emergency dialer intent: TelecomManager.createLaunchEmergencyDialerIntent is a
 * @SystemApi (reflection); the action string is the Telephony app's public entry.
 */
@SuppressLint("DiscouragedPrivateApi")
fun emergencyDialerIntent(context: Context): Intent? {
    if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) return null
    val telecom = context.getSystemService(TelecomManager::class.java)
    val fromTelecom = runCatching {
        telecom.javaClass.getMethod("createLaunchEmergencyDialerIntent", String::class.java).invoke(telecom, null) as Intent?
    }.getOrNull()
    val candidates = listOfNotNull(fromTelecom, Intent("com.android.phone.EmergencyDialer.DIAL"))
    return candidates.firstOrNull { it.resolveActivity(context.packageManager) != null }
        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
}

@Composable
fun WelcomePage(onStart: () -> Unit) {
    val context = LocalContext.current
    val emergency = remember { emergencyDialerIntent(context) }
    val version = if (SystemInfo.isExperienceUi) SystemInfo.experienceDisplayVersion else "Android ${SystemInfo.androidVersion}"
    val primary = MaterialTheme.colorScheme.primaryContainer
    val tertiary = MaterialTheme.colorScheme.tertiaryContainer
    val transition = rememberInfiniteTransition(label = "welcome")
    val drift by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(9000), RepeatMode.Reverse), label = "drift")

    Box(Modifier.fillMaxSize()) {
        // Slowly drifting tonal blobs behind the welcome text.
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Brush.radialGradient(listOf(primary, primary.copy(alpha = 0f)), center = Offset(size.width * (0.2f + 0.2f * drift), size.height * 0.25f), radius = size.minDimension * 0.7f), radius = size.minDimension * 0.7f, center = Offset(size.width * (0.2f + 0.2f * drift), size.height * 0.25f))
            drawCircle(Brush.radialGradient(listOf(tertiary, tertiary.copy(alpha = 0f)), center = Offset(size.width * (0.85f - 0.2f * drift), size.height * 0.7f), radius = size.minDimension * 0.6f), radius = size.minDimension * 0.6f, center = Offset(size.width * (0.85f - 0.2f * drift), size.height * 0.7f))
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Welcome to", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "Experience UI",
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 56.sp, lineHeight = 60.sp, fontWeight = FontWeight.SemiBold),
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(8.dp))
            Text(version, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(48.dp))
            Button(
                onClick = onStart,
                shape = ExperienceShape.pill,
                modifier = Modifier.widthIn(min = 200.dp).heightIn(min = 64.dp),
            ) {
                Text("Start", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.size(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
            }
            if (emergency != null) {
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = { context.tryStart(emergency) }, shape = ExperienceShape.pill, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.Filled.LocalHospital, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Emergency call")
                }
            }
            Spacer(Modifier.fillMaxWidth().height(24.dp))
        }
    }
}
