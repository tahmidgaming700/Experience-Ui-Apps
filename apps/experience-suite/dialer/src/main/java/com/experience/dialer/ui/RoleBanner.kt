package com.experience.dialer.ui

import android.app.role.RoleManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.experience.design.components.HeroCard

/** Offers to make the app the default phone app (DIALER role) when it is not. */
@Composable
fun DialerRoleBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val rm = remember { context.getSystemService(RoleManager::class.java) }
    fun held() = rm == null || !rm.isRoleAvailable(RoleManager.ROLE_DIALER) || rm.isRoleHeld(RoleManager.ROLE_DIALER)
    var isHeld by remember { mutableStateOf(held()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { isHeld = held() }
    if (!isHeld) {
        HeroCard(
            title = "Set as default phone app",
            body = "Needed to show the call screen and manage calls.",
            icon = Icons.Filled.Call,
            modifier = modifier.padding(vertical = 8.dp),
            onClick = { launcher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_DIALER)) },
        )
    }
}
