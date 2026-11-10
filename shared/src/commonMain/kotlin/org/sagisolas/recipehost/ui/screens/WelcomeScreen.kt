package org.sagisolas.recipehost.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.sagisolas.recipehost.presentation.WelcomeViewModel
import org.sagisolas.recipehost.shared.generated.resources.Res
import org.sagisolas.recipehost.shared.generated.resources.scan_qr
import org.sagisolas.recipehost.shared.generated.resources.welcome_hint
import org.sagisolas.recipehost.shared.generated.resources.welcome_no_scanner
import org.sagisolas.recipehost.ui.designsystem.McButton
import org.sagisolas.recipehost.ui.designsystem.McText
import org.sagisolas.recipehost.ui.designsystem.McTheme

/** Full screen, shown while the `Catalog` is empty: the hint centred, "Scan QR" below it. */
@Composable
internal fun WelcomeScreen(
    viewModel: WelcomeViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(
        modifier.fillMaxSize().safeDrawingPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val centred = McTheme.typography.body.copy(textAlign = TextAlign.Center)
        McText(stringResource(Res.string.welcome_hint), color = McTheme.colors.textOnDark, style = centred)
        McButton(stringResource(Res.string.scan_qr), onClick = viewModel::scanQr)
        if (state.scannerUnavailable) {
            McText(stringResource(Res.string.welcome_no_scanner), color = McTheme.colors.textHint, style = centred)
        }
    }
}
