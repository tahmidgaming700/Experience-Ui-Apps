package com.experience.files.ui

import android.webkit.MimeTypeMap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.experience.files.Operation
import com.experience.files.data.Mime
import com.experience.files.model.FileItem
import com.experience.files.model.FileOps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Text-input dialog used for New folder and Rename; validates the name live. */
@Composable
fun NameDialog(title: String, initial: String, confirm: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    // Pre-select the base name (not the extension) like other file managers.
    val selEnd = initial.lastIndexOf('.').takeIf { it > 0 } ?: initial.length
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, selEnd))) }
    val error = if (value.text.isEmpty()) null else FileOps.validateName(value.text.trim())
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val ok = value.text.isNotBlank() && error == null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value, onValueChange = { value = it },
                singleLine = true, isError = error != null,
                supportingText = error?.let { { Text(it) } },
                label = { Text("Name") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (ok) onConfirm(value.text) }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        confirmButton = { TextButton(enabled = ok, onClick = { onConfirm(value.text) }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun DeleteDialog(names: List<String>, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (names.size == 1) "Delete \"${names[0]}\"?" else "Delete ${names.size} items?") },
        text = { Text("This permanently deletes the selected items, including everything inside folders. It can't be undone.") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Delete", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Non-dismissable progress for copy/move/delete/extract with a Cancel button. */
@Composable
fun OperationDialog(op: Operation, onCancel: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text(op.title + "…") },
        text = {
            Column {
                val fraction = if (op.total > 0) (op.done.toFloat() / op.total).coerceIn(0f, 1f) else null
                if (fraction != null) LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.size(12.dp))
                Text(op.current.ifEmpty { "Preparing" }, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                if (op.total > 0) {
                    Text(
                        if (op.countsItems) "${op.done} of ${op.total} items"
                        else "${formatSize(context, op.done)} of ${formatSize(context, op.total)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

@Composable
fun PropertiesDialog(item: FileItem, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val f = item.file
    var sizeInfo by remember(item.path) { mutableStateOf<Pair<Long, Pair<Int, Int>>?>(null) }
    LaunchedEffect(item.path) {
        sizeInfo = withContext(Dispatchers.IO) { FileOps.sizeOf(f) to FileOps.countEntries(f) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Properties") },
        text = {
            SelectionContainer {
                Column {
                    Prop("Name", item.name)
                    Prop("Location", f.parent ?: "/")
                    Prop("Type", if (item.isDirectory) "Folder" else Mime.of(item.name).let { m ->
                        MimeTypeMap.getSingleton().getExtensionFromMimeType(m)?.let { "${it.uppercase()} file ($m)" } ?: m
                    })
                    Prop("Size", sizeInfo?.let { (bytes, _) -> "${formatSize(context, bytes)} ($bytes bytes)" } ?: "Calculating…")
                    if (item.isDirectory) Prop("Contains", sizeInfo?.second?.let { (files, dirs) -> "$files files, $dirs folders" } ?: "Calculating…")
                    Prop("Modified", formatDate(f.lastModified()))
                    Prop("Access", listOfNotNull("read".takeIf { f.canRead() }, "write".takeIf { f.canWrite() }).joinToString(", ").ifEmpty { "none" })
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun Prop(label: String, value: String) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
