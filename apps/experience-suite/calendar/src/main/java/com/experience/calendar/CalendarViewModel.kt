package com.experience.calendar

import android.app.Application
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.experience.calendar.data.CalendarInfo
import com.experience.calendar.data.CalendarRepository
import com.experience.calendar.data.EventDetails
import com.experience.calendar.data.EventInstance
import com.experience.calendar.logic.EventDays
import com.experience.calendar.logic.EventDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

enum class ViewMode { MONTH, AGENDA, DAY }

/** What the detail sheet shows: an event plus the occurrence that was tapped. */
data class OpenEvent(val details: EventDetails, val instanceBegin: Long?, val instanceEnd: Long?)

class CalendarViewModel(app: Application) : AndroidViewModel(app) {
    val repo = CalendarRepository(app)

    var mode by mutableStateOf(ViewMode.MONTH)
    var selectedDate by mutableStateOf(LocalDate.now())
    var calendars by mutableStateOf<List<CalendarInfo>>(emptyList())
        private set
    /** Bumped whenever the provider reports a change; screens re-query on it. */
    var dataVersion by mutableIntStateOf(0)
        private set
    var openEvent by mutableStateOf<OpenEvent?>(null)
    var editing by mutableStateOf<EventDraft?>(null)
    var showCalendars by mutableStateOf(false)
    var error by mutableStateOf<Int?>(null)

    val zone: ZoneId get() = ZoneId.systemDefault()

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { refresh() }
    }
    private var observing = false

    /** Called once permissions are granted. */
    fun start() {
        if (!observing) {
            runCatching { getApplication<Application>().contentResolver.registerContentObserver(CalendarContract.CONTENT_URI, true, observer) }
            observing = true
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            calendars = withContext(Dispatchers.IO) { runCatching { repo.calendars() }.getOrDefault(emptyList()) }
            dataVersion++
        }
    }

    suspend fun instances(from: LocalDate, toExclusive: LocalDate): List<EventInstance> = withContext(Dispatchers.IO) {
        val (s, _) = EventDays.dayBounds(from, zone)
        val (e, _) = EventDays.dayBounds(toExclusive, zone)
        // Widen by a day each side so all-day (UTC) events near the edges are included.
        runCatching { repo.instances(s - 86_400_000L, e + 86_400_000L) }.getOrDefault(emptyList())
    }

    fun setVisible(cal: CalendarInfo, visible: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { repo.setVisible(cal.id, visible); withContext(Dispatchers.Main) { refresh() } }
    }

    fun open(eventId: Long, begin: Long? = null, end: Long? = null, onMissing: () -> Unit = {}) {
        viewModelScope.launch {
            val d = withContext(Dispatchers.IO) { runCatching { repo.event(eventId) }.getOrNull() }
            if (d == null) onMissing() else openEvent = OpenEvent(d, begin, end)
        }
    }

    /** Starts the editor for a new event; creates the local calendar if needed. */
    fun newEvent(base: EventDraft = EventDraft.newAt(selectedDate, zone)) {
        viewModelScope.launch {
            val calId = withContext(Dispatchers.IO) {
                runCatching { repo.ensureWritableCalendar(getApplication<Application>().getString(R.string.local_calendar_name)) }.getOrNull()
            }
            refresh()
            if (calId == null) { error = R.string.error_no_calendar; return@launch }
            val chosen = if (base.calendarId != 0L && calendars.any { it.id == base.calendarId && it.writable }) base.calendarId
            else calendars.firstOrNull { it.writable && it.visible }?.id ?: calId
            editing = base.copy(calendarId = chosen)
        }
    }

    fun edit(eventId: Long, overrides: (EventDraft) -> EventDraft = { it }) {
        viewModelScope.launch {
            val d = withContext(Dispatchers.IO) { runCatching { repo.event(eventId) }.getOrNull() }
            if (d == null || !d.canModify) { error = R.string.error_cannot_edit; return@launch }
            openEvent = null
            editing = overrides(d.draft)
        }
    }

    fun save(draft: EventDraft, onDone: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = withContext(Dispatchers.IO) { runCatching { repo.save(draft) }.getOrNull() }
            if (id == null) { error = R.string.error_save; return@launch }
            editing = null
            selectedDate = draft.startDate
            refresh()
            onDone(id)
        }
    }

    fun delete(eventId: Long, occurrenceBegin: Long?) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                if (occurrenceBegin != null) repo.deleteOccurrence(eventId, occurrenceBegin) else repo.delete(eventId)
            }
            withContext(Dispatchers.Main) { openEvent = null; refresh() }
        }
    }

    override fun onCleared() {
        if (observing) getApplication<Application>().contentResolver.unregisterContentObserver(observer)
    }
}
