package org.sagisolas.recipehost.ui.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

/**
 * Draws [image] as a nine-slice stretched to the painted area: corners keep their proportions at
 * [pixelSize] per texture pixel, edges and centre stretch. Uses nearest-neighbour filtering so
 * texture pixels stay sharp.
 */
internal class NineSlicePainter(
    private val image: ImageBitmap,
    private val border: SliceInsets,
    private val pixelSize: Dp,
) : Painter() {
    private var alpha = 1f
    private var colorFilter: ColorFilter? = null

    /** Stretches to whatever it's drawn into. */
    override val intrinsicSize: Size = Size.Unspecified

    override fun applyAlpha(alpha: Float): Boolean {
        this.alpha = alpha
        return true
    }

    override fun applyColorFilter(colorFilter: ColorFilter?): Boolean {
        this.colorFilter = colorFilter
        return true
    }

    override fun DrawScope.onDraw() {
        val slices = nineSlice(
            sourceSize = IntSize(image.width, image.height),
            border = border,
            targetSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
            borderScale = pixelSize.toPx(),
        )
        for ((src, dst) in slices) {
            drawImage(
                image = image,
                srcOffset = src.topLeft,
                srcSize = src.size,
                dstOffset = dst.topLeft,
                dstSize = dst.size,
                alpha = alpha,
                colorFilter = colorFilter,
                filterQuality = FilterQuality.None,
            )
        }
    }
}

/** Border widths of a nine-slice texture, in texture pixels. */
@Immutable
internal data class SliceInsets(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    constructor(all: Int) : this(all, all, all, all)
}

/** One piece of a nine-slice: the [src] rect of the texture drawn into the [dst] rect. */
internal data class Slice(val src: IntRect, val dst: IntRect)

/**
 * Splits a texture of [sourceSize] with [border] into corners, edges and centre, and places them
 * in [targetSize]. Borders are drawn at [borderScale] target pixels per texture pixel; edges and
 * centre stretch to fill the rest; if the borders don't fit, they shrink in proportion.
 * Slices are row-major from top-left to bottom-right, and empty ones are left out.
 */
internal fun nineSlice(
    sourceSize: IntSize,
    border: SliceInsets,
    targetSize: IntSize,
    borderScale: Float,
): List<Slice> {
    val srcX = intArrayOf(0, border.left, sourceSize.width - border.right, sourceSize.width)
    val srcY = intArrayOf(0, border.top, sourceSize.height - border.bottom, sourceSize.height)
    val dstX = targetBounds(targetSize.width, border.left, border.right, borderScale)
    val dstY = targetBounds(targetSize.height, border.top, border.bottom, borderScale)
    val slices = mutableListOf<Slice>()
    for (row in 0 until 3) {
        for (column in 0 until 3) {
            val src = IntRect(srcX[column], srcY[row], srcX[column + 1], srcY[row + 1])
            val dst = IntRect(dstX[column], dstY[row], dstX[column + 1], dstY[row + 1])
            if (!src.isEmpty && !dst.isEmpty) slices += Slice(src, dst)
        }
    }
    return slices
}

/** Splits [length] into start border, middle and end border; borders shrink to fit if needed. */
private fun targetBounds(length: Int, start: Int, end: Int, scale: Float): IntArray {
    var startPx = (start * scale).roundToInt()
    var endPx = (end * scale).roundToInt()
    if (startPx + endPx > length) {
        startPx = (length * start.toFloat() / (start + end)).roundToInt()
        endPx = length - startPx
    }
    return intArrayOf(0, startPx, length - endPx, length)
}
