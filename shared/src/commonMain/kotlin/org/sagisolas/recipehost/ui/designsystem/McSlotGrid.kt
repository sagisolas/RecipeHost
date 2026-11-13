/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.ui.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A scrolling grid of [McDimens.SlotSize] cells with a [mcVerticalScrollbar] on the right.
 *
 * Cells start [McDimens.ListEdgePadding] from the left edge, the same distance the scrollbar keeps
 * from the right edge. On the right the grid keeps a [McDimens.ScrollbarGutter] and fits only as
 * many columns as the width leaves between the two, so no cell reaches the scrollbar. Space left
 * over after the last column goes to the right.
 */
@Composable
internal fun McSlotGrid(
    modifier: Modifier = Modifier,
    state: LazyGridState = rememberLazyGridState(),
    verticalPadding: Dp = 8.dp,
    content: LazyGridScope.() -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.FixedSize(McDimens.SlotSize),
        modifier = modifier.mcVerticalScrollbar(state),
        state = state,
        // The grid subtracts this padding before FixedSize counts the columns.
        contentPadding =
            PaddingValues(
                start = McDimens.ListEdgePadding,
                top = verticalPadding,
                end = McDimens.ScrollbarGutter,
                bottom = verticalPadding,
            ),
        horizontalArrangement = Arrangement.Start,
        content = content,
    )
}
