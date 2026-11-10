package org.sagisolas.recipehost.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import org.sagisolas.recipehost.presentation.SettingsViewModel
import org.sagisolas.recipehost.shared.generated.resources.Res
import org.sagisolas.recipehost.shared.generated.resources.cancel
import org.sagisolas.recipehost.shared.generated.resources.settings_clear
import org.sagisolas.recipehost.shared.generated.resources.settings_clear_catalog
import org.sagisolas.recipehost.shared.generated.resources.settings_clear_confirm
import org.sagisolas.recipehost.ui.designsystem.McButton
import org.sagisolas.recipehost.ui.designsystem.McPanel
import org.sagisolas.recipehost.ui.designsystem.McText
import org.sagisolas.recipehost.ui.designsystem.McTheme
import org.sagisolas.recipehost.ui.tabScreenInsetsPadding

/**
 * The Settings tab's root: "Clear Catalog", which asks for confirmation first. Calls [onCleared]
 * once the `Catalog` is empty.
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
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        McButton(
            stringResource(Res.string.settings_clear_catalog),
            onClick = viewModel::requestClear,
            enabled = state.clearEnabled,
        )
    }
    if (state.confirming) {
        ClearCatalogDialog(onConfirm = viewModel::confirmClear, onCancel = viewModel::cancelClear)
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
