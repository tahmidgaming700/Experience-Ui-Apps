package com.experience.notes

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import com.experience.notes.data.Note

/** Intent actions understood by [MainActivity] (also used by res/xml/shortcuts.xml). */
object NotesIntents {
    const val ACTION_NEW_NOTE = "com.experience.notes.action.NEW_NOTE"
    const val ACTION_OPEN_NOTE = "com.experience.notes.action.OPEN_NOTE"
    const val EXTRA_NOTE_ID = "com.experience.notes.extra.NOTE_ID"

    fun openNote(context: Context, id: Long): Intent =
        Intent(ACTION_OPEN_NOTE).setClass(context, MainActivity::class.java)
            .putExtra(EXTRA_NOTE_ID, id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
}

object Shortcuts {
    /** Whether the current launcher accepts pinned shortcuts. */
    fun canPin(context: Context): Boolean =
        context.getSystemService(ShortcutManager::class.java)?.isRequestPinShortcutSupported == true

    /** Asks the launcher to pin a home-screen shortcut that opens [note]. */
    fun requestPin(context: Context, note: Note): Boolean {
        val sm = context.getSystemService(ShortcutManager::class.java) ?: return false
        if (!sm.isRequestPinShortcutSupported || note.id == 0L) return false
        val label = note.title.ifBlank { note.body.lineSequence().firstOrNull { it.isNotBlank() } ?: context.getString(R.string.untitled) }
        val info = ShortcutInfo.Builder(context, "note-${note.id}")
            .setShortLabel(label.take(24))
            .setLongLabel(label.take(48))
            .setIcon(Icon.createWithResource(context, R.drawable.ic_shortcut_note))
            .setIntent(NotesIntents.openNote(context, note.id))
            .build()
        return runCatching { sm.requestPinShortcut(info, null) }.getOrDefault(false)
    }

    /** Disables a pinned shortcut whose note was deleted forever. */
    fun disable(context: Context, id: Long) {
        runCatching {
            context.getSystemService(ShortcutManager::class.java)
                ?.disableShortcuts(listOf("note-$id"), context.getString(R.string.note_deleted))
        }
    }
}
