package com.experience.launcher.model

/**
 * The user's pinned apps: an ordered home grid (paged by the UI) and a dock.
 * Immutable; every operation returns a new layout. An app can be on the home grid
 * and in the dock at the same time, but appears at most once in each.
 */
data class HomeLayout(val home: List<AppKey> = emptyList(), val dock: List<AppKey> = emptyList()) {

    fun isOnHome(key: AppKey) = key in home
    fun isInDock(key: AppKey) = key in dock
    fun dockIsFull() = dock.size >= DOCK_CAPACITY

    fun pinToHome(key: AppKey): HomeLayout = if (key in home) this else copy(home = home + key)
    fun unpinFromHome(key: AppKey): HomeLayout = copy(home = home - key)

    /** Adds to the end of the dock; ignored when the dock is full. */
    fun pinToDock(key: AppKey): HomeLayout =
        if (key in dock || dockIsFull()) this else copy(dock = dock + key)

    fun unpinFromDock(key: AppKey): HomeLayout = copy(dock = dock - key)

    /** Moves a home item by [delta] positions, clamped to the list bounds. */
    fun moveOnHome(key: AppKey, delta: Int): HomeLayout = copy(home = home.moved(key, delta))
    fun moveInDock(key: AppKey, delta: Int): HomeLayout = copy(dock = dock.moved(key, delta))

    /** Drops every pin of [packageName] in the profile [userSerial] (app uninstalled). */
    fun removePackage(packageName: String, userSerial: Long): HomeLayout {
        fun keep(k: AppKey) = !(k.packageName == packageName && k.userSerial == userSerial)
        return copy(home = home.filter(::keep), dock = dock.filter(::keep))
    }

    /** Drops every pin belonging to a profile that no longer exists. */
    fun removeProfile(userSerial: Long): HomeLayout =
        copy(home = home.filter { it.userSerial != userSerial }, dock = dock.filter { it.userSerial != userSerial })

    companion object {
        const val DOCK_CAPACITY = 5

        fun encodeList(keys: List<AppKey>): String = keys.joinToString("\n") { it.encode() }
        fun decodeList(value: String?): List<AppKey> =
            value.orEmpty().lineSequence().mapNotNull { AppKey.decode(it.trim()) }.distinct().toList()

        private fun List<AppKey>.moved(key: AppKey, delta: Int): List<AppKey> {
            val from = indexOf(key)
            if (from < 0 || delta == 0) return this
            val to = (from + delta).coerceIn(0, size - 1)
            return toMutableList().apply { removeAt(from); add(to, key) }
        }
    }
}
