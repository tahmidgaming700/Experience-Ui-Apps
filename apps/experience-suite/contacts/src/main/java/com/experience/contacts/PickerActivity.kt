package com.experience.contacts

import android.Manifest
import android.app.Activity
import android.content.ContentUris
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.experience.contacts.ui.ContactAvatar
import com.experience.contacts.ui.ContactList
import com.experience.design.ExperienceActivity
import com.experience.design.components.EmptyState
import com.experience.design.components.ExperienceScaffold
import com.experience.design.components.PermissionGate
import com.experience.design.theme.ExperienceShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * ACTION_PICK / ACTION_GET_CONTENT for contacts, phone numbers, email addresses and
 * postal addresses. The result is the standard provider URI (contact lookup URI or
 * data row URI) with a read grant, as callers of the AOSP picker expect.
 */
class PickerActivity : ExperienceActivity() {
    private val vm: ContactsViewModel by viewModels()

    enum class Kind(val title: String, val contentUri: Uri, val valueColumn: String) {
        CONTACT("Choose a contact", ContactsContract.Contacts.CONTENT_URI, ""),
        PHONE("Choose a phone number", Phone.CONTENT_URI, Phone.NUMBER),
        EMAIL("Choose an email address", Email.CONTENT_URI, Email.ADDRESS),
        POSTAL("Choose an address", StructuredPostal.CONTENT_URI, StructuredPostal.FORMATTED_ADDRESS),
    }

    private data class DataRow(val id: Long, val name: String, val value: String, val label: String, val photo: String?)

    private val kind: Kind by lazy {
        val type = intent.type ?: intent.data?.let { contentResolver.getType(it) }
        when (type) {
            Phone.CONTENT_TYPE, Phone.CONTENT_ITEM_TYPE, "vnd.android.cursor.dir/phone", "vnd.android.cursor.item/phone" -> Kind.PHONE
            Email.CONTENT_TYPE, Email.CONTENT_ITEM_TYPE -> Kind.EMAIL
            StructuredPostal.CONTENT_TYPE, StructuredPostal.CONTENT_ITEM_TYPE -> Kind.POSTAL
            else -> Kind.CONTACT
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
    }

    private fun deliver(uri: Uri) {
        setResult(Activity.RESULT_OK, Intent().setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        finish()
    }

    @Composable
    override fun Content() {
        ExperienceScaffold(title = kind.title, onBack = { finish() }) { padding ->
            Box(Modifier.padding(padding)) {
                PermissionGate(listOf(Manifest.permission.READ_CONTACTS), "Contacts", "Allow access to contacts to choose one.") {
                    if (kind == Kind.CONTACT) {
                        LaunchedEffect(Unit) { vm.refresh() }
                        ContactList(vm, selectedId = null, onOpen = { deliver(it.lookupUri) })
                    } else {
                        DataPicker()
                    }
                }
            }
        }
    }

    @Composable
    private fun DataPicker() {
        var rows by remember { mutableStateOf<List<DataRow>?>(null) }
        var query by remember { mutableStateOf("") }
        LaunchedEffect(kind) {
            rows = withContext(Dispatchers.IO) { loadRows() }
        }
        val shown = remember(rows, query) {
            val q = query.trim().lowercase(Locale.getDefault())
            rows.orEmpty().filter { q.isEmpty() || it.name.lowercase(Locale.getDefault()).contains(q) || it.value.lowercase(Locale.getDefault()).contains(q) }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
            item {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true, shape = ExperienceShape.pill,
                    placeholder = { Text("Search") }, leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                )
            }
            if (rows != null && shown.isEmpty()) item { EmptyState(Icons.Outlined.Contacts, "Nothing to choose") }
            items(shown.size, key = { shown[it].id }) { i ->
                val r = shown[i]
                Surface(shape = ExperienceShape.grouped(i, shown.size), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                    Row(
                        Modifier.clickable(role = Role.Button) { deliver(ContentUris.withAppendedId(kind.contentUri, r.id)) }.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ContactAvatar(r.name, r.photo)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${r.label} · ${r.value}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }

    private fun loadRows(): List<DataRow> {
        val out = ArrayList<DataRow>()
        val projection = arrayOf(ContactsContract.Data._ID, ContactsContract.Data.DISPLAY_NAME_PRIMARY, kind.valueColumn,
            ContactsContract.Data.DATA2, ContactsContract.Data.DATA3, ContactsContract.Data.PHOTO_THUMBNAIL_URI)
        runCatching {
            contentResolver.query(kind.contentUri, projection, null, null, "${ContactsContract.Data.DISPLAY_NAME_PRIMARY} COLLATE LOCALIZED ASC")?.use { c ->
                while (c.moveToNext()) {
                    val value = c.getString(2) ?: continue
                    val type = if (c.isNull(3)) 0 else c.getInt(3)
                    val label = when (kind) {
                        Kind.PHONE -> Phone.getTypeLabel(resources, type, c.getString(4))
                        Kind.EMAIL -> Email.getTypeLabel(resources, type, c.getString(4))
                        else -> StructuredPostal.getTypeLabel(resources, type, c.getString(4))
                    }.toString()
                    out += DataRow(c.getLong(0), c.getString(1) ?: value, value, label, c.getString(5))
                }
            }
        }
        return out
    }
}
