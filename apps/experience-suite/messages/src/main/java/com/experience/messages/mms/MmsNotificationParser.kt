package com.experience.messages.mms

import java.nio.charset.Charset

/** Fields of an MMS m-notification-ind (OMA-TS-MMS-ENC) needed to record it. */
data class MmsNotificationInd(
    val transactionId: String,
    val from: String?,
    val subject: String?,
    val contentLocation: String,
    val messageSize: Long,
    /** Expiry: absolute epoch seconds, or relative seconds when [expiryRelative]. */
    val expiry: Long,
    val expiryRelative: Boolean,
    val mmsVersion: Int,
)

/**
 * Minimal WSP/MMS header decoder for the WAP push that announces an MMS. It reads
 * just the m-notification-ind headers and skips everything else generically; it does
 * not decode message bodies (MMS download is not implemented).
 */
object MmsNotificationParser {
    const val MESSAGE_TYPE_NOTIFICATION_IND = 0x82

    private const val H_CONTENT_LOCATION = 0x83
    private const val H_EXPIRY = 0x88
    private const val H_FROM = 0x89
    private const val H_MESSAGE_TYPE = 0x8C
    private const val H_MMS_VERSION = 0x8D
    private const val H_MESSAGE_SIZE = 0x8E
    private const val H_SUBJECT = 0x96
    private const val H_TRANSACTION_ID = 0x98

    private class Reader(val b: ByteArray) {
        var pos = 0
        fun hasMore() = pos < b.size
        fun u8(): Int = b[pos++].toInt() and 0xFF
        fun peek(): Int = b[pos].toInt() and 0xFF

        fun uintvar(): Long {
            var v = 0L
            repeat(5) {
                val x = u8()
                v = (v shl 7) or (x and 0x7F).toLong()
                if (x and 0x80 == 0) return v
            }
            return v
        }

        /** Value-length: Short-length (0-30) or Length-quote (31) + uintvar. */
        fun valueLength(): Int {
            val first = u8()
            return if (first < 31) first else uintvar().toInt()
        }

        fun textBytes(): ByteArray {
            if (hasMore() && peek() == 0x7F) pos++ // Quote
            val start = pos
            while (pos < b.size && b[pos].toInt() != 0) pos++
            val out = b.copyOfRange(start, pos)
            if (pos < b.size) pos++ // NUL
            return out
        }

        fun text(): String = String(textBytes(), Charsets.ISO_8859_1)

        fun longInteger(): Long {
            val len = u8()
            var v = 0L
            repeat(len) { v = (v shl 8) or u8().toLong() }
            return v
        }

        /** Integer-value: Short-integer or Long-integer. */
        fun integerValue(): Long = if (peek() and 0x80 != 0) (u8() and 0x7F).toLong() else longInteger()

        /** Encoded-string-value: Text-string | Value-length Char-set Text-string. */
        fun encodedString(): String {
            if (peek() in 32..127 || peek() == 0) return String(textBytes(), Charsets.UTF_8)
            val len = valueLength()
            val end = pos + len
            val mib = integerValue().toInt()
            val bytes = textBytes()
            pos = maxOf(pos, end).coerceAtMost(b.size)
            return String(bytes, charsetFor(mib))
        }

        /** Skips any header value using the generic WSP rules. */
        fun skipValue() {
            val first = peek()
            when {
                first < 31 -> { pos++; pos += first }
                first == 31 -> { pos++; pos += uintvar().toInt() }
                first in 32..127 -> textBytes()
                else -> pos++
            }
        }
    }

    internal fun charsetFor(mib: Int): Charset = when (mib) {
        106 -> Charsets.UTF_8
        4 -> Charsets.ISO_8859_1
        3 -> Charsets.US_ASCII
        1000 -> Charsets.UTF_16BE
        1015 -> Charsets.UTF_16
        else -> Charsets.UTF_8
    }

    /** "+15551234567/TYPE=PLMN" → "+15551234567". */
    fun cleanAddress(raw: String): String = raw.substringBefore("/TYPE=").trim()

    /** Returns the notification, or null when [pdu] is not a well-formed m-notification-ind. */
    fun parse(pdu: ByteArray): MmsNotificationInd? = runCatching {
        val r = Reader(pdu)
        var type = -1
        var tid: String? = null
        var from: String? = null
        var subject: String? = null
        var location: String? = null
        var size = 0L
        var expiry = 0L
        var relative = false
        var version = 0
        while (r.hasMore()) {
            val header = r.u8()
            if (header and 0x80 == 0) {
                // Application header (token text name + value); skip both.
                r.pos--
                r.textBytes()
                if (r.hasMore()) r.skipValue()
                continue
            }
            when (header) {
                H_MESSAGE_TYPE -> type = r.u8()
                H_TRANSACTION_ID -> tid = r.text()
                H_MMS_VERSION -> version = r.u8() and 0x7F
                H_FROM -> {
                    val len = r.valueLength()
                    val end = r.pos + len
                    if (r.u8() == 0x80) from = cleanAddress(r.encodedString())
                    r.pos = end
                }
                H_SUBJECT -> subject = r.encodedString()
                H_CONTENT_LOCATION -> location = r.text()
                H_MESSAGE_SIZE -> size = r.longInteger()
                H_EXPIRY -> {
                    val len = r.valueLength()
                    val end = r.pos + len
                    relative = r.u8() == 0x81
                    expiry = r.integerValue()
                    r.pos = end
                }
                else -> r.skipValue()
            }
        }
        if (type != MESSAGE_TYPE_NOTIFICATION_IND || tid == null || location == null) null
        else MmsNotificationInd(tid!!, from, subject, location!!, size, expiry, relative, version)
    }.getOrNull()
}
