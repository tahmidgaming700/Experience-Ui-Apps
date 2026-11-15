package com.experience.notes

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.experience.notes.data.Note
import com.experience.notes.data.NoteFilter
import com.experience.notes.data.NotesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** One-off messages shown as snackbars; [undo] restores the affected note. */
data class UiMessage(val text: Int, val noteId: Long, val undo: UndoAction?)
enum class UndoAction { RESTORE_FROM_TRASH, UNARCHIVE, REARCHIVE }

class NotesViewModel(app: Application) : AndroidViewModel(app) {
    val repo = NotesRepository.get(app)

    val filter = MutableStateFlow(NoteFilter.NOTES)
    val query = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class)
    val notes: StateFlow<List<Note>?> = combine(repo.changes, filter, query) { _, f, q -> f to q }
        .mapLatest { (f, q) -> repo.list(f, q) }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    /** The note open in the editor (id 0 = new, not yet saved), or null. */
    var draft by mutableStateOf<Note?>(null)
        private set
    private var saveJob: Job? = null
    private val saveLock = Mutex()

    init {
        viewModelScope.launch(Dispatchers.IO) { repo.purgeTrash() }
    }

    private fun io(block: suspend () -> Unit) = viewModelScope.launch(Dispatchers.IO) { block() }

    fun openNote(id: Long, onMissing: () -> Unit = {}) {
        viewModelScope.launch {
            flush()
            val note = withContext(Dispatchers.IO) { repo.get(id) }
            if (note == null || note.isTrashed) onMissing() else draft = note
        }
    }

    fun newNote(initial: Note = Note()) {
        viewModelScope.launch {
            flush()
            draft = initial
            if (!initial.isBlank) save()
        }
    }

    /** Applies an edit and autosaves after a short pause in typing. */
    fun edit(transform: (Note) -> Note) {
        val current = draft ?: return
        draft = transform(current)
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(400)
            save()
        }
    }

    /** Serialised so a slow first insert and a later autosave cannot both insert. */
    private suspend fun save() = saveLock.withLock {
        val d = draft ?: return@withLock
        if (d.id == 0L && d.isBlank) return@withLock
        val saved = withContext(Dispatchers.IO) {
            if (d.id == 0L) repo.insert(d) else { repo.update(d); d }
        }
        // Keep the latest typed text; only adopt the id assigned on insert.
        draft?.let { if (it.id == 0L) draft = it.copy(id = saved.id, createdAt = saved.createdAt) }
    }

    /** Saves pending edits now (called when the activity stops). */
    fun persist() { viewModelScope.launch { flush() } }

    private suspend fun flush() {
        saveJob?.cancel()
        saveJob = null
        save()
    }

    /** Closes the editor, saving pending edits and discarding a note left empty. */
    fun closeEditor() {
        viewModelScope.launch {
            flush()
            val d = draft
            draft = null
            if (d != null && d.id != 0L && d.isBlank) withContext(Dispatchers.IO) { repo.deleteForever(d.id) }
        }
    }

    fun trash(id: Long) {
        viewModelScope.launch {
            if (draft?.id == id) { flush(); draft = null }
            withContext(Dispatchers.IO) { repo.trash(id) }
            _messages.tryEmit(UiMessage(R.string.moved_to_trash, id, UndoAction.RESTORE_FROM_TRASH))
        }
    }

    fun setArchived(id: Long, archived: Boolean) {
        viewModelScope.launch {
            if (draft?.id == id) { flush(); draft = null }
            withContext(Dispatchers.IO) { repo.setArchived(id, archived) }
            _messages.tryEmit(
                UiMessage(if (archived) R.string.archived else R.string.unarchived, id, if (archived) UndoAction.UNARCHIVE else UndoAction.REARCHIVE)
            )
        }
    }

    fun undo(message: UiMessage) = io {
        when (message.undo) {
            UndoAction.RESTORE_FROM_TRASH -> repo.restore(message.noteId)
            UndoAction.UNARCHIVE -> repo.setArchived(message.noteId, false)
            UndoAction.REARCHIVE -> repo.setArchived(message.noteId, true)
            null -> Unit
        }
    }

    fun togglePinned(note: Note) {
        if (draft?.id == note.id) edit { it.copy(pinned = !it.pinned) } else io { repo.setPinned(note.id, !note.pinned) }
    }

    fun restore(id: Long) = io {
        repo.restore(id)
        _messages.tryEmit(UiMessage(R.string.restored, id, null))
    }

    fun deleteForever(id: Long) = io { repo.deleteForever(id) }
    fun emptyTrash() = io { repo.emptyTrash() }

    override fun onCleared() {
        // Last-chance save (e.g. process finishing while the editor is open).
        val d = draft
        if (d != null && !(d.id == 0L && d.isBlank)) {
            if (d.id == 0L) repo.insert(d) else repo.update(d)
        }
    }
}
