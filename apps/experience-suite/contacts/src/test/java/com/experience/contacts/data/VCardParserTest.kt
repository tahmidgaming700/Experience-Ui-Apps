package com.experience.contacts.data

import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Phone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VCardParserTest {
    private val v21 = """
        BEGIN:VCARD
        VERSION:2.1
        N:Lovelace;Ada;King;Lady;
        FN:Lady Ada King Lovelace
        TEL;CELL;PREF:+44 7700 900123
        TEL;WORK;VOICE:020 7946 0000
        TEL;HOME;FAX:020 7946 0001
        EMAIL;INTERNET;WORK:ada@example.org
        ADR;HOME:;;12 St James's Square;London;;SW1Y 4JH;United Kingdom
        ORG:Analytical Engines Ltd;R&D
        TITLE:Mathematician
        NOTE;ENCODING=QUOTED-PRINTABLE;CHARSET=UTF-8:First programmer =E2=80=94 notes on the=
         Engine
        URL:https://example.org/ada
        BDAY:18151210
        END:VCARD
    """.trimIndent().replace("\n", "\r\n")

    private val v30 = """
        BEGIN:VCARD
        VERSION:3.0
        N:Hopper;Grace;Brewster;Rear Admiral;
        FN:Grace Hopper
        item1.TEL;TYPE=CELL,VOICE:(555) 010-0199
        TEL;TYPE=work:555-0100
        EMAIL;TYPE=INTERNET,HOME:grace@example.com
        ADR;TYPE=WORK:;Building 2;1 Navy Way\, Suite 4;Arlington;VA;22201;USA
        NOTE:Line one\nLine two\; with semicolon\, and comma
        BDAY:--1209
        ORG:US Navy
        END:VCARD
        BEGIN:VCARD
        VERSION:3.0
        FN:Only Name
        TEL:12345
        END:VCARD
    """.trimIndent()

    @Test fun parsesVCard21() {
        val c = VCardParser.parse(v21).single()
        assertEquals("Lady Ada King Lovelace", c.displayName)
        assertEquals("Ada", c.given)
        assertEquals("Lovelace", c.family)
        assertEquals("King", c.middle)
        assertEquals("Lady", c.prefix)
        assertEquals(3, c.phones.size)
        assertEquals("+44 7700 900123", c.phones[0].value)
        assertEquals(Phone.TYPE_MOBILE, VCardTypes.phoneType(c.phones[0].types))
        assertTrue(VCardTypes.isPreferred(c.phones[0].types))
        assertEquals(Phone.TYPE_WORK, VCardTypes.phoneType(c.phones[1].types))
        assertEquals(Phone.TYPE_FAX_HOME, VCardTypes.phoneType(c.phones[2].types))
        assertEquals(Email.TYPE_WORK, VCardTypes.emailType(c.emails.single().types))
        val adr = c.addresses.single()
        assertEquals("12 St James's Square", adr.street)
        assertEquals("London", adr.locality)
        assertEquals("SW1Y 4JH", adr.postalCode)
        assertEquals("12 St James's Square\nSW1Y 4JH London\nUnited Kingdom", adr.formatted)
        assertEquals("Analytical Engines Ltd, R&D", c.organization)
        assertEquals("Mathematician", c.title)
        assertEquals("First programmer — notes on the Engine", c.note)
        assertEquals(listOf("https://example.org/ada"), c.urls)
        assertEquals("1815-12-10", c.birthday)
    }

    @Test fun parsesVCard30WithGroupsAndEscapes() {
        val cards = VCardParser.parse(v30)
        assertEquals(2, cards.size)
        val g = cards[0]
        assertEquals("Grace Hopper", g.displayName)
        assertEquals("Brewster", g.middle)
        assertEquals(Phone.TYPE_MOBILE, VCardTypes.phoneType(g.phones[0].types))
        assertEquals(Phone.TYPE_WORK, VCardTypes.phoneType(g.phones[1].types))
        assertEquals(Email.TYPE_HOME, VCardTypes.emailType(g.emails[0].types))
        assertEquals("1 Navy Way, Suite 4", g.addresses[0].street)
        assertEquals("Building 2", g.addresses[0].extended)
        assertEquals("Line one\nLine two; with semicolon, and comma", g.note)
        assertEquals("--12-09", g.birthday)
        assertEquals("US Navy", g.organization)
        assertEquals("Only Name", cards[1].displayName)
        assertEquals("12345", cards[1].phones.single().value)
    }

    @Test fun foldedLinesAreJoined() {
        val text = "BEGIN:VCARD\r\nVERSION:3.0\r\nFN:Very Long\r\n  Name\r\nNOTE:abc\r\n\tdef\r\nEND:VCARD\r\n"
        val c = VCardParser.parse(text).single()
        assertEquals("Very Long Name", c.displayName)
        assertEquals("abcdef", c.note)
    }

    @Test fun quotedPrintableWithLatin1Charset() {
        val text = "BEGIN:VCARD\nVERSION:2.1\nN;CHARSET=ISO-8859-1;ENCODING=QUOTED-PRINTABLE:M=FCller;J=F6rg\nEND:VCARD"
        val c = VCardParser.parse(text).single()
        assertEquals("Müller", c.family)
        assertEquals("Jörg", c.given)
        assertEquals("Jörg Müller", c.displayName)
    }

    @Test fun nameFallsBackToOrganisationOrNumber() {
        assertEquals("ACME", VCardParser.parse("BEGIN:VCARD\nORG:ACME\nEND:VCARD").single().displayName)
        assertEquals("555", VCardParser.parse("BEGIN:VCARD\nTEL:555\nEND:VCARD").single().displayName)
        assertTrue(VCardParser.parse("BEGIN:VCARD\nVERSION:3.0\nEND:VCARD").isEmpty())
    }

    @Test fun birthdays() {
        assertEquals("1990-05-17", VCardParser.normalizeBirthday("1990-05-17"))
        assertEquals("1990-05-17", VCardParser.normalizeBirthday("19900517"))
        assertEquals("1990-05-17", VCardParser.normalizeBirthday("1990-05-17T00:00:00Z"))
        assertEquals("--05-17", VCardParser.normalizeBirthday("--0517"))
        assertNull(VCardParser.normalizeBirthday("May 17"))
    }

    @Test fun ignoresNestedAndPhotoData() {
        val text = "BEGIN:VCARD\nFN:Outer\nPHOTO;ENCODING=BASE64;TYPE=JPEG:AAAA\nAGENT:BEGIN:VCARD\nBEGIN:VCARD\nFN:Inner\nEND:VCARD\nTEL;TYPE=HOME:1\nEND:VCARD"
        val cards = VCardParser.parse(text)
        assertEquals(listOf("Outer"), cards.map { it.displayName })
        assertEquals("1", cards[0].phones.single().value)
    }
}
