/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.ui.item

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import org.sagisolas.recipehost.domain.catalog.IconRegion
import org.sagisolas.recipehost.presentation.StackUi
import org.sagisolas.recipehost.ui.AppTheme
import org.sagisolas.recipehost.ui.designsystem.McDimens
import org.sagisolas.recipehost.ui.designsystem.McSlot
import kotlin.io.encoding.Base64
import kotlin.math.floor

/**
 * An item's Icon: its [icon] region of an `Icon atlas` from [atlases], drawn with
 * nearest-neighbour filtering at [iconPixelSize] inside a [McSlot]'s content area, so every
 * texel covers the same whole number of device pixels. Shows nothing while its atlas is loading,
 * and a faint [MissingIcon] when the Catalog has no Icon for the item ([icon] is `null`), the atlas
 * is missing, or the region doesn't fit inside it (a malformed IconBatch).
 */
@Composable
internal fun ItemIcon(
    icon: IconRegion?,
    atlases: IconAtlasCache,
    modifier: Modifier = Modifier,
) {
    val atlasId = icon?.atlasId
    val loaded by produceState<LoadedAtlas?>(null, atlasId, atlases) {
        if (atlasId != null) value = LoadedAtlas(atlasId, atlases.get(atlasId))
    }
    val loadedHere = atlasId?.let { id -> loaded?.takeIf { it.id == id } }
    val atlas = atlasId?.let { id -> loadedHere?.bitmap ?: atlases.peek(id) }
    val painter = remember(atlas, icon) { if (atlas != null && icon != null) regionPainter(atlas, icon) else null }
    when {
        painter != null -> {
            IconBox(modifier) { Image(painter, contentDescription = null, modifier = it) }
        }

        // No Icon, the atlas arrived without such a region, or it's gone: missing, not loading.
        icon == null || atlas != null || (loadedHere != null && loadedHere.bitmap == null) -> {
            MissingIcon(modifier)
        }
    }
}

/**
 * A faint gray square on the Icon's texel grid, for an item whose Icon can't be shown. Translucent
 * white, so it reads on both a slot's gray and the dark background behind a grid.
 */
@Composable
private fun MissingIcon(modifier: Modifier = Modifier) {
    IconBox(modifier) { iconModifier ->
        Canvas(iconModifier) {
            val inset = MISSING_ICON_INSET_TEXELS * size.width / ICON_TEXELS
            drawRect(
                MISSING_ICON_COLOR,
                topLeft = Offset(inset, inset),
                size = Size(size.width - inset * 2, size.height - inset * 2),
            )
        }
    }
}

/** Centres [content], given the modifier that sizes it to [iconPixelSize], in a slot's content area. */
@Composable
private fun IconBox(
    modifier: Modifier,
    content: @Composable (iconModifier: Modifier) -> Unit,
) {
    val iconSize = with(LocalDensity.current) { iconPixelSize(McDimens.SlotContentSize.toPx()).toDp() }
    Box(modifier.size(McDimens.SlotContentSize), contentAlignment = Alignment.Center) {
        content(Modifier.size(iconSize))
    }
}

private val MISSING_ICON_COLOR = Color(0x5BD2D2D2)

/** Texels left clear on each side of the [MissingIcon] square. */
private const val MISSING_ICON_INSET_TEXELS = 1

/**
 * The largest size, in device pixels, that fits in [availablePx] and gives each of an Icon's 16
 * texels a whole number of pixels, so nearest-neighbour scaling draws them all the same width.
 * A space smaller than one pixel per texel is used whole.
 */
internal fun iconPixelSize(availablePx: Float): Int {
    val perTexel = floor(availablePx / ICON_TEXELS).toInt()
    return if (perTexel >= 1) perTexel * ICON_TEXELS else availablePx.toInt()
}

/** Texels across an Icon: the 16 GUI units Minecraft draws an item in. */
private const val ICON_TEXELS = 16

/** The stack's Icon, or a [MissingIcon] when its id doesn't resolve to an Item. */
@Composable
internal fun StackIcon(
    stack: StackUi,
    atlases: IconAtlasCache,
    modifier: Modifier = Modifier,
) {
    val item = stack.item
    if (item != null) {
        ItemIcon(item.icon, atlases, modifier)
    } else {
        MissingIcon(modifier)
    }
}

/**
 * An atlas as loaded for one [id], so a stale atlas is never drawn with a new region after the
 * Icon moves to another atlas.
 */
private class LoadedAtlas(
    val id: Long,
    val bitmap: ImageBitmap?,
)

/** Draws [icon] from [atlas], or `null` when the region falls outside it. */
private fun regionPainter(
    atlas: ImageBitmap,
    icon: IconRegion,
): BitmapPainter? {
    val fits =
        icon.x >= 0 && icon.y >= 0 && icon.size > 0 &&
            icon.x + icon.size <= atlas.width && icon.y + icon.size <= atlas.height
    if (!fits) return null
    return BitmapPainter(atlas, IntOffset(icon.x, icon.y), IntSize(icon.size, icon.size), FilterQuality.None)
}

@Preview
@Composable
private fun ItemIconPreview() {
    val scope = rememberCoroutineScope()
    val atlases =
        remember(scope) {
            IconAtlasCache(scope, load = { id -> if (id == SAMPLE_ATLAS) SAMPLE_ICON_PNG else byteArrayOf(1, 2, 3) })
        }
    AppTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            McSlot { ItemIcon(IconRegion(SAMPLE_ATLAS, x = 0, y = 0, size = 16), atlases) }
            McSlot { ItemIcon(null, atlases) }
            McSlot { ItemIcon(IconRegion(BROKEN_ATLAS, x = 0, y = 0, size = 16), atlases) }
            McSlot { ItemIcon(IconRegion(SAMPLE_ATLAS, x = 16, y = 0, size = 16), atlases) }
            McSlot { MissingIcon() }
        }
    }
}

private const val SAMPLE_ATLAS = 1L
private const val BROKEN_ATLAS = 2L

/** A one-cell atlas: a 16×16 framed blue square with an X, to show nearest-neighbour scaling. */
private val SAMPLE_ICON_PNG: ByteArray =
    Base64.decode(
        "iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAAXElEQVR42mPQ0ND4TwlmABFRJ/7/l6s4QRIG6YEbABMgRTOIRjGAWEOQ" +
            "1WAYQMgQdDmsBuAyBJsYTgPQNeByFV4DYBrxeYm2BlDkBYoCkaJopCghUZSUKc5MlGAAohE8i1zIsmoAAAAASUVORK5CYII=",
    )
