package com.experience.messages.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.telephony.PhoneNumberUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.experience.design.components.EmptyState
import com.experience.design.components.ExperienceScaffold
import com.experience.design.theme.ExperienceShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class Suggestion(val name: String, val number: String, val label: String, val photo: String?)

/** Recipient picker: contacts search by name/number, or any number typed directly. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewConversationScreen(onBack: (() -> Unit)?, onStart: (List<String>) -> Unit) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var numeric by remember { mutableStateOf(false) }
    val recipients = remember { mutableStateListOf<String>() }
    var suggestions by remember { mutableStateOf<List<Suggestion>>(emptyList()) }
    LaunchedEffect(query) {
        suggestions = if (query.isBlank() || context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) emptyList()
        else withContext(Dispatchers.IO) { searchPhones(context, query.trim()) }
    }
    fun add(number: String) {
        val n = number.trim()
        if (n.isNotEmpty() && recipients.none { PhoneNumberUtils.compare(it, n) }) recipients += n
        query = ""
    }
    val typedIsNumber = query.isNotBlank() && PhoneNumberUtils.isWellFormedSmsAddress(query.filter { !it.isWhitespace() })

    ExperienceScaffold(
        title = "New conversation",
        onBack = onBack,
        floatingActionButton = {
            if (recipients.isNotEmpty() || typedIsNumber) {
                ExtendedFloatingActionButton(
                    onClick = {
                        if (typedIsNumber) add(query.filter { !it.isWhitespace() })
                        onStart(recipients.toList())
                    },
                    icon = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) },
                    text = { Text("Start") },
                    modifier = Modifier.imePadding(),
                )
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).padding(horizontal = 16.dp)) {
            if (recipients.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    recipients.forEach { r ->
                        InputChip(
                            selected = true, onClick = { recipients.remove(r) },
                            label = { Text(r) },
                            trailingIcon = { Icon(Icons.Filled.Close, contentDescription = "Remove $r", Modifier.size(18.dp)) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }
            }
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                label = { Text("To: name or number") },
                singleLine = true, shape = ExperienceShape.pill,
                trailingIcon = {
                    IconButton(onClick = { numeric = !numeric }) {
                        Icon(if (numeric) Icons.Filled.PersonSearch else Icons.Filled.Dialpad, contentDescription = if (numeric) "Use text keyboard" else "Use number keyboard")
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Phone else KeyboardType.Text, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (typedIsNumber) add(query.filter { !it.isWhitespace() }) }),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
                if (typedIsNumber) {
                    item {
                        Row(
                            Modifier.fillMaxWidth().clickable(role = Role.Button) { add(query.filter { !it.isWhitespace() }) }.heightIn(min = 56.dp).padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Dialpad, contentDescription = null)
                            Spacer(Modifier.width(16.dp))
                            Text("Send to $query", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
                if (query.isNotBlank() && suggestions.isEmpty() && !typedIsNumber) {
                    item { EmptyState(Icons.Filled.PersonSearch, "No matching contacts") }
                }
                items(suggestions.size) { i ->
                    val s = suggestions[i]
                    Surface(shape = ExperienceShape.grouped(i, suggestions.size), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                        Row(Modifier.clickable(role = Role.Button) { add(s.number) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            ContactAvatar(s.name, s.photo)
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(s.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${s.label} · ${s.number}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun searchPhones(context: android.content.Context, q: String): List<Suggestion> {
    val out = ArrayList<Suggestion>()
    runCatching {
        context.contentResolver.query(
            Uri.withAppendedPath(Phone.CONTENT_FILTER_URI, Uri.encode(q)),
            arrayOf(Phone.DISPLAY_NAME_PRIMARY, Phone.NUMBER, Phone.TYPE, Phone.LABEL, Phone.PHOTO_THUMBNAIL_URI),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext() && out.size < 50) {
                val number = c.getString(1) ?: continue
                out += Suggestion(c.getString(0) ?: number, number, Phone.getTypeLabel(context.resources, c.getInt(2), c.getString(3)).toString(), c.getString(4))
            }
        }
    }
    return out.distinctBy { it.name to it.number.filter(Char::isDigit) }
}
