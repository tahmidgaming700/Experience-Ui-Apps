package com.experience.launcher.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppKeyTest {
    @Test fun roundTrip() {
        val key = AppKey("com.example", "com.example.MainActivity", 10)
        assertEquals(key, AppKey.decode(key.encode()))
    }

    @Test fun innerClassWithDollarAndHash() {
        val key = AppKey("a.b", "a.b.Outer\$Inner", 0)
        assertEquals(key, AppKey.decode(key.encode()))
    }

    @Test fun rejectsMalformed() {
        assertNull(AppKey.decode(""))
        assertNull(AppKey.decode("com.example"))
        assertNull(AppKey.decode("com.example/Main"))
        assertNull(AppKey.decode("com.example/Main#x"))
        assertNull(AppKey.decode("/Main#0"))
        assertNull(AppKey.decode("pkg/#0"))
    }
}
