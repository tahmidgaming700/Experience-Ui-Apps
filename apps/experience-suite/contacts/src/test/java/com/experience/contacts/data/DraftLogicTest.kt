package com.experience.contacts.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DraftLogicTest {
    @Test fun diffInsertsUpdatesAndDeletes() {
        val original = listOf(EditableField(1, "111", 2), EditableField(2, "222", 1), EditableField(3, "333", 3))
        val edited = listOf(
            EditableField(1, "111", 2),          // unchanged
            EditableField(2, "2222", 1),         // changed
            EditableField(null, " 444 ", 2),     // new
            EditableField(null, "   ", 2),       // new but blank: ignored
        ) // id 3 removed
        val ops = DraftDiff.rows(original, edited)
        assertEquals(
            listOf(RowOp.Delete(3), RowOp.Update(2, EditableField(2, "2222", 1)), RowOp.Insert(EditableField(null, "444", 2))),
            ops,
        )
    }

    @Test fun clearingAFieldDeletesIt() {
        assertEquals(listOf(RowOp.Delete(5)), DraftDiff.rows(listOf(EditableField(5, "x", 1)), listOf(EditableField(5, "", 1))))
    }

    @Test fun singleValuedKinds() {
        assertEquals(RowOp.Insert(EditableField(null, "note", 0)), DraftDiff.single(null, null, "note"))
        assertEquals(RowOp.Update(4, EditableField(4, "new", 0)), DraftDiff.single(4, "old", "new"))
        assertEquals(RowOp.Delete(4), DraftDiff.single(4, "old", ""))
        assertNull(DraftDiff.single(4, "same", "same"))
        assertNull(DraftDiff.single(null, null, ""))
    }

    @Test fun nameSplit() {
        assertEquals("Ada King" to "Lovelace", NameSplit.split("  Ada King   Lovelace "))
        assertEquals("Cher" to "", NameSplit.split("Cher"))
        assertEquals("" to "", NameSplit.split(""))
    }

    @Test fun draftFullNameAndEmptiness() {
        val d = ContactDraft(prefix = "Dr", given = "Jane", family = "Doe")
        assertEquals("Dr Jane Doe", d.fullName)
        assertEquals(true, ContactDraft(phones = listOf(EditableField(value = " ", type = 2))).isEmpty)
        assertEquals(false, ContactDraft(company = "ACME").isEmpty)
    }

    @Test fun sections() {
        assertEquals("E", Sections.of("Émile"))
        assertEquals("#", Sections.of("123 Taxi"))
        assertEquals("#", Sections.of(""))
        assertEquals("Z", Sections.of("zoe"))
        val starts = Sections.starts(listOf("Anna", "Alan", "Bob", "Émile", "Eve")) { it }
        assertEquals(linkedMapOf("A" to 0, "B" to 2, "E" to 3), starts)
    }
}
