package com.experience.calculator

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.experience.calculator.ui.CalculatorScreen
import com.experience.design.ExperienceActivity

/** Entry point; also handles android.intent.category.APP_CALCULATOR launches. */
class MainActivity : ExperienceActivity() {
    @Composable
    override fun Content() {
        CalculatorScreen(viewModel())
    }
}
