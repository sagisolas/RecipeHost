package org.sagisolas.recipehost.ui.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/** An item slot: an 18:18 frame around a 16:16 content area. */
@Composable
internal fun McSlot(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {},
) {
    Box(
        modifier = modifier
            .size(McDimens.SlotSize)
            .mcBackground(McTheme.skin.slot)
            .padding(McDimens.SlotInset),
        contentAlignment = Alignment.Center,
        content = content,
    )
}
