package com.experience.files.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.experience.design.components.EmptyState
import com.experience.design.components.ExperienceScaffold
import com.experience.design.components.WidthClass
import com.experience.design.components.currentWidthClass
import com.experience.design.theme.ExperienceShape
import com.experience.files.FileIntents
import com.experience.files.FilesViewModel
import com.experience.files.Location
import com.experience.files.data.Mime
import com.experience.files.model.FileItem
import com.experience.files.model.SortKey
import java.io.File

/** Set when the app was started for ACTION_GET_CONTENT. */
data class PickerConfig(val mimeFilters: List<String>, val allowMultiple: Boolean)

@Composable
fun FilesApp(vm: FilesViewModel, picker: PickerConfig?, onPicked: (List<File>) -> Unit) {
    val wide = currentWidthClass() == WidthClass.EXPANDED
    LaunchedEffect(wide, vm.volumes) {
        // Two-pane tablets always show a folder on the right; Home lives in the side pane.
        if (wide && vm.location == Location.Home) vm.volumes.firstOrNull()?.let { vm.navigate(Location.Folder(it.root)) }
    }
    BackHandler(enabled = vm.backStack.size > 1 || vm.selection.isNotEmpty()) { vm.back() }
    val context = LocalContext.current
    if (wide) {
        Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            Box(Modifier.width(360.dp).fillMaxHeight()) {
                val insets = WindowInsets.safeDrawing.asPaddingValues()
                HomeContent(vm, PaddingValues(top = insets.calculateTopPadding() + 8.dp, bottom = insets.calculateBottomPadding())) { openItem(vm, it, picker, onPicked, vm.selection.isNotEmpty(), context) }
            }
            VerticalDivider()
            Box(Modifier.weight(1f)) { MainPane(vm, picker, onPicked) }
        }
    } else {
        MainPane(vm, picker, onPicked)
    }
    vm.operation?.let { OperationDialog(it, vm::cancelOperation) }
}

private fun openItem(vm: FilesViewModel, item: FileItem, picker: PickerConfig?, onPicked: (List<File>) -> Unit, selecting: Boolean, context: android.content.Context) {
    when {
        selecting -> vm.toggleSelect(item)
        item.isDirectory -> vm.navigate(Location.Folder(item.file))
        picker != null -> onPicked(listOf(item.file))
        else -> FileIntents.open(context, item.file, forceChooser = false)
    }
}

@Composable
private fun MainPane(vm: FilesViewModel, picker: PickerConfig?, onPicked: (List<File>) -> Unit) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var sortMenu by remember { mutableStateOf(false) }
    var newFolder by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<FileItem?>(null) }
    var deleting by remember { mutableStateOf<List<File>?>(null) }
    var properties by remember { mutableStateOf<FileItem?>(null) }

    val loc = vm.location
    val selecting = vm.selection.isNotEmpty()
    val items = vm.items.filter { picker == null || it.isDirectory || Mime.matches(Mime.of(it.name), picker.mimeFilters) }
    val title = when {
        selecting -> "${vm.selection.size} selected"
        loc == Location.Home -> if (picker != null) "Choose a file" else "Files"
        loc is Location.Folder -> vm.volumes.firstOrNull { it.root == loc.dir }?.label ?: loc.dir.name
        loc is Location.Category -> loc.category.title
        loc == Location.Recent -> "Recent"
        loc is Location.Search -> "Results for “${loc.query}”"
        else -> "Files"
    }

    ExperienceScaffold(
        title = title,
        snackbarHostState = snackbar,
        onBack = if (vm.backStack.size > 1 || selecting) ({ vm.back() }) else null,
        actions = {
            if (selecting) {
                IconButton(onClick = vm::selectAll) { Icon(Icons.Outlined.SelectAll, contentDescription = "Select all") }
                if (picker == null) {
                    IconButton(onClick = { vm.copySelection(cut = false) }) { Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy") }
                    IconButton(onClick = { vm.copySelection(cut = true) }) { Icon(Icons.Outlined.ContentCut, contentDescription = "Move (cut)") }
                    IconButton(onClick = { FileIntents.share(context, vm.selectedFiles) }) { Icon(Icons.Filled.Share, contentDescription = "Share") }
                    IconButton(onClick = { deleting = vm.selectedFiles }) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
                } else if (picker.allowMultiple) {
                    TextButton(onClick = { onPicked(vm.selectedFiles.filter { it.isFile }) }) { Text("Select") }
                }
            } else {
                IconButton(onClick = { searching = !searching }) { Icon(if (searching) Icons.Filled.Close else Icons.Filled.Search, contentDescription = if (searching) "Close search" else "Search") }
                if (loc != Location.Home) {
                    Box {
                        IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = "Sort and view options") }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            SortKey.entries.forEach { k ->
                                DropdownMenuItem(
                                    text = { Text(k.label + if (k == vm.sortKey) (if (vm.ascending) " (ascending)" else " (descending)") else "") },
                                    leadingIcon = { if (k == vm.sortKey) Icon(Icons.Filled.CheckCircle, contentDescription = "Current sort") },
                                    onClick = { vm.setSort(k); sortMenu = false },
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(if (vm.showHidden) "Hide hidden files" else "Show hidden files") },
                                onClick = { vm.toggleHidden(); sortMenu = false },
                            )
                        }
                    }
                    IconButton(onClick = vm::toggleGrid) {
                        Icon(if (vm.grid) Icons.AutoMirrored.Outlined.ViewList else Icons.Outlined.GridView, contentDescription = if (vm.grid) "Show as list" else "Show as grid")
                    }
                }
            }
        },
        floatingActionButton = {
            if (loc is Location.Folder && !selecting && picker == null) {
                ExtendedFloatingActionButton(
                    onClick = { newFolder = true },
                    icon = { Icon(Icons.Outlined.CreateNewFolder, contentDescription = null) },
                    text = { Text("New folder") },
                )
            }
        },
    ) { padding ->
        Column(Modifier.padding(top = padding.calculateTopPadding()).fillMaxSize()) {
            if (searching) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    placeholder = { Text(if (loc is Location.Folder) "Search in ${loc.dir.name}" else "Search internal storage") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { if (query.isNotBlank()) vm.search(query) }),
                    shape = ExperienceShape.pill,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            if (loc is Location.Folder) Breadcrumbs(vm)
            val clip = vm.clip
            if (clip != null && picker == null) {
                Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = ExperienceShape.pill, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${clip.files.size} item${if (clip.files.size == 1) "" else "s"} to ${if (clip.cut) "move" else "copy"}",
                            style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = vm::clearClip) { Text("Clear") }
                        TextButton(enabled = loc is Location.Folder, onClick = vm::paste) {
                            Icon(Icons.Outlined.ContentPaste, contentDescription = null); Spacer(Modifier.width(4.dp)); Text("Paste here")
                        }
                    }
                }
            }
            if (vm.loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            val bottom = PaddingValues(bottom = padding.calculateBottomPadding() + 96.dp)
            when {
                loc == Location.Home -> HomeContent(vm, PaddingValues(bottom = padding.calculateBottomPadding())) { openItem(vm, it, picker, onPicked, selecting, context) }
                vm.error != null -> EmptyState(Icons.Outlined.FolderOpen, "Can't open this location", vm.error)
                !vm.loading && items.isEmpty() -> EmptyState(
                    if (loc is Location.Search) Icons.Outlined.SearchOff else Icons.Outlined.FolderOpen,
                    if (loc is Location.Search) "No matches" else "Nothing here",
                    if (loc is Location.Search) "No file or folder name contains “${loc.query}”." else null,
                )
                else -> {
                    val onClick: (FileItem) -> Unit = { openItem(vm, it, picker, onPicked, selecting, context) }
                    val onLong: (FileItem) -> Unit = { if (picker == null || picker.allowMultiple) vm.toggleSelect(it) }
                    val menu: @Composable (FileItem) -> Unit = { item ->
                        ItemMenu(item, vm, picker, loc, onRename = { renaming = it }, onDelete = { deleting = listOf(it.file) }, onProperties = { properties = it })
                    }
                    if (vm.grid) {
                        LazyVerticalGrid(
                            GridCells.Adaptive(120.dp), contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = bottom.calculateBottomPadding()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(items, key = { it.path }) { GridEntry(it, it.path in vm.selection, onClick, onLong, menu) }
                        }
                    } else {
                        LazyColumn(contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = bottom.calculateBottomPadding())) {
                            itemsIndexed(items, key = { _, it -> it.path }) { i, it -> ListEntry(it, i, items.size, it.path in vm.selection, onClick, onLong, menu) }
                        }
                    }
                }
            }
        }
    }

    if (newFolder) NameDialog("New folder", "", "Create", { newFolder = false }) { vm.createFolder(it); newFolder = false }
    renaming?.let { item -> NameDialog("Rename", item.name, "Rename", { renaming = null }) { vm.rename(item, it); renaming = null } }
    deleting?.let { files -> DeleteDialog(files.map { it.name }, { deleting = null }) { vm.delete(files); deleting = null } }
    properties?.let { PropertiesDialog(it) { properties = null } }
}

@Composable
private fun Breadcrumbs(vm: FilesViewModel) {
    val crumbs = vm.backStack.withIndex().filter { it.value is Location.Folder }
    val state = rememberLazyListState()
    LaunchedEffect(crumbs.size) { if (crumbs.isNotEmpty()) state.animateScrollToItem(crumbs.size - 1) }
    LazyRow(state = state, contentPadding = PaddingValues(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        items(crumbs, key = { it.index }) { (index, l) ->
            val dir = (l as Location.Folder).dir
            val label = vm.volumes.firstOrNull { it.root == dir }?.label ?: dir.name
            val last = index == vm.backStack.lastIndex
            Row(verticalAlignment = Alignment.CenterVertically) {
                AssistChip(
                    onClick = { if (!last) vm.popTo(index) },
                    label = { Text(label, maxLines = 1) },
                    modifier = Modifier.semantics { if (last) stateDescription = "Current folder" },
                )
                if (!last) Icon(Icons.Outlined.ChevronRight, contentDescription = null, modifier = Modifier.padding(horizontal = 2.dp))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ListEntry(item: FileItem, index: Int, count: Int, selected: Boolean, onClick: (FileItem) -> Unit, onLong: (FileItem) -> Unit, menu: @Composable (FileItem) -> Unit) {
    val context = LocalContext.current
    Surface(
        shape = ExperienceShape.grouped(index, count),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp).heightIn(min = 64.dp).semantics { this.selected = selected },
    ) {
        Row(
            Modifier.combinedClickable(role = Role.Button, onClick = { onClick(item) }, onLongClick = { onLong(item) }, onLongClickLabel = "Select")
                .padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                FileThumb(item, 44.dp, MaterialTheme.shapes.small)
                if (selected) Icon(Icons.Filled.CheckCircle, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.BottomEnd).size(18.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.summary(context), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            menu(item)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GridEntry(item: FileItem, selected: Boolean, onClick: (FileItem) -> Unit, onLong: (FileItem) -> Unit, menu: @Composable (FileItem) -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.semantics { this.selected = selected },
    ) {
        Column(Modifier.combinedClickable(role = Role.Button, onClick = { onClick(item) }, onLongClick = { onLong(item) }, onLongClickLabel = "Select").padding(8.dp)) {
            Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
                FileThumb(item, 104.dp, MaterialTheme.shapes.medium)
                if (selected) Icon(Icons.Filled.CheckCircle, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.TopEnd).size(24.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 4.dp))
                menu(item)
            }
        }
    }
}

@Composable
private fun ItemMenu(
    item: FileItem, vm: FilesViewModel, picker: PickerConfig?, loc: Location,
    onRename: (FileItem) -> Unit, onDelete: (FileItem) -> Unit, onProperties: (FileItem) -> Unit,
) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More options for ${item.name}") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            val close = { open = false }
            if (picker == null) {
                if (!item.isDirectory) MenuAction("Open with", close) { FileIntents.open(context, item.file, forceChooser = true) }
                if (!item.isDirectory) MenuAction("Share", close) { FileIntents.share(context, listOf(item.file)) }
                if (item.extension == "zip") MenuAction("Extract here", close) { vm.extract(item.file) }
                MenuAction("Rename", close) { onRename(item) }
                MenuAction("Copy", close) { vm.clearSelection(); vm.toggleSelect(item); vm.copySelection(cut = false) }
                MenuAction("Move", close) { vm.clearSelection(); vm.toggleSelect(item); vm.copySelection(cut = true) }
            }
            if (loc !is Location.Folder) MenuAction("Show in folder", close) { item.file.parentFile?.let { vm.navigate(Location.Folder(it)) } }
            MenuAction("Properties", close) { onProperties(item) }
            if (picker == null) {
                HorizontalDivider()
                DropdownMenuItem(text = { Text("Delete", color = MaterialTheme.colorScheme.error) }, onClick = { open = false; onDelete(item) })
            }
        }
    }
}

@Composable
private fun MenuAction(label: String, close: () -> Unit, action: () -> Unit) {
    DropdownMenuItem(text = { Text(label) }, onClick = { close(); action() })
}

/** Shown until the user grants All-files access (Android 11 MANAGE_EXTERNAL_STORAGE). */
@Composable
fun AccessGate(onGrant: () -> Unit) {
    ExperienceScaffold(title = "Files") { padding ->
        Column(Modifier.padding(padding)) {
            EmptyState(
                Icons.Outlined.FolderOpen,
                "Allow access to manage all files",
                "Experience Files needs “All files access” to browse, copy, move and delete files on internal storage and SD cards.",
            ) { Button(onClick = onGrant) { Text("Open settings") } }
        }
    }
}
