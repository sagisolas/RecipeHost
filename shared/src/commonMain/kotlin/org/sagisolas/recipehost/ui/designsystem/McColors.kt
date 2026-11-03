package org.sagisolas.recipehost.ui.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Text colours. Backgrounds come from [McSkin], not from here. */
@Immutable
internal data class McColors(
    val text: Color = Color(0xFF404040),
    val textOnDark: Color = Color(0xFFFFFFFF),
    val textDisabled: Color = Color(0xFFA0A0A0),
    val textHint: Color = Color(0xFF707070),
) {
    /** Text on a dark background, greyed out when not [enabled]. */
    fun onDark(enabled: Boolean): Color = if (enabled) textOnDark else textDisabled
}

internal val LocalMcColors = staticCompositionLocalOf { McColors() }
