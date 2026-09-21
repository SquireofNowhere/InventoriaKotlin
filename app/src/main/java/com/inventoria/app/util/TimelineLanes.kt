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

/**
 * One item placed by [layoutOverlaps]: its horizontal share of the timeline as fractions of the
 * available width ([left] to [right]), and the position of the item it is nested in ([parent], an
 * index into the same result list, or -1 for none). Parents always come before their children.
 */
data class OverlapSlot<T>(val item: T, val left: Float, val right: Float, val parent: Int)

/** How far into its parent's width a nested item starts, as a share of that width. */
private const val NEST_INDENT = 0.3f

/**
 * Lays out overlapping intervals by when they actually ran, never by how tall their cards end up
 * being drawn. Two cases, and nothing else:
 *
 *  - An item that ran entirely inside another one -- starting at least [nestAfter] later, so the
 *    parent's title row stays clear -- is nested in it: it takes the right-hand share of the
 *    parent's frame, and the caller cuts that rectangle out of the parent's card so the two never
 *    draw over each other. The parent is the smallest item that contains it.
 *  - Everything else that overlaps in time -- partial overlaps, or concurrent sessions that start
 *    together -- is packed into side-by-side lanes with [packIntoLanes], at the same nesting depth.
 *
 * [start], [end] and [nestAfter] must be in the same unit. Because only the real times are used, a
 * short item can never spill into the time of the one after it and pull it into an overlap.
 */
fun <T> layoutOverlaps(
    items: List<T>,
    start: (T) -> Float,
    end: (T) -> Float,
    nestAfter: Float
): List<OverlapSlot<T>> {
    val sorted = items.sortedWith(compareBy<T> { start(it) }.thenByDescending { end(it) })
    val children = List(sorted.size) { mutableListOf<Int>() }
    val roots = mutableListOf<Int>()
    sorted.indices.forEach { i ->
        var parent = -1
        for (j in 0 until i) {
            val contains = start(sorted[j]) + nestAfter <= start(sorted[i]) && end(sorted[j]) >= end(sorted[i])
            if (contains && (parent == -1 || end(sorted[j]) - start(sorted[j]) <= end(sorted[parent]) - start(sorted[parent]))) {
                parent = j
            }
        }
        if (parent == -1) roots.add(i) else children[parent].add(i)
    }

    val result = ArrayList<OverlapSlot<T>>(sorted.size)
    fun place(nodes: List<Int>, left: Float, right: Float, parent: Int) {
        packIntoLanes(nodes, start = { start(sorted[it]) }, end = { end(sorted[it]) }).forEach { slot ->
            val width = (right - left) / slot.laneCount
            val slotLeft = left + width * slot.lane
            val index = result.size
            result.add(OverlapSlot(sorted[slot.item], slotLeft, slotLeft + width, parent))
            val nested = children[slot.item]
            if (nested.isNotEmpty()) place(nested, slotLeft + width * NEST_INDENT, slotLeft + width, index)
        }
    }
    place(roots, 0f, 1f, -1)
    return result
}
