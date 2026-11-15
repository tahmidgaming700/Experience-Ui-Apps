package com.experience.calendar.logic

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * Date maths for the month view: a grid of whole weeks starting on [firstDayOfWeek]
 * that covers every day of [month] (leading/trailing days come from adjacent months).
 */
object MonthGrid {
    /** Number of whole weeks needed to show [month] (4, 5 or 6). */
    fun weekCount(month: YearMonth, firstDayOfWeek: DayOfWeek): Int {
        val lead = leadingDays(month, firstDayOfWeek)
        return (lead + month.lengthOfMonth() + 6) / 7
    }

    /** Days from the previous month shown before the 1st. */
    fun leadingDays(month: YearMonth, firstDayOfWeek: DayOfWeek): Int =
        (month.atDay(1).dayOfWeek.value - firstDayOfWeek.value + 7) % 7

    fun firstVisibleDay(month: YearMonth, firstDayOfWeek: DayOfWeek): LocalDate =
        month.atDay(1).minusDays(leadingDays(month, firstDayOfWeek).toLong())

    /** All cells, row by row. With [fixedSixWeeks] the grid always has 42 cells (stable height). */
    fun days(month: YearMonth, firstDayOfWeek: DayOfWeek, fixedSixWeeks: Boolean = true): List<LocalDate> {
        val start = firstVisibleDay(month, firstDayOfWeek)
        val count = if (fixedSixWeeks) 42 else weekCount(month, firstDayOfWeek) * 7
        return List(count) { start.plusDays(it.toLong()) }
    }

    /** Weekday header order. */
    fun weekdays(firstDayOfWeek: DayOfWeek): List<DayOfWeek> = List(7) { firstDayOfWeek.plus(it.toLong()) }

    /** Pager helpers: page index <-> month, relative to an anchor month at [anchorPage]. */
    fun monthForPage(anchor: YearMonth, anchorPage: Int, page: Int): YearMonth = anchor.plusMonths((page - anchorPage).toLong())

    fun pageForMonth(anchor: YearMonth, anchorPage: Int, month: YearMonth): Int =
        anchorPage + ChronoUnit.MONTHS.between(anchor, month).toInt()
}
