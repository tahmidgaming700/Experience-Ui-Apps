package com.experience.settings.ui

import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.experience.design.components.ExperienceScaffold
import com.experience.design.components.GroupedRow
import com.experience.design.components.IconBadge
import com.experience.design.components.SectionHeader
import com.experience.design.theme.ExperienceShape
import kotlinx.coroutines.launch

/** Shows a short message in the current page's snackbar. */
val LocalMessenger = staticCompositionLocalOf<(String) -> Unit> { {} }

/** Standard settings page: collapsing title, scrolling column, snackbar messages. */
@Composable
fun PageScaffold(title: String, onBack: (() -> Unit)?, content: @Composable ColumnScope.() -> Unit) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val messenger: (String) -> Unit = { msg -> scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(msg) } }
    CompositionLocalProvider(LocalMessenger provides messenger) {
        ExperienceScaffold(title = title, onBack = onBack, snackbarHostState = snackbar) { padding ->
            Column(
                Modifier
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
                content = content,
            )
        }
    }
}

/** A grouped section: rows are given their position so corners form one card. */
@Composable
fun Group(header: String?, rows: List<@Composable (index: Int, count: Int) -> Unit>) {
    if (rows.isEmpty()) return
    if (header != null) SectionHeader(header) else Spacer(Modifier.size(12.dp))
    rows.forEachIndexed { i, row -> row(i, rows.size) }
}

/** Builder for [Group] rows. */
class Rows {
    val list = mutableListOf<@Composable (Int, Int) -> Unit>()
    fun row(content: @Composable (index: Int, count: Int) -> Unit) { list += content }
}

@Composable
fun Group(header: String?, build: Rows.() -> Unit) = Group(header, Rows().apply(build).list)

/** Holder for a value read from the system; re-read on resume and on [refresh]. */
class SystemValue<T>(private val state: MutableState<T>, private val reader: () -> T) {
    val value: T get() = state.value
    fun refresh() { state.value = reader() }
}

@Composable
fun <T> rememberSystemValue(read: () -> T): SystemValue<T> {
    val current = rememberUpdatedState(read)
    val holder = remember { SystemValue(mutableStateOf(read())) { current.value() } }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) holder.refresh() }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    return holder
}

/** Registers a runtime broadcast receiver while in composition. */
@Composable
fun BroadcastEffect(vararg actions: String, onReceive: (Intent) -> Unit) {
    val context = LocalContext.current
    val cb = rememberUpdatedState(onReceive)
    DisposableEffect(actions.toList()) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) = cb.value(intent)
        }
        val filter = IntentFilter().apply { actions.forEach(::addAction) }
        // A sticky broadcast (battery) is delivered immediately through the receiver too.
        context.registerReceiver(receiver, filter)
        onDispose { context.unregisterReceiver(receiver) }
    }
}

/** Calls [onChange] when any of the settings [uris] change. */
@Composable
fun SettingsObserverEffect(uris: List<Uri>, onChange: () -> Unit) {
    val context = LocalContext.current
    val cb = rememberUpdatedState(onChange)
    DisposableEffect(uris) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = cb.value()
        }
        uris.forEach { context.contentResolver.registerContentObserver(it, false, observer) }
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
}

fun Context.canStart(intent: Intent): Boolean = intent.resolveActivity(packageManager) != null

/** First intent of [candidates] that resolves on this device. */
fun Context.firstResolvable(vararg candidates: Intent): Intent? = candidates.firstOrNull { canStart(it) }

fun Context.startSafely(intent: Intent): Boolean = try {
    startActivity(intent); true
} catch (_: ActivityNotFoundException) { false } catch (_: SecurityException) { false }

fun settingsIntent(action: String, pkgUri: Boolean = false, context: Context? = null): Intent =
    Intent(action).apply { if (pkgUri && context != null) data = Uri.fromParts("package", context.packageName, null) }

/**
 * Adds a link row that opens the first resolvable [intents]; when none resolves the
 * row is left out rather than shown as a dead control.
 */
fun Rows.link(context: Context, title: String, summary: String? = null, icon: ImageVector? = null, vararg intents: Intent) {
    val intent = context.firstResolvable(*intents) ?: return
    row { i, n -> LinkRow(title, summary, icon, intent, i, n) }
}

@Composable
fun LinkRow(title: String, summary: String?, icon: ImageVector?, intent: Intent, index: Int, count: Int) {
    val context = LocalContext.current
    val msg = LocalMessenger.current
    GroupedRow(
        title = title, summary = summary, icon = icon, index = index, count = count,
        onClick = { if (!context.startSafely(intent)) msg("Not available on this device") },
        trailing = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = "Opens system settings", tint = MaterialTheme.colorScheme.onSurfaceVariant) },
    )
}

@Composable
fun SliderRow(
    title: String, icon: ImageVector?, value: Float, onValueChange: (Float) -> Unit, index: Int, count: Int,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f, steps: Int = 0, valueLabel: String? = null,
    enabled: Boolean = true, onValueChangeFinished: (() -> Unit)? = null,
) {
    Surface(
        shape = ExperienceShape.grouped(index, count),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { IconBadge(icon); Spacer(Modifier.width(16.dp)) }
            Column(Modifier.weight(1f)) {
                Row {
                    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if (valueLabel != null) Text(valueLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = value.coerceIn(valueRange), onValueChange = onValueChange, valueRange = valueRange, steps = steps,
                    enabled = enabled, onValueChangeFinished = onValueChangeFinished,
                    modifier = Modifier.semantics { contentDescription = title },
                )
            }
        }
    }
}

/** Row showing a value; long-press copies it to the clipboard. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InfoRow(title: String, value: String, index: Int, count: Int, onClick: (() -> Unit)? = null) {
    val context = LocalContext.current
    val msg = LocalMessenger.current
    Surface(
        shape = ExperienceShape.grouped(index, count),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp).heightIn(min = 64.dp),
    ) {
        Column(
            Modifier
                .combinedClickable(
                    onClick = { onClick?.invoke() },
                    onLongClick = {
                        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(title, value))
                        msg("$title copied")
                    },
                    onLongClickLabel = "Copy",
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Single-choice radio dialog. */
@Composable
fun <T> ChoiceDialog(title: String, options: List<Pair<T, String>>, selected: T?, onSelect: (T) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.selectableGroup()) {
                options.forEach { (value, label) ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .selectable(selected = value == selected, role = Role.RadioButton) { onSelect(value); onDismiss() },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = value == selected, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Writes to Settings.System/Secure/Global. System needs WRITE_SETTINGS (granted to the
 * platform-signed app; elsewhere the user is sent to "Modify system settings");
 * Secure/Global need WRITE_SECURE_SETTINGS, which only the ROM can grant.
 */
object SystemSettings {
    fun canWriteSystem(context: Context) = Settings.System.canWrite(context)

    fun requestWriteSystem(context: Context) {
        context.startSafely(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.fromParts("package", context.packageName, null)))
    }

    fun putSystemInt(context: Context, key: String, value: Int): Boolean {
        if (!canWriteSystem(context)) { requestWriteSystem(context); return false }
        return runCatching { Settings.System.putInt(context.contentResolver, key, value) }.getOrDefault(false)
    }

    fun putSystemString(context: Context, key: String, value: String?): Boolean {
        if (!canWriteSystem(context)) { requestWriteSystem(context); return false }
        return runCatching { Settings.System.putString(context.contentResolver, key, value) }.getOrDefault(false)
    }

    fun putSystemFloat(context: Context, key: String, value: Float): Boolean {
        if (!canWriteSystem(context)) { requestWriteSystem(context); return false }
        return runCatching { Settings.System.putFloat(context.contentResolver, key, value) }.getOrDefault(false)
    }

    fun putSecureInt(context: Context, key: String, value: Int): Boolean =
        runCatching { Settings.Secure.putInt(context.contentResolver, key, value) }.getOrDefault(false)

    fun putGlobalFloat(context: Context, key: String, value: Float): Boolean =
        runCatching { Settings.Global.putFloat(context.contentResolver, key, value) }.getOrDefault(false)

    fun hasSecureWrite(context: Context): Boolean =
        context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) == android.content.pm.PackageManager.PERMISSION_GRANTED

    const val NEEDS_SYSTEM = "Requires the Experience UI system installation"
}

@Composable
fun OnStart(block: () -> Unit) = LaunchedEffect(Unit) { block() }
