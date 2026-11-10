package org.sagisolas.recipehost.ui.designsystem

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNode
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.toSize
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * A Minecraft list scrollbar for [state] over the right edge of the grid: a black track
 * [McDimens.ScrollbarWidth] wide, [McDimens.ListEdgePadding] in from the right edge and
 * [McDimens.ScrollbarGap] from the top and bottom, with a thumb sized to the visible share of the
 * rows. Nothing is drawn, and touches pass through, while all rows fit.
 *
 * Dragging the thumb scrolls the grid with it. Pressing the track elsewhere jumps there at once,
 * centring the thumb under the finger, and the drag carries on from there. Touches anywhere in the
 * right [McDimens.ScrollbarGutter] count, so the narrow bar is easy to hit; the grid doesn't
 * scroll by itself while the scrollbar has the finger.
 *
 * The thumb is drawn from the grid's layout in the draw phase, so scrolling redraws only it.
 * [McSlotGrid] applies it with the content padding that keeps cells out of its way.
 */
internal fun Modifier.mcVerticalScrollbar(state: LazyGridState): Modifier = this then ScrollbarElement(state)

private data class ScrollbarElement(
    val state: LazyGridState,
) : ModifierNodeElement<ScrollbarNode>() {
    override fun create() = ScrollbarNode(state)

    override fun update(node: ScrollbarNode) = node.update(state)
}

private class ScrollbarNode(
    private var state: LazyGridState,
) : DelegatingNode(),
    DrawModifierNode,
    CompositionLocalConsumerModifierNode {
    private val input = delegate(SuspendingPointerInputModifierNode { dragThumb() })

    fun update(state: LazyGridState) {
        if (state == this.state) return
        this.state = state
        input.resetPointerInputHandler()
        invalidateDraw()
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        val track = trackOf(size) ?: return
        val skin = currentValueOf(LocalMcSkin)
        translate(left = track.left, top = track.top) {
            with(skin.scrollerTrack.painter) { draw(Size(track.width, track.height)) }
            translate(top = track.thumb.top) {
                with(skin.scrollerThumb.painter) { draw(Size(track.width, track.thumb.height)) }
            }
        }
    }

    /**
     * One gesture per press: watched on the initial pass, before the grid sees it, so a press in
     * the gutter is taken from the grid (no fling, no item click) and every other press is left
     * alone.
     */
    private suspend fun PointerInputScope.dragThumb() =
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val track = trackOf(size.toSize()) ?: return@awaitEachGesture
            if (down.position.x < size.width - McDimens.ScrollbarGutter.toPx()) return@awaitEachGesture
            down.consume()
            val y = down.position.y - track.top
            val onThumb = y >= track.thumb.top && y <= track.thumb.top + track.thumb.height
            // Where the finger holds the thumb: where it pressed, or its middle after a jump.
            val grip = if (onThumb) y - track.thumb.top else track.thumb.height / 2
            if (!onThumb) scrollThumbTo(size.toSize(), y - grip)
            followDrag(down.id, grip)
        }

    /** Follows [pointer] until it lifts, keeping the thumb [grip] px above it. */
    private suspend fun AwaitPointerEventScope.followDrag(
        pointer: PointerId,
        grip: Float,
    ) {
        while (true) {
            val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == pointer } ?: return
            change.consume()
            if (!change.pressed) return
            scrollThumbTo(size.toSize(), change.position.y - McDimens.ScrollbarGap.toPx() - grip)
        }
    }

    /**
     * Scrolls a grid of [size] so the thumb's top sits [thumbTop] px below the track's top
     * (clamped to the track). Applied at the grid's next layout, so a fast drag never queues up.
     */
    private fun Density.scrollThumbTo(
        size: Size,
        thumbTop: Float,
    ) {
        val grid = gridGeometry(state) ?: return
        val track = trackOf(size, grid) ?: return
        val scrolled = scrolledForThumbTop(thumbTop, track.height, track.thumb.height, grid.extents.maxScroll)
        val position = gridPositionAt(scrolled, grid.columns, grid.rowExtent)
        state.requestScrollToItem(position.index, position.offset)
    }

    private fun Density.trackOf(size: Size): Track? = trackOf(size, gridGeometry(state) ?: return null)
}

/** Where the track is in the grid's bounds, and its thumb, in px. */
private class Track(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val thumb: ThumbSpan,
)

/** The track in a grid of [size]; `null` when there is nothing to scroll. */
private fun Density.trackOf(
    size: Size,
    grid: GridGeometry,
): Track? {
    val gap = McDimens.ScrollbarGap.toPx()
    val width = McDimens.ScrollbarWidth.toPx()
    val height = size.height - gap * 2
    val thumb = scrollbarThumb(grid.extents, height, McDimens.ScrollbarMinThumbHeight.toPx()) ?: return null
    return Track(
        left = size.width - McDimens.ListEdgePadding.toPx() - width,
        top = gap,
        width = width,
        height = height,
        thumb = thumb,
    )
}

/** How far a list is scrolled, in px: [scrolled] of the [content] that doesn't fit the [viewport]. */
internal data class ScrollExtents(
    val viewport: Float,
    val content: Float,
    val scrolled: Float,
) {
    val maxScroll: Float get() = content - viewport
}

/** The thumb's [top] (from the track's top) and [height], in px. */
internal data class ThumbSpan(
    val top: Float,
    val height: Float,
)

/**
 * The thumb on a track [trackHeight] px tall: as tall as the visible share of the content, but
 * no shorter than [minHeight] (nor taller than the track), and placed by how far it's scrolled.
 * `null` when the content fits the viewport, so there is nothing to scroll.
 */
internal fun scrollbarThumb(
    extents: ScrollExtents,
    trackHeight: Float,
    minHeight: Float,
): ThumbSpan? {
    val (viewport, content, scrolled) = extents
    val maxScroll = content - viewport
    if (maxScroll <= 0f || trackHeight <= 0f) return null
    val height = (trackHeight * viewport / content).coerceIn(minOf(minHeight, trackHeight), trackHeight)
    val fraction = (scrolled / maxScroll).coerceIn(0f, 1f)
    return ThumbSpan(top = (trackHeight - height) * fraction, height = height)
}

/**
 * The inverse of [scrollbarThumb]'s placement: how far to scroll, in px, for a thumb
 * [thumbHeight] tall to sit with its top [thumbTop] down a track [trackHeight] tall. A top
 * outside the track scrolls to that end.
 */
internal fun scrolledForThumbTop(
    thumbTop: Float,
    trackHeight: Float,
    thumbHeight: Float,
    maxScroll: Float,
): Float {
    val travel = trackHeight - thumbHeight
    if (travel <= 0f) return 0f
    return (thumbTop / travel).coerceIn(0f, 1f) * maxScroll
}

/** The first visible item and its offset that scroll a grid [scrolled] px down. */
internal data class GridPosition(
    val index: Int,
    val offset: Int,
)

/**
 * The [GridPosition] for [scrolled] px down a grid of [columns] whose rows are [rowExtent] px
 * apart: the first item of the row there, and how far into that row.
 */
internal fun gridPositionAt(
    scrolled: Float,
    columns: Int,
    rowExtent: Float,
): GridPosition {
    val row = floor(scrolled.coerceAtLeast(0f) / rowExtent).toInt()
    return GridPosition(index = row * columns, offset = (scrolled.coerceAtLeast(0f) - row * rowExtent).roundToInt())
}

/** What the scrollbar needs of a grid's last layout. */
private class GridGeometry(
    val columns: Int,
    val rowExtent: Float,
    val extents: ScrollExtents,
)

/** The grid's geometry from its last layout; `null` before the first layout or when it is empty. */
private fun gridGeometry(state: LazyGridState): GridGeometry? {
    val info = state.layoutInfo
    val visible = info.visibleItemsInfo
    if (visible.isEmpty()) return null
    val columns = visible.maxOf { it.column } + 1
    val rowHeight = visible.first().size.height
    val extents =
        gridScrollExtents(
            totalItems = info.totalItemsCount,
            columns = columns,
            rowHeight = rowHeight,
            spacing = info.mainAxisItemSpacing,
            contentPadding = info.beforeContentPadding + info.afterContentPadding,
            viewport = info.viewportSize.height,
            firstVisibleItem = state.firstVisibleItemIndex,
            firstVisibleItemOffset = state.firstVisibleItemScrollOffset,
            atEnd = !state.canScrollForward,
        )
    return GridGeometry(columns, (rowHeight + info.mainAxisItemSpacing).toFloat(), extents)
}

/**
 * A grid's extents, estimated from its rows: [totalItems] in rows of [columns], each taken to be
 * [rowHeight] tall with [spacing] between rows, plus the vertical [contentPadding]. Scrolled by
 * the rows above [firstVisibleItem]'s plus [firstVisibleItemOffset], except [atEnd], which counts
 * as fully scrolled whatever the estimate. All in px.
 */
internal fun gridScrollExtents(
    totalItems: Int,
    columns: Int,
    rowHeight: Int,
    spacing: Int,
    contentPadding: Int,
    viewport: Int,
    firstVisibleItem: Int,
    firstVisibleItemOffset: Int,
    atEnd: Boolean,
): ScrollExtents {
    val rows = (totalItems + columns - 1) / columns
    val rowExtent = (rowHeight + spacing).toFloat()
    val content = maxOf(rows * rowExtent - spacing, 0f) + contentPadding
    val scrolled = if (atEnd) content - viewport else (firstVisibleItem / columns) * rowExtent + firstVisibleItemOffset
    return ScrollExtents(viewport.toFloat(), content, scrolled)
}
