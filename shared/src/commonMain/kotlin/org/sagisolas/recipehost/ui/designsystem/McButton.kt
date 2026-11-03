package org.sagisolas.recipehost.ui.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role

/**
 * A button with Minecraft's widget proportions: size it by width and the height follows from
 * [McDimens.WIDGET_ASPECT_RATIO]. Normal, pressed and disabled each have their own background.
 */
@Composable
internal fun McButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    McPressable(
        onClick = onClick,
        painters = McTheme.skin.button,
        modifier = modifier.aspectRatio(McDimens.WIDGET_ASPECT_RATIO),
        enabled = enabled,
    ) {
        McText(text, color = McTheme.colors.onDark(enabled), maxLines = 1)
    }
}

/** A square button with one glyph, for arrows and other small controls. */
@Composable
internal fun McSquareButton(
    glyph: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    McPressable(
        onClick = onClick,
        painters = McTheme.skin.button,
        modifier = modifier.size(McDimens.SquareButtonSize),
        enabled = enabled,
    ) {
        McText(glyph, color = McTheme.colors.onDark(enabled))
    }
}

/**
 * A clickable box whose background follows its interaction state through [McStateIndication].
 * The base of buttons, tabs, bottom-bar items and dropdown options.
 */
@Composable
internal fun McPressable(
    onClick: () -> Unit,
    painters: McStatePainters,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    role: Role = Role.Button,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier.clickable(
            interactionSource = interactionSource,
            indication = McStateIndication(painters, enabled),
            enabled = enabled,
            role = role,
            onClick = onClick,
        ),
        contentAlignment = contentAlignment,
        content = content,
    )
}
