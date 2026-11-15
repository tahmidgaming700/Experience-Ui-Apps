package com.experience.settings

/** Top-level sections of Experience Settings. */
enum class Page(val title: String, val summary: String) {
    NETWORK("Network & internet", "Wi-Fi, mobile data, airplane mode"),
    CONNECTED("Connected devices", "Bluetooth, pairing"),
    DISPLAY("Display", "Brightness, dark theme, timeout, font size"),
    STYLE("Style & wallpaper", "Colours, icon shape, font, wallpaper"),
    SOUND("Sound & vibration", "Volume, ringer mode, ringtones"),
    NOTIFICATIONS("Notifications", "Do Not Disturb, notification access"),
    APPS("Apps", "Installed apps, default apps"),
    BATTERY("Battery", "Level, health, battery saver"),
    STORAGE("Storage", "Used and free space"),
    SECURITY("Security & privacy", "Screen lock, permissions, patch level"),
    ACCESSIBILITY("Accessibility", "Display, interaction, services"),
    LOCATION("Location", "Location access"),
    SYSTEM("System", "Languages, date & time, navigation, reset"),
    UPDATES("System updates", "Check for Experience UI updates"),
    ABOUT("About Experience UI", "Version, build, device");
}

/** One searchable setting: shown as a result and opens [page]. */
data class SearchEntry(val title: String, val page: Page, val keywords: List<String> = emptyList())

object SettingsSearch {
    val entries: List<SearchEntry> = buildList {
        Page.entries.forEach { add(SearchEntry(it.title, it, it.summary.split(", ").map(String::lowercase))) }
        fun e(t: String, p: Page, vararg k: String) = add(SearchEntry(t, p, k.toList()))
        e("Wi-Fi", Page.NETWORK, "wifi", "wlan", "wireless", "network")
        e("Airplane mode", Page.NETWORK, "flight", "aeroplane")
        e("Mobile network", Page.NETWORK, "sim", "data", "cellular", "roaming")
        e("Hotspot & tethering", Page.NETWORK, "tether", "hotspot")
        e("VPN", Page.NETWORK)
        e("Bluetooth", Page.CONNECTED, "pair", "headphones", "bt")
        e("Paired devices", Page.CONNECTED, "bonded")
        e("Brightness", Page.DISPLAY, "screen", "dim", "adaptive")
        e("Dark theme", Page.DISPLAY, "night", "dark mode")
        e("Screen timeout", Page.DISPLAY, "sleep", "screen off")
        e("Font size", Page.DISPLAY, "text size", "scale")
        e("Auto-rotate screen", Page.DISPLAY, "rotation", "orientation")
        e("Accent colour", Page.STYLE, "color", "theme", "seed", "palette", "monet")
        e("Match wallpaper", Page.STYLE, "wallpaper colours", "dynamic")
        e("Icon shape", Page.STYLE, "adaptive icon", "squircle")
        e("System font", Page.STYLE, "typeface")
        e("Wallpaper", Page.STYLE, "background", "lock screen")
        e("Ring volume", Page.SOUND, "ringer", "loudness")
        e("Media volume", Page.SOUND, "music")
        e("Alarm volume", Page.SOUND)
        e("Ringtone", Page.SOUND, "phone ringtone", "notification sound", "alarm sound")
        e("Silent mode", Page.SOUND, "vibrate", "mute")
        e("Do Not Disturb", Page.NOTIFICATIONS, "dnd", "quiet", "zen")
        e("Notification dots", Page.NOTIFICATIONS, "badge")
        e("Default apps", Page.APPS, "browser", "launcher", "home app")
        e("App info", Page.APPS, "uninstall", "force stop", "permissions")
        e("Battery saver", Page.BATTERY, "power saving")
        e("Battery percentage", Page.BATTERY)
        e("Battery usage", Page.BATTERY)
        e("Free up space", Page.STORAGE, "files", "cleanup")
        e("Screen lock", Page.SECURITY, "pin", "password", "pattern")
        e("Fingerprint", Page.SECURITY, "biometric", "face")
        e("Permission manager", Page.SECURITY, "privacy", "permissions")
        e("Security patch level", Page.SECURITY, "android security update")
        e("Show passwords", Page.SECURITY)
        e("Install unknown apps", Page.SECURITY, "sideload", "apk")
        e("Remove animations", Page.ACCESSIBILITY, "motion", "animation scale")
        e("Colour inversion", Page.ACCESSIBILITY, "invert")
        e("High contrast text", Page.ACCESSIBILITY)
        e("Captions", Page.ACCESSIBILITY, "subtitles")
        e("Use location", Page.LOCATION, "gps")
        e("Languages", Page.SYSTEM, "language", "locale", "region")
        e("Keyboard", Page.SYSTEM, "input", "ime")
        e("Date & time", Page.SYSTEM, "clock", "time zone")
        e("Navigation mode", Page.SYSTEM, "gestures", "3-button", "navbar")
        e("Developer options", Page.SYSTEM, "adb", "usb debugging")
        e("Reset options", Page.SYSTEM, "factory reset", "erase")
        e("Check for update", Page.UPDATES, "ota", "upgrade", "firmware")
        e("Build number", Page.ABOUT, "version")
        e("Android version", Page.ABOUT)
        e("Kernel version", Page.ABOUT)
        e("Device model", Page.ABOUT, "phone", "hardware")
        e("Open source licences", Page.ABOUT, "licenses", "legal")
    }

    /** Ranked results: title prefix, then word prefix in title, then title/keyword substring. */
    fun search(query: String, source: List<SearchEntry> = entries): List<SearchEntry> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        fun rank(e: SearchEntry): Int? {
            val t = e.title.lowercase()
            return when {
                t.startsWith(q) -> 0
                t.split(' ', '-', '&').any { it.startsWith(q) } -> 1
                t.contains(q) -> 2
                e.keywords.any { it.startsWith(q) } -> 3
                e.keywords.any { it.contains(q) } -> 4
                else -> null
            }
        }
        return source.mapNotNull { e -> rank(e)?.let { it to e } }
            .sortedWith(compareBy({ it.first }, { it.second.title.length }))
            .map { it.second }
            .distinctBy { it.title to it.page }
    }
}
