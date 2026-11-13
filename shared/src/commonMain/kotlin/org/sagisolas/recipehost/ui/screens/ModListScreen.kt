/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.sagisolas.recipehost.presentation.ModListEntry
import org.sagisolas.recipehost.presentation.ModListViewModel
import org.sagisolas.recipehost.shared.generated.resources.Res
import org.sagisolas.recipehost.shared.generated.resources.back
import org.sagisolas.recipehost.shared.generated.resources.mod_list_title
import org.sagisolas.recipehost.ui.designsystem.McHeader
import org.sagisolas.recipehost.ui.designsystem.McText
import org.sagisolas.recipehost.ui.designsystem.McTheme
import org.sagisolas.recipehost.ui.designsystem.mcBackground
import org.sagisolas.recipehost.ui.navigation.Navigator
import org.sagisolas.recipehost.ui.tabScreenInsetsPadding

/** Pushed onto the Items tab's stack: the mods, a tap on one filters the grid by it. */
@Composable
internal fun ModListScreen(
    viewModel: ModListViewModel,
    navigator: Navigator,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(modifier.fillMaxSize().mcBackground(McTheme.skin.screen).tabScreenInsetsPadding()) {
        McHeader(stringResource(Res.string.mod_list_title), navigator::pop, stringResource(Res.string.back))
        // No side padding or gaps here: each row pads itself inside its tap area, edge to edge.
        LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
            items(state.mods, key = { it.modId }) { mod ->
                ModRow(
                    mod,
                    onClick = {
                        viewModel.chooseMod(mod.modId)
                        navigator.pop()
                    },
                )
            }
        }
    }
}

/**
 * The mod's name, and on the right how many Items its selected release adds; a tap anywhere
 * across the row, padding included, filters the grid by it.
 */
@Composable
private fun ModRow(
    mod: ModListEntry,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        McText(mod.name, Modifier.weight(1f), color = McTheme.colors.textOnDark, maxLines = 2)
        McText(mod.itemCount.toString(), color = McTheme.colors.textHint, maxLines = 1)
    }
}
