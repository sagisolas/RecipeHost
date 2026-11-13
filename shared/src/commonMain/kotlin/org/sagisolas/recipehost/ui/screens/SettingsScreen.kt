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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.sagisolas.recipehost.AppInfo
import org.sagisolas.recipehost.domain.catalog.CatalogCounts
import org.sagisolas.recipehost.presentation.ByteSize
import org.sagisolas.recipehost.presentation.ByteUnit
import org.sagisolas.recipehost.presentation.SettingsViewModel
import org.sagisolas.recipehost.presentation.byteSizeOf
import org.sagisolas.recipehost.shared.generated.resources.Res
import org.sagisolas.recipehost.shared.generated.resources.cancel
import org.sagisolas.recipehost.shared.generated.resources.settings_author
import org.sagisolas.recipehost.shared.generated.resources.settings_catalog
import org.sagisolas.recipehost.shared.generated.resources.settings_clear
import org.sagisolas.recipehost.shared.generated.resources.settings_clear_catalog
import org.sagisolas.recipehost.shared.generated.resources.settings_clear_confirm
import org.sagisolas.recipehost.shared.generated.resources.settings_items
import org.sagisolas.recipehost.shared.generated.resources.settings_mod_versions
import org.sagisolas.recipehost.shared.generated.resources.settings_mods
import org.sagisolas.recipehost.shared.generated.resources.settings_mods_section
import org.sagisolas.recipehost.shared.generated.resources.settings_open
import org.sagisolas.recipehost.shared.generated.resources.settings_recipes
import org.sagisolas.recipehost.shared.generated.resources.settings_source_code
import org.sagisolas.recipehost.shared.generated.resources.settings_storage_used
import org.sagisolas.recipehost.shared.generated.resources.settings_version
import org.sagisolas.recipehost.shared.generated.resources.size_bytes
import org.sagisolas.recipehost.shared.generated.resources.size_gigabytes
import org.sagisolas.recipehost.shared.generated.resources.size_kilobytes
import org.sagisolas.recipehost.shared.generated.resources.size_megabytes
import org.sagisolas.recipehost.ui.designsystem.McButton
import org.sagisolas.recipehost.ui.designsystem.McPanel
import org.sagisolas.recipehost.ui.designsystem.McText
import org.sagisolas.recipehost.ui.designsystem.McTheme
import org.sagisolas.recipehost.ui.designsystem.mcBackground
import org.sagisolas.recipehost.ui.tabScreenInsetsPadding

/**
 * The Settings tab's root: the "Mods" section, whose Open calls [onOpenModVersions], and the
 * "Catalog" section with its counts and "Clear Catalog", which asks for confirmation first, and the
 * app's info at the bottom.
 * Calls [onCleared] once the `Catalog` is empty.
 */
@Composable
internal fun SettingsScreen(
    viewModel: SettingsViewModel,
    onCleared: () -> Unit,
    onOpenModVersions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (state.cleared) {
        LaunchedEffect(Unit) { onCleared() }
    }
    Column(
        modifier
            .fillMaxSize()
            .mcBackground(McTheme.skin.screen)
            .tabScreenInsetsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ModsSection(onOpenModVersions)
        CatalogSection(state.counts, state.clearEnabled, onClear = viewModel::requestClear)
        Spacer(Modifier.weight(1f))
        AppInfoFooter()
    }
    if (state.confirming) {
        ClearCatalogDialog(onConfirm = viewModel::confirmClear, onCancel = viewModel::cancelClear)
    }
}

/** The "Mods" section: "Change mod versions" with an Open button. */
@Composable
private fun ModsSection(onOpenModVersions: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        McText(stringResource(Res.string.settings_mods_section), style = McTheme.typography.title)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            McText(stringResource(Res.string.settings_mod_versions))
            McButton(stringResource(Res.string.settings_open), onClick = onOpenModVersions)
        }
    }
}

/**
 * The "Catalog" section: how many mod releases, Items and recipes it holds, how much storage it
 * uses, and "Clear Catalog".
 */
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
        StatRow(stringResource(Res.string.settings_storage_used), counts?.bytes?.let { sizeText(byteSizeOf(it)) })
        McButton(
            stringResource(Res.string.settings_clear_catalog),
            onClick = onClear,
            modifier = Modifier.padding(top = 8.dp),
            enabled = clearEnabled,
        )
    }
}

/**
 * The app's author with a mailto: link to their email, its version and a link to its source code,
 * in muted text.
 */
@Composable
private fun AppInfoFooter() {
    val style = McTheme.typography.small
    val color = McTheme.colors.textOnDarkMuted
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            McText(stringResource(Res.string.settings_author, AppInfo.AUTHOR) + " (", color = color, style = style)
            FooterLink(AppInfo.AUTHOR_EMAIL, "mailto:${AppInfo.AUTHOR_EMAIL}")
            McText(")", color = color, style = style)
        }
        McText(stringResource(Res.string.settings_version, AppInfo.VERSION), color = color, style = style)
        FooterLink(stringResource(Res.string.settings_source_code), AppInfo.SOURCE_URL)
    }
}

/** Underlined muted [text] that opens [uri]. */
@Composable
private fun FooterLink(
    text: String,
    uri: String,
) {
    val uriHandler = LocalUriHandler.current
    McText(
        text,
        modifier = Modifier.clickable { uriHandler.openUri(uri) },
        color = McTheme.colors.textOnDarkMuted,
        style = McTheme.typography.small.copy(textDecoration = TextDecoration.Underline),
    )
}

/** [label] on the left, [count] on the right; a dash until it's counted. */
@Composable
private fun CountRow(
    label: String,
    count: Long?,
) = StatRow(label, count?.toString())

/** [label] on the left, [value] on the right; a dash while it's null. */
@Composable
private fun StatRow(
    label: String,
    value: String?,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        McText(label)
        McText(value ?: "–")
    }
}

/** [size] with its unit as the current locale writes it. */
@Composable
private fun sizeText(size: ByteSize) = stringResource(size.unit.format, size.amount)

private val ByteUnit.format: StringResource
    get() =
        when (this) {
            ByteUnit.Bytes -> Res.string.size_bytes
            ByteUnit.Kilobytes -> Res.string.size_kilobytes
            ByteUnit.Megabytes -> Res.string.size_megabytes
            ByteUnit.Gigabytes -> Res.string.size_gigabytes
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
