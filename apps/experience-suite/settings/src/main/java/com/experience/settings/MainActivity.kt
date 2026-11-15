package com.experience.settings

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.experience.design.ExperienceActivity
import com.experience.design.components.WidthClass
import com.experience.design.components.currentWidthClass
import com.experience.design.theme.ExperienceMotion
import com.experience.settings.ui.Hub
import com.experience.settings.ui.pages.PageContent

/**
 * Experience Settings hub. Also opened directly on a page by:
 * - [ACTION_ABOUT] (the ROM's patched AOSP Settings) -> About Experience UI,
 * - android.settings.SYSTEM_UPDATE_SETTINGS -> System updates,
 * - [EXTRA_PAGE] with a [Page] name.
 */
class MainActivity : ExperienceActivity() {
    companion object {
        const val ACTION_ABOUT = "com.experience.settings.action.ABOUT"
        const val EXTRA_PAGE = "com.experience.settings.extra.PAGE"
    }

    private fun initialPage(): Page? = when (intent?.action) {
        ACTION_ABOUT -> Page.ABOUT
        Settings.ACTION_SYSTEM_UPDATE_SETTINGS -> Page.UPDATES
        else -> intent?.getStringExtra(EXTRA_PAGE)?.let { n -> Page.entries.firstOrNull { it.name == n } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Already running (singleTop): jump to the requested page.
        initialPage()?.let { requested.value = it }
    }

    private val requested = mutableStateOf<Page?>(null)

    @Composable
    override fun Content() {
        val direct = initialPage()
        var pageName by rememberSaveable { mutableStateOf(direct?.name) }
        var query by rememberSaveable { mutableStateOf("") }
        requested.value?.let { pageName = it.name; requested.value = null }
        val page = pageName?.let { Page.valueOf(it) }
        val wide = currentWidthClass() != WidthClass.COMPACT
        // Opened straight onto a page from elsewhere: Back leaves the app, like AOSP subsettings.
        val back: () -> Unit = { if (direct != null && page == direct) finish() else pageName = null }

        if (wide) {
            Row {
                Box(Modifier.width(if (currentWidthClass() == WidthClass.EXPANDED) 400.dp else 320.dp).fillMaxHeight()) {
                    Hub(query, { query = it }, page ?: Page.NETWORK) { pageName = it.name }
                }
                VerticalDivider()
                Box(Modifier.weight(1f)) {
                    AnimatedContent(page ?: Page.NETWORK, transitionSpec = { fadeIn(ExperienceMotion.defaultEffects()) togetherWith fadeOut(ExperienceMotion.fastEffects()) }, label = "detail") { p ->
                        PageContent(p, onBack = null, onOpen = { pageName = it.name })
                    }
                }
            }
        } else {
            BackHandler(enabled = page != null, onBack = back)
            AnimatedContent(
                page,
                transitionSpec = {
                    (slideInHorizontally(ExperienceMotion.defaultSpatial()) { if (targetState != null) it / 4 else -it / 4 } + fadeIn(ExperienceMotion.defaultEffects())) togetherWith
                        fadeOut(ExperienceMotion.fastEffects())
                },
                label = "page",
            ) { p ->
                if (p == null) Hub(query, { query = it }, null) { pageName = it.name }
                else PageContent(p, onBack = back, onOpen = { pageName = it.name })
            }
        }
    }
}
