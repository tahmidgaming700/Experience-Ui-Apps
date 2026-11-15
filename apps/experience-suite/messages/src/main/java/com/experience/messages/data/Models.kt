package com.experience.messages.data

/** A conversation thread from the Telephony provider (plus local pin/archive state). */
data class Conversation(
    val threadId: Long,
    val addresses: List<String>,
    val snippet: String,
    val dateMillis: Long,
    val unread: Boolean,
    val hasError: Boolean,
    val messageCount: Int,
    val pinned: Boolean = false,
    val archived: Boolean = false,
)

enum class MessageStatus(val label: String) { RECEIVED(""), SENDING("Sending…"), SENT("Sent"), DELIVERED("Delivered"), NOT_DELIVERED("Not delivered"), FAILED("Not sent") }

sealed interface MmsPart {
    data class Text(val text: String) : MmsPart
    data class Image(val uri: String, val contentType: String) : MmsPart
    data class Other(val name: String, val contentType: String) : MmsPart
}

/** One SMS or MMS in a thread. */
data class Message(
    val id: Long,
    val isMms: Boolean,
    val incoming: Boolean,
    val address: String?,
    val body: String,
    val dateMillis: Long,
    val status: MessageStatus,
    val subId: Int,
    val parts: List<MmsPart> = emptyList(),
    /** MMS notification-ind that has not been downloaded. */
    val mmsNotDownloaded: Boolean = false,
    val mmsSize: Long = 0,
) {
    /** Stable key across SMS/MMS id spaces. */
    val key: String get() = (if (isMms) "m" else "s") + id
}

/** Visual grouping of chat bubbles and date separators. */
object BubbleLayout {
    data class Position(val firstInGroup: Boolean, val lastInGroup: Boolean, val dateSeparator: Boolean)

    const val GROUP_WINDOW_MS = 3 * 60 * 1000L

    /**
     * For [messages] in chronological order: a group is a run of messages in the same
     * direction (and same sender for group chats) less than [GROUP_WINDOW_MS] apart;
     * a date separator precedes the first message of each local day.
     */
    fun positions(messages: List<Message>, dayOf: (Long) -> Long): List<Position> = messages.mapIndexed { i, m ->
        val prev = messages.getOrNull(i - 1)
        val next = messages.getOrNull(i + 1)
        val newDay = prev == null || dayOf(prev.dateMillis) != dayOf(m.dateMillis)
        val joinPrev = prev != null && !newDay && sameSender(prev, m) && m.dateMillis - prev.dateMillis < GROUP_WINDOW_MS
        val joinNext = next != null && dayOf(next.dateMillis) == dayOf(m.dateMillis) && sameSender(m, next) && next.dateMillis - m.dateMillis < GROUP_WINDOW_MS
        Position(firstInGroup = !joinPrev, lastInGroup = !joinNext, dateSeparator = newDay)
    }

    private fun sameSender(a: Message, b: Message) = a.incoming == b.incoming && (!a.incoming || a.address == b.address)
}

/** Recipient parsing for sms:/smsto:/mms:/mmsto: URIs ("smsto:123;456", "sms:+1 555,+1 556?body=hi"). */
object Recipients {
    fun fromSchemeSpecificPart(ssp: String?): List<String> =
        ssp.orEmpty().substringBefore('?').split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    /** "body=Hello%20there" query of an sms: URI, already decoded by Uri.getQueryParameter in practice. */
    fun bodyFromSchemeSpecificPart(ssp: String?): String? =
        ssp?.substringAfter('?', "")?.split('&')?.firstOrNull { it.startsWith("body=") }?.removePrefix("body=")
}
