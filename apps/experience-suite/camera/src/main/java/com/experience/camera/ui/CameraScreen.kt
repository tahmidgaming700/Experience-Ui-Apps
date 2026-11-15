package com.experience.camera.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.ParcelFileDescriptor
import android.view.OrientationEventListener
import androidx.camera.video.FileDescriptorOutputOptions
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Cameraswitch
import androidx.compose.material.icons.outlined.FlashAuto
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.FlashlightOff
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.outlined.GridOff
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.Timer10
import androidx.compose.material.icons.outlined.Timer3
import androidx.compose.material.icons.outlined.TimerOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.experience.camera.CameraViewModel
import com.experience.camera.engine.CameraEngine
import com.experience.camera.engine.MediaHelpers
import com.experience.camera.engine.SavedMedia
import com.experience.camera.model.CaptureMode
import com.experience.camera.model.DeviceRotation
import com.experience.camera.model.FlashSetting
import com.experience.camera.model.LaunchRequest
import com.experience.camera.model.SelfTimer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

@Composable
fun CameraScreen(vm: CameraViewModel, request: LaunchRequest, shutterKeys: SharedFlow<Unit>, onResult: (Intent?) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val engine = remember { CameraEngine(context, previewView) }
    val cam by engine.state.collectAsState()
    val ui by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val landscape = LocalConfiguration.current.let { it.screenWidthDp > it.screenHeightDp }
    val portraitLocked = LocalConfiguration.current.smallestScreenWidthDp < 600
    val lowQuality = (request as? LaunchRequest.VideoCapture)?.lowQuality == true

    var rotation by remember { mutableIntStateOf(0) }
    var countdown by remember { mutableStateOf<Int?>(null) }
    var countdownJob by remember { mutableStateOf<Job?>(null) }
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var focusKey by remember { mutableIntStateOf(0) }
    val flash = remember { Animatable(0f) }

    // --- Camera lifecycle -----------------------------------------------------------
    LaunchedEffect(ui.mode, ui.lensFacing) { engine.bind(owner, ui.mode, ui.lensFacing, ui.flash, lowQuality) }
    LaunchedEffect(cam.lensFacing, cam.ready) { if (cam.ready && cam.lensFacing != ui.lensFacing) vm.setLens(cam.lensFacing) }
    LaunchedEffect(ui.flash) { engine.setFlash(ui.flash) }
    DisposableEffect(Unit) { onDispose { engine.release() } }
    DisposableEffect(owner) {
        // Leaving the app finalises a recording instead of losing it.
        val observer = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_STOP) engine.stopRecording() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    // Target rotation for saved media follows the physical orientation.
    DisposableEffect(context) {
        val listener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(degrees: Int) {
                val r = DeviceRotation.surfaceRotation(degrees) ?: return
                if (r != rotation) {
                    rotation = r
                    engine.setTargetRotation(r)
                }
            }
        }
        if (listener.canDetectOrientation()) listener.enable()
        onDispose { listener.disable() }
    }
    // On portrait-locked phones the controls rotate so they stay upright.
    val iconTarget = if (portraitLocked) (rotation * 90f).let { if (it > 180f) it - 360f else it } else 0f
    val iconRotation by animateFloatAsState(iconTarget, tween(250), label = "rotation")

    LaunchedEffect(cam.error) { cam.error?.let { snackbar.showSnackbar(it); engine.clearError() } }
    LaunchedEffect(ui.message) { ui.message?.let { snackbar.showSnackbar(it); vm.showMessage(null) } }

    // --- Actions --------------------------------------------------------------------
    fun capturePhoto() {
        scope.launch { flash.snapTo(0.8f); flash.animateTo(0f, tween(220)) }
        if (request is LaunchRequest.ImageCapture) {
            engine.takePhotoBytes(onCaptured = vm::setReview, onError = vm::showMessage)
        } else {
            engine.takePhoto(onSaved = vm::onSaved, onError = vm::showMessage)
        }
    }

    fun startVideo() {
        if (request is LaunchRequest.VideoCapture) {
            var pfd: ParcelFileDescriptor? = null
            val output = request.output?.let { uri ->
                pfd = runCatching { context.contentResolver.openFileDescriptor(uri, "rw") }.getOrNull()
                pfd?.let { fd ->
                    FileDescriptorOutputOptions.Builder(fd).apply {
                        if (request.durationLimitMillis > 0) setDurationLimitMillis(request.durationLimitMillis)
                        if (request.sizeLimitBytes > 0) setFileSizeLimit(request.sizeLimitBytes)
                    }.build()
                }
            } ?: engine.defaultVideoOutput(request.durationLimitMillis, request.sizeLimitBytes)
            engine.startRecording(output) { media, error ->
                runCatching { pfd?.close() }
                if (media != null) {
                    val uri = request.output ?: media.uri
                    onResult(Intent().setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                } else {
                    vm.showMessage(error)
                }
            }
        } else {
            engine.startRecording { media, error -> if (media != null) vm.onSaved(media) else vm.showMessage(error) }
        }
    }

    fun shutter() {
        if (countdown != null) {
            countdownJob?.cancel(); countdown = null; return
        }
        if (ui.review != null || !cam.ready) return
        if (ui.mode == CaptureMode.VIDEO && cam.recording) {
            engine.stopRecording(); return
        }
        val action = { if (ui.mode == CaptureMode.VIDEO) startVideo() else capturePhoto() }
        if (ui.timer == SelfTimer.OFF) {
            action()
        } else {
            countdownJob = scope.launch {
                for (s in ui.timer.seconds downTo 1) { countdown = s; delay(1000) }
                countdown = null
                action()
            }
        }
    }

    LaunchedEffect(shutterKeys) { shutterKeys.collect { shutter() } }

    fun openLast(media: SavedMedia) {
        val view = Intent(Intent.ACTION_VIEW).setDataAndType(media.uri, media.mimeType).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try { context.startActivity(view) } catch (_: ActivityNotFoundException) { vm.showMessage("No app can open this file") }
    }

    // --- Layout ---------------------------------------------------------------------
    val topControls: @Composable () -> Unit = {
        if (request.isCaptureRequest) RoundIconButton(Icons.Filled.Close, "Cancel", iconRotation, { onResult(null) })
        if (cam.hasFlash) {
            if (ui.mode == CaptureMode.PHOTO) {
                val icon = when (ui.flash) { FlashSetting.OFF -> Icons.Outlined.FlashOff; FlashSetting.AUTO -> Icons.Outlined.FlashAuto; FlashSetting.ON -> Icons.Outlined.FlashOn }
                RoundIconButton(icon, ui.flash.label, iconRotation, vm::cycleFlash)
            } else {
                RoundIconButton(
                    if (cam.torch) Icons.Outlined.FlashlightOn else Icons.Outlined.FlashlightOff,
                    if (cam.torch) "Torch on" else "Torch off", iconRotation, { engine.setTorch(!cam.torch) },
                )
            }
        }
        val timerIcon = when (ui.timer) { SelfTimer.OFF -> Icons.Outlined.TimerOff; SelfTimer.THREE -> Icons.Outlined.Timer3; SelfTimer.TEN -> Icons.Outlined.Timer10 }
        RoundIconButton(timerIcon, ui.timer.label, iconRotation, vm::cycleTimer)
        RoundIconButton(if (ui.grid) Icons.Outlined.GridOn else Icons.Outlined.GridOff, if (ui.grid) "Grid on" else "Grid off", iconRotation, vm::toggleGrid)
    }

    val bottomControls: @Composable () -> Unit = {
        Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
            if (!request.isCaptureRequest) {
                ThumbnailButton(ui.thumbnail, ui.lastMedia?.mimeType?.startsWith("video") == true, iconRotation) { ui.lastMedia?.let(::openLast) }
            }
        }
        ShutterButton(ui.mode, cam.recording, enabled = cam.ready, onClick = ::shutter)
        Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
            when {
                cam.recording -> RoundIconButton(
                    if (cam.paused) Icons.Filled.PlayArrow else Icons.Outlined.Pause,
                    if (cam.paused) "Resume recording" else "Pause recording", iconRotation,
                    { if (cam.paused) engine.resumeRecording() else engine.pauseRecording() }, size = 56,
                )
                cam.hasFront && cam.hasBack -> RoundIconButton(Icons.Outlined.Cameraswitch, "Switch camera", iconRotation, vm::toggleLens, size = 56)
            }
        }
    }

    val preview: @Composable (Modifier) -> Unit = { modifier ->
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val ratio = when {
                ui.mode == CaptureMode.PHOTO && !landscape -> 3f / 4f
                ui.mode == CaptureMode.PHOTO -> 4f / 3f
                !landscape -> 9f / 16f
                else -> 16f / 9f
            }
            Box(Modifier.aspectRatio(ratio)) {
                AndroidView({ previewView }, Modifier.fillMaxSize())
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures { p ->
                                engine.focus(p.x, p.y)
                                focusPoint = p; focusKey++
                            }
                        }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, _, zoom, _ -> engine.setZoom(engine.state.value.zoomRatio * zoom) }
                        },
                ) {
                    if (ui.grid) GridOverlay()
                    focusPoint?.let { FocusRing(it, focusKey) }
                    ShutterFlash(flash.value)
                    countdown?.let { Countdown(it) }
                    if (cam.recording) RecordingPill(cam.elapsedNanos, cam.paused, Modifier.align(Alignment.TopCenter).padding(12.dp))
                    Box(Modifier.align(Alignment.BottomCenter).padding(12.dp)) {
                        ZoomChips(cam.zoomRatio, cam.minZoom, cam.maxZoom, iconRotation, engine::setZoom)
                    }
                }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (!landscape) {
            Column(Modifier.fillMaxSize().safeDrawingPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceEvenly) { topControls() }
                preview(Modifier.weight(1f))
                Spacer(Modifier.height(12.dp))
                if (!request.isCaptureRequest) ModeSwitch(ui.mode, enabled = !cam.recording, onMode = vm::setMode)
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) { bottomControls() }
            }
        } else {
            Row(Modifier.fillMaxSize().safeDrawingPadding(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.fillMaxHeight().padding(12.dp), verticalArrangement = Arrangement.SpaceEvenly) { topControls() }
                preview(Modifier.weight(1f))
                Column(
                    Modifier.fillMaxHeight().padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.SpaceEvenly,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    bottomControls()
                    if (!request.isCaptureRequest) ModeSwitch(ui.mode, enabled = !cam.recording, onMode = vm::setMode)
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 140.dp))

        ui.review?.let { jpeg ->
            ReviewScreen(
                jpeg = jpeg,
                onRetake = { vm.setReview(null) },
                onAccept = {
                    scope.launch {
                        val req = request as LaunchRequest.ImageCapture
                        if (req.output != null) {
                            if (MediaHelpers.writeTo(context, req.output, jpeg)) onResult(Intent().setData(req.output))
                            else vm.showMessage("Couldn't save the photo for the requesting app")
                        } else {
                            // No EXTRA_OUTPUT: return a small bitmap in "data", per the ACTION_IMAGE_CAPTURE contract.
                            val small = MediaHelpers.decodeUpright(jpeg, 512)
                            onResult(Intent().putExtra("data", small))
                        }
                    }
                },
            )
        }
    }
}
