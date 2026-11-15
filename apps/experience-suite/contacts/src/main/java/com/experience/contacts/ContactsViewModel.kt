package com.experience.contacts

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.experience.contacts.data.ContactDetails
import com.experience.contacts.data.ContactItem
import com.experience.contacts.data.ContactsRepository
import com.experience.contacts.data.changes
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class ContactsViewModel(app: Application) : AndroidViewModel(app) {
    val repo = ContactsRepository(app)
    private val refreshTick = MutableStateFlow(0)
    val query = MutableStateFlow("")

    private fun canRead() = getApplication<Application>().checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    val contacts: StateFlow<List<ContactItem>> = refreshTick.flatMapLatest {
        if (!canRead()) flowOf(emptyList())
        else app.contentResolver.changes(ContactsContract.Contacts.CONTENT_URI).map { repo.list() }.flowOn(Dispatchers.IO)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Contacts filtered by [query] (name, number or email through the provider's filter URIs). */
    val visible: StateFlow<List<ContactItem>> = combine(contacts, query.debounce(150)) { all, q -> all to q }
        .mapLatest { (all, q) ->
            if (q.isBlank()) all else {
                val ids = withContext(Dispatchers.IO) { repo.search(q) }
                all.filter { it.id in ids }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun refresh() { refreshTick.value++ }

    fun details(lookupUri: Uri): Flow<ContactDetails?> =
        getApplication<Application>().contentResolver.changes(ContactsContract.Contacts.CONTENT_URI)
            .map { repo.details(lookupUri) }.flowOn(Dispatchers.IO)

    fun setStarred(id: Long, starred: Boolean) = viewModelScope.launch(Dispatchers.IO) { repo.setStarred(id, starred) }

    fun delete(lookupUri: Uri, done: (Boolean) -> Unit) = viewModelScope.launch {
        val ok = withContext(Dispatchers.IO) { repo.delete(lookupUri) }
        done(ok)
    }

    /** Exports [lookupKeys] (all contacts when null) as vCards into the document [dest]. */
    fun export(dest: Uri, lookupKeys: List<String>?, done: (Int) -> Unit) = viewModelScope.launch {
        val count = withContext(Dispatchers.IO) {
            runCatching {
                val keys = lookupKeys ?: repo.list().map { it.lookupKey }
                getApplication<Application>().contentResolver.openOutputStream(dest, "w")?.use { repo.exportVCards(keys, it) } ?: 0
            }.getOrDefault(-1)
        }
        done(count)
    }
}
