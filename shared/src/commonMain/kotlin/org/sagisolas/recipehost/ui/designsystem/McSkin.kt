package org.sagisolas.recipehost.ui.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter

/**
 * Every component background. Compose stretches each painter to the component's bounds.
 * Today these are flat colours; issue 11 swaps them for the user-supplied textures
 * (as `BitmapPainter(..., filterQuality = FilterQuality.None)`).
 */
@Immutable
internal data class McSkin(
    val screen: Painter,
    val panel: Painter,
    val button: McStatePainters,
    val textField: Painter,
    val textFieldFocused: Painter,
    val textFieldDisabled: Painter,
    val dropdownOption: McStatePainters,
    val slot: Painter,
    val tabSelected: Painter,
    val tab: McStatePainters,
    val progressTrack: Painter,
    val progressFill: Painter,
    val bottomBar: Painter,
    val bottomBarItemSelected: Painter,
    val bottomBarItem: McStatePainters,
    val itemPlaceholder: Painter,
) {
    companion object {
        fun flat(): McSkin {
            val transparent = ColorPainter(Color.Transparent)
            return McSkin(
                screen = ColorPainter(Color(0xFF1E1E1E)),
                panel = ColorPainter(Color(0xFFC6C6C6)),
                button = McStatePainters(
                    normal = ColorPainter(Color(0xFF6F6F6F)),
                    pressed = ColorPainter(Color(0xFF4A4A4A)),
                    disabled = ColorPainter(Color(0xFF2C2C2C)),
                ),
                textField = ColorPainter(Color(0xFF000000)),
                textFieldFocused = ColorPainter(Color(0xFF2A2A2A)),
                textFieldDisabled = ColorPainter(Color(0xFF3A3A3A)),
                dropdownOption = McStatePainters(
                    normal = transparent,
                    pressed = ColorPainter(Color(0xFFA0A0A0)),
                    disabled = transparent,
                ),
                slot = ColorPainter(Color(0xFF8B8B8B)),
                tabSelected = ColorPainter(Color(0xFFC6C6C6)),
                tab = McStatePainters(
                    normal = ColorPainter(Color(0xFF9E9E9E)),
                    pressed = ColorPainter(Color(0xFF7A7A7A)),
                    disabled = ColorPainter(Color(0xFF5A5A5A)),
                ),
                progressTrack = ColorPainter(Color(0xFF404040)),
                progressFill = ColorPainter(Color(0xFF80FF20)),
                bottomBar = ColorPainter(Color(0xFF2B2B2B)),
                bottomBarItemSelected = ColorPainter(Color(0xFF555555)),
                bottomBarItem = McStatePainters(
                    normal = transparent,
                    pressed = ColorPainter(Color(0xFF3C3C3C)),
                    disabled = transparent,
                ),
                itemPlaceholder = ColorPainter(Color(0xFFB040B0)),
            )
        }
    }
}

internal val LocalMcSkin = staticCompositionLocalOf { McSkin.flat() }
