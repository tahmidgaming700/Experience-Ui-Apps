package com.experience.messages.mms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class MmsNotificationParserTest {
    private fun ByteArrayOutputStream.text(s: String) { write(s.toByteArray(Charsets.UTF_8)); write(0) }

    private fun notificationInd(messageType: Int = 0x82, withLocation: Boolean = true): ByteArray = ByteArrayOutputStream().apply {
        write(0x8C); write(messageType)
        write(0x98); text("T-123")
        write(0x8D); write(0x92)
        val from = "+15551234567/TYPE=PLMN".toByteArray()
        write(0x89); write(1 + from.size + 1); write(0x80); write(from); write(0)
        val subject = "Héllo".toByteArray(Charsets.UTF_8)
        write(0x96); write(1 + subject.size + 1); write(0xEA); write(subject); write(0)
        write(0x8A); write(0x80)
        // Unknown header with short-length value, must be skipped.
        write(0x95); write(0x02); write(0x01); write(0x02)
        write(0x8E); write(0x02); write(0x30); write(0x39)
        write(0x88); write(0x04); write(0x81); write(0x02); write(0x0E); write(0x10)
        if (withLocation) { write(0x83); text("http://mmsc.example/abc") }
    }.toByteArray()

    @Test fun parsesNotificationInd() {
        val ind = MmsNotificationParser.parse(notificationInd())!!
        assertEquals("T-123", ind.transactionId)
        assertEquals("+15551234567", ind.from)
        assertEquals("Héllo", ind.subject)
        assertEquals("http://mmsc.example/abc", ind.contentLocation)
        assertEquals(12345L, ind.messageSize)
        assertEquals(3600L, ind.expiry)
        assertTrue(ind.expiryRelative)
        assertEquals(0x12, ind.mmsVersion)
    }

    @Test fun rejectsOtherMessageTypes() {
        assertNull(MmsNotificationParser.parse(notificationInd(messageType = 0x86))) // m-delivery-ind
    }

    @Test fun rejectsIncomplete() {
        assertNull(MmsNotificationParser.parse(notificationInd(withLocation = false)))
        assertNull(MmsNotificationParser.parse(byteArrayOf(0x8C.toByte())))
        assertNull(MmsNotificationParser.parse(ByteArray(0)))
    }

    @Test fun cleansAddresses() {
        assertEquals("+4477", MmsNotificationParser.cleanAddress("+4477/TYPE=PLMN"))
        assertEquals("user@example.com", MmsNotificationParser.cleanAddress("user@example.com"))
    }
}
