package com.experience.calculator

import androidx.test.core.app.ApplicationProvider
import com.experience.calculator.data.HistoryEntry
import com.experience.calculator.data.HistoryStore
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HistoryStoreTest {
    @Test fun persistsNewestFirstAndDeduplicates() {
        val store = HistoryStore(ApplicationProvider.getApplicationContext<android.content.Context>())
        store.add(HistoryEntry("1+1", "2", 1))
        store.add(HistoryEntry("2×3", "6", 2))
        store.add(HistoryEntry("1+1", "2", 3))
        val loaded = HistoryStore(ApplicationProvider.getApplicationContext<android.content.Context>()).load()
        assertEquals(listOf("1+1", "2×3"), loaded.map { it.expression })
        store.clear()
        assertEquals(0, store.load().size)
    }

    @Test fun capsSize() {
        val store = HistoryStore(ApplicationProvider.getApplicationContext<android.content.Context>())
        repeat(150) { store.add(HistoryEntry("$it+0", "$it", it.toLong())) }
        assertEquals(HistoryStore.MAX_ENTRIES, store.load().size)
    }
}
