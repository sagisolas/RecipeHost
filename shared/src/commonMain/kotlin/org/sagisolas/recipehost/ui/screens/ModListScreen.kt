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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.sagisolas.recipehost.presentation.ModListEntry
import org.sagisolas.recipehost.presentation.ModListViewModel
import org.sagisolas.recipehost.shared.generated.resources.Res
import org.sagisolas.recipehost.shared.generated.resources.mod_list_title
import org.sagisolas.recipehost.ui.designsystem.McText
import org.sagisolas.recipehost.ui.designsystem.McTheme
import org.sagisolas.recipehost.ui.navigation.Navigator
import org.sagisolas.recipehost.ui.tabScreenInsetsPadding

/** Pushed onto the Items tab's stack: each mod with its selected `Mod release`; a tap filters the grid by it. */
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
        LazyColumn(
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
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

/** The mod's name on the left; its version over its Minecraft version on the right. */
@Composable
private fun ModRow(
    mod: ModListEntry,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        McText(mod.name, Modifier.weight(1f), color = McTheme.colors.textOnDark, maxLines = 2)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), horizontalAlignment = Alignment.End) {
            McText(
                mod.version,
                color = McTheme.colors.textOnDark,
                style = McTheme.typography.body.copy(textAlign = TextAlign.End),
                maxLines = 1,
            )
            mod.mcVersion?.let {
                McText(
                    it,
                    color = McTheme.colors.textHint,
                    style = McTheme.typography.small.copy(textAlign = TextAlign.End),
                    maxLines = 1,
                )
            }
        }
    }
}
