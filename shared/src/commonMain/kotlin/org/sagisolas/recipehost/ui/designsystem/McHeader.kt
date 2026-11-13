/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.ui.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.sagisolas.recipehost.shared.generated.resources.Res
import org.sagisolas.recipehost.shared.generated.resources.mc_back_arrow

/**
 * A screen's header: an [McIconButton] with the back chevron at the start when [onBack] is set,
 * and [title] centred on the full width. The title never goes under the button: one too wide to
 * stay centred slides right just enough to clear it, and is cut only at the end edge.
 * [backDescription] names the button for accessibility.
 */
@Composable
internal fun McHeader(
    onBack: (() -> Unit)?,
    backDescription: String,
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
) {
    Layout(
        content = {
            Box(contentAlignment = Alignment.Center) { title() }
            if (onBack != null) {
                McIconButton(painterResource(Res.drawable.mc_back_arrow), backDescription, onClick = onBack)
            }
        },
        modifier = modifier.fillMaxWidth().padding(HeaderPadding),
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val back = measurables.getOrNull(1)?.measure(Constraints())
        val titleStart = back?.let { it.width + HeaderPadding.roundToPx() } ?: 0
        val title = measurables[0].measure(Constraints(maxWidth = (width - titleStart).coerceAtLeast(0)))
        val height =
            maxOf(McDimens.SquareButtonSize.roundToPx(), back?.height ?: 0, title.height)
                .coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(width, height) {
            back?.place(0, (height - back.height) / 2)
            title.place(maxOf(titleStart, (width - title.width) / 2), (height - title.height) / 2)
        }
    }
}

/** An [McHeader] whose title is one line of [title] text in the title style. */
@Composable
internal fun McHeader(
    title: String,
    onBack: (() -> Unit)?,
    backDescription: String,
    modifier: Modifier = Modifier,
) {
    McHeader(onBack, backDescription, modifier) {
        McText(
            title,
            color = McTheme.colors.textOnDark,
            style = McTheme.typography.title.copy(textAlign = TextAlign.Center),
            maxLines = 1,
        )
    }
}

/** Space around the header, and between the back button and the title. */
private val HeaderPadding = 8.dp
