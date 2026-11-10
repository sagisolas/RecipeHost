package org.sagisolas.recipehost.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.sagisolas.recipehost.domain.catalog.CatalogCounts
import org.sagisolas.recipehost.presentation.SettingsViewModel
import org.sagisolas.recipehost.shared.generated.resources.Res
import org.sagisolas.recipehost.shared.generated.resources.cancel
import org.sagisolas.recipehost.shared.generated.resources.settings_catalog
import org.sagisolas.recipehost.shared.generated.resources.settings_clear
import org.sagisolas.recipehost.shared.generated.resources.settings_clear_catalog
import org.sagisolas.recipehost.shared.generated.resources.settings_clear_confirm
import org.sagisolas.recipehost.shared.generated.resources.settings_items
import org.sagisolas.recipehost.shared.generated.resources.settings_mods
import org.sagisolas.recipehost.shared.generated.resources.settings_recipes
import org.sagisolas.recipehost.ui.designsystem.McButton
import org.sagisolas.recipehost.ui.designsystem.McPanel
import org.sagisolas.recipehost.ui.designsystem.McText
import org.sagisolas.recipehost.ui.designsystem.McTheme
import org.sagisolas.recipehost.ui.tabScreenInsetsPadding

/**
 * The Settings tab's root: the "Catalog" section with its counts and "Clear Catalog", which asks
 * for confirmation first. Calls [onCleared] once the `Catalog` is empty.
 */
@Composable
internal fun SettingsScreen(
    viewModel: SettingsViewModel,
    onCleared: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (state.cleared) {
        LaunchedEffect(Unit) { onCleared() }
    }
    Column(
        modifier.fillMaxSize().tabScreenInsetsPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CatalogSection(state.counts, state.clearEnabled, onClear = viewModel::requestClear)
    }
    if (state.confirming) {
        ClearCatalogDialog(onConfirm = viewModel::confirmClear, onCancel = viewModel::cancelClear)
    }
}

/** The "Catalog" section: how many mod releases, Items and recipes it holds, and "Clear Catalog". */
@Composable
private fun CatalogSection(
    counts: CatalogCounts?,
    clearEnabled: Boolean,
    onClear: () -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        McText(stringResource(Res.string.settings_catalog), style = McTheme.typography.title)
        CountRow(stringResource(Res.string.settings_mods), counts?.mods)
        CountRow(stringResource(Res.string.settings_items), counts?.items)
        CountRow(stringResource(Res.string.settings_recipes), counts?.recipes)
        McButton(
            stringResource(Res.string.settings_clear_catalog),
            onClick = onClear,
            modifier = Modifier.padding(top = 8.dp),
            enabled = clearEnabled,
        )
    }
}

/** [label] on the left, [count] on the right; a dash until it's counted. */
@Composable
private fun CountRow(
    label: String,
    count: Long?,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        McText(label)
        McText(count?.toString() ?: "–")
    }
}

/** Asks before the `Catalog` goes; back and a tap outside cancel. */
@Composable
private fun ClearCatalogDialog(
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Dialog(onDismissRequest = onCancel) {
        McPanel(contentPadding = PaddingValues(16.dp)) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                McText(
                    stringResource(Res.string.settings_clear_confirm),
                    style = McTheme.typography.body.copy(textAlign = TextAlign.Center),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    McButton(stringResource(Res.string.cancel), onClick = onCancel)
                    McButton(stringResource(Res.string.settings_clear), onClick = onConfirm)
                }
            }
        }
    }
}
