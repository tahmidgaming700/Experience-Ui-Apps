package com.experience.camera

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.camera.core.CameraSelector
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.experience.camera.engine.MediaHelpers
import com.experience.camera.engine.SavedMedia
import com.experience.camera.model.CaptureMode
import com.experience.camera.model.FlashSetting
import com.experience.camera.model.LaunchRequest
import com.experience.camera.model.SelfTimer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CameraUiState(
    val mode: CaptureMode = CaptureMode.PHOTO,
    val lensFacing: Int = CameraSelector.LENS_FACING_BACK,
    val flash: FlashSetting = FlashSetting.OFF,
    val timer: SelfTimer = SelfTimer.OFF,
    val grid: Boolean = false,
    val lastMedia: SavedMedia? = null,
    val thumbnail: Bitmap? = null,
    /** JPEG awaiting confirmation in an ACTION_IMAGE_CAPTURE request. */
    val review: ByteArray? = null,
    val message: String? = null,
)

/** Camera UI state and persisted preferences (flash, timer, grid, lens). */
class CameraViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("camera", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(
        CameraUiState(
            lensFacing = prefs.getInt(KEY_LENS, CameraSelector.LENS_FACING_BACK),
            flash = FlashSetting.entries.getOrElse(prefs.getInt(KEY_FLASH, 0)) { FlashSetting.OFF },
            timer = SelfTimer.entries.getOrElse(prefs.getInt(KEY_TIMER, 0)) { SelfTimer.OFF },
            grid = prefs.getBoolean(KEY_GRID, false),
        ),
    )
    val state: StateFlow<CameraUiState> = _state.asStateFlow()
    private var initialized = false

    fun init(request: LaunchRequest) {
        if (initialized) return
        initialized = true
        _state.update { it.copy(mode = request.initialMode) }
        if (!request.isCaptureRequest) {
            viewModelScope.launch { MediaHelpers.latestCapture(getApplication())?.let { onSaved(it) } }
        }
    }

    fun setMode(mode: CaptureMode) = _state.update { it.copy(mode = mode) }

    fun setLens(lens: Int) {
        _state.update { it.copy(lensFacing = lens) }
        prefs.edit().putInt(KEY_LENS, lens).apply()
    }

    fun toggleLens() = setLens(
        if (_state.value.lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK,
    )

    fun cycleFlash() {
        val next = _state.value.flash.next()
        _state.update { it.copy(flash = next) }
        prefs.edit().putInt(KEY_FLASH, next.ordinal).apply()
    }

    fun cycleTimer() {
        val next = _state.value.timer.next()
        _state.update { it.copy(timer = next) }
        prefs.edit().putInt(KEY_TIMER, next.ordinal).apply()
    }

    fun toggleGrid() {
        val next = !_state.value.grid
        _state.update { it.copy(grid = next) }
        prefs.edit().putBoolean(KEY_GRID, next).apply()
    }

    fun onSaved(media: SavedMedia) {
        _state.update { it.copy(lastMedia = media) }
        viewModelScope.launch {
            val thumb = MediaHelpers.thumbnail(getApplication(), media.uri, 192)
            _state.update { if (it.lastMedia == media) it.copy(thumbnail = thumb) else it }
        }
    }

    fun setReview(jpeg: ByteArray?) = _state.update { it.copy(review = jpeg) }

    fun showMessage(text: String?) = _state.update { it.copy(message = text) }

    private companion object {
        const val KEY_LENS = "lens"
        const val KEY_FLASH = "flash"
        const val KEY_TIMER = "timer"
        const val KEY_GRID = "grid"
    }
}
