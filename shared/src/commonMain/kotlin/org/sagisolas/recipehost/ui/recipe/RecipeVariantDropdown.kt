/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.ui.recipe

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import org.sagisolas.recipehost.shared.generated.resources.Res
import org.sagisolas.recipehost.shared.generated.resources.item_from_instances
import org.sagisolas.recipehost.ui.designsystem.McDropdown

/**
 * The "From <instance>" choice between a recipe's bodies: [variants] lists each variant's Instance
 * names, newest first. Nothing shows when it is empty, as it is for a recipe with one body.
 */
@Composable
internal fun RecipeVariantDropdown(
    variants: List<List<String>>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (variants.isEmpty()) return
    // McDropdown's label isn't composable, so the labels are resolved here.
    val labels = variants.map { stringResource(Res.string.item_from_instances, it.joinToString(", ")) }
    McDropdown(
        options = variants.indices.toList(),
        selected = selected,
        onSelect = onSelect,
        label = { labels[it] },
        modifier = modifier.fillMaxWidth(),
    )
}
