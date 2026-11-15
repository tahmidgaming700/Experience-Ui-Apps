package com.experience.calendar.logic

/**
 * Side-by-side placement of overlapping timed events in the day view.
 * Events that overlap (transitively) form a cluster; each gets the first free column and
 * the cluster's column count decides the width.
 */
object DayLayout {
    data class Placed<T>(val item: T, val start: Long, val end: Long, val column: Int, val columns: Int)

    fun <T> layout(items: List<T>, start: (T) -> Long, end: (T) -> Long): List<Placed<T>> {
        val sorted = items.sortedWith(compareBy<T>({ start(it) }, { -end(it) }))
        val result = ArrayList<Placed<T>>()
        var cluster = ArrayList<Pair<T, Int>>()
        val columnEnds = ArrayList<Long>()
        var clusterEnd = Long.MIN_VALUE

        fun flush() {
            val cols = columnEnds.size.coerceAtLeast(1)
            cluster.forEach { (item, col) -> result += Placed(item, start(item), end(item), col, cols) }
            cluster = ArrayList()
            columnEnds.clear()
        }

        for (item in sorted) {
            val s = start(item)
            // Zero-length events still occupy a minimal slot.
            val e = maxOf(end(item), s + 1)
            if (cluster.isNotEmpty() && s >= clusterEnd) flush()
            var col = columnEnds.indexOfFirst { it <= s }
            if (col == -1) { columnEnds += e; col = columnEnds.size - 1 } else columnEnds[col] = e
            cluster += item to col
            clusterEnd = if (cluster.size == 1) e else maxOf(clusterEnd, e)
        }
        if (cluster.isNotEmpty()) flush()
        return result
    }
}
