package com.experience.clock

import androidx.test.core.app.ApplicationProvider
import com.experience.clock.alarm.Alarm
import com.experience.clock.alarm.AlarmStore
import com.experience.clock.timer.Timer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AlarmStoreTest {
    @Test fun crudInDeviceProtectedStorage() {
        val store = AlarmStore(ApplicationProvider.getApplicationContext(), null)
        val a = store.insert(Alarm(hour = 6, minute = 45, days = Alarm.WEEKDAYS, label = "Gym", ringtone = "", vibrate = false, snoozeMinutes = 5))
        val b = store.insert(Alarm(hour = 5, minute = 0, ringtone = null, snoozedUntil = 123L, skipUntil = 456L))
        assertEquals(listOf(b.id, a.id), store.all().map { it.id }) // ordered by time
        assertEquals(a, store.get(a.id))
        assertEquals(b, store.get(b.id))
        store.update(a.copy(enabled = false, label = "Run"))
        assertEquals("Run", store.get(a.id)!!.label)
        store.delete(a.id)
        assertNull(store.get(a.id))
    }

    @Test fun timerJsonRoundTrip() {
        val list = listOf(Timer(1, 1000, "Tea"), Timer(2, 5000).start(1, 2))
        assertEquals(list, Timer.fromJson(Timer.toJson(list)))
        assertEquals(emptyList<Timer>(), Timer.fromJson("not json"))
    }
}
