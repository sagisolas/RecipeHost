package org.sagisolas.recipehost.ui.designsystem

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality

/**
 * An item's Icon drawn with nearest-neighbour filtering. Shows the placeholder while [png] is
 * `null` (not arrived yet) or when it can't be decoded.
 */
@Composable
internal fun ItemIcon(
    key: String,
    png: ByteArray?,
    cache: ItemIconCache,
    modifier: Modifier = Modifier,
) {
    val bitmap = remember(cache, key, png) { cache.get(key, png) }
    val iconModifier = modifier.size(McDimens.IconSize)
    if (bitmap != null) {
        Image(bitmap, contentDescription = null, modifier = iconModifier, filterQuality = FilterQuality.None)
    } else {
        Image(McTheme.skin.itemPlaceholder, contentDescription = null, modifier = iconModifier)
    }
}
