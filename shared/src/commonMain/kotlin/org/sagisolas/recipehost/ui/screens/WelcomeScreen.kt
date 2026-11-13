/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.sagisolas.recipehost.ModLinks
import org.sagisolas.recipehost.presentation.QrScanResult
import org.sagisolas.recipehost.presentation.WelcomeViewModel
import org.sagisolas.recipehost.shared.generated.resources.Res
import org.sagisolas.recipehost.shared.generated.resources.scan_qr
import org.sagisolas.recipehost.shared.generated.resources.welcome_camera_denied
import org.sagisolas.recipehost.shared.generated.resources.welcome_curseforge
import org.sagisolas.recipehost.shared.generated.resources.welcome_hint
import org.sagisolas.recipehost.shared.generated.resources.welcome_modrinth
import org.sagisolas.recipehost.shared.generated.resources.welcome_no_scanner
import org.sagisolas.recipehost.shared.generated.resources.welcome_pairing_button
import org.sagisolas.recipehost.shared.generated.resources.welcome_screenshot
import org.sagisolas.recipehost.shared.generated.resources.welcome_step_install
import org.sagisolas.recipehost.shared.generated.resources.welcome_step_scan
import org.sagisolas.recipehost.ui.designsystem.McButton
import org.sagisolas.recipehost.ui.designsystem.McText
import org.sagisolas.recipehost.ui.designsystem.McTheme
import org.sagisolas.recipehost.ui.designsystem.mcBackground

/**
 * Full screen, shown while the `Catalog` is empty: a centred, numbered list of the steps to pair — install the mod,
 * open the pairing screen in game, then scan its code. The numbers share one column, and each step's content starts
 * at the same edge.
 */
@Composable
internal fun WelcomeScreen(
    viewModel: WelcomeViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .mcBackground(McTheme.skin.screen)
            .safeDrawingPadding(),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = maxHeight)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier.widthIn(max = 480.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(32.dp),
            ) {
                WelcomeStep(1, stringResource(Res.string.welcome_step_install)) {
                    WelcomeLink(stringResource(Res.string.welcome_modrinth), ModLinks.MODRINTH_URL)
                    WelcomeLink(stringResource(Res.string.welcome_curseforge), ModLinks.CURSEFORGE_URL)
                }
                WelcomeStep(2, stringResource(Res.string.welcome_hint)) {
                    Image(
                        painterResource(Res.drawable.welcome_pairing_button),
                        contentDescription = stringResource(Res.string.welcome_screenshot),
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = ContentScale.FillWidth,
                    )
                }
                WelcomeStep(3, stringResource(Res.string.welcome_step_scan)) {
                    McButton(stringResource(Res.string.scan_qr), onClick = viewModel::scanQr)
                    scanNoticeOf(state.lastScan)?.let { McText(stringResource(it), color = McTheme.colors.textHint) }
                }
            }
        }
    }
}

/** One numbered step: the number in its own fixed-width column, the title and [content] start-aligned beside it. */
@Composable
private fun WelcomeStep(
    number: Int,
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Row(Modifier.fillMaxWidth()) {
        McText(
            "$number.",
            modifier = Modifier.width(32.dp),
            color = McTheme.colors.textOnDark,
            style = McTheme.typography.title,
        )
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            McText(title, color = McTheme.colors.textOnDark, style = McTheme.typography.title)
            content()
        }
    }
}

@Composable
private fun WelcomeLink(
    text: String,
    url: String,
) {
    val uriHandler = LocalUriHandler.current
    McText(
        text,
        modifier = Modifier.clickable { uriHandler.openUri(url) }.padding(vertical = 4.dp),
        color = McTheme.colors.textOnDark,
        style = McTheme.typography.body.copy(textDecoration = TextDecoration.Underline),
    )
}

/** The notice under "Scan QR" for how the last scan went, or `null` for none. */
private fun scanNoticeOf(lastScan: QrScanResult?): StringResource? =
    when (lastScan) {
        QrScanResult.NoScanner -> Res.string.welcome_no_scanner
        QrScanResult.CameraDenied -> Res.string.welcome_camera_denied
        QrScanResult.Scanned, null -> null
    }
