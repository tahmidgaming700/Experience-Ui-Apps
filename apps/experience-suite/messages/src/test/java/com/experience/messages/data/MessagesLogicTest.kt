package com.experience.messages.data

import android.provider.Telephony.Sms
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MessagesLogicTest {
    private val minute = 60_000L
    private val day = 24 * 60 * minute
    private fun msg(id: Long, incoming: Boolean, at: Long, address: String? = "1") =
        Message(id, false, incoming, address, "m$id", at, if (incoming) MessageStatus.RECEIVED else MessageStatus.SENT, 1)

    @Test fun groupsBubblesBySenderAndTime() {
        val base = 10 * day
        val list = listOf(
            msg(1, true, base),
            msg(2, true, base + minute),
            msg(3, false, base + 2 * minute),
            msg(4, false, base + 20 * minute), // gap > window: new group
            msg(5, true, base + day),          // next day: separator
        )
        val p = BubbleLayout.positions(list) { it / day }
        assertEquals(BubbleLayout.Position(firstInGroup = true, lastInGroup = false, dateSeparator = true), p[0])
        assertEquals(BubbleLayout.Position(firstInGroup = false, lastInGroup = true, dateSeparator = false), p[1])
        assertEquals(BubbleLayout.Position(firstInGroup = true, lastInGroup = true, dateSeparator = false), p[2])
        assertEquals(BubbleLayout.Position(firstInGroup = true, lastInGroup = true, dateSeparator = false), p[3])
        assertEquals(BubbleLayout.Position(firstInGroup = true, lastInGroup = true, dateSeparator = true), p[4])
    }

    @Test fun groupChatsSplitBySender() {
        val p = BubbleLayout.positions(listOf(msg(1, true, 0, "a"), msg(2, true, minute, "b"))) { 0 }
        assertEquals(true, p[1].firstInGroup)
        assertEquals(true, p[0].lastInGroup)
    }

    @Test fun recipientsFromUris() {
        assertEquals(listOf("+15551234", "5556"), Recipients.fromSchemeSpecificPart("+15551234;5556"))
        assertEquals(listOf("123", "456"), Recipients.fromSchemeSpecificPart("123,456?body=hi"))
        assertEquals(emptyList<String>(), Recipients.fromSchemeSpecificPart(""))
        assertEquals(emptyList<String>(), Recipients.fromSchemeSpecificPart(null))
        assertEquals("hi", Recipients.bodyFromSchemeSpecificPart("123?body=hi"))
        assertNull(Recipients.bodyFromSchemeSpecificPart("123"))
    }

    @Test fun smsStatusMapping() {
        assertEquals(MessageStatus.RECEIVED, MessagesRepository.smsStatus(Sms.MESSAGE_TYPE_INBOX, Sms.STATUS_NONE))
        assertEquals(MessageStatus.SENDING, MessagesRepository.smsStatus(Sms.MESSAGE_TYPE_OUTBOX, Sms.STATUS_NONE))
        assertEquals(MessageStatus.SENT, MessagesRepository.smsStatus(Sms.MESSAGE_TYPE_SENT, Sms.STATUS_NONE))
        assertEquals(MessageStatus.SENT, MessagesRepository.smsStatus(Sms.MESSAGE_TYPE_SENT, Sms.STATUS_PENDING))
        assertEquals(MessageStatus.DELIVERED, MessagesRepository.smsStatus(Sms.MESSAGE_TYPE_SENT, Sms.STATUS_COMPLETE))
        assertEquals(MessageStatus.NOT_DELIVERED, MessagesRepository.smsStatus(Sms.MESSAGE_TYPE_SENT, Sms.STATUS_FAILED))
        assertEquals(MessageStatus.FAILED, MessagesRepository.smsStatus(Sms.MESSAGE_TYPE_FAILED, Sms.STATUS_NONE))
    }
}
