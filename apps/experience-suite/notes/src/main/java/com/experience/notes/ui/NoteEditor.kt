package com.experience.notes.ui

import android.content.Intent
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddHome
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.experience.design.theme.ExperienceMotion
import com.experience.notes.R
import com.experience.notes.Shortcuts
import com.experience.notes.data.ChecklistItem
import com.experience.notes.data.Note
import com.experience.notes.data.NoteColor

/**
 * Note editor. Every change goes through [onEdit] (autosaved by the view model);
 * archive/trash leave the editor and offer Undo in the list's snackbar.
 */
@Composable
fun NoteEditor(
    note: Note,
    onEdit: ((Note) -> Note) -> Unit,
    onClose: () -> Unit,
    onTrash: () -> Unit,
    onArchive: (Boolean) -> Unit,
    closeIsBack: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val bg by animateColorAsState(note.color.container(), ExperienceMotion.defaultEffects(), label = "noteBg")
    val fg = note.color.content()
    var menu by remember { mutableStateOf(false) }
    var palette by remember { mutableStateOf(false) }
    var focusItem by remember { mutableIntStateOf(-1) }

    Surface(color = bg, contentColor = fg, modifier = modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
            // Toolbar
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(
                        if (closeIsBack) Icons.AutoMirrored.Filled.ArrowBack else Icons.Outlined.Close,
                        stringResource(R.string.close_editor),
                    )
                }
                Spacer(Modifier.weight(1f))
                IconToggleButton(checked = note.pinned, onCheckedChange = { p -> onEdit { it.copy(pinned = p) } }, enabled = !note.archived) {
                    Icon(Icons.Outlined.PushPin, stringResource(if (note.pinned) R.string.unpin else R.string.pin),
                        tint = if (note.pinned) MaterialTheme.colorScheme.primary else fg)
                }
                IconButton(onClick = { palette = !palette }) { Icon(Icons.Outlined.Palette, stringResource(R.string.choose_color)) }
                IconButton(onClick = { onArchive(!note.archived) }, enabled = note.id != 0L) {
                    Icon(if (note.archived) Icons.Outlined.Unarchive else Icons.Outlined.Archive,
                        stringResource(if (note.archived) R.string.unarchive else R.string.archive))
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.more_options)) }
                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.share)) },
                            leadingIcon = { Icon(Icons.Outlined.Share, null) },
                            enabled = !note.isBlank,
                            onClick = {
                                menu = false
                                val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                                    .putExtra(Intent.EXTRA_TEXT, note.toShareText())
                                    .apply { if (note.title.isNotBlank()) putExtra(Intent.EXTRA_SUBJECT, note.title) }
                                context.startActivity(Intent.createChooser(send, context.getString(R.string.share)))
                            },
                        )
                        if (Shortcuts.canPin(context)) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.add_to_home)) },
                                leadingIcon = { Icon(Icons.Outlined.AddHome, null) },
                                enabled = note.id != 0L,
                                onClick = {
                                    menu = false
                                    if (!Shortcuts.requestPin(context, note)) {
                                        Toast.makeText(context, R.string.pin_failed, Toast.LENGTH_SHORT).show()
                                    }
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete)) },
                            leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                            enabled = note.id != 0L,
                            onClick = { menu = false; onTrash() },
                        )
                    }
                }
            }
            if (palette) ColorPicker(note.color) { c -> onEdit { it.copy(color = c) } }

            val fieldColors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = fg, unfocusedTextColor = fg,
            )
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                item(key = "title") {
                    TextField(
                        value = note.title,
                        onValueChange = { t -> onEdit { it.copy(title = t) } },
                        placeholder = { Text(stringResource(R.string.title_hint), style = MaterialTheme.typography.headlineSmall) },
                        textStyle = MaterialTheme.typography.headlineSmall,
                        colors = fieldColors,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item(key = "body") {
                    TextField(
                        value = note.body,
                        onValueChange = { t -> onEdit { it.copy(body = t) } },
                        placeholder = { Text(stringResource(R.string.body_hint)) },
                        textStyle = MaterialTheme.typography.bodyLarge,
                        colors = fieldColors,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                itemsIndexed(note.checklist, key = { i, _ -> "item-$i" }) { i, item ->
                    ChecklistRow(
                        item = item, index = i, count = note.checklist.size, colors = fieldColors,
                        requestFocus = focusItem == i,
                        onFocused = { focusItem = -1 },
                        onChange = { newItem -> onEdit { n -> n.copy(checklist = n.checklist.toMutableList().also { it[i] = newItem }) } },
                        onMove = { delta ->
                            onEdit { n ->
                                val l = n.checklist.toMutableList()
                                val j = i + delta
                                if (j in l.indices) { val t = l[i]; l[i] = l[j]; l[j] = t }
                                n.copy(checklist = l)
                            }
                        },
                        onRemove = { onEdit { n -> n.copy(checklist = n.checklist.toMutableList().also { it.removeAt(i) }) } },
                        onNext = {
                            onEdit { n -> n.copy(checklist = n.checklist.toMutableList().also { it.add(i + 1, ChecklistItem("")) }) }
                            focusItem = i + 1
                        },
                    )
                }
                item(key = "add") {
                    TextButton(
                        onClick = {
                            onEdit { it.copy(checklist = it.checklist + ChecklistItem("")) }
                            focusItem = note.checklist.size
                        },
                        modifier = Modifier.padding(horizontal = 8.dp),
                    ) {
                        Icon(if (note.checklist.isEmpty()) Icons.Outlined.Checklist else Icons.Outlined.Add, null, tint = fg)
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(if (note.checklist.isEmpty()) R.string.add_checklist else R.string.add_item), color = fg)
                    }
                }
                if (note.updatedAt > 0) {
                    item(key = "meta") {
                        Text(
                            stringResource(R.string.edited, DateUtils.getRelativeDateTimeString(context, note.updatedAt,
                                DateUtils.MINUTE_IN_MILLIS, DateUtils.WEEK_IN_MILLIS, 0)),
                            style = MaterialTheme.typography.labelMedium,
                            color = fg.copy(alpha = 0.75f),
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChecklistRow(
    item: ChecklistItem,
    index: Int,
    count: Int,
    colors: androidx.compose.material3.TextFieldColors,
    requestFocus: Boolean,
    onFocused: () -> Unit,
    onChange: (ChecklistItem) -> Unit,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
    onNext: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(requestFocus) {
        if (requestFocus) { runCatching { focus.requestFocus() }; onFocused() }
    }
    Row(Modifier.fillMaxWidth().padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = item.checked, onCheckedChange = { onChange(item.copy(checked = it)) })
        TextField(
            value = item.text,
            onValueChange = { onChange(item.copy(text = it.replace("\n", ""))) },
            placeholder = { Text(stringResource(R.string.item_hint)) },
            textStyle = MaterialTheme.typography.bodyLarge.copy(textDecoration = if (item.checked) TextDecoration.LineThrough else null),
            colors = colors,
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { onNext() }),
            modifier = Modifier.weight(1f).focusRequester(focus),
        )
        IconButton(onClick = { onMove(-1) }, enabled = index > 0) { Icon(Icons.Outlined.KeyboardArrowUp, stringResource(R.string.move_up)) }
        IconButton(onClick = { onMove(1) }, enabled = index < count - 1) { Icon(Icons.Outlined.KeyboardArrowDown, stringResource(R.string.move_down)) }
        IconButton(onClick = onRemove) { Icon(Icons.Outlined.Close, stringResource(R.string.remove_item)) }
    }
}

/** Swatches are labelled (content description + check mark) so colour is not the only cue. */
@Composable
private fun ColorPicker(selected: NoteColor, onPick: (NoteColor) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        NoteColor.entries.forEach { c ->
            val label = stringResource(c.label)
            Box(
                Modifier
                    .size(48.dp)
                    .background(c.container(), CircleShape)
                    .border(if (c == selected) 3.dp else 1.dp, if (c == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
                    .clickable(role = Role.RadioButton) { onPick(c) }
                    .semantics { contentDescription = label; this.selected = c == selected },
                contentAlignment = Alignment.Center,
            ) {
                if (c == selected) Icon(Icons.Outlined.Check, null, tint = c.content())
            }
        }
    }
    Spacer(Modifier.height(4.dp))
}
