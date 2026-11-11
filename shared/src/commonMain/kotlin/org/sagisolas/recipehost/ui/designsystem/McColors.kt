package org.sagisolas.recipehost.ui.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Text colours. Backgrounds come from [McSkin], not from here. */
@Immutable
internal data class McColors(
    /** Text on a light background: a panel, a tab. */
    val text: Color = Color(0xFF404040),
    /** Text on a dark background, such as the screen background every screen sits on. */
    val textOnDark: Color = Color(0xFFFFFFFF),
    /** Secondary text on a dark background, less prominent than [textOnDark]: footers, fine print. */
    val textOnDarkMuted: Color = Color(0xFF9A9A9A),
    val textDisabled: Color = Color(0xFFA0A0A0),
    val textHint: Color = Color(0xFF707070),
) {
    /** Text on a dark background, greyed out when not [enabled]. */
    fun onDark(enabled: Boolean): Color = if (enabled) textOnDark else textDisabled
}

internal val LocalMcColors = staticCompositionLocalOf { McColors() }

/**
 * The colour [McText] uses when none is given: the text colour of the background behind it.
 * [McTheme] starts with [McColors.textOnDark] for the screen background; [McPanel] switches to [McColors.text].
 */
internal val LocalMcContentColor = compositionLocalOf { McColors().textOnDark }
