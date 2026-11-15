package com.experience.notes.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.TimeUnit

/** SQLite schema for notes. Checklist items are stored as JSON plus a flattened text column for search. */
class NotesDatabase(context: Context, name: String? = DB_NAME) : SQLiteOpenHelper(context, name, null, VERSION) {
    companion object {
        const val DB_NAME = "notes.db"
        const val VERSION = 1
        const val T = "notes"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE $T (
                _id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL DEFAULT '',
                body TEXT NOT NULL DEFAULT '',
                checklist TEXT,
                checklist_text TEXT NOT NULL DEFAULT '',
                color TEXT NOT NULL DEFAULT 'DEFAULT',
                pinned INTEGER NOT NULL DEFAULT 0,
                archived INTEGER NOT NULL DEFAULT 0,
                trashed_at INTEGER,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )"""
        )
        db.execSQL("CREATE INDEX notes_updated ON $T(updated_at)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
}

/**
 * All note persistence. Methods are blocking (call from a background dispatcher);
 * [changes] increments after every write so observers can re-query.
 */
class NotesRepository(private val helper: NotesDatabase, private val clock: () -> Long = System::currentTimeMillis) {
    constructor(context: Context) : this(NotesDatabase(context.applicationContext))

    companion object {
        val TRASH_RETENTION_MS: Long = TimeUnit.DAYS.toMillis(30)

        @Volatile private var instance: NotesRepository? = null
        fun get(context: Context): NotesRepository =
            instance ?: synchronized(this) { instance ?: NotesRepository(context).also { instance = it } }
    }

    private val _changes = MutableStateFlow(0L)
    val changes: StateFlow<Long> = _changes
    private fun changed() { _changes.value = _changes.value + 1 }

    private val db get() = helper.writableDatabase

    private fun values(n: Note) = ContentValues().apply {
        put("title", n.title)
        put("body", n.body)
        put("checklist", ChecklistCodec.encode(n.checklist))
        put("checklist_text", n.checklist.joinToString("\n") { it.text })
        put("color", n.color.name)
        put("pinned", if (n.pinned) 1 else 0)
        put("archived", if (n.archived) 1 else 0)
        if (n.trashedAt == null) putNull("trashed_at") else put("trashed_at", n.trashedAt)
    }

    private fun Cursor.toNote() = Note(
        id = getLong(getColumnIndexOrThrow("_id")),
        title = getString(getColumnIndexOrThrow("title")),
        body = getString(getColumnIndexOrThrow("body")),
        checklist = ChecklistCodec.decode(getString(getColumnIndexOrThrow("checklist"))),
        color = runCatching { NoteColor.valueOf(getString(getColumnIndexOrThrow("color"))) }.getOrDefault(NoteColor.DEFAULT),
        pinned = getInt(getColumnIndexOrThrow("pinned")) != 0,
        archived = getInt(getColumnIndexOrThrow("archived")) != 0,
        trashedAt = getColumnIndexOrThrow("trashed_at").let { if (isNull(it)) null else getLong(it) },
        createdAt = getLong(getColumnIndexOrThrow("created_at")),
        updatedAt = getLong(getColumnIndexOrThrow("updated_at")),
    )

    /** Inserts a new note (id 0) and returns it with its id and timestamps. */
    fun insert(note: Note): Note {
        val now = clock()
        val cv = values(note).apply { put("created_at", now); put("updated_at", now) }
        val id = db.insertOrThrow(NotesDatabase.T, null, cv)
        changed()
        return note.copy(id = id, createdAt = now, updatedAt = now)
    }

    /** Saves edits, bumping updated_at. Returns false if the note no longer exists. */
    fun update(note: Note): Boolean {
        val cv = values(note).apply { put("updated_at", clock()) }
        val rows = db.update(NotesDatabase.T, cv, "_id = ?", arrayOf(note.id.toString()))
        changed()
        return rows > 0
    }

    fun get(id: Long): Note? = db.query(NotesDatabase.T, null, "_id = ?", arrayOf(id.toString()), null, null, null).use {
        if (it.moveToFirst()) it.toNote() else null
    }

    /**
     * Notes for [filter], matching [query] (case-insensitive substring of title, body or
     * checklist text). Pinned notes come first in NOTES; otherwise newest first.
     */
    fun list(filter: NoteFilter, query: String = ""): List<Note> {
        val where = StringBuilder(
            when (filter) {
                NoteFilter.NOTES -> "trashed_at IS NULL AND archived = 0"
                NoteFilter.ARCHIVE -> "trashed_at IS NULL AND archived = 1"
                NoteFilter.TRASH -> "trashed_at IS NOT NULL"
            }
        )
        val args = ArrayList<String>()
        val q = query.trim()
        if (q.isNotEmpty()) {
            // Escape LIKE wildcards so "50%" searches literally.
            val like = "%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
            where.append(" AND (title LIKE ? ESCAPE '\\' OR body LIKE ? ESCAPE '\\' OR checklist_text LIKE ? ESCAPE '\\')")
            repeat(3) { args += like }
        }
        val order = if (filter == NoteFilter.NOTES) "pinned DESC, updated_at DESC, _id DESC" else "updated_at DESC, _id DESC"
        return db.query(NotesDatabase.T, null, where.toString(), args.toTypedArray(), null, null, order).use { c ->
            buildList { while (c.moveToNext()) add(c.toNote()) }
        }
    }

    fun setPinned(id: Long, pinned: Boolean) = patch(id) { put("pinned", if (pinned) 1 else 0) }
    fun setArchived(id: Long, archived: Boolean) = patch(id) { put("archived", if (archived) 1 else 0); if (archived) put("pinned", 0) }
    fun trash(id: Long) = patch(id, touch = false) { put("trashed_at", clock()) }
    fun restore(id: Long) = patch(id) { putNull("trashed_at") }

    private fun patch(id: Long, touch: Boolean = true, block: ContentValues.() -> Unit) {
        val cv = ContentValues().apply(block)
        if (touch) cv.put("updated_at", clock())
        db.update(NotesDatabase.T, cv, "_id = ?", arrayOf(id.toString()))
        changed()
    }

    fun deleteForever(id: Long) {
        db.delete(NotesDatabase.T, "_id = ?", arrayOf(id.toString()))
        changed()
    }

    fun emptyTrash(): Int = db.delete(NotesDatabase.T, "trashed_at IS NOT NULL", null).also { changed() }

    /** Deletes notes that have been in the trash for longer than 30 days. Returns the count. */
    fun purgeTrash(now: Long = clock()): Int {
        val cutoff = now - TRASH_RETENTION_MS
        val n = db.delete(NotesDatabase.T, "trashed_at IS NOT NULL AND trashed_at <= ?", arrayOf(cutoff.toString()))
        if (n > 0) changed()
        return n
    }
}
