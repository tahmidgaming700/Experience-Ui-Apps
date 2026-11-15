package com.experience.contacts.data

import android.content.ContentValues
import android.os.Bundle
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.CommonDataKinds.Note
import android.provider.ContactsContract.CommonDataKinds.Organization
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import android.provider.ContactsContract.CommonDataKinds.Website
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.Intents.Insert

/**
 * Applies ContactsContract.Intents.Insert extras (NAME, PHONE[_TYPE], SECONDARY_/
 * TERTIARY_PHONE, EMAIL[_TYPE] …, POSTAL, COMPANY, JOB_TITLE, NOTES and the DATA
 * ContentValues list) on top of [draft], adding rows rather than replacing.
 */
object InsertExtras {
    fun apply(draft: ContactDraft, extras: Bundle?): ContactDraft {
        if (extras == null) return draft
        var d = draft
        extras.getString(Insert.NAME)?.takeIf { it.isNotBlank() && d.fullName.isBlank() }?.let {
            val (g, f) = NameSplit.split(it)
            d = d.copy(given = g, family = f)
        }
        fun typeOf(key: String, default: Int): Pair<Int, String?> = when (val t = extras.get(key)) {
            is Int -> t to null
            is String -> if (t.isBlank()) default to null else 0 to t // custom label (TYPE_CUSTOM = 0)
            else -> default to null
        }
        val phones = d.phones.toMutableList()
        listOf(Insert.PHONE to Insert.PHONE_TYPE, Insert.SECONDARY_PHONE to Insert.SECONDARY_PHONE_TYPE, Insert.TERTIARY_PHONE to Insert.TERTIARY_PHONE_TYPE)
            .forEach { (k, t) ->
                extras.getString(k)?.takeIf { it.isNotBlank() }?.let { v ->
                    if (phones.none { it.value == v }) { val (type, label) = typeOf(t, Phone.TYPE_MOBILE); phones += EditableField(null, v, type, label) }
                }
            }
        val emails = d.emails.toMutableList()
        listOf(Insert.EMAIL to Insert.EMAIL_TYPE, Insert.SECONDARY_EMAIL to Insert.SECONDARY_EMAIL_TYPE, Insert.TERTIARY_EMAIL to Insert.TERTIARY_EMAIL_TYPE)
            .forEach { (k, t) ->
                extras.getString(k)?.takeIf { it.isNotBlank() }?.let { v ->
                    if (emails.none { it.value.equals(v, true) }) { val (type, label) = typeOf(t, Email.TYPE_HOME); emails += EditableField(null, v, type, label) }
                }
            }
        val addresses = d.addresses.toMutableList()
        extras.getString(Insert.POSTAL)?.takeIf { it.isNotBlank() }?.let { v ->
            val (type, label) = typeOf(Insert.POSTAL_TYPE, StructuredPostal.TYPE_HOME)
            addresses += EditableField(null, v, type, label)
        }
        val websites = d.websites.toMutableList()
        extras.getString(Insert.COMPANY)?.takeIf { it.isNotBlank() }?.let { d = d.copy(company = it) }
        extras.getString(Insert.JOB_TITLE)?.takeIf { it.isNotBlank() }?.let { d = d.copy(jobTitle = it) }
        extras.getString(Insert.NOTES)?.takeIf { it.isNotBlank() }?.let { d = d.copy(note = listOf(d.note, it).filter(String::isNotBlank).joinToString("\n")) }

        @Suppress("DEPRECATION")
        val data: List<ContentValues> = runCatching { extras.getParcelableArrayList<ContentValues>(Insert.DATA) }.getOrNull().orEmpty()
        data.forEach { cv ->
            val type = cv.getAsInteger(Data.DATA2) ?: 0
            val label = cv.getAsString(Data.DATA3)
            when (cv.getAsString(Data.MIMETYPE)) {
                Phone.CONTENT_ITEM_TYPE -> cv.getAsString(Phone.NUMBER)?.let { phones += EditableField(null, it, type.takeIf { t -> t != 0 || label != null } ?: Phone.TYPE_MOBILE, label) }
                Email.CONTENT_ITEM_TYPE -> cv.getAsString(Email.ADDRESS)?.let { emails += EditableField(null, it, type.takeIf { t -> t != 0 || label != null } ?: Email.TYPE_HOME, label) }
                StructuredPostal.CONTENT_ITEM_TYPE -> cv.getAsString(StructuredPostal.FORMATTED_ADDRESS)?.let { addresses += EditableField(null, it, type.takeIf { t -> t != 0 || label != null } ?: StructuredPostal.TYPE_HOME, label) }
                Website.CONTENT_ITEM_TYPE -> cv.getAsString(Website.URL)?.let { websites += EditableField(null, it, type.takeIf { t -> t != 0 } ?: Website.TYPE_OTHER, label) }
                Organization.CONTENT_ITEM_TYPE -> d = d.copy(
                    company = cv.getAsString(Organization.COMPANY) ?: d.company,
                    jobTitle = cv.getAsString(Organization.TITLE) ?: d.jobTitle,
                )
                Note.CONTENT_ITEM_TYPE -> cv.getAsString(Note.NOTE)?.let { d = d.copy(note = it) }
                Event.CONTENT_ITEM_TYPE -> if (type == Event.TYPE_BIRTHDAY) cv.getAsString(Event.START_DATE)?.let { d = d.copy(birthday = it) }
                StructuredName.CONTENT_ITEM_TYPE -> d = d.copy(
                    given = cv.getAsString(StructuredName.GIVEN_NAME) ?: d.given,
                    family = cv.getAsString(StructuredName.FAMILY_NAME) ?: d.family,
                    middle = cv.getAsString(StructuredName.MIDDLE_NAME) ?: d.middle,
                )
            }
        }
        return d.copy(phones = phones, emails = emails, addresses = addresses, websites = websites)
    }
}
