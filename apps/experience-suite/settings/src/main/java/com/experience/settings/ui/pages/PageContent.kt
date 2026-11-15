package com.experience.settings.ui.pages

import androidx.compose.runtime.Composable
import com.experience.settings.Page

/** Renders [page]; [onBack] is null in the two-pane layout. */
@Composable
fun PageContent(page: Page, onBack: (() -> Unit)?, onOpen: (Page) -> Unit) {
    when (page) {
        Page.NETWORK -> NetworkPage(onBack)
        Page.CONNECTED -> ConnectedPage(onBack)
        Page.DISPLAY -> DisplayPage(onBack)
        Page.STYLE -> StylePage(onBack)
        Page.SOUND -> SoundPage(onBack)
        Page.NOTIFICATIONS -> NotificationsPage(onBack)
        Page.APPS -> AppsPage(onBack)
        Page.BATTERY -> BatteryPage(onBack)
        Page.STORAGE -> StoragePage(onBack)
        Page.SECURITY -> SecurityPage(onBack)
        Page.ACCESSIBILITY -> AccessibilityPage(onBack)
        Page.LOCATION -> LocationPage(onBack)
        Page.SYSTEM -> SystemPage(onBack, onOpen)
        Page.UPDATES -> UpdatesPage(onBack)
        Page.ABOUT -> AboutPage(onBack)
    }
}
