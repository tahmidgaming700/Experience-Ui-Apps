package com.experience.contacts.data

import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import android.provider.ContactsContract.CommonDataKinds.Website

/** One editable data row (phone, email, address, website). [dataId] is null for new rows. */
data class EditableField(val dataId: Long? = null, val value: String = "", val type: Int, val label: String? = null)

/**
 * Editable contact. For an existing contact this mirrors the data rows of one raw
 * contact ([rawContactId]); single-valued kinds remember their row id so edits become
 * updates and cleared fields become deletes.
 */
data class ContactDraft(
    val rawContactId: Long? = null,
    val nameDataId: Long? = null,
    val prefix: String = "",
    val given: String = "",
    val middle: String = "",
    val family: String = "",
    val suffix: String = "",
    val phones: List<EditableField> = emptyList(),
    val emails: List<EditableField> = emptyList(),
    val addresses: List<EditableField> = emptyList(),
    val websites: List<EditableField> = emptyList(),
    val orgDataId: Long? = null,
    val company: String = "",
    val jobTitle: String = "",
    val noteDataId: Long? = null,
    val note: String = "",
    val birthdayDataId: Long? = null,
    val birthday: String = "",
    val photoDataId: Long? = null,
    /** New photo (JPEG) to store, or null to leave the photo unchanged. */
    val newPhoto: ByteArray? = null,
    val removePhoto: Boolean = false,
) {
    val fullName: String get() = listOf(prefix, given, middle, family, suffix).filter { it.isNotBlank() }.joinToString(" ")

    val isEmpty: Boolean
        get() = fullName.isBlank() && company.isBlank() &&
            (phones + emails + addresses + websites).all { it.value.isBlank() } && note.isBlank()

    override fun equals(other: Any?): Boolean = other is ContactDraft && toString() == other.toString() &&
        (newPhoto?.contentEquals(other.newPhoto) ?: (other.newPhoto == null))

    override fun hashCode(): Int = toString().hashCode()
}

/** Data row change computed from an original and an edited list. */
sealed interface RowOp {
    data class Insert(val field: EditableField) : RowOp
    data class Update(val dataId: Long, val field: EditableField) : RowOp
    data class Delete(val dataId: Long) : RowOp
}

object DraftDiff {
    fun rows(original: List<EditableField>, edited: List<EditableField>): List<RowOp> {
        val ops = ArrayList<RowOp>()
        val editedIds = edited.mapNotNull { it.dataId }.toSet()
        original.forEach { o -> if (o.dataId != null && o.dataId !in editedIds) ops += RowOp.Delete(o.dataId) }
        val byId = original.filter { it.dataId != null }.associateBy { it.dataId }
        edited.forEach { e ->
            val blank = e.value.isBlank()
            when {
                e.dataId == null && !blank -> ops += RowOp.Insert(e.copy(value = e.value.trim()))
                e.dataId != null && blank -> ops += RowOp.Delete(e.dataId)
                e.dataId != null && byId[e.dataId] != e -> ops += RowOp.Update(e.dataId, e.copy(value = e.value.trim()))
            }
        }
        return ops
    }

    /** Diff for a single-valued kind: text with an optional existing row. */
    fun single(dataId: Long?, original: String?, edited: String): RowOp? = rows(
        listOfNotNull(dataId?.let { EditableField(it, original.orEmpty(), 0) }),
        listOf(EditableField(dataId, edited, 0)),
    ).firstOrNull()
}

object NameSplit {
    /** "Ada King Lovelace" → given "Ada King", family "Lovelace"; single word → given. */
    fun split(full: String): Pair<String, String> {
        val parts = full.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return when (parts.size) {
            0 -> "" to ""
            1 -> parts[0] to ""
            else -> parts.dropLast(1).joinToString(" ") to parts.last()
        }
    }
}

/** Type pickers offered in the editor. */
object FieldTypes {
    val phone = listOf(
        Phone.TYPE_MOBILE to "Mobile", Phone.TYPE_HOME to "Home", Phone.TYPE_WORK to "Work",
        Phone.TYPE_MAIN to "Main", Phone.TYPE_WORK_MOBILE to "Work mobile", Phone.TYPE_FAX_WORK to "Work fax",
        Phone.TYPE_FAX_HOME to "Home fax", Phone.TYPE_PAGER to "Pager", Phone.TYPE_OTHER to "Other",
    )
    val email = listOf(Email.TYPE_HOME to "Home", Email.TYPE_WORK to "Work", Email.TYPE_MOBILE to "Mobile", Email.TYPE_OTHER to "Other")
    val postal = listOf(StructuredPostal.TYPE_HOME to "Home", StructuredPostal.TYPE_WORK to "Work", StructuredPostal.TYPE_OTHER to "Other")
    val website = listOf(Website.TYPE_HOMEPAGE to "Homepage", Website.TYPE_WORK to "Work", Website.TYPE_BLOG to "Blog", Website.TYPE_OTHER to "Other")

    fun label(options: List<Pair<Int, String>>, type: Int, custom: String?): String =
        custom?.takeIf { it.isNotBlank() } ?: options.firstOrNull { it.first == type }?.second ?: "Other"
}
