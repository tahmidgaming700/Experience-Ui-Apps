package com.experience.launcher

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.experience.design.theme.ExperienceTheme
import com.experience.launcher.ui.LauncherRoot

/**
 * The HOME activity (singleTask, stateNotNeeded). The wallpaper shows through the
 * transparent window (Theme.Experience.Wallpaper), so the system bars always use
 * light icons regardless of the light/dark theme.
 */
class LauncherActivity : ComponentActivity() {

    private val viewModel: LauncherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        // Back never leaves home: it only closes the drawer (Compose sheets handle their own back).
        onBackPressedDispatcher.addCallback(this) { viewModel.closeDrawer() }
        setContent { ExperienceTheme { LauncherRoot(viewModel) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            // HOME pressed while home was already in front (not brought back from another
            // task): animate back to the first page, like Launcher3 does.
            val alreadyOnHome = hasWindowFocus() &&
                (intent.flags and Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT) != Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT
            viewModel.onHomePressed(animate = alreadyOnHome)
        }
    }
}
