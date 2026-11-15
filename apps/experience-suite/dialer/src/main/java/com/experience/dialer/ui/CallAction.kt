package com.experience.dialer.ui

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.experience.design.components.GroupedRow
import com.experience.dialer.telecom.AccountChoice
import com.experience.dialer.telecom.CallPlacer

/**
 * Returns a "call this number" action that requests CALL_PHONE when needed and asks
 * which SIM to use when several call-capable accounts exist and none is the default.
 */
@Composable
fun rememberCallAction(): (String) -> Unit {
    val context = LocalContext.current
    val placer = remember { CallPlacer(context) }
    var chooser by remember { mutableStateOf<Pair<String, List<AccountChoice>>?>(null) }
    var pending by remember { mutableStateOf<String?>(null) }

    fun place(number: String, choice: AccountChoice? = null) {
        if (!placer.place(number, choice?.handle)) {
            Toast.makeText(context, "Couldn't place the call", Toast.LENGTH_SHORT).show()
        }
    }

    fun proceed(number: String) {
        if (placer.needsAccountChoice(number)) chooser = number to placer.accounts() else place(number)
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val n = pending ?: return@rememberLauncherForActivityResult
        pending = null
        if (placer.hasCallPermission() || placer.isEmergency(n)) proceed(n)
    }

    chooser?.let { (number, accounts) ->
        var remember by remember(number) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { chooser = null },
            title = { Text("Call with") },
            text = {
                Column {
                    accounts.forEachIndexed { i, a ->
                        GroupedRow(
                            title = a.label, summary = a.detail, icon = Icons.Filled.SimCard, index = i, count = accounts.size,
                            onClick = {
                                chooser = null
                                if (remember && !placer.setDefaultAccount(a.handle)) {
                                    Toast.makeText(context, "Couldn't save the default SIM", Toast.LENGTH_SHORT).show()
                                }
                                place(number, a)
                            },
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Checkbox) { remember = !remember }.padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = remember, onCheckedChange = null)
                        Text("Always use this SIM for calls", Modifier.padding(start = 8.dp))
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { chooser = null }) { Text("Cancel") } },
        )
    }

    return remember(placer) {
        { raw: String ->
            val number = raw.trim()
            if (number.isNotEmpty()) {
                if (!placer.hasCallPermission() && !placer.isEmergency(number)) {
                    pending = number
                    launcher.launch(arrayOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE))
                } else {
                    proceed(number)
                }
            }
        }
    }
}
