package org.sagisolas.recipehost.ui.item

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlin.io.encoding.Base64
import org.sagisolas.recipehost.ui.AppTheme
import org.sagisolas.recipehost.ui.designsystem.McDimens
import org.sagisolas.recipehost.ui.designsystem.McSlot
import org.sagisolas.recipehost.ui.designsystem.McTheme

/**
 * An item's Icon, sized to fill a [McSlot] and drawn with nearest-neighbour filtering. Shows the
 * skin's missing texture while [png] is `null` (not arrived yet) or when it can't be decoded.
 */
@Composable
internal fun ItemIcon(
    key: String,
    png: ByteArray?,
    cache: ItemIconCache,
    modifier: Modifier = Modifier,
) {
    val bitmap = remember(cache, key, png) { cache.get(key, png) }
    val iconModifier = modifier.size(McDimens.SlotContentSize)
    if (bitmap != null) {
        Image(bitmap, contentDescription = null, modifier = iconModifier, filterQuality = FilterQuality.None)
    } else {
        Image(
            McTheme.skin.missingTexture,
            contentDescription = null,
            modifier = iconModifier,
            filterQuality = FilterQuality.None,
        )
    }
}

@Preview
@Composable
private fun ItemIconPreview() {
    val cache = remember { ItemIconCache() }
    AppTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            McSlot { ItemIcon("preview:sample", SAMPLE_ICON_PNG, cache) }
            McSlot { ItemIcon("preview:missing", null, cache) }
            McSlot { ItemIcon("preview:broken", byteArrayOf(1, 2, 3), cache) }
        }
    }
}

/** A 16×16 framed blue square with an X, to show nearest-neighbour scaling. */
private val SAMPLE_ICON_PNG: ByteArray = Base64.decode(
    "iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAAXElEQVR42mPQ0ND4TwlmABFRJ/7/l6s4QRIG6YEbABMgRTOIRjGAWEOQ" +
        "1WAYQMgQdDmsBuAyBJsYTgPQNeByFV4DYBrxeYm2BlDkBYoCkaJopCghUZSUKc5MlGAAohE8i1zIsmoAAAAASUVORK5CYII=",
)
