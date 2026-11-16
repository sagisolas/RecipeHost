/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.ui.designsystem

import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role

/**
 * A button [McDimens.WidgetHeight] tall; it wraps its text unless the caller gives it a width.
 * Normal, pressed and disabled each have their own background.
 */
@Composable
internal fun McButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
) {
    McPressable(
        onClick = onClick,
        onLongClick = onLongClick,
        textures = McTheme.skin.button,
        modifier = modifier.height(McDimens.WidgetHeight),
        enabled = enabled,
    ) {
        McText(
            text = text,
            modifier = Modifier.padding(horizontal = McDimens.WidgetPadding),
            color = McTheme.colors.onDark(enabled),
            maxLines = 1,
        )
    }
}

/** A square button with one glyph, for arrows and other small controls; [McIconButton] takes an icon. */
@Composable
internal fun McSquareButton(
    glyph: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    McPressable(
        onClick = onClick,
        textures = McTheme.skin.button,
        modifier = modifier.size(McDimens.SquareButtonSize),
        enabled = enabled,
    ) {
        McText(glyph, color = McTheme.colors.onDark(enabled))
    }
}

/**
 * A square button with one [icon], tinted like button text; [contentDescription] names what it
 * does, since there is no text.
 */
@Composable
internal fun McIconButton(
    icon: Painter,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    McPressable(
        onClick = onClick,
        textures = McTheme.skin.button,
        modifier = modifier.size(McDimens.SquareButtonSize),
        enabled = enabled,
    ) {
        Image(
            icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(McDimens.ButtonIconSize),
            colorFilter = ColorFilter.tint(McTheme.colors.onDark(enabled)),
        )
    }
}

/**
 * A clickable box whose background follows its interaction state through [McStateIndication].
 * The base of buttons, tabs, bottom-bar items and dropdown options. [onLongClick], when given,
 * runs on a long press instead of [onClick].
 */
@Composable
internal fun McPressable(
    onClick: () -> Unit,
    textures: McStateTextures,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    role: Role = Role.Button,
    contentAlignment: Alignment = Alignment.Center,
    onLongClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier =
            modifier.combinedClickable(
                interactionSource = interactionSource,
                indication = McStateIndication(textures, enabled),
                enabled = enabled,
                role = role,
                onLongClick = onLongClick,
                onClick = onClick,
            ),
        contentAlignment = contentAlignment,
        content = content,
    )
}
