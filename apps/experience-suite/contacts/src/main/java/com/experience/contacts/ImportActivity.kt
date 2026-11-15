package com.experience.contacts

import android.Manifest
import android.net.Uri
import android.os.Bundle
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.experience.contacts.data.VCardContact
import com.experience.contacts.data.VCardImporter
import com.experience.design.ExperienceActivity
import com.experience.design.components.EmptyState
import com.experience.design.components.ExperienceScaffold
import com.experience.design.components.GroupedRow
import com.experience.design.components.PermissionGate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Imports a vCard file (ACTION_VIEW text/x-vcard, text/vcard, or the in-app import). */
class ImportActivity : ExperienceActivity() {
    private sealed interface State {
        data object Reading : State
        data class Preview(val contacts: List<VCardContact>) : State
        data class Importing(val done: Int, val total: Int) : State
        data class Done(val count: Int, val total: Int) : State
        data object Failed : State
    }

    private var state by mutableStateOf<State>(State.Reading)
    private val importer by lazy { VCardImporter(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri: Uri? = intent.data
        if (uri == null) { state = State.Failed; return }
        lifecycleScope.launch {
            val parsed = withContext(Dispatchers.IO) { runCatching { importer.read(uri) }.getOrNull() }
            state = if (parsed.isNullOrEmpty()) State.Failed else State.Preview(parsed)
        }
    }

    private fun runImport(contacts: List<VCardContact>) {
        state = State.Importing(0, contacts.size)
        lifecycleScope.launch {
            val stored = withContext(Dispatchers.IO) {
                importer.import(contacts) { n -> lifecycleScope.launch { if (state is State.Importing) state = State.Importing(n, contacts.size) } }
            }
            state = State.Done(stored, contacts.size)
        }
    }

    @Composable
    override fun Content() {
        ExperienceScaffold(title = "Import contacts", onBack = { finish() }) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                PermissionGate(listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS), "Contacts", "Allow access to contacts to import this file.") {
                    when (val s = state) {
                        State.Reading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                        State.Failed -> EmptyState(Icons.Filled.ErrorOutline, "No contacts found", "This file could not be read as a vCard.") {
                            Button(onClick = { finish() }) { Text("Close") }
                        }
                        is State.Preview -> Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                            Text("${s.contacts.size} contact${if (s.contacts.size == 1) "" else "s"} will be saved on this device.", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 8.dp))
                            LazyColumn(Modifier.weight(1f)) {
                                items(s.contacts.size) { i ->
                                    val c = s.contacts[i]
                                    GroupedRow(c.displayName, i, s.contacts.size, summary = (c.phones.map { it.value } + c.emails.map { it.value }).take(2).joinToString(" · ").ifBlank { null })
                                }
                            }
                            Button(onClick = { runImport(s.contacts) }, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp).heightIn(min = 56.dp)) {
                                Text("Import ${s.contacts.size}")
                            }
                        }
                        is State.Importing -> Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            LinearProgressIndicator(progress = { if (s.total == 0) 0f else s.done / s.total.toFloat() }, modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(12.dp))
                            Text("Imported ${s.done} of ${s.total}")
                        }
                        is State.Done -> EmptyState(
                            if (s.count == s.total) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
                            "Imported ${s.count} of ${s.total}",
                            if (s.count < s.total) "Some contacts could not be saved." else null,
                        ) { Button(onClick = { finish() }) { Text("Done") } }
                    }
                }
            }
        }
    }
}
