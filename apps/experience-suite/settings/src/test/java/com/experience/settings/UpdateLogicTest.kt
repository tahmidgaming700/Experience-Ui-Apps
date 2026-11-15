package com.experience.settings

import com.experience.settings.update.ManifestException
import com.experience.settings.update.UpdateLogic
import com.experience.settings.update.UpdateStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** org.json is part of the Android framework, so these run under Robolectric. */
@RunWith(RobolectricTestRunner::class)
class UpdateLogicTest {
    private val sha = "a".repeat(64)

    private fun manifest(stableUrl: String? = "https://example.org/eui-20250301.img.xz", betaUrl: String? = null, sdk: Int = 30, schema: Int = 1, stableBuild: String = "20250301.1") = """
        {"schema_version":$schema,"product":"Experience UI","android_version":"11","sdk":$sdk,
         "channels":{
           "stable":{"version":"1.0.0","build_number":"$stableBuild","display":"Experience UI 1.0","date":"2025-03-01",
                     "variant":"arm64_bvN","url":${stableUrl?.let { "\"$it\"" } ?: "null"},"size":1234,"sha256":"${sha.uppercase()}",
                     "changelog":"First stable","boot_tested":true},
           "beta":{"version":"1.1.0","build_number":"20250410.0","display":"Experience UI 1.1 Beta","date":"2025-04-10",
                   "variant":"arm64_bvN","url":${betaUrl?.let { "\"$it\"" } ?: "null"},"size":0,"sha256":null,
                   "changelog":"","boot_tested":false}
         }}
    """.trimIndent()

    @Test fun parsesManifest() {
        val m = UpdateLogic.parse(manifest())
        assertEquals(1, m.schemaVersion)
        assertEquals("Experience UI", m.product)
        assertEquals(30, m.sdk)
        assertEquals(setOf("stable", "beta"), m.channels.keys)
        val s = m.channels.getValue("stable")
        assertEquals("20250301.1", s.buildNumber)
        assertEquals(sha, s.sha256) // normalised to lower case
        assertEquals("eui-20250301.img.xz", s.fileName)
        assertTrue(s.bootTested)
        val b = m.channels.getValue("beta")
        assertNull(b.url)
        assertNull(b.sha256)
        assertFalse(b.bootTested)
    }

    @Test fun buildNumbersCompareLexically() {
        assertTrue(UpdateLogic.isNewer("20250302.0", "20250301.9"))
        assertTrue(UpdateLogic.isNewer("20250301.2", "20250301.1"))
        assertFalse(UpdateLogic.isNewer("20250301.1", "20250301.1"))
        assertFalse(UpdateLogic.isNewer("20240101.0", "20250101.0"))
        assertTrue(UpdateLogic.isNewer("20250101.0", "0")) // unknown installed build
    }

    @Test fun evaluateStates() {
        val m = UpdateLogic.parse(manifest())
        assertTrue(UpdateLogic.evaluate(m, "stable", "20250201.0", 30) is UpdateStatus.Available)
        assertTrue(UpdateLogic.evaluate(m, "stable", "20250301.1", 30) is UpdateStatus.UpToDate)
        assertTrue(UpdateLogic.evaluate(m, "stable", "20250401.0", 30) is UpdateStatus.UpToDate)
        // url null -> no build published, even if the number is newer.
        assertTrue(UpdateLogic.evaluate(m, "beta", "20250201.0", 30) is UpdateStatus.NotPublished)
        assertTrue(UpdateLogic.evaluate(m, "nightly", "1", 30) is UpdateStatus.Error)
        val wrongSdk = UpdateLogic.parse(manifest(sdk = 31))
        assertTrue(UpdateLogic.evaluate(wrongSdk, "stable", "1", 30) is UpdateStatus.Error)
    }

    @Test fun rejectsBadManifests() {
        fun bad(json: String, fragment: String) {
            try { UpdateLogic.parse(json); fail("accepted: $json") } catch (e: ManifestException) {
                assertTrue("${e.message} should mention $fragment", e.message!!.contains(fragment))
            }
        }
        bad("not json", "JSON")
        bad(manifest(schema = 2), "schema")
        bad("""{"schema_version":1}""", "channels")
        bad(manifest(stableUrl = "http://insecure.example/x.img.xz"), "HTTPS")
        bad(manifest().replace("\"${sha.uppercase()}\"", "\"abc\""), "SHA-256")
        bad(manifest().replace("\"build_number\":\"20250301.1\",", ""), "build_number")
    }

    @Test fun installStepsUseFastbootd() {
        val steps = UpdateLogic.installSteps("eui.img.xz")
        assertTrue(steps.any { it.contains("xz -d eui.img.xz") })
        assertTrue(steps.any { it.contains("adb reboot fastboot") })
        assertTrue(steps.any { it.contains("fastboot flash system eui.img") })
        assertTrue(steps.any { it.contains("fastboot -w") })
        assertFalse(UpdateLogic.installSteps("eui.img").any { it.contains("xz -d") })
    }

    @Test fun hexEncoding() {
        assertEquals("00ff10", UpdateLogic.hex(byteArrayOf(0, -1, 16)))
    }
}
