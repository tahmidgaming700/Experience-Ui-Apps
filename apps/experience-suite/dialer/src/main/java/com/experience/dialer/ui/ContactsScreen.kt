package com.experience.dialer.ui

import android.Manifest
import android.content.Intent
import android.provider.ContactsContract
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.experience.design.components.EmptyState
import com.experience.design.components.ExperienceScaffold
import com.experience.design.components.PermissionGate
import com.experience.design.theme.ExperienceShape
import com.experience.dialer.DialerViewModel
import com.experience.dialer.data.DialContact
import java.util.Locale

@Composable
fun ContactsScreen(vm: DialerViewModel, bottomBar: @Composable () -> Unit) {
    val context = LocalContext.current
    ExperienceScaffold(
        title = "Contacts",
        bottomBar = bottomBar,
        floatingActionButton = {
            FloatingActionButton(onClick = {
                context.safeStart(Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI))
            }) { Icon(Icons.Filled.PersonAdd, contentDescription = "New contact") }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            PermissionGate(listOf(Manifest.permission.READ_CONTACTS), "Contacts", "Allow access to contacts to search and call them.") {
                LaunchedEffect(Unit) { vm.refresh() }
                ContactList(vm)
            }
        }
    }
}

@Composable
private fun ContactList(vm: DialerViewModel) {
    val context = LocalContext.current
    val call = rememberCallAction()
    val contacts by vm.contacts.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(contacts, query) {
        val q = query.trim().lowercase(Locale.getDefault())
        val digits = q.filter { it.isDigit() }
        if (q.isEmpty()) contacts
        else contacts.filter { c ->
            c.name.lowercase(Locale.getDefault()).contains(q) || (digits.isNotEmpty() && c.number.filter { it.isDigit() }.contains(digits))
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp)) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                placeholder = { Text("Search names and numbers") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, contentDescription = "Clear search") }
                },
                singleLine = true,
                shape = ExperienceShape.pill,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )
        }
        if (filtered.isEmpty()) {
            item { EmptyState(Icons.Outlined.Contacts, if (query.isEmpty()) "No contacts with phone numbers" else "No matches") }
        }
        items(filtered.size, key = { filtered[it].dataId }) { i ->
            ContactPhoneRow(vm, filtered[i], i, filtered.size,
                onCall = { call(filtered[i].number) },
                onInfo = { context.safeStart(Intent(Intent.ACTION_VIEW, filtered[i].lookupUri)) })
        }
    }
}

@Composable
private fun ContactPhoneRow(vm: DialerViewModel, c: DialContact, index: Int, count: Int, onCall: () -> Unit, onInfo: () -> Unit) {
    Surface(shape = ExperienceShape.grouped(index, count), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Row(
            Modifier.clickable(role = Role.Button, onClickLabel = "Call", onClick = onCall).padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ContactAvatar(c.name, c.photoUri)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(c.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(c.label, vm.formatter.format(c.number)).joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            IconButton(onClick = onInfo, modifier = Modifier.size(48.dp)) { Icon(Icons.Filled.Info, contentDescription = "Contact details for ${c.name}") }
        }
    }
}
