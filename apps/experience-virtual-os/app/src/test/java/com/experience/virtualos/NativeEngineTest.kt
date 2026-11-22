package com.experience.virtualos

import com.experience.virtualos.core.engine.NativeEngine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class NativeEngineTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun bundleUsesSharedRootfsAndPersistentHome() {
        val rootfs = tmp.newFolder("rootfs")
        val home = File(tmp.root, "home")
        val bundle = File(tmp.root, "bundle")
        NativeEngine(tmp.newFolder("lib"), tmp.newFolder("engine"), tmp.newFolder("tmp"))
            .writeBundle(bundle, rootfs, home, "evos-test", listOf("/bin/sh", "-c", "true"))
        assertTrue(home.isDirectory)
        val c = Json.parseToJsonElement(File(bundle, "config.json").readText()).jsonObject
        assertEquals(rootfs.canonicalPath, c["root"]!!.jsonObject["path"]!!.jsonPrimitive.content)
        val mounts = c["mounts"]!!.jsonArray.map { it.jsonObject }
        val rootHome = mounts.single { it["destination"]!!.jsonPrimitive.content == "/root" }
        assertEquals("bind", rootHome["type"]!!.jsonPrimitive.content)
        assertEquals(home.canonicalPath, rootHome["source"]!!.jsonPrimitive.content)
        // No namespaces: Android app processes cannot create them.
        assertTrue(c["linux"]!!.jsonObject["namespaces"]!!.jsonArray.isEmpty())
        val caps = c["process"]!!.jsonObject["capabilities"]!!.jsonObject["effective"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertTrue("CAP_DAC_OVERRIDE" in caps)
        assertFalse("CAP_SYS_ADMIN" in caps)

        // Optional: hand the generated bundle to a real runsc (see engine harness).
        System.getenv("EVOS_ENGINE_BUNDLE_OUT")?.takeIf { it.isNotBlank() }?.let { out ->
            val real = System.getenv("EVOS_ENGINE_ROOTFS")!!
            NativeEngine(tmp.newFolder("lib2"), tmp.newFolder("engine2"), tmp.newFolder("tmp2"))
                .writeBundle(File(out), File(real), File(out, "../home"), "evos-test",
                    listOf("/bin/sh", "-c", "echo APP_BUNDLE_OK; id; echo kept > /root/kept.txt; ls /root"))
        }
    }

    @Test fun reportsMissingBinaries() {
        val engine = NativeEngine(tmp.newFolder("lib"), tmp.newFolder("engine"), tmp.newFolder("tmp"))
        assertFalse(engine.available)
        assertTrue(engine.check().first().contains("not included"))
    }
}
