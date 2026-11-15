package com.experience.notes.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.experience.design.components.EmptyState
import com.experience.design.components.ExperienceScaffold
import com.experience.design.theme.ExperienceShape
import com.experience.notes.R
import com.experience.notes.data.Note
import com.experience.notes.data.NoteFilter

@Composable
fun NotesListScreen(
    notes: List<Note>?,
    filter: NoteFilter,
    query: String,
    selectedId: Long?,
    snackbar: SnackbarHostState,
    onFilter: (NoteFilter) -> Unit,
    onQuery: (String) -> Unit,
    onOpen: (Note) -> Unit,
    onNew: () -> Unit,
    onRestore: (Long) -> Unit,
    onDeleteForever: (Long) -> Unit,
    onEmptyTrash: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var trashDialog by remember { mutableStateOf<Note?>(null) }
    var confirmEmpty by remember { mutableStateOf(false) }

    ExperienceScaffold(
        title = stringResource(
            when (filter) {
                NoteFilter.NOTES -> R.string.app_name
                NoteFilter.ARCHIVE -> R.string.filter_archive
                NoteFilter.TRASH -> R.string.filter_trash
            }
        ),
        modifier = modifier,
        snackbarHostState = snackbar,
        maxContentWidth = 1600.dp,
        actions = {
            if (filter == NoteFilter.TRASH && !notes.isNullOrEmpty()) {
                IconButton(onClick = { confirmEmpty = true }) { Icon(Icons.Outlined.DeleteForever, stringResource(R.string.empty_trash)) }
            }
        },
        floatingActionButton = {
            if (filter == NoteFilter.NOTES) {
                ExtendedFloatingActionButton(
                    onClick = onNew,
                    icon = { Icon(Icons.Outlined.Edit, null) },
                    text = { Text(stringResource(R.string.new_note)) },
                )
            }
        },
    ) { padding ->
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Adaptive(168.dp),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 96.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalItemSpacing = 10.dp,
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = StaggeredGridItemSpan.FullLine, key = "search") { SearchField(query, onQuery) }
            item(span = StaggeredGridItemSpan.FullLine, key = "filters") { FilterRow(filter, onFilter) }
            if (filter == NoteFilter.TRASH) {
                item(span = StaggeredGridItemSpan.FullLine, key = "trashinfo") {
                    Text(stringResource(R.string.trash_info), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(4.dp))
                }
            }
            val list = notes.orEmpty()
            if (notes != null && list.isEmpty()) {
                item(span = StaggeredGridItemSpan.FullLine, key = "empty") {
                    val (title, body) = when {
                        query.isNotBlank() -> R.string.empty_search to R.string.empty_search_body
                        filter == NoteFilter.ARCHIVE -> R.string.empty_archive to R.string.empty_archive_body
                        filter == NoteFilter.TRASH -> R.string.empty_trash_title to R.string.empty_trash_body
                        else -> R.string.empty_notes to R.string.empty_notes_body
                    }
                    EmptyState(Icons.Outlined.StickyNote2, stringResource(title), stringResource(body))
                }
            }
            val pinned = if (filter == NoteFilter.NOTES) list.filter { it.pinned } else emptyList()
            val others = list - pinned.toSet()
            if (pinned.isNotEmpty()) {
                item(span = StaggeredGridItemSpan.FullLine, key = "h-pinned") { GridHeader(stringResource(R.string.pinned)) }
                items(pinned, key = { it.id }) { NoteCard(it, it.id == selectedId, Modifier.animateItem()) { onOpen(it) } }
                if (others.isNotEmpty()) item(span = StaggeredGridItemSpan.FullLine, key = "h-others") { GridHeader(stringResource(R.string.others)) }
            }
            items(others, key = { it.id }) { note ->
                NoteCard(note, note.id == selectedId, Modifier.animateItem()) {
                    if (filter == NoteFilter.TRASH) trashDialog = note else onOpen(note)
                }
            }
        }
    }

    trashDialog?.let { note ->
        AlertDialog(
            onDismissRequest = { trashDialog = null },
            title = { Text(note.title.ifBlank { stringResource(R.string.untitled) }) },
            text = { Text(stringResource(R.string.trashed_note_body)) },
            confirmButton = { TextButton(onClick = { onRestore(note.id); trashDialog = null }) { Text(stringResource(R.string.restore)) } },
            dismissButton = { TextButton(onClick = { onDeleteForever(note.id); trashDialog = null }) { Text(stringResource(R.string.delete_forever)) } },
        )
    }
    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            title = { Text(stringResource(R.string.empty_trash_confirm)) },
            text = { Text(stringResource(R.string.empty_trash_confirm_body)) },
            confirmButton = { TextButton(onClick = { onEmptyTrash(); confirmEmpty = false }) { Text(stringResource(R.string.empty_trash)) } },
            dismissButton = { TextButton(onClick = { confirmEmpty = false }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }
}

@Composable
private fun GridHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 2.dp))
}

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit) {
    TextField(
        value = query,
        onValueChange = onQuery,
        placeholder = { Text(stringResource(R.string.search_hint)) },
        leadingIcon = { Icon(Icons.Outlined.Search, null) },
        trailingIcon = {
            if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Outlined.Close, stringResource(R.string.clear_search)) }
        },
        singleLine = true,
        shape = ExperienceShape.pill,
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun FilterRow(filter: NoteFilter, onFilter: (NoteFilter) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            Triple(NoteFilter.NOTES, R.string.filter_notes, Icons.Outlined.StickyNote2),
            Triple(NoteFilter.ARCHIVE, R.string.filter_archive, Icons.Outlined.Archive),
            Triple(NoteFilter.TRASH, R.string.filter_trash, Icons.Outlined.Delete),
        ).forEach { (f, label, icon) ->
            FilterChip(
                selected = filter == f,
                onClick = { onFilter(f) },
                label = { Text(stringResource(label)) },
                leadingIcon = { Icon(icon, null, Modifier.size(18.dp)) },
                modifier = Modifier.height(48.dp).padding(vertical = 4.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteCard(note: Note, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val fg = note.color.content()
    Surface(
        shape = MaterialTheme.shapes.large,
        color = note.color.container(),
        contentColor = fg,
        border = if (selected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        else if (note.color == com.experience.notes.data.NoteColor.DEFAULT) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        else null,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier
                .combinedClickable(role = Role.Button, onClick = onClick)
                .padding(16.dp)
        ) {
            if (note.title.isNotBlank() || note.pinned) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        note.title.ifBlank { "" }, style = MaterialTheme.typography.titleMedium,
                        maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                    )
                    if (note.pinned) Icon(Icons.Outlined.PushPin, stringResource(R.string.pinned), Modifier.size(18.dp))
                }
                Spacer(Modifier.height(6.dp))
            }
            if (note.body.isNotBlank()) {
                Text(note.body.trim(), style = MaterialTheme.typography.bodyMedium, maxLines = 10, overflow = TextOverflow.Ellipsis)
            }
            if (note.checklist.isNotEmpty()) {
                if (note.body.isNotBlank()) Spacer(Modifier.height(8.dp))
                note.checklist.take(6).forEach { item ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                        Icon(
                            if (item.checked) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
                            stringResource(if (item.checked) R.string.checked else R.string.unchecked),
                            Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            item.text, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            textDecoration = if (item.checked) TextDecoration.LineThrough else null,
                        )
                    }
                }
                if (note.checklist.size > 6) {
                    Text(pluralStringResource(R.plurals.more_items_plural, note.checklist.size - 6, note.checklist.size - 6), style = MaterialTheme.typography.labelMedium)
                }
            }
            if (note.isBlank) {
                Text(stringResource(R.string.empty_note), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Normal), color = fg.copy(alpha = 0.7f))
            }
        }
    }
}
