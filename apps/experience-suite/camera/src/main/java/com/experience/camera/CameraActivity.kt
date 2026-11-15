package com.experience.camera

import android.Manifest
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.experience.camera.model.LaunchRequest
import com.experience.camera.ui.CameraScreen
import com.experience.design.components.PermissionGate
import com.experience.design.theme.ExperienceTheme
import kotlinx.coroutines.flow.MutableSharedFlow

class CameraActivity : ComponentActivity() {

    private val viewModel: CameraViewModel by viewModels()

    /** Volume keys act as a shutter button. */
    private val shutterKeys = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        // Phones stay in portrait and rotate the controls instead (target rotation comes
        // from the orientation listener); tablets follow the device orientation.
        if (resources.configuration.smallestScreenWidthDp < 600) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val request = parseRequest(intent)
        viewModel.init(request)
        setContent {
            ExperienceTheme(darkTheme = true) {
                Surface(color = androidx.compose.ui.graphics.Color.Black, contentColor = MaterialTheme.colorScheme.onSurface) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        PermissionGate(
                            listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
                            rationaleTitle = "Camera and microphone",
                            rationale = "Camera needs access to the camera to take photos, and to the microphone to record sound with videos.",
                        ) {
                            CameraScreen(viewModel, request, shutterKeys, onResult = ::deliver)
                        }
                    }
                }
            }
        }
    }

    private fun parseRequest(intent: Intent): LaunchRequest {
        @Suppress("DEPRECATION")
        val output = intent.getParcelableExtra<Uri>(MediaStore.EXTRA_OUTPUT)
        return LaunchRequest.parse(
            action = intent.action,
            output = output,
            durationLimitSeconds = intent.getIntExtra(MediaStore.EXTRA_DURATION_LIMIT, 0),
            sizeLimit = intent.getLongExtra(MediaStore.EXTRA_SIZE_LIMIT, 0L),
            videoQuality = intent.getIntExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1),
        )
    }

    /** Returns a capture result to the calling app (null = cancelled). */
    private fun deliver(result: Intent?) {
        setResult(if (result != null) RESULT_OK else RESULT_CANCELED, result)
        finish()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if ((keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_CAMERA)) {
            if (event.repeatCount == 0) shutterKeys.tryEmit(Unit)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
