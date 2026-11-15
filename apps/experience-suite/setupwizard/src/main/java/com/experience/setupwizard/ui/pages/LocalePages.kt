package com.experience.setupwizard.ui.pages

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Public
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.experience.design.components.SwitchRow
import com.experience.setupwizard.SetupViewModel
import com.experience.setupwizard.model.LocaleCatalog
import com.experience.setupwizard.ui.ChoiceRow
import com.experience.setupwizard.ui.PageLayout
import com.experience.setupwizard.ui.SearchField
import java.util.Locale

@Composable
fun LanguagePage(vm: SetupViewModel) {
    val selected by vm.languageTag.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    val items = remember(query) { LocaleCatalog.filterLanguages(vm.languages, query) }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = vm.languages.indexOfFirst { it.tag == selected }.coerceAtLeast(0))
    PageLayout(Icons.Outlined.Language, "Choose your language", "Experience UI and your apps will use this language.") {
        Column(Modifier.fillMaxSize()) {
            SearchField(query, { query = it }, "Search languages")
            LazyColumn(state = state, contentPadding = PaddingValues(bottom = 16.dp)) {
                itemsIndexed(items, key = { _, it -> it.tag }) { i, lang ->
                    ChoiceRow(
                        title = lang.nativeName,
                        summary = lang.localizedName.takeIf { it != lang.nativeName },
                        selected = lang.tag == selected,
                        index = i, count = items.size,
                    ) { vm.selectLanguage(lang.tag) }
                }
            }
        }
    }
}

@Composable
fun RegionPage(vm: SetupViewModel) {
    val language by vm.languageTag.collectAsState()
    val selected by vm.region.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    val offered = remember(language) { vm.regions() }
    var showAll by rememberSaveable { mutableStateOf(offered.isEmpty()) }
    val all = remember(language, showAll) { if (showAll) LocaleCatalog.allRegions(language) else offered }
    val items = remember(all, query) { LocaleCatalog.filterRegions(all, query) }
    val languageName = remember(language) { Locale.forLanguageTag(language).let { it.getDisplayName(it) } }
    PageLayout(Icons.Outlined.Public, "Choose your region", "Sets formats for dates, numbers and units, and the country variant of $languageName.") {
        Column(Modifier.fillMaxSize()) {
            SearchField(query, { query = it }, "Search regions")
            if (offered.isNotEmpty()) {
                SwitchRow("Show all regions", showAll, { showAll = it }, 0, 1)
            }
            LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp)) {
                itemsIndexed(items, key = { _, it -> it.country }) { i, region ->
                    ChoiceRow(region.name, region.country == selected, i, items.size) { vm.selectRegion(region.country) }
                }
            }
        }
    }
}
