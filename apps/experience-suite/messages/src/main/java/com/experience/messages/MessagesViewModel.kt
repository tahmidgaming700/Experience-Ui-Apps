package com.experience.messages

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Telephony
import android.telephony.SubscriptionManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.experience.messages.data.ContactLookup
import com.experience.messages.data.Conversation
import com.experience.messages.data.LocalPrefs
import com.experience.messages.data.Message
import com.experience.messages.data.MessagesRepository
import com.experience.messages.data.changes
import com.experience.messages.notify.MessageNotifier
import com.experience.messages.sms.SmsSender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** A conversation with its resolved display name and avatar. */
data class ConversationUi(val conversation: Conversation, val title: String, val photoUri: String?) {
    val threadId get() = conversation.threadId
}

data class SimInfo(val subId: Int, val label: String, val slot: Int)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class MessagesViewModel(app: Application) : AndroidViewModel(app) {
    val repo = MessagesRepository(app)
    val prefs = LocalPrefs.get(app)
    private val refreshTick = MutableStateFlow(0)
    val query = MutableStateFlow("")

    private fun granted(p: String) = getApplication<Application>().checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    /** Emits whenever SMS, MMS or thread rows change. */
    private fun providerChanges(): Flow<Unit> {
        val cr = getApplication<Application>().contentResolver
        return merge(cr.changes(Telephony.Sms.CONTENT_URI), cr.changes(Telephony.Mms.CONTENT_URI), cr.changes(Uri.parse("content://mms-sms/")))
            .debounce(100)
    }

    private val all: StateFlow<List<ConversationUi>> = refreshTick.flatMapLatest {
        if (!granted(Manifest.permission.READ_SMS)) flowOf(emptyList())
        else combine(providerChanges().map { repo.conversations() }.flowOn(Dispatchers.IO), prefs.pinned, prefs.archived) { list, pinned, archived ->
            list.map { it.copy(pinned = it.threadId in pinned, archived = it.threadId in archived) }
        }.map { list ->
            val app = getApplication<Application>()
            list.map { c ->
                val info = c.addresses.singleOrNull()?.let { ContactLookup.lookup(app, it) }
                ConversationUi(c, ContactLookup.displayName(app, c.addresses), info?.photoUri)
            }
        }.flowOn(Dispatchers.IO)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val showArchived = MutableStateFlow(false)

    /** Conversations for the list: archived or not, searched, pinned first. */
    val conversations: StateFlow<List<ConversationUi>> =
        combine(all, query.debounce(150), showArchived) { list, q, archived -> Triple(list, q, archived) }
            .mapLatest { (list, q, archived) ->
                val inBox = list.filter { it.conversation.archived == archived }
                val filtered = if (q.isBlank()) inBox else {
                    val needle = q.trim().lowercase(Locale.getDefault())
                    val digits = needle.filter { it.isDigit() }
                    val bodyHits = withContext(Dispatchers.IO) { repo.searchBodies(q.trim()) }
                    inBox.filter { c ->
                        c.title.lowercase(Locale.getDefault()).contains(needle) ||
                            (digits.length >= 3 && c.conversation.addresses.any { a -> a.filter { it.isDigit() }.contains(digits) }) ||
                            c.conversation.snippet.lowercase(Locale.getDefault()).contains(needle) ||
                            c.threadId in bodyHits
                    }
                }
                filtered.sortedWith(compareByDescending<ConversationUi> { it.conversation.pinned }.thenByDescending { it.conversation.dateMillis })
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun refresh() { refreshTick.value++; ContactLookup.invalidate() }

    fun messages(threadId: Long): Flow<List<Message>> = providerChanges().map { repo.messages(threadId) }.flowOn(Dispatchers.IO)

    fun markRead(threadId: Long) = viewModelScope.launch(Dispatchers.IO) {
        repo.markRead(threadId)
        MessageNotifier(getApplication()).cancel(threadId)
    }

    fun delete(threadId: Long) = viewModelScope.launch(Dispatchers.IO) {
        repo.deleteThread(threadId)
        prefs.forget(threadId)
        MessageNotifier(getApplication()).cancel(threadId)
    }

    fun deleteMessage(m: Message) = viewModelScope.launch(Dispatchers.IO) { repo.deleteMessage(m) }

    fun setPinned(threadId: Long, pinned: Boolean) = prefs.setPinned(threadId, pinned)
    fun setArchived(threadId: Long, archived: Boolean) = prefs.setArchived(threadId, archived)

    fun send(addresses: List<String>, text: String, subId: Int, threadId: Long?) = viewModelScope.launch(Dispatchers.IO) {
        SmsSender.send(getApplication(), addresses, text, subId, threadId)
    }

    fun resend(m: Message) = viewModelScope.launch(Dispatchers.IO) {
        SmsSender.resend(getApplication(), Uri.withAppendedPath(Telephony.Sms.CONTENT_URI, m.id.toString()))
    }

    suspend fun threadIdFor(addresses: List<String>): Long? = withContext(Dispatchers.IO) {
        runCatching { repo.threadIdFor(addresses) }.getOrNull()
    }

    suspend fun addressesOf(threadId: Long): List<String> = withContext(Dispatchers.IO) { repo.addressesOf(threadId) }

    /** Active SIMs (READ_PHONE_STATE); empty when unknown. */
    @SuppressLint("MissingPermission")
    fun sims(): List<SimInfo> {
        if (!granted(Manifest.permission.READ_PHONE_STATE)) return emptyList()
        val sm = getApplication<Application>().getSystemService(SubscriptionManager::class.java) ?: return emptyList()
        return runCatching {
            sm.activeSubscriptionInfoList.orEmpty().map { SimInfo(it.subscriptionId, it.displayName?.toString() ?: "SIM ${it.simSlotIndex + 1}", it.simSlotIndex) }
        }.getOrDefault(emptyList())
    }

    fun defaultSmsSubId(): Int = SubscriptionManager.getDefaultSmsSubscriptionId()

    fun isDefaultSmsApp() = SmsSender.isDefaultSmsApp(getApplication())
}
