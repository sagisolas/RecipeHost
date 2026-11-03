package org.sagisolas.recipehost.ui.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import org.jetbrains.compose.resources.Font
import org.sagisolas.recipehost.shared.generated.resources.Res
import org.sagisolas.recipehost.shared.generated.resources.monocraft

internal object McTheme {
    val colors: McColors
        @Composable @ReadOnlyComposable get() = LocalMcColors.current
    val skin: McSkin
        @Composable @ReadOnlyComposable get() = LocalMcSkin.current
    val typography: McTypography
        @Composable @ReadOnlyComposable get() = LocalMcTypography.current
}

/** Provides the Monocraft typography, the text colours and [skin] to [content]. */
@Composable
internal fun McTheme(
    skin: McSkin = remember { McSkin.flat() },
    content: @Composable () -> Unit,
) {
    val monocraft = FontFamily(Font(Res.font.monocraft))
    val typography = remember(monocraft) { McTypography.of(monocraft) }
    CompositionLocalProvider(
        LocalMcColors provides McColors(),
        LocalMcSkin provides skin,
        LocalMcTypography provides typography,
        content = content,
    )
}
