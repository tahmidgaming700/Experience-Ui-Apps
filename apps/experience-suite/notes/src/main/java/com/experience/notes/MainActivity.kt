package com.experience.notes

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import com.experience.design.ExperienceActivity
import com.experience.notes.data.Note
import com.experience.notes.ui.NotesApp

/**
 * Handles: launcher, static "New note" shortcut, pinned note shortcuts and
 * ACTION_SEND text/plain (creates a note from shared text).
 */
class MainActivity : ExperienceActivity() {
    private val vm: NotesViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    override fun onStop() {
        super.onStop()
        vm.persist()
    }

    private fun handle(intent: Intent?) {
        when (intent?.action) {
            NotesIntents.ACTION_NEW_NOTE -> vm.newNote()
            NotesIntents.ACTION_OPEN_NOTE -> {
                val id = intent.getLongExtra(NotesIntents.EXTRA_NOTE_ID, 0L)
                vm.openNote(id) { Toast.makeText(this, R.string.note_missing, Toast.LENGTH_SHORT).show() }
            }
            Intent.ACTION_SEND -> {
                val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty()
                val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty()
                if (text.isNotBlank() || subject.isNotBlank()) vm.newNote(Note(title = subject.trim(), body = text.trim()))
            }
        }
    }

    @Composable
    override fun Content() {
        NotesApp(vm)
    }
}
