package com.experience.virtualos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.experience.virtualos.ui.AppViewModel
import com.experience.virtualos.ui.screens.DeviceDetailScreen
import com.experience.virtualos.ui.screens.DeviceEditorScreen
import com.experience.virtualos.ui.screens.DevicesScreen
import com.experience.virtualos.ui.screens.HomeScreen
import com.experience.virtualos.ui.screens.ImagesScreen
import com.experience.virtualos.ui.screens.RuntimeScreen
import com.experience.virtualos.ui.screens.SettingsScreen
import com.experience.virtualos.ui.screens.SetupScreen
import com.experience.virtualos.ui.theme.ExperienceTheme
import kotlinx.coroutines.launch

private data class Tab(val route: String, val label: String, val selected: ImageVector, val unselected: ImageVector)

private val tabs = listOf(
    Tab("home", "Home", Icons.Filled.Dashboard, Icons.Outlined.Dashboard),
    Tab("devices", "Devices", Icons.Filled.Computer, Icons.Outlined.Computer),
    Tab("images", "Images", Icons.Filled.Album, Icons.Outlined.Album),
    Tab("runtime", "Runtime", Icons.Filled.Inventory2, Icons.Outlined.Inventory2),
    Tab("settings", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings),
)

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Host facts (free RAM, storage) change while the app is in the background.
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.RESUMED) { vm.refreshAll() } }
        setContent {
            val settings by vm.settings.collectAsState()
            ExperienceTheme(settings.themeMode, settings.dynamicColor) {
                if (!settings.setupComplete) {
                    SetupScreen(vm) { vm.updateSettings { it.copy(setupComplete = true) } }
                } else {
                    AppShell(vm)
                }
            }
        }
    }
}

@Composable
private fun AppShell(vm: AppViewModel) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val message by vm.messages.collectAsState()
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.consumeMessage() } }
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showBar = tabs.any { it.route == route }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (showBar) NavigationBar {
                tabs.forEach { t ->
                    val selected = route == t.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = { nav.switchTab(t.route) },
                        icon = { Icon(if (selected) t.selected else t.unselected, contentDescription = null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            nav, startDestination = "home",
            enterTransition = { fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) },
            exitTransition = { fadeOut(spring(stiffness = Spring.StiffnessMedium)) },
        ) {
            composable("home") {
                HomeScreen(vm, padding,
                    openRuntime = { nav.switchTab("runtime") },
                    openDevices = { nav.switchTab("devices") },
                    openDevice = { nav.navigate("device/$it") },
                    openImages = { nav.switchTab("images") },
                    newDevice = { nav.navigate("edit/new") })
            }
            composable("devices") { DevicesScreen(vm, padding, openDevice = { nav.navigate("device/$it") }, newDevice = { nav.navigate("edit/new") }) }
            composable("images") { ImagesScreen(vm, padding) }
            composable("runtime") { RuntimeScreen(vm, padding) }
            composable("settings") { SettingsScreen(vm, padding) { vm.updateSettings { it.copy(setupComplete = false) } } }
            composable("device/{id}") { e ->
                val id = e.arguments?.getString("id")!!
                DeviceDetailScreen(vm, id, onBack = { nav.popBackStack() }, onEdit = { nav.navigate("edit/$id") })
            }
            composable("edit/{id}") { e ->
                val id = e.arguments?.getString("id")?.takeIf { it != "new" }
                DeviceEditorScreen(vm, id, onBack = { nav.popBackStack() }, onSaved = { saved ->
                    nav.popBackStack()
                    if (id == null) nav.navigate("device/$saved")
                })
            }
        }
    }
}

private fun NavHostController.switchTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
