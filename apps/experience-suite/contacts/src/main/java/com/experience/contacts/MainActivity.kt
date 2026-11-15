package com.experience.contacts

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.experience.contacts.ui.ContactDetailScreen
import com.experience.contacts.ui.ContactList
import com.experience.design.ExperienceActivity
import com.experience.design.components.EmptyState
import com.experience.design.components.ExperienceScaffold
import com.experience.design.components.PermissionGate
import com.experience.design.components.WidthClass
import com.experience.design.components.currentWidthClass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Contacts home: list with detail (two panes from medium width). Also opens contacts
 * from ACTION_VIEW / QUICK_CONTACT on contact, lookup and raw-contact URIs.
 */
class MainActivity : ExperienceActivity() {
    private val vm: ContactsViewModel by viewModels()
    private var selected by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selected = savedInstanceState?.getString(KEY_SELECTED)?.let(Uri::parse)
        if (savedInstanceState == null) handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        selected?.let { outState.putString(KEY_SELECTED, it.toString()) }
    }

    private fun handle(intent: Intent?) {
        val data = intent?.data ?: return
        if (intent.action != Intent.ACTION_VIEW && intent.action != ContactsContract.QuickContact.ACTION_QUICK_CONTACT) return
        if (data.authority != ContactsContract.AUTHORITY) return
        lifecycleScope.launch {
            val resolved = withContext(Dispatchers.IO) { vm.repo.resolve(data) }
            if (resolved != null) selected = resolved
            else Toast.makeText(this@MainActivity, "Contact not found", Toast.LENGTH_SHORT).show()
        }
    }

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val width = currentWidthClass()
        val twoPane = width != WidthClass.COMPACT
        val sel = selected
        val snackbar = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        var menu by remember { mutableStateOf(false) }
        // Highlight the open contact in two-pane mode (lookup URIs end with the contact id).
        val selectedContactId = sel?.lastPathSegment?.toLongOrNull()
        val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) startActivity(Intent(Intent.ACTION_VIEW, uri, context, ImportActivity::class.java).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        }
        val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/x-vcard")) { dest ->
            if (dest != null) vm.export(dest, null) { n ->
                scope.launch { snackbar.showSnackbar(if (n >= 0) "Exported $n contacts" else "Export failed") }
            }
        }

        if (!twoPane && sel != null) {
            BackHandler { selected = null }
            ContactDetailScreen(vm, sel, onBack = { selected = null }, onDeleted = { selected = null })
            return
        }

        val list: @Composable (Modifier) -> Unit = { m ->
            ExperienceScaffold(
                title = "Contacts",
                modifier = m,
                snackbarHostState = snackbar,
                actions = {
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More options") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Import vCard file") }, leadingIcon = { Icon(Icons.Filled.FileUpload, null) }, onClick = {
                                menu = false
                                importer.launch(arrayOf("text/x-vcard", "text/vcard", "text/directory", "text/plain", "application/octet-stream"))
                            })
                            DropdownMenuItem(text = { Text("Export all contacts") }, leadingIcon = { Icon(Icons.Filled.FileDownload, null) }, onClick = {
                                menu = false
                                exporter.launch("contacts.vcf")
                            })
                        }
                    }
                },
                floatingActionButton = {
                    ExtendedFloatingActionButton(
                        onClick = { startActivity(Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI, context, EditContactActivity::class.java)) },
                        icon = { Icon(Icons.Filled.PersonAdd, contentDescription = null) },
                        text = { Text("New contact") },
                    )
                },
            ) { padding ->
                Box(Modifier.padding(padding)) {
                    PermissionGate(
                        listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS),
                        "Contacts",
                        "Allow access to your contacts to view, edit and back them up.",
                    ) {
                        LaunchedEffect(Unit) { vm.refresh() }
                        ContactList(vm, selectedId = selectedContactId, onOpen = { selected = it.lookupUri })
                    }
                }
            }
        }

        if (twoPane) {
            Row(Modifier.fillMaxSize()) {
                list(Modifier.weight(if (width == WidthClass.EXPANDED) 0.4f else 0.5f))
                Box(Modifier.weight(if (width == WidthClass.EXPANDED) 0.6f else 0.5f)) {
                    if (sel != null) {
                        ContactDetailScreen(vm, sel, onBack = null, onDeleted = { selected = null })
                    } else {
                        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(top = 120.dp)) {
                            EmptyState(Icons.Outlined.Contacts, "Select a contact")
                        }
                    }
                }
            }
        } else {
            list(Modifier)
        }
    }

    private companion object { const val KEY_SELECTED = "selected" }
}
