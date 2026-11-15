package com.experience.notes

import androidx.test.core.app.ApplicationProvider
import com.experience.notes.data.ChecklistItem
import com.experience.notes.data.Note
import com.experience.notes.data.NoteColor
import com.experience.notes.data.NoteFilter
import com.experience.notes.data.NotesDatabase
import com.experience.notes.data.NotesRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class NotesRepositoryTest {
    private var now = 1_000_000L
    private lateinit var repo: NotesRepository

    @Before fun setUp() {
        // In-memory database (null name) per test.
        repo = NotesRepository(NotesDatabase(ApplicationProvider.getApplicationContext(), null)) { now }
    }

    @Test fun insertGetUpdateDelete() {
        val n = repo.insert(Note(title = "Groceries", body = "milk", color = NoteColor.TERTIARY,
            checklist = listOf(ChecklistItem("eggs"), ChecklistItem("bread", true))))
        assertTrue(n.id > 0)
        val loaded = repo.get(n.id)!!
        assertEquals("Groceries", loaded.title)
        assertEquals(NoteColor.TERTIARY, loaded.color)
        assertEquals(listOf(ChecklistItem("eggs"), ChecklistItem("bread", true)), loaded.checklist)
        assertEquals(now, loaded.createdAt)

        now += 5000
        assertTrue(repo.update(loaded.copy(body = "oat milk")))
        val updated = repo.get(n.id)!!
        assertEquals("oat milk", updated.body)
        assertEquals(now, updated.updatedAt)
        assertEquals(1_000_000L, updated.createdAt)

        repo.deleteForever(n.id)
        assertNull(repo.get(n.id))
        assertFalse(repo.update(updated))
    }

    @Test fun pinnedFirstThenNewest() {
        val a = repo.insert(Note(title = "a")); now += 10
        val b = repo.insert(Note(title = "b")); now += 10
        val c = repo.insert(Note(title = "c"))
        repo.setPinned(a.id, true)
        // setPinned touches a; b and c keep their order after it.
        assertEquals(listOf("a", "c", "b"), repo.list(NoteFilter.NOTES).map { it.title })
        assertTrue(repo.list(NoteFilter.NOTES).first().pinned)
        assertNotNull(c)
    }

    @Test fun searchMatchesTitleBodyAndChecklistCaseInsensitively() {
        repo.insert(Note(title = "Trip plan", body = "Book hotel"))
        repo.insert(Note(title = "Shopping", checklist = listOf(ChecklistItem("Sunscreen"))))
        repo.insert(Note(title = "Discount", body = "50% off"))
        repo.insert(Note(title = "Other", body = "5000 off"))
        assertEquals(listOf("Trip plan"), repo.list(NoteFilter.NOTES, "HOTEL").map { it.title })
        assertEquals(listOf("Shopping"), repo.list(NoteFilter.NOTES, "sunscreen").map { it.title })
        assertEquals(listOf("Discount"), repo.list(NoteFilter.NOTES, "50%").map { it.title })
        assertEquals(4, repo.list(NoteFilter.NOTES, "  ").size)
    }

    @Test fun filtersSeparateArchiveAndTrash() {
        val a = repo.insert(Note(title = "keep"))
        val b = repo.insert(Note(title = "old", pinned = true))
        val c = repo.insert(Note(title = "junk"))
        repo.setArchived(b.id, true)
        repo.trash(c.id)
        assertEquals(listOf("keep"), repo.list(NoteFilter.NOTES).map { it.title })
        assertEquals(listOf("old"), repo.list(NoteFilter.ARCHIVE).map { it.title })
        assertFalse("archiving unpins", repo.get(b.id)!!.pinned)
        assertEquals(listOf("junk"), repo.list(NoteFilter.TRASH).map { it.title })
        assertEquals(listOf<String>(), repo.list(NoteFilter.ARCHIVE, "junk").map { it.title })

        repo.restore(c.id)
        assertEquals(setOf("keep", "junk"), repo.list(NoteFilter.NOTES).map { it.title }.toSet())
        assertNotNull(a)
    }

    @Test fun purgeDeletesOnlyNotesTrashedMoreThan30DaysAgo() {
        val old = repo.insert(Note(title = "old"))
        repo.trash(old.id)
        now += TimeUnit.DAYS.toMillis(20)
        val recent = repo.insert(Note(title = "recent"))
        repo.trash(recent.id)
        val live = repo.insert(Note(title = "live"))

        now += TimeUnit.DAYS.toMillis(5) // old: 25 days in trash
        assertEquals(0, repo.purgeTrash())
        now += TimeUnit.DAYS.toMillis(6) // old: 31 days, recent: 11 days
        assertEquals(1, repo.purgeTrash())
        assertNull(repo.get(old.id))
        assertNotNull(repo.get(recent.id))
        assertNotNull(repo.get(live.id))

        assertEquals(1, repo.emptyTrash())
        assertEquals(listOf("live"), repo.list(NoteFilter.NOTES).map { it.title })
    }

    @Test fun changesCounterAdvancesOnWrites() {
        val before = repo.changes.value
        repo.insert(Note(title = "x"))
        assertTrue(repo.changes.value > before)
    }

    @Test fun shareTextIncludesChecklist() {
        val n = Note(title = "T", body = "B", checklist = listOf(ChecklistItem("one", true), ChecklistItem("two")))
        assertEquals("T\n\nB\n\n☑ one\n☐ two", n.toShareText())
    }
}
