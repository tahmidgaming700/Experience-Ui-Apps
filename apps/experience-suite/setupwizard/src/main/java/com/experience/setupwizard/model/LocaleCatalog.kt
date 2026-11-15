package com.experience.setupwizard.model

import java.text.Collator
import java.text.Normalizer
import java.util.Locale

/** A language (language + optional script), named in its own language and in the UI language. */
data class LanguageOption(val tag: String, val nativeName: String, val localizedName: String)

/** A region for a language, named in that language. */
data class RegionOption(val country: String, val name: String)

/**
 * Builds the language and region lists from the locales the system image ships
 * resources for (Resources.getSystem().assets.locales), like the AOSP locale picker.
 */
object LocaleCatalog {

    /** Pseudo-locales (en-XA, ar-XB) and empty/undetermined tags are not offered. */
    private fun usable(locale: Locale) =
        locale.language.isNotEmpty() && locale.language != "und" && locale.country != "XA" && locale.country != "XB"

    fun parse(tags: Array<String>): List<Locale> =
        tags.map { Locale.forLanguageTag(it.replace('_', '-')) }.filter(::usable)

    /** Language identity: language plus script (keeps sr-Latn apart from sr). */
    fun languageTagOf(locale: Locale): String =
        Locale.Builder().setLanguage(locale.language).setScript(locale.script).build().toLanguageTag()

    private fun titleCase(text: String, locale: Locale) =
        text.replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }

    fun languages(tags: Array<String>, uiLocale: Locale = Locale.getDefault()): List<LanguageOption> {
        val collator = Collator.getInstance(uiLocale).apply { strength = Collator.PRIMARY }
        return parse(tags)
            .map { languageTagOf(it) }
            .distinct()
            .map { tag ->
                val l = Locale.forLanguageTag(tag)
                LanguageOption(tag, titleCase(l.getDisplayName(l), l), titleCase(l.getDisplayName(uiLocale), uiLocale))
            }
            .sortedWith { a, b -> collator.compare(a.nativeName, b.nativeName) }
    }

    /** Regions the system has resources for in [languageTag], named in that language. */
    fun regions(tags: Array<String>, languageTag: String): List<RegionOption> {
        val language = Locale.forLanguageTag(languageTag)
        val collator = Collator.getInstance(language).apply { strength = Collator.PRIMARY }
        return parse(tags)
            .filter { languageTagOf(it) == languageTag && it.country.isNotEmpty() }
            .map { it.country }
            .distinct()
            .map { RegionOption(it, regionName(it, language)) }
            .sortedWith { a, b -> collator.compare(a.name, b.name) }
    }

    /** Every ISO country, for "show all regions". */
    fun allRegions(languageTag: String): List<RegionOption> {
        val language = Locale.forLanguageTag(languageTag)
        val collator = Collator.getInstance(language).apply { strength = Collator.PRIMARY }
        return Locale.getISOCountries()
            .map { RegionOption(it, regionName(it, language)) }
            .sortedWith { a, b -> collator.compare(a.name, b.name) }
    }

    fun regionName(country: String, inLanguage: Locale): String =
        Locale.Builder().setRegion(country).build().getDisplayCountry(inLanguage).ifEmpty { country }

    /** The locale to apply: language (+script) with the chosen region. */
    fun compose(languageTag: String, country: String?): Locale {
        val builder = Locale.Builder().setLanguageTag(languageTag)
        if (!country.isNullOrEmpty()) builder.setRegion(country)
        return builder.build()
    }

    private val marks = Regex("\\p{Mn}+")
    private fun norm(s: String) = marks.replace(Normalizer.normalize(s, Normalizer.Form.NFD), "").lowercase(Locale.ROOT)

    fun filterLanguages(items: List<LanguageOption>, query: String): List<LanguageOption> {
        val q = norm(query.trim())
        if (q.isEmpty()) return items
        return items.filter { norm(it.nativeName).contains(q) || norm(it.localizedName).contains(q) || it.tag.lowercase(Locale.ROOT).startsWith(q) }
    }

    fun filterRegions(items: List<RegionOption>, query: String): List<RegionOption> {
        val q = norm(query.trim())
        if (q.isEmpty()) return items
        return items.filter { norm(it.name).contains(q) || it.country.lowercase(Locale.ROOT) == q }
    }
}
