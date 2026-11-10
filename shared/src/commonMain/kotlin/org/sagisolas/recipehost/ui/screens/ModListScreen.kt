package org.sagisolas.recipehost.ui.screens

import androidx.compose.foundation.clickable
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
import org.sagisolas.recipehost.presentation.ModListEntry
import org.sagisolas.recipehost.presentation.ModListViewModel
import org.sagisolas.recipehost.shared.generated.resources.Res
import org.sagisolas.recipehost.shared.generated.resources.mod_list_title
import org.sagisolas.recipehost.shared.generated.resources.mod_release
import org.sagisolas.recipehost.ui.designsystem.McDropdown
import org.sagisolas.recipehost.ui.designsystem.McText
import org.sagisolas.recipehost.ui.designsystem.McTheme
import org.sagisolas.recipehost.ui.navigation.Navigator
import org.sagisolas.recipehost.ui.tabScreenInsetsPadding

/** Pushed onto the Items tab's stack: each mod with a dropdown of its releases, newest first. */
@Composable
internal fun ModListScreen(
    viewModel: ModListViewModel,
    navigator: Navigator,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(modifier.fillMaxSize().tabScreenInsetsPadding()) {
        McText(
            stringResource(Res.string.mod_list_title),
            Modifier.padding(8.dp),
            color = McTheme.colors.textOnDark,
            style = McTheme.typography.title,
        )
        ModRows(viewModel, navigator, state.mods)
    }
}

@Composable
private fun ModRows(
    viewModel: ModListViewModel,
    navigator: Navigator,
    mods: List<ModListEntry>,
) {
    LazyColumn(
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(mods, key = { it.modId }) { mod ->
            ModRow(
                mod,
                onClick = {
                    viewModel.chooseMod(mod.modId)
                    navigator.pop()
                },
                onSelectRelease = { viewModel.select(mod.modId, it) },
            )
        }
    }
}

/** Tapping the row filters the grid by the mod; the dropdown picks its `Mod release`. */
@Composable
private fun ModRow(
    mod: ModListEntry,
    onClick: () -> Unit,
    onSelectRelease: (Long) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        McText(mod.name, Modifier.weight(1f), color = McTheme.colors.textOnDark, maxLines = 2)
        val selected = mod.releases.single { it.id == mod.selectedReleaseId }
        // McDropdown's label isn't composable, so the "6.0.1 · MC 1.21.1" labels are resolved here.
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
private val ReleaseDropdownWidth = 200.dp
