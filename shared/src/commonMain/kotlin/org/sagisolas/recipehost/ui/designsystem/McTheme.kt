package org.sagisolas.recipehost.ui.designsystem

import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
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

    /** The text colour for the background behind the caller (see [LocalMcContentColor]). */
    val contentColor: Color
        @Composable @ReadOnlyComposable
        get() = LocalMcContentColor.current
}

/**
 * Provides the design system's skin, [typography] and [colors] to [content]. The app passes
 * its typography (built from its bundled font); the default is a system monospace font.
 * [content] sits on [McSkin.screen], the standard background, so its text is [McColors.textOnDark].
 * The app has no animations, so scrolling past an end shows no overscroll effect (Android's stretch).
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
        LocalMcContentColor provides colors.textOnDark,
        LocalOverscrollFactory provides null,
        content = content,
    )
}
