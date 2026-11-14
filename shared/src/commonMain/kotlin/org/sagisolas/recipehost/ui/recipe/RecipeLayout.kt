/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.ui.recipe

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.sagisolas.recipehost.domain.recipes.LayoutImageKey
import org.sagisolas.recipehost.presentation.LayoutSlotUi
import org.sagisolas.recipehost.presentation.RecipeLayoutUi
import org.sagisolas.recipehost.presentation.StackUi
import org.sagisolas.recipehost.shared.generated.resources.Res
import org.sagisolas.recipehost.shared.generated.resources.pixel_arrow
import org.sagisolas.recipehost.shared.generated.resources.pixel_question_mark
import org.sagisolas.recipehost.shared.generated.resources.recipe_inputs_unknown
import org.sagisolas.recipehost.ui.designsystem.McDimens
import org.sagisolas.recipehost.ui.designsystem.McPanel
import org.sagisolas.recipehost.ui.designsystem.McSlot
import org.sagisolas.recipehost.ui.designsystem.McText
import org.sagisolas.recipehost.ui.designsystem.McTheme
import org.sagisolas.recipehost.ui.item.IconAtlasCache
import org.sagisolas.recipehost.ui.item.LayoutImageCache
import org.sagisolas.recipehost.ui.item.StackIcon

/**
 * Draws a recipe in the layout its ViewModel chose for it: a captured `Recipe layout` (ADR 0015)
 * or the generic row. Tapping a stack calls [onOpen] with its item id.
 */
@Composable
internal fun RecipeLayout(
    layout: RecipeLayoutUi,
    atlases: IconAtlasCache,
    layoutImages: LayoutImageCache,
    onOpen: (String) -> Unit,
) {
    when (layout) {
        is RecipeLayoutUi.Generic -> GenericLayout(layout, atlases, onOpen)
        is RecipeLayoutUi.Captured -> CapturedLayout(layout, atlases, layoutImages, onOpen)
    }
}

/**
 * The inputs in a wrapped row, an arrow, then the results, each in an [McSlot]. A recipe with no
 * inputs shows a pixel "?" in their place.
 */
@Composable
private fun GenericLayout(
    layout: RecipeLayoutUi.Generic,
    atlases: IconAtlasCache,
    onOpen: (String) -> Unit,
) {
    Row(
        modifier =
            Modifier.wrapContentSize(
                unbounded = true,
                align = Alignment.TopStart,
            ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (layout.inputs.isEmpty()) {
            UnknownInputs()
        } else {
            FlowRow(Modifier.weight(1f, fill = false)) {
                layout.inputs.forEach {
                    McSlot {
                        it?.let { stack ->
                            StackCell(stack, atlases, onOpen, Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
        Image(
            painterResource(Res.drawable.pixel_arrow),
            contentDescription = null,
            colorFilter = ColorFilter.tint(McTheme.colors.textOnDark),
        )
        FlowRow {
            layout.results.forEach { McSlot { StackCell(it, atlases, onOpen, Modifier.fillMaxSize()) } }
        }
    }
}

/**
 * A pixel "?", slot-sized so the row is as tall as one with inputs, tinted like the arrow. A long
 * press explains it until dismissed, as a stack's name does.
 */
@Composable
private fun UnknownInputs() {
    val explanation = stringResource(Res.string.recipe_inputs_unknown)
    var showExplanation by remember { mutableStateOf(false) }
    var height by remember { mutableIntStateOf(0) }
    Box(
        Modifier
            .size(McDimens.SlotSize)
            .onSizeChanged { height = it.height }
            .combinedClickable(onLongClick = { showExplanation = true }, onClick = {}),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painterResource(Res.drawable.pixel_question_mark),
            contentDescription = explanation,
            colorFilter = ColorFilter.tint(McTheme.colors.textOnDark),
        )
        if (showExplanation) {
            Popup(offset = IntOffset(0, height), onDismissRequest = { showExplanation = false }) {
                McPanel { McText(explanation) }
            }
        }
    }
}

/**
 * The layout's image, centred, at the largest whole scale that fits [LAYOUT_WIDTH_FRACTION] of the
 * screen width (and the section), nearest-neighbour, with each stack in its scaled slot
 * rectangle. The image already has the slots' frames, so no [McSlot]. Its space is kept while
 * the image loads, so the list doesn't jump.
 */
@Composable
private fun CapturedLayout(
    layout: RecipeLayoutUi.Captured,
    atlases: IconAtlasCache,
    layoutImages: LayoutImageCache,
    onOpen: (String) -> Unit,
) {
    val image = layoutImage(LayoutImageKey.Layout(layout.key), layoutImages)
    val patch = layout.patch?.let { layoutImage(LayoutImageKey.Patch(it.key), layoutImages) }
    val screenWidth = LocalWindowInfo.current.containerSize.width
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val available = (screenWidth * LAYOUT_WIDTH_FRACTION).toInt().coerceAtMost(constraints.maxWidth)
        val scale = layoutScale(available, layout.width)
        val density = LocalDensity.current
        val size = with(density) { Modifier.size((layout.width * scale).toDp(), (layout.height * scale).toDp()) }
        Box(size) {
            // A patched layout waits for its patch, so it never shows another recipe's text or frame.
            if (image != null && (layout.patch == null || patch != null)) {
                // Offscreen, so a patch tile's transparent pixels replace the layout's rather than clearing the screen.
                Canvas(Modifier.fillMaxSize().graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
                    drawPixels(
                        image,
                        IntRect(0, 0, image.width, image.height),
                        IntRect(IntOffset.Zero, this.size.toIntSize()),
                    )
                    if (patch != null) {
                        patchTiles(layout.patch.tiles, layout.width, layout.height).forEach { tile ->
                            val dst = tile.dst
                            drawPixels(
                                patch,
                                tile.src,
                                scaledRect(dst.left, dst.top, dst.width, dst.height, scale),
                                BlendMode.Src,
                            )
                        }
                    }
                }
            }
            layout.slots.forEach { slot ->
                val rect = slot.scaled(scale)
                Box(
                    Modifier
                        .offset { IntOffset(rect.left, rect.top) }
                        .size(with(density) { rect.width.toDp() }, with(density) { rect.height.toDp() }),
                    contentAlignment = Alignment.Center,
                ) {
                    // An Item is 16 GUI px, centred in its slot whatever the slot's size, as in game.
                    val itemSize = with(density) { (ITEM_GUI_PX * scale).toDp() }
                    slot.stack?.let { StackCell(it, atlases, onOpen, Modifier.size(itemSize)) }
                }
            }
        }
    }
}

/** Draws [src] of [image] into [dst], nearest-neighbour. */
private fun DrawScope.drawPixels(
    image: ImageBitmap,
    src: IntRect,
    dst: IntRect,
    blendMode: BlendMode = BlendMode.SrcOver,
) = drawImage(
    image,
    srcOffset = src.topLeft,
    srcSize = src.size,
    dstOffset = dst.topLeft,
    dstSize = dst.size,
    blendMode = blendMode,
    filterQuality = FilterQuality.None,
)

private fun Size.toIntSize() = IntSize(width.toInt(), height.toInt())

/**
 * The decoded layout or patch image of [key], or `null` while it loads or when it's missing. Loads
 * again when [images] is cleared, since a Scan may have replaced a layout's image under the same key.
 */
@Composable
private fun layoutImage(
    key: LayoutImageKey,
    images: LayoutImageCache,
): ImageBitmap? {
    val generation = images.generation
    val loaded by produceState<LoadedImage?>(null, key, generation, images) {
        value = LoadedImage(key, generation, images.get(key))
    }
    return loaded?.takeIf { it.key == key && it.generation == generation }?.bitmap ?: images.peek(key)
}

/** An image as loaded for one [key] and cache [generation], so a stale one is never drawn. */
private class LoadedImage(
    val key: LayoutImageKey,
    val generation: Int,
    val bitmap: ImageBitmap?,
)

/**
 * A stack filling [modifier]'s box: its Icon, its count bottom-right when above 1. Tap opens the
 * Item, a long press shows its name and id until dismissed.
 */
@Composable
private fun StackCell(
    stack: StackUi,
    atlases: IconAtlasCache,
    onOpen: (String) -> Unit,
    modifier: Modifier,
) {
    // Keyed by the stack: when the alternatives cycle, an open popup closes instead of changing Item.
    var showId by remember(stack.itemId) { mutableStateOf(false) }
    var height by remember { mutableIntStateOf(0) }
    Box(
        modifier
            .onSizeChanged { height = it.height }
            .combinedClickable(onLongClick = { showId = true }, onClick = { onOpen(stack.itemId) }),
        contentAlignment = Alignment.Center,
    ) {
        StackIcon(stack, atlases, Modifier.fillMaxSize())
        if (stack.count > 1) {
            McText(
                stack.count.toString(),
                Modifier.align(Alignment.BottomEnd),
                color = McTheme.colors.textOnDark,
                style = McTheme.typography.small,
                maxLines = 1,
            )
        }
        if (showId) {
            Popup(offset = IntOffset(0, height), onDismissRequest = { showId = false }) {
                McPanel {
                    Column {
                        stack.item?.let { McText(it.displayName, maxLines = 1) }
                        McText(stack.itemId, style = McTheme.typography.small, maxLines = 1)
                    }
                }
            }
        }
    }
}

private fun LayoutSlotUi.scaled(scale: Int) = scaledRect(x, y, width, height, scale)

/**
 * The largest whole number of screen px per GUI px at which a [layoutWidth] GUI px wide layout
 * fits in [availableWidthPx], so nearest-neighbour scaling keeps every texture pixel the same
 * size. A layout wider than the space is still drawn at 1.
 */
internal fun layoutScale(
    availableWidthPx: Int,
    layoutWidth: Int,
): Int = if (layoutWidth <= 0) 1 else (availableWidthPx / layoutWidth).coerceAtLeast(1)

/** A `Recipe slot`'s rectangle in GUI px, in screen px at [scale]. */
internal fun scaledRect(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    scale: Int,
): IntRect = IntRect(left = x * scale, top = y * scale, right = (x + width) * scale, bottom = (y + height) * scale)

/** One patch tile (ADR 0019): its rectangle in the patch's strip, and where it goes in the layout, in GUI px. */
internal data class PatchTile(
    val src: IntRect,
    val dst: IntRect,
)

/**
 * Where each of a patch's [tiles] comes from and goes to. The patch's strip holds them left to
 * right; a tile's index is row-major over the layout's columns of [PATCH_TILE] px tiles, and a tile
 * at the right or bottom edge is cut to the layout.
 */
internal fun patchTiles(
    tiles: List<Int>,
    layoutWidth: Int,
    layoutHeight: Int,
): List<PatchTile> {
    val columns = (layoutWidth + PATCH_TILE - 1) / PATCH_TILE
    return tiles.mapIndexed { i, tile ->
        val left = tile % columns * PATCH_TILE
        val top = tile / columns * PATCH_TILE
        val width = minOf(PATCH_TILE, layoutWidth - left)
        val height = minOf(PATCH_TILE, layoutHeight - top)
        PatchTile(
            src = IntRect(i * PATCH_TILE, 0, i * PATCH_TILE + width, height),
            dst = IntRect(left, top, left + width, top + height),
        )
    }
}

/** A patch tile's side in GUI px; the protocol fixes it. */
private const val PATCH_TILE = 8

/** How much of the screen width a captured layout may take. */
private const val LAYOUT_WIDTH_FRACTION = 0.8f

/** An Item's size in GUI px. */
private const val ITEM_GUI_PX = 16
