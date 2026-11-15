package com.experience.setupwizard.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.experience.design.theme.ExperienceMotion
import com.experience.design.theme.ExperienceShape
import com.experience.setupwizard.Completion
import com.experience.setupwizard.SetupViewModel
import com.experience.setupwizard.model.SetupFlow
import com.experience.setupwizard.model.SetupPage
import com.experience.setupwizard.ui.pages.AccessibilityPage
import com.experience.setupwizard.ui.pages.AccountsPage
import com.experience.setupwizard.ui.pages.CopyDataPage
import com.experience.setupwizard.ui.pages.DateTimePage
import com.experience.setupwizard.ui.pages.DonePage
import com.experience.setupwizard.ui.pages.LanguagePage
import com.experience.setupwizard.ui.pages.NavigationPage
import com.experience.setupwizard.ui.pages.PrivacyPage
import com.experience.setupwizard.ui.pages.RegionPage
import com.experience.setupwizard.ui.pages.WelcomePage
import com.experience.setupwizard.ui.pages.WifiPage

@Composable
fun SetupScreen(vm: SetupViewModel, onFinished: () -> Unit) {
    val page by vm.page.collectAsState()
    val message by vm.message.collectAsState()
    val completion by vm.completion.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); vm.consumeMessage() }
    }
    LaunchedEffect(completion) {
        val c = completion
        if (c is Completion.Done && c.failures.isEmpty()) onFinished()
    }
    // Back goes to the previous page; on the first page it does nothing (setup cannot be left).
    BackHandler { vm.back() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            if (page != SetupPage.WELCOME) ProgressHeader(page)
            AnimatedContent(
                targetState = page,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    val forward = targetState.ordinal > initialState.ordinal
                    val sign = if (forward) 1 else -1
                    (slideInHorizontally(ExperienceMotion.defaultSpatial<IntOffset>()) { sign * it / 3 } + fadeIn(ExperienceMotion.defaultEffects()))
                        .togetherWith(slideOutHorizontally(ExperienceMotion.defaultSpatial<IntOffset>()) { -sign * it / 3 } + fadeOut(ExperienceMotion.fastEffects()))
                        .using(SizeTransform(clip = false))
                },
                label = "page",
            ) { p ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    Box(Modifier.widthIn(max = 1040.dp).fillMaxSize()) { PageContent(p, vm) }
                }
            }
            if (page != SetupPage.WELCOME) BottomBar(page, vm, completion is Completion.Running)
        }
    }

    val c = completion
    if (c is Completion.Done && c.failures.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Some settings weren't applied") },
            text = { Text(c.failures.joinToString("\n") { "• $it" } + "\n\nYou can change them later in Settings.") },
            confirmButton = { TextButton(onClick = onFinished) { Text("Continue") } },
        )
    }
}

@Composable
private fun PageContent(page: SetupPage, vm: SetupViewModel) = when (page) {
    SetupPage.WELCOME -> WelcomePage(onStart = vm::next)
    SetupPage.LANGUAGE -> LanguagePage(vm)
    SetupPage.REGION -> RegionPage(vm)
    SetupPage.ACCESSIBILITY -> AccessibilityPage(vm)
    SetupPage.WIFI -> WifiPage(vm)
    SetupPage.DATE_TIME -> DateTimePage(vm)
    SetupPage.PRIVACY -> PrivacyPage(vm)
    SetupPage.COPY_DATA -> CopyDataPage(vm)
    SetupPage.ACCOUNTS -> AccountsPage(vm)
    SetupPage.NAVIGATION -> NavigationPage(vm)
    SetupPage.DONE -> DonePage(vm)
}

@Composable
private fun ProgressHeader(page: SetupPage) {
    val progress = SetupFlow.progress(page)
    val label = if (page == SetupPage.DONE) "Setup complete" else "Step ${SetupFlow.step(page)} of ${SetupFlow.stepCount}"
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp).semantics(mergeDescendants = true) { contentDescription = label }) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(6.dp))
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun BottomBar(page: SetupPage, vm: SetupViewModel, finishing: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = vm::back, modifier = Modifier.heightIn(min = 48.dp), enabled = !finishing) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Back")
        }
        Spacer(Modifier.weight(1f))
        if (page.skippable) {
            TextButton(onClick = vm::skip, modifier = Modifier.heightIn(min = 48.dp)) { Text("Skip") }
            Spacer(Modifier.width(8.dp))
        }
        if (page == SetupPage.DONE) {
            Button(onClick = vm::finish, enabled = !finishing, shape = ExperienceShape.pill, modifier = Modifier.heightIn(min = 56.dp)) {
                if (finishing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Finish", style = MaterialTheme.typography.titleMedium)
            }
        } else {
            Button(onClick = vm::next, shape = ExperienceShape.pill, modifier = Modifier.heightIn(min = 56.dp)) {
                Text("Next", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
            }
        }
    }
}
