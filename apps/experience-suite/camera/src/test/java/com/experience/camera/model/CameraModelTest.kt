package com.experience.camera.model

import android.provider.MediaStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class CameraModelTest {
    @Test fun parsesLaunchRequests() {
        assertEquals(LaunchRequest.Standalone(CaptureMode.PHOTO), LaunchRequest.parse(null, null, 0, 0, 1))
        assertEquals(LaunchRequest.Standalone(CaptureMode.PHOTO), LaunchRequest.parse(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA, null, 0, 0, 1))
        assertEquals(LaunchRequest.Standalone(CaptureMode.VIDEO), LaunchRequest.parse(MediaStore.INTENT_ACTION_VIDEO_CAMERA, null, 0, 0, 1))
        val image = LaunchRequest.parse(MediaStore.ACTION_IMAGE_CAPTURE, null, 0, 0, 1)
        assertTrue(image is LaunchRequest.ImageCapture && image.isCaptureRequest)
        val video = LaunchRequest.parse(MediaStore.ACTION_VIDEO_CAPTURE, null, 30, 1_000_000, 0) as LaunchRequest.VideoCapture
        assertEquals(30_000L, video.durationLimitMillis)
        assertEquals(1_000_000L, video.sizeLimitBytes)
        assertTrue(video.lowQuality)
        assertEquals(CaptureMode.VIDEO, video.initialMode)
        assertNull(video.output)
        assertFalse(LaunchRequest.Standalone(CaptureMode.PHOTO).isCaptureRequest)
    }

    @Test fun namesAndElapsed() {
        val cal = Calendar.getInstance(TimeZone.getDefault()).apply { set(2026, Calendar.NOVEMBER, 15, 9, 5, 7) }
        assertEquals("IMG_20261115_090507", MediaNaming.photoName(cal.time))
        assertEquals("VID_20261115_090507", MediaNaming.videoName(cal.time))
        assertEquals("0:00", MediaNaming.elapsed(0))
        assertEquals("1:05", MediaNaming.elapsed(65_000_000_000))
        assertEquals("1:01:01", MediaNaming.elapsed(3_661_000_000_000))
    }

    @Test fun zoomPresets() {
        assertEquals(listOf(1f, 2f, 5f), ZoomPresets.presets(1f, 8f))
        assertEquals(listOf(0.6f, 1f, 2f, 5f, 10f), ZoomPresets.presets(0.6f, 10f))
        assertEquals(listOf(1f), ZoomPresets.presets(1f, 1f))
        val p = listOf(0.6f, 1f, 2f, 5f)
        assertEquals(1, ZoomPresets.activeIndex(p, 1.4f))
        assertEquals(0, ZoomPresets.activeIndex(p, 0.6f))
        assertEquals(3, ZoomPresets.activeIndex(p, 7f))
        assertEquals("1.4x", ZoomPresets.label(1.4f))
        assertEquals("2x", ZoomPresets.label(2f))
    }

    @Test fun cyclesSettings() {
        assertEquals(FlashSetting.AUTO, FlashSetting.OFF.next())
        assertEquals(FlashSetting.OFF, FlashSetting.ON.next())
        assertEquals(SelfTimer.OFF, SelfTimer.TEN.next())
    }

    @Test fun deviceRotation() {
        assertNull(DeviceRotation.surfaceRotation(-1))
        assertEquals(0, DeviceRotation.surfaceRotation(10))
        assertEquals(0, DeviceRotation.surfaceRotation(350))
        assertEquals(3, DeviceRotation.surfaceRotation(90))
        assertEquals(2, DeviceRotation.surfaceRotation(180))
        assertEquals(1, DeviceRotation.surfaceRotation(270))
    }
}
