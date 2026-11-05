package org.sagisolas.recipehost.ui.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.imageResource
import org.sagisolas.recipehost.shared.generated.resources.Res
import org.sagisolas.recipehost.shared.generated.resources.boss_bar_green_background
import org.sagisolas.recipehost.shared.generated.resources.boss_bar_green_progress
import org.sagisolas.recipehost.shared.generated.resources.container_slot
import org.sagisolas.recipehost.shared.generated.resources.menu_background
import org.sagisolas.recipehost.shared.generated.resources.missing_texture
import org.sagisolas.recipehost.shared.generated.resources.options_background
import org.sagisolas.recipehost.shared.generated.resources.popup_background
import org.sagisolas.recipehost.shared.generated.resources.recipehost_bottom_bar
import org.sagisolas.recipehost.shared.generated.resources.recipehost_bottom_bar_item_pressed
import org.sagisolas.recipehost.shared.generated.resources.recipehost_bottom_bar_item_selected
import org.sagisolas.recipehost.shared.generated.resources.recipehost_dropdown_option_pressed
import org.sagisolas.recipehost.shared.generated.resources.recipehost_scroller
import org.sagisolas.recipehost.shared.generated.resources.recipehost_scroller_background
import org.sagisolas.recipehost.shared.generated.resources.recipehost_text_field_disabled
import org.sagisolas.recipehost.shared.generated.resources.widget_button
import org.sagisolas.recipehost.shared.generated.resources.widget_button_disabled
import org.sagisolas.recipehost.shared.generated.resources.widget_button_highlighted
import org.sagisolas.recipehost.shared.generated.resources.widget_tab
import org.sagisolas.recipehost.shared.generated.resources.widget_tab_highlighted
import org.sagisolas.recipehost.shared.generated.resources.widget_tab_selected
import org.sagisolas.recipehost.shared.generated.resources.widget_text_field
import org.sagisolas.recipehost.shared.generated.resources.widget_text_field_highlighted

/** Every component's texture; [McTheme] provides the one from [rememberMcSkin]. */
@Immutable
internal data class McSkin(
    val screen: McTexture,
    val panel: McTexture,
    val button: McStateTextures,
    val textField: McTexture,
    val textFieldFocused: McTexture,
    val textFieldDisabled: McTexture,
    val dropdownOption: McStateTextures,
    val slot: McTexture,
    val tabSelected: McTexture,
    val tab: McStateTextures,
    val progressTrack: McTexture,
    val progressFill: McTexture,
    /** A list scrollbar's track and thumb, drawn by [mcVerticalScrollbar]. */
    val scrollerTrack: McTexture,
    val scrollerThumb: McTexture,
    val bottomBar: McTexture,
    val bottomBarItemSelected: McTexture,
    val bottomBarItem: McStateTextures,
    /** Drawn tiled (not sliced) behind lists such as an item grid, with [mcTiledBackground]. */
    val listBackground: ImageBitmap,
    /** Shown, unsliced and nearest-neighbour, where an image is missing or can't be decoded. */
    val missingTexture: ImageBitmap,
)

internal val LocalMcSkin = staticCompositionLocalOf<McSkin> {
    error("No McSkin provided: wrap the content in McTheme")
}

/**
 * The design system's skin, from its textures in `composeResources/drawable` (named after
 * Minecraft's sprite paths with `/` replaced by `_`). Borders are in texture pixels.
 */
@Composable
internal fun rememberMcSkin(): McSkin {
    val button = texture(Res.drawable.widget_button, border = 2)
    val tab = texture(Res.drawable.widget_tab, border = 2)
    return McSkin(
        screen = texture(Res.drawable.menu_background, border = 0),
        panel = texture(Res.drawable.popup_background, border = 2),
        button = McStateTextures(
            normal = button,
            pressed = texture(Res.drawable.widget_button_highlighted, border = 2),
            disabled = texture(Res.drawable.widget_button_disabled, border = 1),
        ),
        textField = texture(Res.drawable.widget_text_field, border = 1),
        textFieldFocused = texture(Res.drawable.widget_text_field_highlighted, border = 1),
        textFieldDisabled = texture(Res.drawable.recipehost_text_field_disabled, border = 1),
        dropdownOption = McStateTextures(
            normal = null,
            pressed = texture(Res.drawable.recipehost_dropdown_option_pressed, border = 1),
            disabled = null,
        ),
        slot = texture(Res.drawable.container_slot, border = 1),
        tabSelected = texture(Res.drawable.widget_tab_selected, border = 2),
        tab = McStateTextures(
            normal = tab,
            pressed = texture(Res.drawable.widget_tab_highlighted, border = 2),
            disabled = tab,
        ),
        progressTrack = texture(Res.drawable.boss_bar_green_background, border = 1),
        progressFill = texture(Res.drawable.boss_bar_green_progress, border = 1),
        scrollerTrack = texture(Res.drawable.recipehost_scroller_background, border = 0),
        scrollerThumb = texture(Res.drawable.recipehost_scroller, border = 1),
        bottomBar = texture(Res.drawable.recipehost_bottom_bar, border = 1),
        bottomBarItemSelected = texture(Res.drawable.recipehost_bottom_bar_item_selected, border = 1),
        bottomBarItem = McStateTextures(
            normal = null,
            pressed = texture(Res.drawable.recipehost_bottom_bar_item_pressed, border = 0),
            disabled = null,
        ),
        listBackground = imageResource(Res.drawable.options_background),
        missingTexture = imageResource(Res.drawable.missing_texture),
    )
}

@Composable
private fun texture(resource: DrawableResource, border: Int): McTexture {
    val image = imageResource(resource)
    return remember(image, border) { McTexture(image, SliceInsets(border), McDimens.TexturePixelSize) }
}
