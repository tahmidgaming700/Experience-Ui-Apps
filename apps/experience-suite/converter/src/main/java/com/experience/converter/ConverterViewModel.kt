package com.experience.converter

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.experience.converter.model.Category
import com.experience.converter.model.RecentConversion
import com.experience.converter.model.RecentsCodec
import com.experience.converter.model.RecentsStore
import com.experience.converter.model.UnitCatalog

/** Input and unit choice for one category. */
data class CategoryState(val input: String, val fromId: String, val toId: String)

class ConverterViewModel(app: Application) : AndroidViewModel(app) {
    private val store = RecentsStore(app)

    var selected by mutableStateOf<Category?>(null)
        private set

    val recents = mutableStateListOf<RecentConversion>().apply { addAll(store.load()) }

    private val states = mutableStateMapOf<Category, CategoryState>()

    fun state(category: Category): CategoryState = states[category] ?: UnitCatalog.defaults(category).let { (f, t) ->
        CategoryState(if (category == Category.NUMBER_BASE) "255" else "1", f, t)
    }

    fun update(category: Category, state: CategoryState) { states[category] = state }

    fun open(category: Category?) { selected = category }

    fun openRecent(r: RecentConversion) {
        states[r.category] = CategoryState(r.input, r.fromId, r.toId)
        selected = r.category
    }

    /** Remembers the current conversion of [category] (called after the input settles). */
    fun remember(category: Category) {
        val s = state(category)
        if (s.input.isBlank()) return
        val next = RecentsCodec.push(recents.toList(), RecentConversion(category, s.fromId, s.toId, s.input))
        if (next == recents.toList()) return
        recents.clear(); recents.addAll(next)
        store.save(next)
    }

    fun clearRecents() { recents.clear(); store.clear() }
}
