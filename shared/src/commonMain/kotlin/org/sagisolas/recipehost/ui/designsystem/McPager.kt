/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.ui.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** ◀ "n / N" ▶ for paging through [count] items; [index] is zero-based. Shows "0 / 0" when empty. */
@Composable
internal fun McPager(
    index: Int,
    count: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        McSquareButton("◀", onPrevious, enabled = index > 0)
        val counter = if (count == 0) "0 / 0" else "${index + 1} / $count"
        McText(counter, color = McTheme.colors.textOnDark, maxLines = 1)
        McSquareButton("▶", onNext, enabled = index < count - 1)
    }
}
