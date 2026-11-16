package com.experience.setupwizard

import android.content.Intent
import android.content.res.Resources
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.experience.design.theme.ExperienceTheme
import com.experience.setupwizard.system.AndroidSystemSetup
import com.experience.setupwizard.system.PreviewSystemSetup
import com.experience.setupwizard.ui.SetupScreen

/**
 * First-boot HOME activity (priority 5, ahead of the launcher). On an already
 * provisioned device it hands over immediately: disables itself and starts HOME.
 */
open class SetupActivity : ComponentActivity() {

    /** True for [PreviewActivity]: walk through setup without changing the device. */
    protected open val preview = false

    private val viewModel: SetupViewModel by viewModels {
        viewModelFactory {
            initializer {
                val real = AndroidSystemSetup(application)
                val system = if (preview) PreviewSystemSetup(real) else real
                SetupViewModel(createSavedStateHandle(), system, Resources.getSystem().assets.locales)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null && !preview) {
            val system = AndroidSystemSetup(this)
            if (system.isProvisioned()) {
                system.disableSetupComponent()
                goHome()
                return
            }
        }
        setContent { ExperienceTheme { SetupScreen(viewModel, onFinished = ::goHome) } }
    }

    override fun onResume() {
        super.onResume()
        if (!isFinishing) {
            viewModel.refresh()
            viewModel.onStarted()
        }
    }

    private fun goHome() {
        if (preview) {
            finish()
            return
        }
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }
}

/**
 * "Setup preview" launcher entry, present only in builds made with
 * -Pexperience.setupPreview=true (for trying the wizard on any phone). Never part of
 * the ROM build.
 */
class PreviewActivity : SetupActivity() {
    override val preview = true
}
