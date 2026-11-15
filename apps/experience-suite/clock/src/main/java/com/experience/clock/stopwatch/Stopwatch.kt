package com.experience.clock.stopwatch

/**
 * Stopwatch state on the elapsed-realtime clock. [laps] holds the cumulative time at each
 * lap press; lap durations are derived. Pure, so it can be unit-tested.
 */
data class Stopwatch(
    val running: Boolean = false,
    val startElapsed: Long = 0,
    val accumulatedMs: Long = 0,
    val laps: List<Long> = emptyList(),
) {
    fun elapsed(now: Long): Long = accumulatedMs + if (running) (now - startElapsed).coerceAtLeast(0) else 0

    fun start(now: Long) = if (running) this else copy(running = true, startElapsed = now)
    fun pause(now: Long) = if (!running) this else copy(running = false, accumulatedMs = elapsed(now))
    fun reset() = Stopwatch()
    fun lap(now: Long) = if (!running) this else copy(laps = laps + elapsed(now))

    val isReset get() = !running && accumulatedMs == 0L && laps.isEmpty()

    /** Duration of each completed lap, oldest first. */
    fun lapDurations(): List<Long> = laps.mapIndexed { i, t -> t - (if (i == 0) 0 else laps[i - 1]) }

    /** Indices of the fastest and slowest laps (only meaningful with 2+ laps; ties keep the first). */
    fun bestAndWorst(): Pair<Int, Int>? {
        val d = lapDurations()
        if (d.size < 2) return null
        val best = d.indices.minByOrNull { d[it] }!!
        val worst = d.indices.maxByOrNull { d[it] }!!
        return if (d[best] == d[worst]) null else best to worst
    }

    /** After a reboot the elapsed clock restarts; keep the time measured so far, paused. */
    fun afterReboot(now: Long): Stopwatch =
        if (running && startElapsed > now) copy(running = false) else this
}
