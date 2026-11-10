package org.sagisolas.recipehost.ui.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** A panel stretched to its content or to the size the caller gives it; text on it is [McColors.text]. */
@Composable
internal fun McPanel(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(8.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    CompositionLocalProvider(LocalMcContentColor provides McTheme.colors.text) {
        Box(
            modifier = modifier.mcBackground(McTheme.skin.panel).padding(contentPadding),
            content = content,
        )
    }
}
