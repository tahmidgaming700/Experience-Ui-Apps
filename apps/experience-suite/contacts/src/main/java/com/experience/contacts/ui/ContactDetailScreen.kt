package com.experience.contacts.ui

import android.Manifest
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.experience.contacts.ContactsViewModel
import com.experience.contacts.EditContactActivity
import com.experience.contacts.data.ContactDetails
import com.experience.design.components.EmptyState
import com.experience.design.components.ExperienceScaffold
import com.experience.design.components.GroupedRow
import com.experience.design.components.SectionHeader
import java.time.LocalDate
import java.time.MonthDay
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun ContactDetailScreen(vm: ContactsViewModel, lookupUri: Uri, onBack: (() -> Unit)?, onDeleted: () -> Unit) {
    val context = LocalContext.current
    val flow = remember(lookupUri) { vm.details(lookupUri) }
    val details by flow.collectAsStateWithLifecycle(initialValue = null)
    var loaded by remember(lookupUri) { mutableStateOf(false) }
    LaunchedEffect(details) { if (details != null) loaded = true }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/x-vcard")) { dest ->
        val d = details ?: return@rememberLauncherForActivityResult
        if (dest != null) vm.export(dest, listOf(d.lookupKey)) { n ->
            Toast.makeText(context, if (n > 0) "Contact exported" else "Export failed", Toast.LENGTH_SHORT).show()
        }
    }
    val d = details

    ExperienceScaffold(
        title = d?.name?.ifBlank { "(No name)" } ?: "",
        onBack = onBack,
        maxContentWidth = 720.dp,
        actions = {
            if (d != null) {
                IconButton(onClick = { vm.setStarred(d.id, !d.starred) }) {
                    Icon(if (d.starred) Icons.Filled.Star else Icons.Filled.StarBorder, contentDescription = if (d.starred) "Remove from favourites" else "Add to favourites")
                }
                IconButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_EDIT, d.lookupUri).setClass(context, EditContactActivity::class.java))
                }) { Icon(Icons.Filled.Edit, contentDescription = "Edit") }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More options") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Share") }, leadingIcon = { Icon(Icons.Filled.Share, null) }, onClick = {
                            menu = false
                            val uri = vm.repo.vCardUri(d.lookupKey)
                            val send = Intent(Intent.ACTION_SEND).setType("text/x-vcard").putExtra(Intent.EXTRA_STREAM, uri)
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            send.clipData = ClipData.newRawUri(d.name, uri)
                            context.safeStart(Intent.createChooser(send, "Share contact"))
                        })
                        DropdownMenuItem(text = { Text("Export to file") }, leadingIcon = { Icon(Icons.Filled.FileDownload, null) }, onClick = {
                            menu = false
                            exporter.launch("${d.name.ifBlank { "contact" }}.vcf")
                        })
                        DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Filled.Delete, null) }, onClick = {
                            menu = false
                            confirmDelete = true
                        })
                    }
                }
            }
        },
    ) { padding ->
        if (d == null) {
            if (loaded) EmptyState(Icons.Filled.PersonOff, "Contact not found", modifier = Modifier.padding(padding))
            return@ExperienceScaffold
        }
        DetailBody(d, Modifier.padding(padding))
    }

    if (confirmDelete && d != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${d.name}?") },
            text = { Text("This contact will be removed from this device and any synced accounts.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.delete(d.lookupUri) { ok -> if (ok) onDeleted() else Toast.makeText(context, "Couldn't delete", Toast.LENGTH_SHORT).show() }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun DetailBody(d: ContactDetails, modifier: Modifier) {
    val context = LocalContext.current
    fun call(number: String) {
        val granted = context.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        context.safeStart(Intent(if (granted) Intent.ACTION_CALL else Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)))
    }
    fun sms(number: String) = context.safeStart(Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null)))
    fun email(address: String) = context.safeStart(Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", address, null)))

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            ContactAvatar(d.name, d.photoUri, size = 144.dp)
            listOfNotNull(d.nickname?.let { "“$it”" }, listOfNotNull(d.jobTitle, d.company).joinToString(" · ").ifBlank { null }).forEach {
                Text(it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        val primaryPhone = d.phones.firstOrNull { it.primary } ?: d.phones.firstOrNull()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            QuickAction(Icons.Filled.Call, "Call", enabled = primaryPhone != null) { primaryPhone?.let { call(it.value) } }
            QuickAction(Icons.AutoMirrored.Filled.Message, "Text", enabled = primaryPhone != null) { primaryPhone?.let { sms(it.value) } }
            QuickAction(Icons.Filled.Email, "Email", enabled = d.emails.isNotEmpty()) { d.emails.firstOrNull()?.let { email(it.value) } }
        }
        if (d.phones.isNotEmpty()) {
            SectionHeader("Phone")
            d.phones.forEachIndexed { i, p ->
                GroupedRow(p.value, i, d.phones.size, summary = p.label, icon = Icons.Filled.Phone, onClick = { call(p.value) }, trailing = {
                    IconButton(onClick = { sms(p.value) }) { Icon(Icons.AutoMirrored.Filled.Message, contentDescription = "Text ${p.value}") }
                })
            }
        }
        if (d.emails.isNotEmpty()) {
            SectionHeader("Email")
            d.emails.forEachIndexed { i, e -> GroupedRow(e.value, i, d.emails.size, summary = e.label, icon = Icons.Filled.Email, onClick = { email(e.value) }) }
        }
        if (d.addresses.isNotEmpty()) {
            SectionHeader("Address")
            d.addresses.forEachIndexed { i, a ->
                GroupedRow(a.value, i, d.addresses.size, summary = a.label, icon = Icons.Filled.Place, onClick = {
                    context.safeStart(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(a.value.replace('\n', ' ')))))
                })
            }
        }
        if (d.websites.isNotEmpty()) {
            SectionHeader("Website")
            d.websites.forEachIndexed { i, w ->
                GroupedRow(w, i, d.websites.size, icon = Icons.Filled.Language, onClick = {
                    val url = if (w.contains("://")) w else "https://$w"
                    context.safeStart(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                })
            }
        }
        val about = buildList {
            if (d.company != null || d.jobTitle != null) add(Triple(Icons.Filled.Business, listOfNotNull(d.company, d.jobTitle).joinToString(" · "), "Organisation"))
            d.birthday?.let { add(Triple(Icons.Filled.Cake, formatBirthday(it), "Birthday")) }
            d.notes.forEach { add(Triple(Icons.AutoMirrored.Filled.Notes, it, "Notes")) }
        }
        if (about.isNotEmpty()) {
            SectionHeader("About")
            about.forEachIndexed { i, (icon, value, label) -> GroupedRow(value, i, about.size, summary = label, icon = icon) }
        }
        Text(
            "Saved in: " + d.accounts.joinToString(", "),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 24.dp, start = 16.dp),
        )
    }
}

private fun formatBirthday(raw: String): String = runCatching {
    if (raw.startsWith("--")) MonthDay.parse(raw).let { "${it.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())} ${it.dayOfMonth}" }
    else LocalDate.parse(raw).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG))
}.getOrDefault(raw)

@Composable
private fun QuickAction(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.widthIn(min = 72.dp).clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = CircleShape,
            color = if (enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            modifier = Modifier.size(56.dp),
        ) { Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null) } }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}
