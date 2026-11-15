package com.experience.files

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.experience.design.ExperienceActivity
import com.experience.files.ui.AccessGate
import com.experience.files.ui.FilesApp
import com.experience.files.ui.PickerConfig
import java.io.File

/**
 * Experience Files. Also serves ACTION_GET_CONTENT: the chosen file is returned as
 * a FileProvider content URI with a read grant.
 */
class MainActivity : ExperienceActivity() {

    private val picker: PickerConfig? by lazy {
        if (intent?.action != Intent.ACTION_GET_CONTENT) return@lazy null
        val filters = intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)?.toList()
            ?: listOfNotNull(intent.type).filter { it != "*/*" }
        PickerConfig(filters, intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false))
    }

    @Composable
    override fun Content() {
        var granted by remember { mutableStateOf(Environment.isExternalStorageManager()) }
        val owner = LocalLifecycleOwner.current
        DisposableEffect(owner) {
            val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) granted = Environment.isExternalStorageManager() }
            owner.lifecycle.addObserver(obs)
            onDispose { owner.lifecycle.removeObserver(obs) }
        }
        if (!granted) {
            AccessGate(onGrant = ::requestAllFilesAccess)
            return
        }
        val vm: FilesViewModel = viewModel()
        LaunchedEffect(Unit) { if (vm.location == Location.Home) vm.refreshHome() else vm.load() }
        FilesApp(vm, picker, onPicked = ::deliver)
    }

    private fun requestAllFilesAccess() {
        val specific = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.fromParts("package", packageName, null))
        runCatching { startActivity(specific) }.onFailure {
            runCatching { startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
        }
    }

    private fun deliver(files: List<File>) {
        val uris = files.mapNotNull { runCatching { FileIntents.uri(this, it) }.getOrNull() }
        if (uris.isEmpty()) { setResult(Activity.RESULT_CANCELED); finish(); return }
        val result = Intent().setData(uris[0]).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (uris.size > 1 || picker?.allowMultiple == true) {
            result.clipData = ClipData.newRawUri(null, uris[0]).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
        }
        setResult(Activity.RESULT_OK, result)
        finish()
    }
}
