/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.sagisolas.recipehost.presentation.ModVersionsEntry
import org.sagisolas.recipehost.presentation.ModVersionsViewModel
import org.sagisolas.recipehost.shared.generated.resources.Res
import org.sagisolas.recipehost.shared.generated.resources.back
import org.sagisolas.recipehost.shared.generated.resources.mod_release
import org.sagisolas.recipehost.shared.generated.resources.mod_versions_title
import org.sagisolas.recipehost.ui.designsystem.McDropdown
import org.sagisolas.recipehost.ui.designsystem.McHeader
import org.sagisolas.recipehost.ui.designsystem.McText
import org.sagisolas.recipehost.ui.designsystem.McTheme
import org.sagisolas.recipehost.ui.designsystem.mcBackground
import org.sagisolas.recipehost.ui.navigation.Navigator
import org.sagisolas.recipehost.ui.tabScreenInsetsPadding

/**
 * Pushed onto the Settings tab's stack: each mod with a dropdown of its releases, newest first.
 * A pick is saved at once; back just leaves.
 */
@Composable
internal fun ModVersionsScreen(
    viewModel: ModVersionsViewModel,
    navigator: Navigator,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(modifier.fillMaxSize().mcBackground(McTheme.skin.screen).tabScreenInsetsPadding()) {
        McHeader(stringResource(Res.string.mod_versions_title), navigator::pop, stringResource(Res.string.back))
        ModRows(viewModel::select, state.mods)
    }
}

@Composable
private fun ModRows(
    onSelectRelease: (modId: String, releaseId: Long) -> Unit,
    mods: List<ModVersionsEntry>,
) {
    LazyColumn(
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(mods, key = { it.modId }) { mod ->
            ModRow(mod, onSelectRelease = { onSelectRelease(mod.modId, it) })
        }
    }
}

/** The mod's name, and a dropdown that picks its `Mod release`. */
@Composable
private fun ModRow(
    mod: ModVersionsEntry,
    onSelectRelease: (Long) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        McText(mod.name, Modifier.weight(1f), color = McTheme.colors.textOnDark, maxLines = 2)
        val selected = mod.releases.single { it.id == mod.selectedReleaseId }
        // McDropdown's label isn't composable, so the "6.0.1 · 1.21.1" labels are resolved here.
        val labels =
            mod.releases.associate { release ->
                val label = release.mcVersion?.let { stringResource(Res.string.mod_release, release.version, it) }
                release.id to (label ?: release.version)
            }
        McDropdown(
            options = mod.releases,
            selected = selected,
            onSelect = { onSelectRelease(it.id) },
            label = { labels.getValue(it.id) },
            modifier = Modifier.width(ReleaseDropdownWidth),
        )
    }
}

/** Fixed, so every row's dropdown lines up whatever its release labels are. */
private val ReleaseDropdownWidth = 160.dp
