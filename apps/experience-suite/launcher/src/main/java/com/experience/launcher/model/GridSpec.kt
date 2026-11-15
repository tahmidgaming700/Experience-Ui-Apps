package com.experience.launcher.model

/** Pure grid arithmetic shared by the home pages and the app drawer. */
object GridSpec {
    /** Minimum width of one drawer cell; columns grow with the screen width. */
    const val DRAWER_CELL_DP = 88
    const val HOME_CELL_HEIGHT_DP = 104

    /** Drawer columns for an available width: 4 on phones up to 8 on large tablets. */
    fun drawerColumns(widthDp: Int): Int = (widthDp / DRAWER_CELL_DP).coerceIn(4, 8)

    /** Home rows that fit in [heightDp], between 3 and 7. */
    fun homeRows(heightDp: Int): Int = (heightDp / HOME_CELL_HEIGHT_DP).coerceIn(3, 7)

    /** Allowed home column counts for a width (wider screens may use more columns). */
    fun homeColumnChoices(widthDp: Int): List<Int> = if (widthDp >= 600) listOf(4, 5, 6, 7) else listOf(3, 4, 5)

    /** Clamps a stored preference to the choices available on this screen. */
    fun effectiveHomeColumns(preferred: Int, widthDp: Int): Int {
        val choices = homeColumnChoices(widthDp)
        return preferred.coerceIn(choices.first(), choices.last())
    }

    /** Splits items into pages of [perPage]; always at least one (possibly empty) page. */
    fun <T> pages(items: List<T>, perPage: Int): List<List<T>> {
        if (perPage <= 0 || items.isEmpty()) return listOf(emptyList())
        return items.chunked(perPage)
    }
}
