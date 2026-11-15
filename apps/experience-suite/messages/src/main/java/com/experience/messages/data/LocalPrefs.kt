package com.experience.messages.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App-local conversation state that the Telephony provider has no column for:
 * pinned and archived thread ids, and the delivery-report preference.
 */
class LocalPrefs private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("conversations", Context.MODE_PRIVATE)

    private val _pinned = MutableStateFlow(read(KEY_PINNED))
    val pinned: StateFlow<Set<Long>> = _pinned.asStateFlow()
    private val _archived = MutableStateFlow(read(KEY_ARCHIVED))
    val archived: StateFlow<Set<Long>> = _archived.asStateFlow()
    private val _deliveryReports = MutableStateFlow(prefs.getBoolean(KEY_DELIVERY, false))
    val deliveryReports: StateFlow<Boolean> = _deliveryReports.asStateFlow()

    private fun read(key: String): Set<Long> = prefs.getStringSet(key, emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet()

    private fun write(key: String, value: Set<Long>, flow: MutableStateFlow<Set<Long>>) {
        prefs.edit().putStringSet(key, value.map { it.toString() }.toSet()).apply()
        flow.value = value
    }

    fun setPinned(threadId: Long, pinned: Boolean) =
        write(KEY_PINNED, if (pinned) _pinned.value + threadId else _pinned.value - threadId, _pinned)

    fun setArchived(threadId: Long, archived: Boolean) =
        write(KEY_ARCHIVED, if (archived) _archived.value + threadId else _archived.value - threadId, _archived)

    fun setDeliveryReports(on: Boolean) {
        prefs.edit().putBoolean(KEY_DELIVERY, on).apply()
        _deliveryReports.value = on
    }

    /** Called when a thread is deleted so stale ids do not accumulate. */
    fun forget(threadId: Long) {
        setPinned(threadId, false)
        setArchived(threadId, false)
    }

    companion object {
        private const val KEY_PINNED = "pinned"
        private const val KEY_ARCHIVED = "archived"
        private const val KEY_DELIVERY = "delivery_reports"

        @Volatile private var instance: LocalPrefs? = null
        fun get(context: Context): LocalPrefs = instance ?: synchronized(this) { instance ?: LocalPrefs(context).also { instance = it } }
    }
}
