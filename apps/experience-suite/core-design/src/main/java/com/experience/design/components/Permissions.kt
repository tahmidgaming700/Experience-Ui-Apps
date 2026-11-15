package com.experience.design.components

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

fun Context.hasPermissions(perms: Collection<String>) =
    perms.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

/**
 * Shows [content] when all [permissions] are granted; otherwise an explanation with
 * a request button (and a shortcut to App info once the user has denied twice).
 * Re-checks on resume so grants made in Settings take effect immediately.
 */
@Composable
fun PermissionGate(
    permissions: List<String>,
    rationaleTitle: String,
    rationale: String,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(context.hasPermissions(permissions)) }
    var asked by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = context.hasPermissions(permissions)
        asked = true
    }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) granted = context.hasPermissions(permissions) }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    if (granted) {
        content()
    } else {
        EmptyState(Icons.Outlined.Lock, rationaleTitle, rationale) {
            Row {
                Button(onClick = { launcher.launch(permissions.toTypedArray()) }) { Text("Allow") }
                if (asked) {
                    TextButton(onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }) { Text("App info") }
                }
            }
        }
    }
}
