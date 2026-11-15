package com.experience.camera.model

import android.net.Uri
import android.provider.MediaStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class CaptureMode { PHOTO, VIDEO }

/** Photo flash setting (video uses the torch instead). */
enum class FlashSetting(val label: String) {
    OFF("Flash off"), AUTO("Flash auto"), ON("Flash on");

    fun next(): FlashSetting = entries[(ordinal + 1) % entries.size]
}

enum class SelfTimer(val seconds: Int, val label: String) {
    OFF(0, "Timer off"), THREE(3, "3 second timer"), TEN(10, "10 second timer");

    fun next(): SelfTimer = entries[(ordinal + 1) % entries.size]
}

/**
 * How the camera was launched. Capture requests come from other apps through
 * MediaStore.ACTION_IMAGE_CAPTURE / ACTION_VIDEO_CAPTURE and expect a result.
 */
sealed interface LaunchRequest {
    val initialMode: CaptureMode

    data class Standalone(override val initialMode: CaptureMode) : LaunchRequest

    /** [output] = EXTRA_OUTPUT; without it a small bitmap is returned in "data". */
    data class ImageCapture(val output: Uri?) : LaunchRequest {
        override val initialMode get() = CaptureMode.PHOTO
    }

    data class VideoCapture(
        val output: Uri?,
        val durationLimitMillis: Long,
        val sizeLimitBytes: Long,
        val lowQuality: Boolean,
    ) : LaunchRequest {
        override val initialMode get() = CaptureMode.VIDEO
    }

    val isCaptureRequest: Boolean get() = this !is Standalone

    companion object {
        fun parse(action: String?, output: Uri?, durationLimitSeconds: Int, sizeLimit: Long, videoQuality: Int): LaunchRequest = when (action) {
            MediaStore.ACTION_IMAGE_CAPTURE -> ImageCapture(output)
            MediaStore.ACTION_VIDEO_CAPTURE -> VideoCapture(
                output = output,
                durationLimitMillis = durationLimitSeconds.coerceAtLeast(0) * 1000L,
                sizeLimitBytes = sizeLimit.coerceAtLeast(0),
                lowQuality = videoQuality == 0,
            )
            MediaStore.INTENT_ACTION_VIDEO_CAMERA -> Standalone(CaptureMode.VIDEO)
            else -> Standalone(CaptureMode.PHOTO)
        }
    }
}

/** File naming and formatting helpers. */
object MediaNaming {
    const val RELATIVE_PATH = "DCIM/Camera"
    private fun stamp(date: Date) = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(date)
    fun photoName(date: Date) = "IMG_${stamp(date)}"
    fun videoName(date: Date) = "VID_${stamp(date)}"

    /** "0:07", "12:34", "1:02:03" for a recording duration. */
    fun elapsed(nanos: Long): String {
        val total = (nanos / 1_000_000_000L).coerceAtLeast(0)
        val h = total / 3600; val m = (total % 3600) / 60; val s = total % 60
        return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) else String.format(Locale.ROOT, "%d:%02d", m, s)
    }
}

/** Zoom presets for the zoom chips. */
object ZoomPresets {
    /** Wide (if available), 1x, 2x, 5x, 10x within the camera's range. */
    fun presets(min: Float, max: Float): List<Float> {
        val list = mutableListOf<Float>()
        if (min < 0.95f) list += (Math.round(min * 10) / 10f)
        list += listOf(1f, 2f, 5f, 10f).filter { it in min..max }
        return list.distinct()
    }

    /** Index of the preset the current ratio belongs to (the largest preset <= ratio). */
    fun activeIndex(presets: List<Float>, ratio: Float): Int =
        presets.indexOfLast { ratio >= it - 0.05f }.coerceAtLeast(0)

    fun label(ratio: Float): String =
        if (ratio < 10f && Math.round(ratio * 10) % 10 != 0) String.format(Locale.ROOT, "%.1fx", ratio)
        else "${Math.round(ratio)}x"
}

/** Maps OrientationEventListener degrees to a Surface rotation (0..3), with hysteresis-free buckets. */
object DeviceRotation {
    const val UNKNOWN = -1

    /** Returns Surface.ROTATION_0..ROTATION_270 as 0..3, or null for ORIENTATION_UNKNOWN. */
    fun surfaceRotation(orientationDegrees: Int): Int? = when {
        orientationDegrees == UNKNOWN -> null
        orientationDegrees in 45..134 -> 3   // device turned clockwise: ROTATION_270
        orientationDegrees in 135..224 -> 2  // upside down: ROTATION_180
        orientationDegrees in 225..314 -> 1  // device turned counter-clockwise: ROTATION_90
        else -> 0
    }
}
