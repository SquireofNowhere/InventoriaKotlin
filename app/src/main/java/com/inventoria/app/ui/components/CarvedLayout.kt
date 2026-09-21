package com.inventoria.app.ui.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/** Each level of a carved cascade steps in by this share of the area's width... */
private const val CARVE_MAX_STEP = 0.28f

/** ...but the deepest level never starts past this share of it, so its card keeps room for text. */
private const val CARVE_MAX_INDENT = 0.55f

private const val CARD_CORNER_DP = 6f

/** A card's frame, in dp, within its timeline. */
data class CardFrame(val left: Dp, val top: Dp, val width: Dp, val height: Dp) {
    val bottom: Dp get() = top + height
}

/** A rectangle cut out of a card, in dp relative to the card's top-left. */
data class CarveRect(val left: Float, val top: Float, val right: Float, val bottom: Float)

/**
 * A card's rounded rectangle with the deeper cards' rectangles cut out of it. That is how
 * overlapping tasks are drawn on the timelines: a task that runs inside another one takes a notch
 * out of the parent's card instead of covering it, so the parent's title and left edge stay whole.
 */
data class CarvedShape(val cutouts: List<CarveRect> = emptyList()) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val d = density.density
        val corner = CornerRadius(CARD_CORNER_DP * d)
        val card = RoundRect(0f, 0f, size.width, size.height, corner)
        if (cutouts.isEmpty()) return Outline.Rounded(card)
        val base = Path().apply { addRoundRect(card) }
        val cut = Path().apply {
            cutouts.forEach { addRoundRect(RoundRect(it.left * d, it.top * d, it.right * d, it.bottom * d, corner)) }
        }
        return Outline.Generic(Path.combine(PathOperation.Difference, base, cut))
    }

    /**
     * How far in from the right edge text has to stop to stay clear of a cutout that reaches into
     * the card's top [band] -- the rows where its title and time sit.
     */
    fun endInset(band: Dp, cardWidth: Dp): Dp {
        val widest = cutouts
            .filter { it.top < band.value && it.bottom > 0f }
            .maxOfOrNull { cardWidth.value - it.left }
            ?: return 0.dp
        return widest.coerceAtLeast(0f).dp
    }
}

/**
 * The frame of a card at [level] in an area [areaWidth] wide: indented one step per level and
 * running to the right edge. The step shrinks with [maxLevel] so the deepest card keeps at least
 * `1 - CARVE_MAX_INDENT` of the width.
 */
fun carveFrame(areaWidth: Dp, level: Int, maxLevel: Int, top: Dp, height: Dp): CardFrame {
    val step = if (maxLevel == 0) 0f else minOf(CARVE_MAX_STEP, CARVE_MAX_INDENT / maxLevel)
    val left = areaWidth * (step * level)
    return CardFrame(left, top, areaWidth - left, height)
}

/**
 * The shape for each of [frames]: its own rectangle minus every deeper card that overlaps it in
 * time, plus [gap] of clear space around each notch.
 */
fun carveShapes(levels: List<Int>, frames: List<CardFrame>, gap: Dp = 2.dp): List<CarvedShape> =
    frames.indices.map { i ->
        val me = frames[i]
        val cuts = frames.indices
            .filter { j -> levels[j] > levels[i] && frames[j].top < me.bottom && frames[j].bottom > me.top }
            .map { j ->
                val other = frames[j]
                CarveRect(
                    left = other.left.value - me.left.value - gap.value,
                    top = other.top.value - me.top.value - gap.value,
                    right = me.width.value + 4f,
                    bottom = other.bottom.value - me.top.value + gap.value
                )
            }
        CarvedShape(cuts)
    }
