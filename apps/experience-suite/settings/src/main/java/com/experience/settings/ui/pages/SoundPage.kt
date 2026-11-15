package com.experience.settings.ui.pages

import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Vibrator
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.DoNotDisturbOn
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.RingVolume
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import com.experience.design.components.GroupedRow
import com.experience.design.components.SwitchRow
import com.experience.settings.ui.BroadcastEffect
import com.experience.settings.ui.ChoiceDialog
import com.experience.settings.ui.Group
import com.experience.settings.ui.LocalMessenger
import com.experience.settings.ui.PageScaffold
import com.experience.settings.ui.SliderRow
import com.experience.settings.ui.SystemSettings
import com.experience.settings.ui.link
import com.experience.settings.ui.rememberSystemValue
import com.experience.settings.ui.startSafely

private data class StreamDef(val stream: Int, val title: String, val icon: ImageVector)

private val STREAMS = listOf(
    StreamDef(AudioManager.STREAM_MUSIC, "Media volume", Icons.Outlined.MusicNote),
    StreamDef(AudioManager.STREAM_RING, "Ring volume", Icons.Outlined.RingVolume),
    StreamDef(AudioManager.STREAM_NOTIFICATION, "Notification volume", Icons.Outlined.Notifications),
    StreamDef(AudioManager.STREAM_ALARM, "Alarm volume", Icons.Outlined.Alarm),
)

private fun ringtoneTitle(context: Context, type: Int): String {
    val uri = RingtoneManager.getActualDefaultRingtoneUri(context, type) ?: return "None"
    return runCatching { RingtoneManager.getRingtone(context, uri)?.getTitle(context) }.getOrNull() ?: "Unknown"
}

@Composable
fun SoundPage(onBack: (() -> Unit)?) {
    val context = LocalContext.current
    val msg = LocalMessenger.current
    val am = context.getSystemService(AudioManager::class.java)
    val nm = context.getSystemService(NotificationManager::class.java)
    val volumes = rememberSystemValue { STREAMS.associate { it.stream to am.getStreamVolume(it.stream) } }
    val ringer = rememberSystemValue { am.ringerMode }
    val tones = rememberSystemValue {
        listOf(RingtoneManager.TYPE_RINGTONE, RingtoneManager.TYPE_NOTIFICATION, RingtoneManager.TYPE_ALARM).associateWith { ringtoneTitle(context, it) }
    }
    val touchSounds = rememberSystemValue { Settings.System.getInt(context.contentResolver, Settings.System.SOUND_EFFECTS_ENABLED, 1) == 1 }
    val haptics = rememberSystemValue { Settings.System.getInt(context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) == 1 }
    // "android.media.VOLUME_CHANGED_ACTION" is the (hidden-constant) volume broadcast.
    BroadcastEffect("android.media.VOLUME_CHANGED_ACTION", AudioManager.RINGER_MODE_CHANGED_ACTION) { volumes.refresh(); ringer.refresh() }
    val hasVibrator = remember { context.getSystemService(Vibrator::class.java)?.hasVibrator() == true }
    val voiceCapable = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY) }

    var pickingType by remember { mutableIntStateOf(0) }
    var ringerDialog by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            @Suppress("DEPRECATION")
            val uri: Uri? = r.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            if (!SystemSettings.canWriteSystem(context)) SystemSettings.requestWriteSystem(context)
            else runCatching { RingtoneManager.setActualDefaultRingtoneUri(context, pickingType, uri) }
                .onFailure { msg("Couldn't change the sound") }
            tones.refresh()
        }
    }
    fun pick(type: Int, title: String) {
        pickingType = type
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, type)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, title)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, false)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, RingtoneManager.getActualDefaultRingtoneUri(context, type))
        runCatching { picker.launch(intent) }.onFailure { msg("No sound picker is installed") }
    }

    PageScaffold("Sound & vibration", onBack) {
        Group("Volume") {
            STREAMS.forEach { s ->
                row { i, n ->
                    val min = am.getStreamMinVolume(s.stream)
                    val max = am.getStreamMaxVolume(s.stream)
                    val v = volumes.value[s.stream] ?: 0
                    SliderRow(
                        title = s.title, icon = s.icon, value = v.toFloat(), valueRange = min.toFloat()..max.toFloat(),
                        steps = (max - min - 1).coerceAtLeast(0), index = i, count = n, valueLabel = "$v / $max",
                        onValueChange = { nv ->
                            // Changing ring/notification volume while DND silences them throws without policy access.
                            runCatching { am.setStreamVolume(s.stream, nv.toInt(), 0) }.onFailure { msg("Do Not Disturb prevents changing this volume") }
                            volumes.refresh()
                        },
                    )
                }
            }
        }
        Group("Ringer") {
            row { i, n ->
                val label = when (ringer.value) { AudioManager.RINGER_MODE_SILENT -> "Silent"; AudioManager.RINGER_MODE_VIBRATE -> "Vibrate"; else -> "Sound on" }
                GroupedRow("Ringer mode", i, n, summary = label, icon = Icons.Outlined.Vibration, onClick = { ringerDialog = true })
            }
            if (!nm.isNotificationPolicyAccessGranted) {
                link(context, "Allow Do Not Disturb access", "Needed to switch to Silent", Icons.Outlined.DoNotDisturbOn, Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            }
        }
        Group("Sounds") {
            if (voiceCapable) row { i, n -> GroupedRow("Phone ringtone", i, n, summary = tones.value[RingtoneManager.TYPE_RINGTONE], icon = Icons.Outlined.RingVolume, onClick = { pick(RingtoneManager.TYPE_RINGTONE, "Phone ringtone") }) }
            row { i, n -> GroupedRow("Default notification sound", i, n, summary = tones.value[RingtoneManager.TYPE_NOTIFICATION], icon = Icons.Outlined.Notifications, onClick = { pick(RingtoneManager.TYPE_NOTIFICATION, "Notification sound") }) }
            row { i, n -> GroupedRow("Default alarm sound", i, n, summary = tones.value[RingtoneManager.TYPE_ALARM], icon = Icons.Outlined.Alarm, onClick = { pick(RingtoneManager.TYPE_ALARM, "Alarm sound") }) }
            row { i, n ->
                SwitchRow("Touch sounds", touchSounds.value, { on ->
                    if (SystemSettings.putSystemInt(context, Settings.System.SOUND_EFFECTS_ENABLED, if (on) 1 else 0)) {
                        if (on) am.loadSoundEffects() else am.unloadSoundEffects()
                    }
                    touchSounds.refresh()
                }, i, n, icon = Icons.Outlined.TouchApp)
            }
            if (hasVibrator) row { i, n ->
                SwitchRow("Touch vibration", haptics.value, { on ->
                    SystemSettings.putSystemInt(context, Settings.System.HAPTIC_FEEDBACK_ENABLED, if (on) 1 else 0); haptics.refresh()
                }, i, n, icon = Icons.Outlined.Vibration)
            }
            link(context, "More sound settings", null, Icons.Outlined.Tune, Intent(Settings.ACTION_SOUND_SETTINGS))
        }
    }

    if (ringerDialog) {
        val options = buildList {
            add(AudioManager.RINGER_MODE_NORMAL to "Sound on")
            if (hasVibrator) add(AudioManager.RINGER_MODE_VIBRATE to "Vibrate")
            add(AudioManager.RINGER_MODE_SILENT to "Silent")
        }
        ChoiceDialog("Ringer mode", options, ringer.value, onSelect = { mode ->
            if (mode == AudioManager.RINGER_MODE_SILENT && !nm.isNotificationPolicyAccessGranted) {
                msg("Allow Do Not Disturb access first")
                context.startSafely(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            } else {
                runCatching { am.ringerMode = mode }.onFailure { msg("Couldn't change the ringer mode") }
            }
            ringer.refresh()
        }, onDismiss = { ringerDialog = false })
    }
}
