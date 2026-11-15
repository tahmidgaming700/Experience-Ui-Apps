package com.experience.messages.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.Telephony
import android.provider.Telephony.Mms
import android.provider.Telephony.Sms

/** Telephony provider access: threads, SMS and (read-only) MMS. */
class MessagesRepository(private val context: Context) {
    private val resolver get() = context.contentResolver

    private fun canonicalAddresses(): Map<Long, String> {
        val out = HashMap<Long, String>()
        runCatching {
            resolver.query(CANONICAL_ADDRESSES, arrayOf("_id", "address"), null, null, null)?.use { c ->
                while (c.moveToNext()) out[c.getLong(0)] = c.getString(1).orEmpty()
            }
        }
        return out
    }

    fun conversations(): List<Conversation> {
        val addresses = canonicalAddresses()
        val out = ArrayList<Conversation>()
        val projection = arrayOf(
            Telephony.Threads._ID, Telephony.Threads.DATE, Telephony.Threads.MESSAGE_COUNT, Telephony.Threads.RECIPIENT_IDS,
            Telephony.Threads.SNIPPET, Telephony.Threads.READ, Telephony.Threads.ERROR,
        )
        runCatching {
            resolver.query(SIMPLE_CONVERSATIONS, projection, null, null, "${Telephony.Threads.DATE} DESC")?.use { c ->
                while (c.moveToNext()) {
                    val count = c.getInt(2)
                    if (count == 0) continue
                    val recipients = c.getString(3).orEmpty().split(' ').mapNotNull { it.toLongOrNull()?.let(addresses::get) }
                    out += Conversation(
                        threadId = c.getLong(0),
                        addresses = recipients,
                        snippet = c.getString(4).orEmpty(),
                        dateMillis = c.getLong(1),
                        unread = c.getInt(5) == 0,
                        hasError = c.getInt(6) != 0,
                        messageCount = count,
                    )
                }
            }
        }
        return out
    }

    /** Thread ids whose SMS bodies contain [query]. */
    fun searchBodies(query: String): Set<Long> {
        if (query.isBlank()) return emptySet()
        val out = HashSet<Long>()
        runCatching {
            resolver.query(Sms.CONTENT_URI, arrayOf(Sms.THREAD_ID), "${Sms.BODY} LIKE ?", arrayOf("%${query.replace("%", "")}%"), null)?.use { c ->
                while (c.moveToNext()) out += c.getLong(0)
            }
        }
        return out
    }

    fun messages(threadId: Long): List<Message> {
        val out = ArrayList<Message>()
        val smsProjection = arrayOf(Sms._ID, Sms.ADDRESS, Sms.BODY, Sms.DATE, Sms.TYPE, Sms.STATUS, Sms.SUBSCRIPTION_ID)
        runCatching {
            resolver.query(Sms.CONTENT_URI, smsProjection, "${Sms.THREAD_ID} = ?", arrayOf(threadId.toString()), "${Sms.DATE} ASC")?.use { c ->
                while (c.moveToNext()) {
                    val type = c.getInt(4)
                    val status = c.getInt(5)
                    out += Message(
                        id = c.getLong(0), isMms = false,
                        incoming = type == Sms.MESSAGE_TYPE_INBOX,
                        address = c.getString(1), body = c.getString(2).orEmpty(), dateMillis = c.getLong(3),
                        status = smsStatus(type, status), subId = c.getInt(6),
                    )
                }
            }
        }
        val mmsProjection = arrayOf(Mms._ID, Mms.DATE, Mms.MESSAGE_BOX, Mms.MESSAGE_TYPE, Mms.SUBSCRIPTION_ID, Mms.MESSAGE_SIZE, Mms.SUBJECT)
        runCatching {
            resolver.query(Mms.CONTENT_URI, mmsProjection, "${Mms.THREAD_ID} = ?", arrayOf(threadId.toString()), null)?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val box = c.getInt(2)
                    val notInd = c.getInt(3) == MESSAGE_TYPE_NOTIFICATION_IND
                    val parts = if (notInd) emptyList() else mmsParts(id)
                    val subject = c.getString(6)
                    val text = (listOfNotNull(subject?.takeIf { it.isNotBlank() }) + parts.filterIsInstance<MmsPart.Text>().map { it.text }).joinToString("\n")
                    out += Message(
                        id = id, isMms = true,
                        incoming = box == Mms.MESSAGE_BOX_INBOX,
                        address = mmsSender(id), body = text,
                        dateMillis = c.getLong(1) * 1000L, // MMS dates are in seconds
                        status = when (box) {
                            Mms.MESSAGE_BOX_INBOX -> MessageStatus.RECEIVED
                            Mms.MESSAGE_BOX_OUTBOX -> MessageStatus.SENDING
                            Mms.MESSAGE_BOX_FAILED -> MessageStatus.FAILED
                            else -> MessageStatus.SENT
                        },
                        subId = c.getInt(4),
                        parts = parts.filter { it !is MmsPart.Text },
                        mmsNotDownloaded = notInd,
                        mmsSize = c.getLong(5),
                    )
                }
            }
        }
        return out.sortedBy { it.dateMillis }
    }

    private fun mmsParts(mmsId: Long): List<MmsPart> {
        val out = ArrayList<MmsPart>()
        runCatching {
            resolver.query(MMS_PART, arrayOf(Mms.Part._ID, Mms.Part.CONTENT_TYPE, Mms.Part.TEXT, Mms.Part.NAME, Mms.Part.CONTENT_LOCATION),
                "${Mms.Part.MSG_ID} = ?", arrayOf(mmsId.toString()), null)?.use { c ->
                while (c.moveToNext()) {
                    val partId = c.getLong(0)
                    val type = c.getString(1).orEmpty()
                    when {
                        type == "application/smil" -> Unit
                        type.startsWith("text/") -> c.getString(2)?.takeIf { it.isNotBlank() }?.let { out += MmsPart.Text(it) }
                        type.startsWith("image/") -> out += MmsPart.Image("content://mms/part/$partId", type)
                        else -> out += MmsPart.Other(c.getString(3) ?: c.getString(4) ?: "Attachment", type)
                    }
                }
            }
        }
        return out
    }

    /** Sender (PduHeaders.FROM = 137) of an MMS. */
    private fun mmsSender(mmsId: Long): String? = runCatching {
        resolver.query(Uri.parse("content://mms/$mmsId/addr"), arrayOf(Mms.Addr.ADDRESS, Mms.Addr.TYPE), "${Mms.Addr.TYPE} = 137", null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0)?.takeIf { it != "insert-address-token" } else null
        }
    }.getOrNull()

    fun markRead(threadId: Long) {
        val values = ContentValues().apply { put(Sms.READ, 1); put(Sms.SEEN, 1) }
        runCatching { resolver.update(Sms.CONTENT_URI, values, "${Sms.THREAD_ID} = ? AND (${Sms.READ} = 0 OR ${Sms.SEEN} = 0)", arrayOf(threadId.toString())) }
        val mmsValues = ContentValues().apply { put(Mms.READ, 1); put(Mms.SEEN, 1) }
        runCatching { resolver.update(Mms.CONTENT_URI, mmsValues, "${Mms.THREAD_ID} = ? AND (${Mms.READ} = 0 OR ${Mms.SEEN} = 0)", arrayOf(threadId.toString())) }
    }

    fun deleteThread(threadId: Long): Boolean =
        runCatching { resolver.delete(Uri.withAppendedPath(Telephony.Threads.CONTENT_URI, threadId.toString()), null, null) >= 0 }.getOrDefault(false)

    fun deleteMessage(m: Message): Boolean = runCatching {
        resolver.delete(Uri.withAppendedPath(if (m.isMms) Mms.CONTENT_URI else Sms.CONTENT_URI, m.id.toString()), null, null) > 0
    }.getOrDefault(false)

    fun threadIdFor(addresses: List<String>): Long = Telephony.Threads.getOrCreateThreadId(context, addresses.toSet())

    /** Recipient addresses of an existing thread. */
    fun addressesOf(threadId: Long): List<String> = conversations().firstOrNull { it.threadId == threadId }?.addresses.orEmpty()

    companion object {
        val SIMPLE_CONVERSATIONS: Uri get() = Uri.parse("content://mms-sms/conversations?simple=true")
        val CANONICAL_ADDRESSES: Uri get() = Uri.parse("content://mms-sms/canonical-addresses")
        val MMS_PART: Uri get() = Uri.parse("content://mms/part")
        /** PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND. */
        const val MESSAGE_TYPE_NOTIFICATION_IND = 130

        fun smsStatus(type: Int, status: Int): MessageStatus = when (type) {
            Sms.MESSAGE_TYPE_INBOX -> MessageStatus.RECEIVED
            Sms.MESSAGE_TYPE_OUTBOX, Sms.MESSAGE_TYPE_QUEUED -> MessageStatus.SENDING
            Sms.MESSAGE_TYPE_FAILED -> MessageStatus.FAILED
            Sms.MESSAGE_TYPE_SENT -> when {
                status == Sms.STATUS_COMPLETE -> MessageStatus.DELIVERED
                status >= Sms.STATUS_FAILED -> MessageStatus.NOT_DELIVERED
                else -> MessageStatus.SENT
            }
            else -> MessageStatus.SENT
        }
    }
}
