package com.experience.clock.timer

import org.json.JSONArray
import org.json.JSONObject

/**
 * Countdown timer state. Running timers keep an end time on the elapsed-realtime clock
 * (immune to wall-clock changes) plus the wall-clock end used to recover after a reboot.
 * All transitions are pure functions of the current clock readings.
 */
data class Timer(
    val id: Int,
    val lengthMs: Long,
    val label: String = "",
    val state: State = State.RESET,
    /** Remaining time while RESET/PAUSED. */
    val remainingMs: Long = lengthMs,
    /** End on SystemClock.elapsedRealtime() while RUNNING/EXPIRED. */
    val endElapsed: Long = 0,
    val endWall: Long = 0,
) {
    enum class State { RESET, RUNNING, PAUSED, EXPIRED }

    /** Remaining time; negative while EXPIRED (overtime). */
    fun remaining(nowElapsed: Long): Long = when (state) {
        State.RUNNING, State.EXPIRED -> endElapsed - nowElapsed
        else -> remainingMs
    }

    fun start(nowElapsed: Long, nowWall: Long): Timer =
        if (state == State.RUNNING) this
        else {
            val r = if (state == State.EXPIRED || remainingMs <= 0) lengthMs else remainingMs
            copy(state = State.RUNNING, endElapsed = nowElapsed + r, endWall = nowWall + r)
        }

    fun pause(nowElapsed: Long): Timer =
        if (state != State.RUNNING) this else copy(state = State.PAUSED, remainingMs = (endElapsed - nowElapsed).coerceAtLeast(0))

    fun reset(): Timer = copy(state = State.RESET, remainingMs = lengthMs, endElapsed = 0, endWall = 0)

    fun expire(): Timer = if (state == State.RUNNING) copy(state = State.EXPIRED) else this

    /** +1 minute: extends a running/paused timer; an expired timer restarts with one minute. */
    fun addMinute(nowElapsed: Long, nowWall: Long): Timer = when (state) {
        State.RUNNING -> copy(endElapsed = endElapsed + MINUTE, endWall = endWall + MINUTE)
        State.PAUSED, State.RESET -> copy(remainingMs = remainingMs + MINUTE)
        State.EXPIRED -> copy(state = State.RUNNING, endElapsed = nowElapsed + MINUTE, endWall = nowWall + MINUTE)
    }

    /** After a reboot the elapsed clock restarted: rebuild the end from the wall-clock end. */
    fun afterReboot(nowElapsed: Long, nowWall: Long): Timer = when (state) {
        State.RUNNING, State.EXPIRED -> {
            val left = endWall - nowWall
            copy(state = if (left <= 0) State.EXPIRED else State.RUNNING, endElapsed = nowElapsed + left)
        }
        else -> this
    }

    companion object {
        const val MINUTE = 60_000L

        fun toJson(list: List<Timer>): String = JSONArray().also { arr ->
            list.forEach {
                arr.put(JSONObject().put("id", it.id).put("len", it.lengthMs).put("label", it.label).put("state", it.state.name)
                    .put("rem", it.remainingMs).put("endE", it.endElapsed).put("endW", it.endWall))
            }
        }.toString()

        fun fromJson(raw: String?): List<Timer> = runCatching {
            val arr = JSONArray(raw ?: return emptyList())
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Timer(o.getInt("id"), o.getLong("len"), o.optString("label"), State.valueOf(o.getString("state")),
                    o.getLong("rem"), o.getLong("endE"), o.getLong("endW"))
            }
        }.getOrDefault(emptyList())
    }
}
