package org.sagisolas.recipehost.ui.item

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.io.encoding.Base64
import org.sagisolas.recipehost.domain.catalog.IconRegion
import org.sagisolas.recipehost.ui.AppTheme
import org.sagisolas.recipehost.ui.designsystem.McDimens
import org.sagisolas.recipehost.ui.designsystem.McSlot
import org.sagisolas.recipehost.ui.designsystem.McTheme

/**
 * An item's Icon: its [icon] region of an `Icon atlas` from [atlases], sized to fill a [McSlot]
 * and drawn with nearest-neighbour filtering. Shows the skin's missing texture while [icon] is
 * `null` (not arrived yet), while its atlas is loading, when the atlas is missing, or when the
 * region doesn't fit inside it (a malformed IconBatch).
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
    val atlas = atlasId?.let { id -> loaded?.takeIf { it.id == id }?.bitmap ?: atlases.peek(id) }
    val painter = remember(atlas, icon) { if (atlas != null && icon != null) regionPainter(atlas, icon) else null }
    val iconModifier = modifier.size(McDimens.SlotContentSize)
    if (painter != null) {
        Image(painter, contentDescription = null, modifier = iconModifier)
    }
}

/**
 * An atlas as loaded for one [id], so a stale atlas is never drawn with a new region after the
 * Icon moves to another atlas.
 */
private class LoadedAtlas(val id: Long, val bitmap: ImageBitmap?)

/** Draws [icon] from [atlas], or `null` when the region falls outside it. */
private fun regionPainter(atlas: ImageBitmap, icon: IconRegion): BitmapPainter? {
    val fits = icon.x >= 0 && icon.y >= 0 && icon.size > 0 &&
        icon.x + icon.size <= atlas.width && icon.y + icon.size <= atlas.height
    if (!fits) return null
    return BitmapPainter(atlas, IntOffset(icon.x, icon.y), IntSize(icon.size, icon.size), FilterQuality.None)
}

@Preview
@Composable
private fun ItemIconPreview() {
    val scope = rememberCoroutineScope()
    val atlases = remember(scope) {
        IconAtlasCache(scope, load = { id -> if (id == SAMPLE_ATLAS) SAMPLE_ICON_PNG else byteArrayOf(1, 2, 3) })
    }
    AppTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            McSlot { ItemIcon(IconRegion(SAMPLE_ATLAS, x = 0, y = 0, size = 16), atlases) }
            McSlot { ItemIcon(null, atlases) }
            McSlot { ItemIcon(IconRegion(BROKEN_ATLAS, x = 0, y = 0, size = 16), atlases) }
            McSlot { ItemIcon(IconRegion(SAMPLE_ATLAS, x = 16, y = 0, size = 16), atlases) }
        }
    }
}

private const val SAMPLE_ATLAS = 1L
private const val BROKEN_ATLAS = 2L

/** A one-cell atlas: a 16×16 framed blue square with an X, to show nearest-neighbour scaling. */
private val SAMPLE_ICON_PNG: ByteArray = Base64.decode(
    "iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAAXElEQVR42mPQ0ND4TwlmABFRJ/7/l6s4QRIG6YEbABMgRTOIRjGAWEOQ" +
        "1WAYQMgQdDmsBuAyBJsYTgPQNeByFV4DYBrxeYm2BlDkBYoCkaJopCghUZSUKc5MlGAAohE8i1zIsmoAAAAASUVORK5CYII=",
)
