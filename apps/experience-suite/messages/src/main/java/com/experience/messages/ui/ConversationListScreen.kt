package com.experience.messages.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.experience.design.components.EmptyState
import com.experience.design.components.SectionHeader
import com.experience.design.theme.ExperienceShape
import com.experience.messages.ConversationUi
import com.experience.messages.MessagesViewModel

@Composable
fun ConversationList(
    vm: MessagesViewModel,
    selectedThread: Long?,
    onOpen: (ConversationUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items by vm.conversations.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val archivedView by vm.showArchived.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf<ConversationUi?>(null) }
    val pinned = items.filter { it.conversation.pinned }
    val others = items.filter { !it.conversation.pinned }

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp)) {
        item { SmsRoleBanner(onChanged = { vm.refresh() }) }
        item {
            OutlinedTextField(
                value = query, onValueChange = { vm.query.value = it },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                placeholder = { Text("Search conversations") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { vm.query.value = "" }) { Icon(Icons.Filled.Clear, contentDescription = "Clear search") } },
                singleLine = true, shape = ExperienceShape.pill,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )
        }
        if (items.isEmpty()) {
            item {
                EmptyState(
                    if (archivedView) Icons.Outlined.Archive else Icons.AutoMirrored.Filled.Chat,
                    when { query.isNotBlank() -> "No matches"; archivedView -> "No archived conversations"; else -> "No conversations yet" },
                )
            }
        }
        if (pinned.isNotEmpty()) {
            item { SectionHeader("Pinned") }
            items(pinned.size, key = { "p" + pinned[it].threadId }) { i ->
                ConversationRow(vm, pinned[i], i, pinned.size, pinned[i].threadId == selectedThread, onOpen, onDelete = { confirmDelete = it })
            }
            if (others.isNotEmpty()) item { SectionHeader(if (archivedView) "Archived" else "All") }
        }
        items(others.size, key = { "c" + others[it].threadId }) { i ->
            ConversationRow(vm, others[i], i, others.size, others[i].threadId == selectedThread, onOpen, onDelete = { confirmDelete = it })
        }
    }

    confirmDelete?.let { c ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete conversation?") },
            text = { Text("All messages with ${c.title} will be deleted from this device.") },
            confirmButton = { TextButton(onClick = { vm.delete(c.threadId); confirmDelete = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationRow(
    vm: MessagesViewModel,
    item: ConversationUi,
    index: Int,
    count: Int,
    selected: Boolean,
    onOpen: (ConversationUi) -> Unit,
    onDelete: (ConversationUi) -> Unit,
) {
    val context = LocalContext.current
    val c = item.conversation
    var menu by remember { mutableStateOf(false) }
    val dismiss = rememberSwipeToDismissBoxState(confirmValueChange = { value ->
        when (value) {
            SwipeToDismissBoxValue.StartToEnd -> vm.setArchived(c.threadId, !c.archived)
            SwipeToDismissBoxValue.EndToStart -> onDelete(item)
            SwipeToDismissBoxValue.Settled -> Unit
        }
        false // always snap back; the list updates from the provider / prefs
    })
    SwipeToDismissBox(
        state = dismiss,
        modifier = Modifier.padding(vertical = 1.dp),
        backgroundContent = {
            val toDelete = dismiss.dismissDirection == SwipeToDismissBoxValue.EndToStart
            val color by animateColorAsState(if (toDelete) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer, label = "swipe")
            Row(
                Modifier.fillMaxSize().background(color, ExperienceShape.grouped(index, count)).padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (toDelete) Arrangement.End else Arrangement.Start,
            ) {
                Icon(if (toDelete) Icons.Filled.Delete else if (c.archived) Icons.Filled.Unarchive else Icons.Filled.Archive, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (toDelete) "Delete" else if (c.archived) "Unarchive" else "Archive", style = MaterialTheme.typography.labelLarge)
            }
        },
    ) {
        Surface(
            shape = ExperienceShape.grouped(index, count),
            color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box {
                Row(
                    Modifier
                        .combinedClickable(
                            onClickLabel = "Open conversation",
                            onClick = { onOpen(item) },
                            onLongClickLabel = "More actions",
                            onLongClick = { menu = true },
                        )
                        .heightIn(min = 72.dp)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ContactAvatar(item.title, item.photoUri, size = 48.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                item.title, style = MaterialTheme.typography.titleMedium,
                                fontWeight = if (c.unread) FontWeight.ExtraBold else FontWeight.Normal,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                            )
                            if (c.pinned) Icon(Icons.Filled.PushPin, contentDescription = "Pinned", modifier = Modifier.padding(start = 4.dp).size(16.dp))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (c.hasError) {
                                Icon(Icons.Filled.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                Text("Not sent · ", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                            }
                            Text(
                                c.snippet.replace('\n', ' '), style = MaterialTheme.typography.bodyMedium,
                                color = if (c.unread) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (c.unread) FontWeight.SemiBold else null,
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(shortTime(context, c.dateMillis), style = MaterialTheme.typography.labelMedium,
                            color = if (c.unread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        if (c.unread) {
                            Spacer(Modifier.height(6.dp))
                            Box(Modifier.size(10.dp).background(MaterialTheme.colorScheme.primary, CircleShape).semantics { contentDescription = "Unread" })
                        }
                    }
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(if (c.pinned) "Unpin" else "Pin") }, leadingIcon = { Icon(Icons.Filled.PushPin, null) },
                        onClick = { menu = false; vm.setPinned(c.threadId, !c.pinned) })
                    if (c.unread) DropdownMenuItem(text = { Text("Mark as read") }, leadingIcon = { Icon(Icons.Filled.MarkChatRead, null) },
                        onClick = { menu = false; vm.markRead(c.threadId) })
                    DropdownMenuItem(text = { Text(if (c.archived) "Unarchive" else "Archive") }, leadingIcon = { Icon(if (c.archived) Icons.Filled.Unarchive else Icons.Filled.Archive, null) },
                        onClick = { menu = false; vm.setArchived(c.threadId, !c.archived) })
                    DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Filled.Delete, null) },
                        onClick = { menu = false; onDelete(item) })
                }
            }
        }
    }
}
