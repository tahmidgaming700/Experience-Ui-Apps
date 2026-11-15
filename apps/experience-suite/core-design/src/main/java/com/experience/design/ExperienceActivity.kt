package com.experience.design

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import com.experience.design.theme.ExperienceTheme

/** Base activity: edge-to-edge window with [ExperienceTheme] applied to [Content]. */
abstract class ExperienceActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { ExperienceTheme { Content() } }
    }

    @Composable
    abstract fun Content()
}
