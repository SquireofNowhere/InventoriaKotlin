package com.inventoria.app.util

/** One item's place in a packed timeline: which lane it sits in, out of how many lanes its
 * overlap cluster needed. Items that overlap nothing get lane 0 of 1 -- full width. */
data class LaneSlot<T>(val item: T, val lane: Int, val laneCount: Int)

/**
 * Packs overlapping intervals into side-by-side lanes so concurrent things stack instead of
 * painting over each other.
 *
 * Items are first grouped into clusters -- maximal runs where each item overlaps the union of the
 * ones before it -- and each cluster is lane-packed greedily: an item takes the first lane whose
 * last occupant has already ended, or opens a new one. Every item in a cluster reports the same
 * [LaneSlot.laneCount], so a cluster's width divides evenly, and the rest of the timeline stays
 * full width. This is the algorithm LinearProductivityChart has always used for the Today
 * timeline, pulled out so the Schedule day view can pack its blocks and tasks the same way.
 *
 * [start] and [end] can be in any unit (day ratios, minutes) as long as they agree. Output order
 * is by start.
 */
fun <T> packIntoLanes(items: List<T>, start: (T) -> Float, end: (T) -> Float): List<LaneSlot<T>> {
    val sorted = items.sortedBy(start)
    val result = mutableListOf<LaneSlot<T>>()

    var cluster = mutableListOf<T>()
    var clusterEnd = Float.NEGATIVE_INFINITY

    fun flushCluster() {
        if (cluster.isEmpty()) return
        // lane index -> end of the last item placed in that lane
        val laneEnds = mutableListOf<Float>()
        val lanes = IntArray(cluster.size)
        cluster.forEachIndexed { index, item ->
            val itemStart = start(item)
            val lane = laneEnds.indexOfFirst { laneEnd -> itemStart >= laneEnd }
            if (lane >= 0) {
                laneEnds[lane] = end(item)
                lanes[index] = lane
            } else {
                lanes[index] = laneEnds.size
                laneEnds.add(end(item))
            }
        }
        val laneCount = laneEnds.size
        cluster.forEachIndexed { index, item -> result.add(LaneSlot(item, lanes[index], laneCount)) }
        cluster = mutableListOf()
    }

    for (item in sorted) {
        if (start(item) >= clusterEnd) {
            flushCluster()
            clusterEnd = end(item)
        } else {
            clusterEnd = maxOf(clusterEnd, end(item))
        }
        cluster.add(item)
    }
    flushCluster()
    return result
}

/** One item's place in a cascaded timeline: how many other items were still running when it
 * started ([level]) and the deepest level anywhere in its overlap cluster ([maxLevel]), which the
 * caller needs to size the indent step so the deepest card still has room. */
data class CascadeSlot<T>(val item: T, val level: Int, val maxLevel: Int)

/**
 * Cascades overlapping intervals instead of splitting the width between them: an item is indented
 * one step for every other item still running when it starts, and every card runs to the right
 * edge. Items that start after everything else has finished sit at level 0 -- full width -- so
 * sequential neighbours line up; a card that starts inside another sits on top of it, indented, so
 * the earlier card's title (top-left) stays readable and a sub-task reads as nested in its parent.
 *
 * Unlike [packIntoLanes] a card never shrinks below the width left after its indent, so the caller
 * caps the step at (available width / 2) / [CascadeSlot.maxLevel]. Output is ordered by start, then
 * longest first, which is also the draw order: later starts land on top.
 */
fun <T> cascadeByDepth(items: List<T>, start: (T) -> Float, end: (T) -> Float): List<CascadeSlot<T>> {
    val sorted = items.sortedWith(compareBy<T> { start(it) }.thenByDescending { end(it) })
    val levels = IntArray(sorted.size) { index ->
        val itemStart = start(sorted[index])
        (0 until index).count { end(sorted[it]) > itemStart }
    }

    val result = ArrayList<CascadeSlot<T>>(sorted.size)
    var clusterFirst = 0
    var clusterEnd = Float.NEGATIVE_INFINITY
    fun flushCluster(until: Int) {
        val maxLevel = (clusterFirst until until).maxOfOrNull { levels[it] } ?: 0
        for (i in clusterFirst until until) result.add(CascadeSlot(sorted[i], levels[i], maxLevel))
        clusterFirst = until
    }
    sorted.forEachIndexed { index, item ->
        if (start(item) >= clusterEnd) {
            flushCluster(index)
            clusterEnd = end(item)
        } else {
            clusterEnd = maxOf(clusterEnd, end(item))
        }
    }
    flushCluster(sorted.size)
    return result
}
