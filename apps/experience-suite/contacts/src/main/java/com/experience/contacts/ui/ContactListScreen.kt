package com.experience.contacts.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.experience.contacts.ContactsViewModel
import com.experience.contacts.data.ContactItem
import com.experience.contacts.data.Sections
import com.experience.design.components.EmptyState
import com.experience.design.components.SectionHeader
import com.experience.design.theme.ExperienceShape
import kotlinx.coroutines.launch

private sealed interface Entry {
    data class Header(val letter: String) : Entry
    data class Row(val item: ContactItem, val index: Int, val count: Int) : Entry
}

/**
 * Contact list: search, favourites row, A–Z sticky headers and a fast-scroll rail.
 * [selected] highlights the open contact in two-pane layouts.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContactList(
    vm: ContactsViewModel,
    selectedId: Long?,
    onOpen: (ContactItem) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val query by vm.query.collectAsStateWithLifecycle()
    val visible by vm.visible.collectAsStateWithLifecycle()
    val all by vm.contacts.collectAsStateWithLifecycle()
    val favourites = remember(all) { all.filter { it.starred } }
    val showFavourites = query.isBlank() && favourites.isNotEmpty()

    // Flatten into headers + rows, remembering where each letter starts.
    val (entries, letterIndex) = remember(visible) {
        val list = ArrayList<Entry>()
        val index = LinkedHashMap<String, Int>()
        val groups = visible.groupBy { Sections.of(it.name) }
        // Keep the provider's localized order of first appearance.
        Sections.starts(visible) { it.name }.keys.forEach { letter ->
            val rows = groups[letter].orEmpty()
            index[letter] = list.size
            list += Entry.Header(letter)
            rows.forEachIndexed { i, c -> list += Entry.Row(c, i, rows.size) }
        }
        list to index
    }
    val fixedItems = 1 + if (showFavourites) 1 else 0
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    Box(modifier) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(
            start = 16.dp, end = 32.dp, top = contentPadding.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding() + 96.dp,
        )) {
            item(key = "search") {
                OutlinedTextField(
                    value = query,
                    onValueChange = { vm.query.value = it },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    placeholder = { Text("Search names, numbers, email") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton(onClick = { vm.query.value = "" }) { Icon(Icons.Filled.Clear, contentDescription = "Clear search") }
                    },
                    singleLine = true,
                    shape = ExperienceShape.pill,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                )
            }
            if (showFavourites) {
                item(key = "favourites") {
                    Column {
                        SectionHeader("Favourites")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(favourites, key = { it.id }) { c ->
                                Column(
                                    Modifier.width(84.dp).clickable(role = Role.Button) { onOpen(c) }.padding(vertical = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    ContactAvatar(c.name, c.photoUri, size = 64.dp)
                                    Spacer(Modifier.height(4.dp))
                                    Text(c.name, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }
                }
            }
            if (visible.isEmpty()) {
                item(key = "empty") {
                    EmptyState(Icons.Outlined.Contacts, if (query.isBlank()) "No contacts yet" else "No matches",
                        if (query.isBlank()) "Add a contact or import a vCard file." else null)
                }
            }
            entries.forEach { e ->
                when (e) {
                    is Entry.Header -> stickyHeader(key = "h_${e.letter}") {
                        Text(
                            e.letter,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(start = 8.dp, top = 12.dp, bottom = 6.dp),
                        )
                    }
                    is Entry.Row -> item(key = e.item.id) {
                        ContactRow(e.item, e.index, e.count, selected = e.item.id == selectedId) { onOpen(e.item) }
                    }
                }
            }
        }
        if (letterIndex.size > 1) {
            LetterRail(
                letters = letterIndex.keys.toList(),
                modifier = Modifier.align(Alignment.CenterEnd).padding(top = contentPadding.calculateTopPadding()),
            ) { letter ->
                letterIndex[letter]?.let { idx -> scope.launch { listState.scrollToItem(idx + fixedItems) } }
            }
        }
    }
}

@Composable
private fun ContactRow(c: ContactItem, index: Int, count: Int, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = ExperienceShape.grouped(index, count),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
    ) {
        Row(Modifier.clickable(role = Role.Button, onClick = onClick).heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            ContactAvatar(c.name, c.photoUri)
            Spacer(Modifier.width(14.dp))
            Text(c.name.ifBlank { "(No name)" }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (c.starred) Icon(Icons.Filled.Star, contentDescription = "Favourite", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }
    }
}

/**
 * Drag/tap rail for jumping between letters. It is a touch shortcut only and hidden
 * from accessibility services, which navigate by the sticky section headers instead.
 */
@Composable
private fun LetterRail(letters: List<String>, modifier: Modifier, onLetter: (String) -> Unit) {
    var height by remember { mutableIntStateOf(1) }
    var active by remember { mutableStateOf<String?>(null) }
    fun pick(y: Float) {
        val i = ((y / height) * letters.size).toInt().coerceIn(0, letters.lastIndex)
        val l = letters[i]
        if (l != active) { active = l; onLetter(l) }
    }
    Box(modifier) {
        Column(
            Modifier
                .width(28.dp)
                .onSizeChanged { height = it.height.coerceAtLeast(1) }
                .clearAndSetSemantics { }
                .pointerInput(letters) {
                    detectVerticalDragGestures(
                        onDragStart = { pick(it.y) },
                        onDragEnd = { active = null },
                        onDragCancel = { active = null },
                    ) { change, _ -> pick(change.position.y) }
                }
                .pointerInput(letters) { detectTapGestures { pick(it.y); active = null } },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            letters.forEach { l ->
                Text(l, style = MaterialTheme.typography.labelSmall, color = if (l == active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        active?.let { l ->
            Surface(
                shape = ExperienceShape.hero, color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.align(Alignment.CenterStart).offset(x = (-88).dp).size(72.dp),
            ) {
                Box(contentAlignment = Alignment.Center) { Text(l, style = MaterialTheme.typography.headlineLarge) }
            }
        }
    }
}
