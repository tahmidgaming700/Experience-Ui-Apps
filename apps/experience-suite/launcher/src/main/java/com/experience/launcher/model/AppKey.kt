package com.experience.launcher.model

/**
 * Stable identity of a launchable activity in one user profile.
 *
 * The profile is identified by its user serial number
 * (UserManager.getSerialNumberForUser), which, unlike the user id, is never reused,
 * so persisted pins cannot silently move to a different profile.
 */
data class AppKey(val packageName: String, val className: String, val userSerial: Long) {

    fun encode(): String = "$packageName/$className#$userSerial"

    companion object {
        /** Parses [encode]'s output; returns null for malformed input. */
        fun decode(value: String): AppKey? {
            val hash = value.lastIndexOf('#')
            val slash = value.indexOf('/')
            if (hash <= 0 || slash <= 0 || slash >= hash - 1) return null
            val serial = value.substring(hash + 1).toLongOrNull() ?: return null
            val pkg = value.substring(0, slash)
            val cls = value.substring(slash + 1, hash)
            if (pkg.isBlank() || cls.isBlank()) return null
            return AppKey(pkg, cls, serial)
        }
    }
}
