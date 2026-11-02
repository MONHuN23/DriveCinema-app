package com.drivecinema.app.ui.design

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import kotlin.math.abs

/**
 * How far a focused control of [extent] (its width or height) draws past its bounds: half the growth from
 * [scale] plus the scaled ring and gap.
 */
fun dcFocusOverflow(extent: Dp, scale: Float = DcTokens.Focus.CONTROL_SCALE): Dp =
    extent * (scale - 1f) / 2f + (DcTokens.Focus.RING_GAP + DcTokens.Focus.RING_WIDTH).dp * scale

/**
 * Room a focused control needs beyond its bounds in a scroll container, which clips its content in its scroll
 * direction (P.7: TV rows and tabs cut off at the edges). Covers [dcFocusOverflow] for controls up to 150 dp and
 * rows (scale 1.02) up to 500 dp. Give a scroll this much padding inside, at its ends, and wrap TV screens in
 * [DcFocusRoomScrolling] for the items in between.
 */
val DcFocusRoom: Dp = 10.dp

/**
 * Lets the content reach [horizontal] past both sides of this element, which keeps its own width: for a scrolling
 * row with [DcFocusRoom] inside, so its items still line up with the content above it.
 */
fun Modifier.dcBleed(horizontal: Dp): Modifier = layout { measurable, constraints ->
    val extra = horizontal.roundToPx() * 2
    val placeable = measurable.measure(constraints.offset(horizontal = extra))
    val width = constraints.constrainWidth(placeable.width - extra)
    layout(width, placeable.height) { placeable.place(-extra / 2, 0) }
}

/**
 * Scrolls inside [content] bring a newly focused item into view with [DcFocusRoom] around it, so its scale and ring
 * aren't clipped at the viewport's edge (the default stops with the item flush against it).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DcFocusRoomScrolling(content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val spec = remember(density) { FocusRoomBringIntoViewSpec(with(density) { DcFocusRoom.toPx() }) }
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec, content = content)
}

/** The default spec's rule on the item grown by [room] on both sides, or on the bare item when that doesn't fit. */
@OptIn(ExperimentalFoundationApi::class)
internal class FocusRoomBringIntoViewSpec(private val room: Float) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
        if (size + 2 * room <= containerSize) distance(offset - room, offset + size + room, containerSize)
        else distance(offset, offset + size, containerSize)

    private fun distance(leading: Float, trailing: Float, containerSize: Float): Float = when {
        leading >= 0 && trailing <= containerSize -> 0f // already in view
        leading < 0 && trailing > containerSize -> 0f // larger than the view: leave it
        abs(leading) < abs(trailing - containerSize) -> leading
        else -> trailing - containerSize
    }
}
