package com.experience.setupwizard

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.experience.setupwizard.model.DisplaySize
import com.experience.setupwizard.model.LanguageOption
import com.experience.setupwizard.model.LocaleCatalog
import com.experience.setupwizard.model.SetupFlow
import com.experience.setupwizard.model.SetupPage
import com.experience.setupwizard.system.NavMode
import com.experience.setupwizard.system.SetupCompletion
import com.experience.setupwizard.system.SystemSetup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.TimeZone

/** Values of the system settings the wizard shows; refreshed from [SystemSetup]. */
data class SystemState(
    val fontScale: Float = 1f,
    val defaultDensity: Int? = null,
    val density: Int = 0,
    val inversion: Boolean = false,
    val highContrast: Boolean = false,
    val autoTime: Boolean = true,
    val autoTimeZone: Boolean = true,
    val timeZoneId: String = TimeZone.getDefault().id,
    val location: Boolean = false,
    val navMode: NavMode? = null,
)

/** Result of the final step. */
sealed interface Completion {
    data object Idle : Completion
    data object Running : Completion
    data class Done(val failures: List<String>) : Completion
}

/**
 * Wizard state. Page and choices live in [SavedStateHandle], so they survive the
 * activity being recreated by the locale, font-size and display-size changes the
 * wizard itself applies (and process death).
 */
class SetupViewModel(
    private val handle: SavedStateHandle,
    private val system: SystemSetup,
    private val localeTags: Array<String>,
) : ViewModel() {

    private val pageFlow = MutableStateFlow(SetupFlow.fromName(handle[KEY_PAGE]))
    val page: StateFlow<SetupPage> = pageFlow.asStateFlow()

    val languages: List<LanguageOption> = LocaleCatalog.languages(localeTags)

    private val _languageTag = MutableStateFlow(handle[KEY_LANGUAGE] ?: LocaleCatalog.languageTagOf(Locale.getDefault()))
    val languageTag: StateFlow<String> = _languageTag.asStateFlow()

    private val _region = MutableStateFlow(handle[KEY_REGION] ?: Locale.getDefault().country)
    val region: StateFlow<String> = _region.asStateFlow()

    private val _system = MutableStateFlow(SystemState())
    val systemState: StateFlow<SystemState> = _system.asStateFlow()

    /** One-shot error to show in a snackbar. */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _accounts = MutableStateFlow(handle.get<ArrayList<String>>(KEY_ACCOUNTS) ?: arrayListOf())
    val accountsAdded: StateFlow<List<String>> = _accounts.asStateFlow()

    private val _completion = MutableStateFlow<Completion>(Completion.Idle)
    val completion: StateFlow<Completion> = _completion.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _system.value = SystemState(
            fontScale = system.fontScale(),
            defaultDensity = system.defaultDensity(),
            density = system.currentDensity(),
            inversion = system.isSecureFlagOn(SystemSetup.SECURE_INVERSION),
            highContrast = system.isSecureFlagOn(SystemSetup.SECURE_HIGH_TEXT_CONTRAST),
            autoTime = system.isAutoTime(),
            autoTimeZone = system.isAutoTimeZone(),
            timeZoneId = TimeZone.getDefault().id,
            location = system.isLocationEnabled(),
            navMode = system.navMode(),
        )
    }

    fun regions() = LocaleCatalog.regions(localeTags, _languageTag.value)

    private fun report(result: Result<Unit>): Boolean {
        result.exceptionOrNull()?.let { _message.value = it.message }
        return result.isSuccess
    }

    fun consumeMessage() { _message.value = null }

    // --- Navigation -----------------------------------------------------------------

    private fun go(target: SetupPage) {
        pageFlow.value = target
        handle[KEY_PAGE] = target.name
    }

    fun next() {
        val current = pageFlow.value
        go(SetupFlow.next(current))
        when (current) {
            // Apply after moving on: the configuration change recreates the activity,
            // which then restores the new page from the saved state.
            SetupPage.LANGUAGE -> applyLocale(withRegion = regions().any { it.country == _region.value })
            SetupPage.REGION -> applyLocale(withRegion = true)
            else -> Unit
        }
    }

    fun back() {
        if (!pageFlow.value.isFirst) go(SetupFlow.previous(pageFlow.value))
    }

    fun skip() = go(SetupFlow.next(pageFlow.value))

    // --- Language & region ----------------------------------------------------------

    fun selectLanguage(tag: String) {
        _languageTag.value = tag
        handle[KEY_LANGUAGE] = tag
        // Keep the region only if the system offers it for the new language.
        val offered = LocaleCatalog.regions(localeTags, tag)
        if (offered.none { it.country == _region.value }) {
            selectRegion(offered.firstOrNull()?.country ?: "")
        }
    }

    fun selectRegion(country: String) {
        _region.value = country
        handle[KEY_REGION] = country
    }

    fun chosenLocale(): Locale = LocaleCatalog.compose(_languageTag.value, _region.value.ifEmpty { null })

    private fun applyLocale(withRegion: Boolean) {
        val locale = LocaleCatalog.compose(_languageTag.value, if (withRegion) _region.value.ifEmpty { null } else null)
        if (locale != Locale.getDefault()) report(system.applyLocale(locale))
    }

    // --- Accessibility --------------------------------------------------------------

    fun setFontScale(scale: Float) {
        if (report(system.setFontScale(scale))) _system.update { it.copy(fontScale = scale) }
    }

    fun setDensityStep(index: Int) {
        val base = _system.value.defaultDensity ?: return
        val dpi = DisplaySize.densityFor(base, DisplaySize.densitySteps[index].scale)
        if (report(system.setDensity(dpi))) _system.update { it.copy(density = dpi) }
    }

    fun setInversion(on: Boolean) {
        if (report(system.setSecureFlag(SystemSetup.SECURE_INVERSION, on))) _system.update { it.copy(inversion = on) }
    }

    fun setHighContrast(on: Boolean) {
        if (report(system.setSecureFlag(SystemSetup.SECURE_HIGH_TEXT_CONTRAST, on))) _system.update { it.copy(highContrast = on) }
    }

    // --- Date & time ----------------------------------------------------------------

    fun setAutoTime(on: Boolean) {
        if (report(system.setAutoTime(on))) _system.update { it.copy(autoTime = on) }
    }

    fun setAutoTimeZone(on: Boolean) {
        if (report(system.setAutoTimeZone(on))) _system.update { it.copy(autoTimeZone = on) }
    }

    fun setTimeZone(id: String) {
        if (report(system.setTimeZone(id))) _system.update { it.copy(timeZoneId = id) }
    }

    fun setTime(millis: Long) {
        report(system.setTime(millis))
    }

    // --- Privacy & navigation -------------------------------------------------------

    fun setLocation(on: Boolean) {
        if (report(system.setLocationEnabled(on))) _system.update { it.copy(location = system.isLocationEnabled()) }
    }

    fun setNavMode(mode: NavMode) {
        if (report(system.setNavMode(mode))) _system.update { it.copy(navMode = mode) }
    }

    // --- Accounts -------------------------------------------------------------------

    fun onAccountAdded(description: String) {
        val list = ArrayList(_accounts.value).apply { add(description) }
        _accounts.value = list
        handle[KEY_ACCOUNTS] = list
    }

    fun showMessage(text: String) { _message.value = text }

    // --- Start & finish -------------------------------------------------------------

    fun isProvisioned() = system.isProvisioned()

    fun onStarted() {
        system.setStatusBarExpansionDisabled(true) // best effort; failure is harmless
    }

    fun finish() {
        if (_completion.value == Completion.Running) return
        _completion.value = Completion.Running
        viewModelScope.launch {
            _completion.value = Completion.Done(SetupCompletion(system).run())
        }
    }

    private companion object {
        const val KEY_PAGE = "page"
        const val KEY_LANGUAGE = "language"
        const val KEY_REGION = "region"
        const val KEY_ACCOUNTS = "accounts"
    }
}
