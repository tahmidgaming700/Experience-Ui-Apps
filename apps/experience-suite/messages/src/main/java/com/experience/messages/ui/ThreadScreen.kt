package com.experience.messages.ui

import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.experience.messages.MessagesViewModel
import com.experience.messages.data.BubbleLayout
import com.experience.messages.data.ContactInfo
import com.experience.messages.data.ContactLookup
import com.experience.messages.data.Message
import com.experience.messages.notify.ActiveThread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

/** A conversation: chat bubbles, composer and header actions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadScreen(
    vm: MessagesViewModel,
    initialThreadId: Long?,
    addresses: List<String>,
    initialText: String,
    onBack: (() -> Unit)?,
    onDeleted: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var threadId by remember(initialThreadId, addresses) { mutableStateOf(initialThreadId) }
    LaunchedEffect(initialThreadId, addresses) {
        if (threadId == null && addresses.isNotEmpty()) threadId = vm.threadIdFor(addresses)
    }
    val tid = threadId
    val flow = remember(tid) { if (tid != null) vm.messages(tid) else emptyFlow() }
    val messages by flow.collectAsStateWithLifecycle(initialValue = emptyList())
    var contacts by remember(addresses) { mutableStateOf<Map<String, ContactInfo>>(emptyMap()) }
    LaunchedEffect(addresses) {
        contacts = withContext(Dispatchers.IO) { addresses.associateWith { ContactLookup.lookup(context, it) } }
    }
    val title = addresses.joinToString(", ") { contacts[it]?.name ?: it }.ifBlank { "Conversation" }
    val single = addresses.singleOrNull()

    // Mark read and suppress notifications for this thread while it is on screen.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, tid) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> { ActiveThread.threadId = tid; tid?.let { vm.markRead(it) } }
                Lifecycle.Event.ON_PAUSE -> if (ActiveThread.threadId == tid) ActiveThread.threadId = null
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose {
            owner.lifecycle.removeObserver(obs)
            if (ActiveThread.threadId == tid) ActiveThread.threadId = null
        }
    }
    LaunchedEffect(messages.size) {
        if (tid != null && messages.any { it.incoming } && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) vm.markRead(tid)
    }

    var text by rememberSaveable(tid, addresses) { mutableStateOf(initialText) }
    val sims = remember { vm.sims() }
    var subId by rememberSaveable { mutableIntStateOf(vm.defaultSmsSubId().takeIf { id -> sims.isEmpty() || sims.any { it.subId == id } } ?: sims.firstOrNull()?.subId ?: -1) }
    val canSend = remember { vm.isDefaultSmsApp() }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val zone = remember { ZoneId.systemDefault() }
    val positions = remember(messages) { BubbleLayout.positions(messages) { Instant.ofEpochMilli(it).atZone(zone).toLocalDate().toEpochDay() } }
    val listState = rememberLazyListState()
    LaunchedEffect(messages.lastOrNull()?.key) { if (messages.isNotEmpty()) listState.animateScrollToItem(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ContactAvatar(title, single?.let { contacts[it]?.photoUri }, size = 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                            if (single != null && contacts[single]?.name != null) Text(single, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                actions = {
                    if (single != null) {
                        IconButton(onClick = { context.safeStart(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", single, null))) }) {
                            Icon(Icons.Filled.Call, contentDescription = "Call $title")
                        }
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More options") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            if (single != null) {
                                val lookup = contacts[single]?.lookupUri
                                DropdownMenuItem(
                                    text = { Text(if (lookup != null) "View contact" else "Add contact") },
                                    leadingIcon = { Icon(if (lookup != null) Icons.Filled.Person else Icons.Filled.PersonAdd, null) },
                                    onClick = {
                                        menu = false
                                        context.safeStart(
                                            if (lookup != null) Intent(Intent.ACTION_VIEW, lookup)
                                            else Intent(Intent.ACTION_INSERT_OR_EDIT).setType(ContactsContract.Contacts.CONTENT_ITEM_TYPE)
                                                .putExtra(ContactsContract.Intents.Insert.PHONE, single)
                                        )
                                    },
                                )
                            }
                            if (tid != null) DropdownMenuItem(text = { Text("Delete conversation") }, leadingIcon = { Icon(Icons.Filled.Delete, null) },
                                onClick = { menu = false; confirmDelete = true })
                        }
                    }
                },
            )
        },
        bottomBar = {
            Composer(
                text = text, onText = { text = it }, sims = sims, subId = subId, onSubId = { subId = it }, enabled = canSend && addresses.isNotEmpty(),
                onSend = {
                    vm.send(addresses, text, subId, tid)
                    text = ""
                },
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { padding ->
        LazyColumn(
            state = listState, reverseLayout = true,
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            // reverseLayout: index 0 is the newest message at the bottom.
            val n = messages.size
            items(n, key = { messages[n - 1 - it].key }) { ri ->
                val i = n - 1 - ri
                val m = messages[i]
                val pos = positions[i]
                Column {
                    if (pos.dateSeparator) DateSeparator(m.dateMillis)
                    MessageBubble(
                        m, pos,
                        senderName = if (addresses.size > 1) m.address?.let { contacts[it]?.name ?: it } else null,
                        onRetry = { vm.resend(m) },
                        onCopy = {
                            clipboard.setText(AnnotatedString(m.body))
                            Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                        },
                        onDelete = { vm.deleteMessage(m) },
                    )
                }
            }
        }
    }

    if (confirmDelete && tid != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete conversation?") },
            text = { Text("All messages in this conversation will be deleted from this device.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete(tid); onDeleted() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

