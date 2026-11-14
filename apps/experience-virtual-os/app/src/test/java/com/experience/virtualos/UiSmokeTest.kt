package com.experience.virtualos

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Launches the real MainActivity under Robolectric (Android API 28, the
 * Huawei Y9 2019's level) and walks the setup wizard and main tabs. Network
 * calls fail in the sandbox, which also exercises the error states.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class UiSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun setupWizardWalksThroughAllSteps() {
        compose.onNodeWithText("Hello.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Step 1 of 5").assertExists()
        compose.onNodeWithText("Get started").performClick()
        compose.onNodeWithText("Checking this phone").assertIsDisplayed()
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("What to expect").assertIsDisplayed()
        compose.onNodeWithText("Android system images — experimental, console only").assertExists()
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Install the runtime").assertIsDisplayed()
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Stay informed").assertIsDisplayed()
        compose.onNodeWithText("Start using Experience Virtual OS").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Runtime not installed").assertIsDisplayed()
        val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("settings", Context.MODE_PRIVATE)
        assertTrue("setup completion is persisted", prefs.getBoolean("setup_complete", false))
    }

    @Test fun mainTabsRenderHonestEmptyStates() {
        compose.runOnUiThread { compose.activity.graph.settings.update { it.copy(setupComplete = true) } }
        compose.waitForIdle()
        compose.onNodeWithText("Runtime not installed").assertIsDisplayed()
        compose.onAllNodesWithText("Devices")[0].performClick()
        compose.onNodeWithText("No devices").assertIsDisplayed()
        compose.onNodeWithText("New device", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Create device").assertExists()
        compose.activity.onBackPressedDispatcher.let { compose.runOnUiThread { it.onBackPressed() } }
        compose.onAllNodesWithText("Runtime")[0].performClick()
        compose.onNodeWithText("Not installed").assertIsDisplayed()
        compose.onAllNodesWithText("Settings")[0].performClick()
        compose.onNodeWithText("Theme").assertIsDisplayed()
        compose.onNodeWithText("Follow system").assertIsDisplayed()
    }
}
