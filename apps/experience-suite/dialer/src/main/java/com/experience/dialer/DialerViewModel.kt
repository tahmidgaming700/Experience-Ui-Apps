package com.experience.dialer

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.pm.PackageManager
import android.provider.CallLog
import android.provider.ContactsContract
import android.telecom.TelecomManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.experience.dialer.data.CallGroup
import com.experience.dialer.data.CallLogGrouper
import com.experience.dialer.data.CallLogRepository
import com.experience.dialer.data.DialContact
import com.experience.dialer.data.DialContactsRepository
import com.experience.dialer.data.PhoneFormatter
import com.experience.dialer.data.changes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class DialerViewModel(app: Application) : AndroidViewModel(app) {
    private val callLogRepo = CallLogRepository(app)
    private val contactsRepo = DialContactsRepository(app)
    val formatter = PhoneFormatter(app)

    /** Bumped after permissions are granted so the data flows re-subscribe. */
    private val refreshTick = MutableStateFlow(0)

    /** Keypad input, kept here so it survives tab switches and rotation. */
    val dialInput = MutableStateFlow("")

    private fun granted(p: String) = getApplication<Application>().checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    val contacts: StateFlow<List<DialContact>> = refreshTick.flatMapLatest {
        if (!granted(Manifest.permission.READ_CONTACTS)) flowOf(emptyList())
        else app.contentResolver.changes(ContactsContract.Contacts.CONTENT_URI).map { contactsRepo.loadPhones() }.flowOn(Dispatchers.IO)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val callLog: StateFlow<List<CallGroup>> = refreshTick.flatMapLatest {
        if (!granted(Manifest.permission.READ_CALL_LOG)) flowOf(emptyList())
        else app.contentResolver.changes(CallLog.Calls.CONTENT_URI).map { CallLogGrouper.group(callLogRepo.load()) }.flowOn(Dispatchers.IO)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun refresh() { refreshTick.value++ }

    fun deleteCalls(ids: Collection<Long>) = viewModelScope.launch(Dispatchers.IO) { callLogRepo.delete(ids) }

    fun clearCallLog() = viewModelScope.launch(Dispatchers.IO) { callLogRepo.clearAll() }

    /** Called when Recents is shown: marks missed calls read and clears Telecom's missed-call notification. */
    @SuppressLint("MissingPermission")
    fun markMissedRead() = viewModelScope.launch(Dispatchers.IO) {
        if (!granted(Manifest.permission.WRITE_CALL_LOG)) return@launch
        callLogRepo.markMissedRead()
        // Allowed for the default dialer (or with MODIFY_PHONE_STATE).
        runCatching { getApplication<Application>().getSystemService(TelecomManager::class.java)?.cancelMissedCallsNotification() }
    }
}
