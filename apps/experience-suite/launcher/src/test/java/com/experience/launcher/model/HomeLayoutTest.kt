package com.experience.launcher.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLayoutTest {
    private fun k(n: String, user: Long = 0) = AppKey("pkg.$n", "pkg.$n.Main", user)

    @Test fun pinIsIdempotent() {
        val l = HomeLayout().pinToHome(k("a")).pinToHome(k("a"))
        assertEquals(listOf(k("a")), l.home)
    }

    @Test fun dockRespectsCapacity() {
        var l = HomeLayout()
        repeat(HomeLayout.DOCK_CAPACITY + 2) { l = l.pinToDock(k("$it")) }
        assertEquals(HomeLayout.DOCK_CAPACITY, l.dock.size)
        assertTrue(l.dockIsFull())
    }

    @Test fun moveClampsAtEdges() {
        val l = HomeLayout(home = listOf(k("a"), k("b"), k("c")))
        assertEquals(listOf(k("b"), k("a"), k("c")), l.moveOnHome(k("a"), 1).home)
        assertEquals(listOf(k("c"), k("a"), k("b")), l.moveOnHome(k("c"), -5).home)
        assertEquals(l, l.moveOnHome(k("zzz"), 1))
    }

    @Test fun removePackageOnlyAffectsThatProfile() {
        val l = HomeLayout(home = listOf(k("a", 0), k("a", 10), k("b")), dock = listOf(k("a", 0)))
            .removePackage("pkg.a", 0)
        assertEquals(listOf(k("a", 10), k("b")), l.home)
        assertTrue(l.dock.isEmpty())
    }

    @Test fun removeProfile() {
        val l = HomeLayout(home = listOf(k("a", 0), k("a", 10)), dock = listOf(k("b", 10))).removeProfile(10)
        assertEquals(listOf(k("a", 0)), l.home)
        assertFalse(l.isInDock(k("b", 10)))
    }

    @Test fun listEncodingRoundTripsAndSkipsGarbage() {
        val keys = listOf(k("a"), k("b", 11))
        assertEquals(keys, HomeLayout.decodeList(HomeLayout.encodeList(keys)))
        assertEquals(keys, HomeLayout.decodeList(HomeLayout.encodeList(keys) + "\nnot a key\n\n"))
        assertTrue(HomeLayout.decodeList(null).isEmpty())
    }
}
