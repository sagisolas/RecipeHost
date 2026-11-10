package org.sagisolas.recipehost.ui.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.font.FontFamily

internal object McTheme {
    val colors: McColors
        @Composable @ReadOnlyComposable
        get() = LocalMcColors.current
    val skin: McSkin
        @Composable @ReadOnlyComposable
        get() = LocalMcSkin.current
    val typography: McTypography
        @Composable @ReadOnlyComposable
        get() = LocalMcTypography.current
}

/**
 * Provides the design system's skin, [typography] and [colors] to [content]. The app passes
 * its typography (built from its bundled font); the default is a system monospace font.
 */
@Composable
internal fun McTheme(
    typography: McTypography = McTypography.of(FontFamily.Monospace),
    colors: McColors = McColors(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalMcColors provides colors,
        LocalMcSkin provides rememberMcSkin(),
        LocalMcTypography provides typography,
        content = content,
    )
}
