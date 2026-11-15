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
import com.experience.setupwizard.ui.SetupScreen

/**
 * First-boot HOME activity (priority 5, ahead of the launcher). On an already
 * provisioned device it hands over immediately: disables itself and starts HOME.
 */
class SetupActivity : ComponentActivity() {

    private val viewModel: SetupViewModel by viewModels {
        viewModelFactory {
            initializer {
                SetupViewModel(createSavedStateHandle(), AndroidSystemSetup(application), Resources.getSystem().assets.locales)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
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
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }
}
