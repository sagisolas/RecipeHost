package org.sagisolas.recipehost.ui.recipe

import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import org.sagisolas.recipehost.presentation.LayoutSlotUi
import org.sagisolas.recipehost.presentation.RecipeLayoutUi
import org.sagisolas.recipehost.presentation.StackUi
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

/** The inputs in a wrapped row, an arrow, then the results, each in an [McSlot]. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GenericLayout(
    layout: RecipeLayoutUi.Generic,
    atlases: IconAtlasCache,
    onOpen: (String) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        FlowRow(Modifier.weight(1f, fill = false)) {
            layout.inputs.forEach {
                McSlot {
                    it?.let { stack ->
                        StackCell(stack, atlases, onOpen, Modifier.fillMaxSize())
                    }
                }
            }
        }
        McText("→", color = McTheme.colors.textOnDark, style = McTheme.typography.title)
        FlowRow {
            layout.results.forEach { McSlot { StackCell(it, atlases, onOpen, Modifier.fillMaxSize()) } }
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
    val image = layoutImage(layout.key, layoutImages)
    val screenWidth = LocalWindowInfo.current.containerSize.width
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val available = (screenWidth * LAYOUT_WIDTH_FRACTION).toInt().coerceAtMost(constraints.maxWidth)
        val scale = layoutScale(available, layout.width)
        val density = LocalDensity.current
        val size = with(density) { Modifier.size((layout.width * scale).toDp(), (layout.height * scale).toDp()) }
        Box(size) {
            if (image != null) {
                Image(
                    BitmapPainter(image, filterQuality = FilterQuality.None),
                    contentDescription = null,
                    Modifier.fillMaxSize(),
                )
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

/**
 * The decoded image of layout [key], or `null` while it loads or when it's missing. Loads again
 * when [images] is cleared, since a Scan may have replaced the image under the same key.
 */
@Composable
private fun layoutImage(
    key: String,
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
    val key: String,
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

/** How much of the screen width a captured layout may take. */
private const val LAYOUT_WIDTH_FRACTION = 0.8f

/** An Item's size in GUI px. */
private const val ITEM_GUI_PX = 16
