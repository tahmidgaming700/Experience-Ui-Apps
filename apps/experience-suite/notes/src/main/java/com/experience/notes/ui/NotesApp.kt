package com.experience.notes.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.experience.design.components.EmptyState
import com.experience.design.components.WidthClass
import com.experience.design.components.currentWidthClass
import com.experience.design.theme.ExperienceMotion
import com.experience.notes.NotesViewModel
import com.experience.notes.R
import com.experience.notes.Shortcuts

/** Phone: list or editor full screen. Tablet (expanded width): list and editor side by side. */
@Composable
fun NotesApp(vm: NotesViewModel) {
    val context = LocalContext.current
    val notes by vm.notes.collectAsState()
    val filter by vm.filter.collectAsState()
    val query by vm.query.collectAsState()
    val draft = vm.draft
    val snackbar = remember { SnackbarHostState() }
    val twoPane = currentWidthClass() == WidthClass.EXPANDED

    LaunchedEffect(vm) {
        vm.messages.collect { msg ->
            val result = snackbar.showSnackbar(
                context.getString(msg.text),
                actionLabel = if (msg.undo != null) context.getString(R.string.undo) else null,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) vm.undo(msg)
        }
    }

    BackHandler(enabled = draft != null) { vm.closeEditor() }

    val list = @Composable { modifier: Modifier ->
        NotesListScreen(
            notes = notes, filter = filter, query = query, selectedId = draft?.id?.takeIf { twoPane },
            snackbar = snackbar,
            onFilter = { vm.filter.value = it },
            onQuery = { vm.query.value = it },
            onOpen = { vm.openNote(it.id) },
            onNew = { vm.newNote() },
            onRestore = vm::restore,
            onDeleteForever = { vm.deleteForever(it); Shortcuts.disable(context, it) },
            onEmptyTrash = vm::emptyTrash,
            modifier = modifier,
        )
    }
    val editor = @Composable { modifier: Modifier ->
        draft?.let { note ->
            NoteEditor(
                note = note,
                onEdit = vm::edit,
                onClose = vm::closeEditor,
                onTrash = { vm.trash(note.id) },
                onArchive = { vm.setArchived(note.id, it) },
                closeIsBack = !twoPane,
                modifier = modifier,
            )
        }
    }

    if (twoPane) {
        Row(Modifier.fillMaxSize()) {
            list(Modifier.weight(0.55f).fillMaxHeight())
            VerticalDivider()
            Box(Modifier.weight(0.45f).fillMaxHeight()) {
                if (draft != null) editor(Modifier.fillMaxSize())
                else Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxSize()) {
                    Box(contentAlignment = Alignment.Center) {
                        EmptyState(Icons.Outlined.EditNote, stringResource(R.string.select_note), stringResource(R.string.select_note_body))
                    }
                }
            }
        }
    } else {
        AnimatedContent(
            targetState = draft != null,
            transitionSpec = {
                (fadeIn(ExperienceMotion.defaultEffects()) + scaleIn(ExperienceMotion.defaultSpatial(), initialScale = 0.94f)) togetherWith
                    fadeOut(ExperienceMotion.fastEffects())
            },
            label = "notesNav",
        ) { editing ->
            if (editing) editor(Modifier.fillMaxSize()) else list(Modifier.fillMaxSize())
        }
    }
}
