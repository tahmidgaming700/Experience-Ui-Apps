package com.experience.setupwizard.model

/** The setup pages, in order. [skippable] pages show a "Skip" action. */
enum class SetupPage(val skippable: Boolean) {
    WELCOME(false),
    LANGUAGE(false),
    REGION(false),
    ACCESSIBILITY(false),
    WIFI(true),
    DATE_TIME(false),
    PRIVACY(false),
    COPY_DATA(true),
    ACCOUNTS(true),
    NAVIGATION(false),
    DONE(false);

    val isFirst get() = this == entries.first()
    val isLast get() = this == entries.last()
}

/** Pure navigation through [SetupPage]s. */
object SetupFlow {
    fun next(page: SetupPage): SetupPage = SetupPage.entries.getOrElse(page.ordinal + 1) { page }
    fun previous(page: SetupPage): SetupPage = SetupPage.entries.getOrElse(page.ordinal - 1) { page }

    /** 1-based step number among the pages between Welcome and Done. */
    fun step(page: SetupPage): Int = page.ordinal

    /** Number of steps counted by [step] (Welcome and Done are not steps). */
    val stepCount: Int get() = SetupPage.entries.size - 2

    /** Progress in 0..1 for the progress indicator. */
    fun progress(page: SetupPage): Float = (page.ordinal.toFloat() / (SetupPage.entries.size - 1)).coerceIn(0f, 1f)

    fun fromName(name: String?): SetupPage = SetupPage.entries.firstOrNull { it.name == name } ?: SetupPage.WELCOME
}
