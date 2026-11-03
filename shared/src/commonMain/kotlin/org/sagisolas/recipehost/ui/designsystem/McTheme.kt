package org.sagisolas.recipehost.ui.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.font.FontFamily

internal object McTheme {
    val colors: McColors
        @Composable @ReadOnlyComposable get() = LocalMcColors.current
    val skin: McSkin
        @Composable @ReadOnlyComposable get() = LocalMcSkin.current
    val typography: McTypography
        @Composable @ReadOnlyComposable get() = LocalMcTypography.current
}

/**
 * Provides [typography], [colors] and [skin] to [content]. The design system owns no fonts or
 * textures: the app passes its own (e.g. a typography built from its bundled font). The
 * defaults are neutral, for previews.
 */
@Composable
internal fun McTheme(
    typography: McTypography = McTypography.of(FontFamily.Monospace),
    colors: McColors = McColors(),
    skin: McSkin = McSkin.flat(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalMcColors provides colors,
        LocalMcSkin provides skin,
        LocalMcTypography provides typography,
        content = content,
    )
}
