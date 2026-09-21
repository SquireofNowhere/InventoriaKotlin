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
 * The shape for each of [frames]: its own rectangle minus the frames of the cards nested in it
 * (those whose entry in [parents] is its index), plus [gap] of clear space around each notch.
 */
fun carveShapes(parents: List<Int>, frames: List<CardFrame>, gap: Dp = 2.dp): List<CarvedShape> =
    frames.indices.map { i ->
        val me = frames[i]
        val cuts = frames.indices
            .filter { j -> parents[j] == i }
            .map { j ->
                val other = frames[j]
                CarveRect(
                    left = other.left.value - me.left.value - gap.value,
                    top = other.top.value - me.top.value - gap.value,
                    right = other.left.value + other.width.value - me.left.value + gap.value,
                    bottom = other.bottom.value - me.top.value + gap.value
                )
            }
        CarvedShape(cuts)
    }
