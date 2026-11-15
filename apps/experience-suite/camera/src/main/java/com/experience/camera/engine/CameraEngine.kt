package com.experience.camera.engine

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileDescriptorOutputOptions
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.OutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.experience.camera.model.CaptureMode
import com.experience.camera.model.FlashSetting
import com.experience.camera.model.MediaNaming
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Date
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class EngineState(
    val ready: Boolean = false,
    val lensFacing: Int = CameraSelector.LENS_FACING_BACK,
    val hasBack: Boolean = true,
    val hasFront: Boolean = false,
    val hasFlash: Boolean = false,
    val zoomRatio: Float = 1f,
    val minZoom: Float = 1f,
    val maxZoom: Float = 1f,
    val recording: Boolean = false,
    val paused: Boolean = false,
    val elapsedNanos: Long = 0,
    val torch: Boolean = false,
    val error: String? = null,
)

/** Where a finished capture went. */
data class SavedMedia(val uri: Uri, val mimeType: String)

/**
 * CameraX wrapper. Photo mode binds Preview + ImageCapture, video mode binds
 * Preview + VideoCapture<Recorder> (binding per mode works on LIMITED devices that
 * cannot run all three use cases at once).
 */
class CameraEngine(private val context: Context, val previewView: PreviewView) {

    private val mainExecutor = ContextCompat.getMainExecutor(context)
    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var targetRotation: Int = previewView.display?.rotation ?: 0

    private val _state = MutableStateFlow(EngineState())
    val state: StateFlow<EngineState> = _state.asStateFlow()

    private suspend fun provider(): ProcessCameraProvider = provider ?: suspendCancellableCoroutine { cont ->
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try { cont.resume(future.get()) } catch (e: Exception) { cont.resumeWithException(e) }
        }, mainExecutor)
    }.also { provider = it }

    /**
     * (Re)binds the use cases for [mode]. Falls back to the other lens when the
     * requested one does not exist (e.g. devices without a front camera).
     */
    suspend fun bind(owner: LifecycleOwner, mode: CaptureMode, lensFacing: Int, flash: FlashSetting, lowQualityVideo: Boolean = false) {
        val p = try { provider() } catch (e: Exception) {
            _state.update { it.copy(error = "Camera unavailable: ${e.message}") }; return
        }
        val hasBack = runCatching { p.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) }.getOrDefault(false)
        val hasFront = runCatching { p.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) }.getOrDefault(false)
        val lens = when {
            lensFacing == CameraSelector.LENS_FACING_FRONT && hasFront -> lensFacing
            lensFacing == CameraSelector.LENS_FACING_BACK && hasBack -> lensFacing
            hasBack -> CameraSelector.LENS_FACING_BACK
            hasFront -> CameraSelector.LENS_FACING_FRONT
            else -> { _state.update { it.copy(error = "No camera found on this device") }; return }
        }
        val ratio = if (mode == CaptureMode.PHOTO) AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY else AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY
        val resolution = ResolutionSelector.Builder().setAspectRatioStrategy(ratio).build()
        val preview = Preview.Builder().setResolutionSelector(resolution).build()
        preview.setSurfaceProvider(previewView.surfaceProvider)

        val useCase = if (mode == CaptureMode.PHOTO) {
            ImageCapture.Builder()
                .setResolutionSelector(resolution)
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setFlashMode(flash.toCameraX())
                .setTargetRotation(targetRotation)
                .build().also { imageCapture = it; videoCapture = null }
        } else {
            val quality = if (lowQualityVideo) QualitySelector.from(Quality.SD, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD))
            else QualitySelector.from(Quality.FHD, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD))
            VideoCapture.Builder(Recorder.Builder().setQualitySelector(quality).build())
                .setTargetRotation(targetRotation)
                .build().also { videoCapture = it; imageCapture = null }
        }
        try {
            p.unbindAll()
            val selector = CameraSelector.Builder().requireLensFacing(lens).build()
            val cam = p.bindToLifecycle(owner, selector, preview, useCase)
            camera = cam
            cam.cameraInfo.zoomState.removeObservers(owner)
            cam.cameraInfo.zoomState.observe(owner) { z ->
                _state.update { it.copy(zoomRatio = z.zoomRatio, minZoom = z.minZoomRatio, maxZoom = z.maxZoomRatio) }
            }
            _state.update {
                it.copy(
                    ready = true, lensFacing = lens, hasBack = hasBack, hasFront = hasFront,
                    hasFlash = cam.cameraInfo.hasFlashUnit(), torch = false, error = null,
                )
            }
        } catch (e: Exception) {
            _state.update { it.copy(ready = false, error = "Couldn't start the camera: ${e.message}") }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    /** Updates the rotation used for saved photos and videos (from the orientation listener). */
    fun setTargetRotation(rotation: Int) {
        targetRotation = rotation
        imageCapture?.targetRotation = rotation
        // A recording in progress keeps the rotation it started with.
        if (recording == null) videoCapture?.targetRotation = rotation
    }

    fun setFlash(flash: FlashSetting) {
        imageCapture?.flashMode = flash.toCameraX()
    }

    fun setTorch(on: Boolean) {
        val cam = camera ?: return
        cam.cameraControl.enableTorch(on)
        _state.update { it.copy(torch = on) }
    }

    fun setZoom(ratio: Float) {
        val s = _state.value
        camera?.cameraControl?.setZoomRatio(ratio.coerceIn(s.minZoom, s.maxZoom))
    }

    /** Focus and meter at a point in [previewView] coordinates. */
    fun focus(x: Float, y: Float) {
        val cam = camera ?: return
        val point = previewView.meteringPointFactory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
            .setAutoCancelDuration(4, TimeUnit.SECONDS)
            .build()
        cam.cameraControl.startFocusAndMetering(action)
    }

    // --- Photos ---------------------------------------------------------------------

    /** Saves a JPEG to MediaStore DCIM/Camera. */
    fun takePhoto(onSaved: (SavedMedia) -> Unit, onError: (String) -> Unit) {
        val capture = imageCapture ?: return onError("Camera not ready")
        val now = Date()
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, MediaNaming.photoName(now))
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, MediaNaming.RELATIVE_PATH)
            put(MediaStore.Images.Media.DATE_TAKEN, now.time)
        }
        val options = ImageCapture.OutputFileOptions.Builder(context.contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            .setMetadata(ImageCapture.Metadata().apply { isReversedHorizontal = false })
            .build()
        capture.takePicture(options, mainExecutor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                val uri = result.savedUri ?: return onError("Photo saved without a location")
                onSaved(SavedMedia(uri, "image/jpeg"))
            }
            override fun onError(e: ImageCaptureException) = onError("Couldn't take photo: ${e.message}")
        })
    }

    /** Captures a JPEG into memory (capture-intent review). */
    fun takePhotoBytes(onCaptured: (ByteArray) -> Unit, onError: (String) -> Unit) {
        val capture = imageCapture ?: return onError("Camera not ready")
        capture.takePicture(mainExecutor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val bytes = image.use { img ->
                    val buffer = img.planes[0].buffer
                    ByteArray(buffer.remaining()).also { buffer.get(it) }
                }
                onCaptured(bytes)
            }
            override fun onError(e: ImageCaptureException) = onError("Couldn't take photo: ${e.message}")
        })
    }

    // --- Video ----------------------------------------------------------------------

    /**
     * Starts recording to [output] (MediaStore by default). Audio is recorded when
     * RECORD_AUDIO is granted. [onFinished] receives the saved media or an error.
     */
    @SuppressLint("MissingPermission")
    fun startRecording(output: OutputOptions? = null, onFinished: (SavedMedia?, String?) -> Unit) {
        val capture = videoCapture ?: return onFinished(null, "Camera not ready")
        if (recording != null) return
        val options = output ?: defaultVideoOutput()
        val pending = when (options) {
            is MediaStoreOutputOptions -> capture.output.prepareRecording(context, options)
            is FileDescriptorOutputOptions -> capture.output.prepareRecording(context, options)
            else -> return onFinished(null, "Unsupported output")
        }
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            pending.withAudioEnabled()
        }
        recording = pending.start(mainExecutor) { event ->
            when (event) {
                is VideoRecordEvent.Start -> _state.update { it.copy(recording = true, paused = false, elapsedNanos = 0) }
                is VideoRecordEvent.Pause -> _state.update { it.copy(paused = true) }
                is VideoRecordEvent.Resume -> _state.update { it.copy(paused = false) }
                is VideoRecordEvent.Status -> _state.update { it.copy(elapsedNanos = event.recordingStats.recordedDurationNanos) }
                is VideoRecordEvent.Finalize -> {
                    recording = null
                    _state.update { it.copy(recording = false, paused = false, elapsedNanos = 0) }
                    val uri = event.outputResults.outputUri
                    // Reaching a duration or size limit still produces a complete file.
                    val ok = event.error == VideoRecordEvent.Finalize.ERROR_NONE ||
                        event.error == VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED ||
                        event.error == VideoRecordEvent.Finalize.ERROR_FILE_SIZE_LIMIT_REACHED
                    if (ok) onFinished(SavedMedia(uri, "video/mp4"), null)
                    else onFinished(null, "Recording failed (${event.error})")
                }
            }
        }
    }

    fun defaultVideoOutput(durationLimitMillis: Long = 0, sizeLimitBytes: Long = 0): MediaStoreOutputOptions {
        val now = Date()
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, MediaNaming.videoName(now))
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, MediaNaming.RELATIVE_PATH)
            put(MediaStore.Video.Media.DATE_TAKEN, now.time)
        }
        return MediaStoreOutputOptions.Builder(context.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(values)
            .apply {
                if (durationLimitMillis > 0) setDurationLimitMillis(durationLimitMillis)
                if (sizeLimitBytes > 0) setFileSizeLimit(sizeLimitBytes)
            }
            .build()
    }

    fun pauseRecording() = recording?.pause()
    fun resumeRecording() = recording?.resume()
    fun stopRecording() = recording?.stop()

    fun release() {
        recording?.stop()
        provider?.unbindAll()
    }

    private fun FlashSetting.toCameraX() = when (this) {
        FlashSetting.OFF -> ImageCapture.FLASH_MODE_OFF
        FlashSetting.AUTO -> ImageCapture.FLASH_MODE_AUTO
        FlashSetting.ON -> ImageCapture.FLASH_MODE_ON
    }
}
