package com.experience.dialer

import android.content.Intent
import android.os.Bundle
import android.provider.CallLog
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.experience.design.ExperienceActivity
import com.experience.design.components.WidthClass
import com.experience.design.components.currentWidthClass
import com.experience.dialer.ui.ContactsScreen
import com.experience.dialer.ui.FavouritesScreen
import com.experience.dialer.ui.KeypadScreen
import com.experience.dialer.ui.RecentsScreen

enum class DialerTab(val label: String, val icon: ImageVector) {
    FAVOURITES("Favourites", Icons.Filled.Star),
    RECENTS("Recents", Icons.Filled.History),
    CONTACTS("Contacts", Icons.Filled.Contacts),
    KEYPAD("Keypad", Icons.Filled.Dialpad),
}

/**
 * Dialer home. Handles ACTION_DIAL (with/without tel:), VIEW tel:, CALL_BUTTON and
 * VIEW of the call log (Telecom's missed-call notification).
 */
class MainActivity : ExperienceActivity() {
    private val vm: DialerViewModel by viewModels()
    private var tab by mutableStateOf(DialerTab.RECENTS)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.getString(KEY_TAB)?.let { name -> DialerTab.entries.firstOrNull { it.name == name }?.let { tab = it } }
        if (savedInstanceState == null) handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_TAB, tab.name)
    }

    private fun handle(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_DIAL, Intent.ACTION_VIEW, Intent.ACTION_CALL_BUTTON -> {
                val data = intent.data
                if (data != null && data.scheme == "tel") {
                    vm.dialInput.value = data.schemeSpecificPart.orEmpty()
                    tab = DialerTab.KEYPAD
                } else if (intent.type == CallLog.Calls.CONTENT_TYPE || data?.authority == CallLog.AUTHORITY) {
                    tab = DialerTab.RECENTS
                } else {
                    tab = DialerTab.KEYPAD
                }
            }
        }
    }

    @Composable
    override fun Content() {
        val width = currentWidthClass()
        if (width == WidthClass.COMPACT) {
            TabContent(bottomBar = {
                NavigationBar {
                    DialerTab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t, onClick = { tab = t },
                            icon = { Icon(t.icon, contentDescription = null) }, label = { Text(t.label) },
                        )
                    }
                }
            })
        } else {
            Row {
                NavigationRail(Modifier.fillMaxHeight().windowInsetsPadding(WindowInsets.safeDrawing)) {
                    DialerTab.entries.forEach { t ->
                        NavigationRailItem(
                            selected = tab == t, onClick = { tab = t },
                            icon = { Icon(t.icon, contentDescription = null) }, label = { Text(t.label) },
                        )
                    }
                }
                androidx.compose.foundation.layout.Box(Modifier.weight(1f)) { TabContent(bottomBar = {}) }
            }
        }
    }

    @Composable
    private fun TabContent(bottomBar: @Composable () -> Unit) {
        when (tab) {
            DialerTab.FAVOURITES -> FavouritesScreen(vm, bottomBar)
            DialerTab.RECENTS -> RecentsScreen(vm, bottomBar)
            DialerTab.CONTACTS -> ContactsScreen(vm, bottomBar)
            DialerTab.KEYPAD -> KeypadScreen(vm, bottomBar)
        }
    }

    private companion object { const val KEY_TAB = "tab" }
}
