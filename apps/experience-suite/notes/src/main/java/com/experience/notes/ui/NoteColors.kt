package com.experience.notes.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.experience.notes.R
import com.experience.notes.data.NoteColor

/** Note colours are tonal palette roles, so they follow the system accent and dark mode. */
@Composable
fun NoteColor.container(): Color = MaterialTheme.colorScheme.let {
    when (this) {
        NoteColor.DEFAULT -> it.surfaceContainerLow
        NoteColor.PRIMARY -> it.primaryContainer
        NoteColor.SECONDARY -> it.secondaryContainer
        NoteColor.TERTIARY -> it.tertiaryContainer
        NoteColor.ERROR -> it.errorContainer
        NoteColor.SURFACE_HIGH -> it.surfaceContainerHighest
    }
}

@Composable
fun NoteColor.content(): Color = MaterialTheme.colorScheme.let {
    when (this) {
        NoteColor.DEFAULT, NoteColor.SURFACE_HIGH -> it.onSurface
        NoteColor.PRIMARY -> it.onPrimaryContainer
        NoteColor.SECONDARY -> it.onSecondaryContainer
        NoteColor.TERTIARY -> it.onTertiaryContainer
        NoteColor.ERROR -> it.onErrorContainer
    }
}

/** Spoken/visible name so colour is never the only cue. */
val NoteColor.label: Int
    get() = when (this) {
        NoteColor.DEFAULT -> R.string.color_default
        NoteColor.PRIMARY -> R.string.color_primary
        NoteColor.SECONDARY -> R.string.color_secondary
        NoteColor.TERTIARY -> R.string.color_tertiary
        NoteColor.ERROR -> R.string.color_error
        NoteColor.SURFACE_HIGH -> R.string.color_surface
    }
