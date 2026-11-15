package com.experience.calendar

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.experience.calendar.logic.EventDraft
import com.experience.calendar.reminders.ReminderReceiver
import com.experience.calendar.ui.CalendarApp
import com.experience.design.ExperienceActivity
import com.experience.design.components.PermissionGate
import java.time.Instant
import java.time.LocalDate

class MainActivity : ExperienceActivity() {
    private val vm: CalendarViewModel by viewModels()
    private var pending by mutableStateOf<LaunchRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ReminderReceiver.ensureChannel(this)
        if (savedInstanceState == null) pending = LaunchRequest.parse(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pending = LaunchRequest.parse(intent)
    }

    private fun apply(req: LaunchRequest) {
        val missing = { Toast.makeText(this, R.string.event_missing, Toast.LENGTH_SHORT).show() }
        when (req) {
            is LaunchRequest.ViewEvent -> {
                req.begin?.let { vm.selectedDate = Instant.ofEpochMilli(it).atZone(vm.zone).toLocalDate() }
                vm.open(req.id, req.begin, req.end, missing)
            }
            is LaunchRequest.ViewTime -> {
                vm.selectedDate = Instant.ofEpochMilli(req.millis).atZone(vm.zone).toLocalDate()
                vm.mode = ViewMode.DAY
            }
            is LaunchRequest.Insert -> vm.newEvent(req.apply(EventDraft.newAt(LocalDate.now(), vm.zone)))
            is LaunchRequest.Edit -> vm.edit(req.id, req.apply)
        }
    }

    @Composable
    override fun Content() {
        PermissionGate(
            permissions = listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR),
            rationaleTitle = stringResource(R.string.permission_title),
            rationale = stringResource(R.string.permission_body),
        ) {
            LaunchedEffect(Unit) { vm.start() }
            LaunchedEffect(pending) { pending?.let { apply(it); pending = null } }
            CalendarApp(vm)
        }
    }
}
