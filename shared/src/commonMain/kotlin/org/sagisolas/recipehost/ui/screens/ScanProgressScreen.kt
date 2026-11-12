package org.sagisolas.recipehost.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.sagisolas.recipehost.presentation.ScanProgressUiState
import org.sagisolas.recipehost.presentation.ScanProgressUiState.FailureReason
import org.sagisolas.recipehost.presentation.ScanProgressUiState.Step
import org.sagisolas.recipehost.presentation.ScanProgressViewModel
import org.sagisolas.recipehost.shared.generated.resources.Res
import org.sagisolas.recipehost.shared.generated.resources.scan_again
import org.sagisolas.recipehost.shared.generated.resources.scan_error_busy
import org.sagisolas.recipehost.shared.generated.resources.scan_error_game
import org.sagisolas.recipehost.shared.generated.resources.scan_error_interrupted
import org.sagisolas.recipehost.shared.generated.resources.scan_error_invalid_code
import org.sagisolas.recipehost.shared.generated.resources.scan_error_local_network
import org.sagisolas.recipehost.shared.generated.resources.scan_error_network
import org.sagisolas.recipehost.shared.generated.resources.scan_error_unauthorized
import org.sagisolas.recipehost.shared.generated.resources.scan_error_version_mismatch
import org.sagisolas.recipehost.shared.generated.resources.scan_step_connecting
import org.sagisolas.recipehost.shared.generated.resources.scan_step_items
import org.sagisolas.recipehost.shared.generated.resources.scan_step_items_count
import org.sagisolas.recipehost.shared.generated.resources.scan_step_recipes
import org.sagisolas.recipehost.shared.generated.resources.scan_step_recipes_count
import org.sagisolas.recipehost.ui.designsystem.McButton
import org.sagisolas.recipehost.ui.designsystem.McText
import org.sagisolas.recipehost.ui.designsystem.McTheme
import org.sagisolas.recipehost.ui.designsystem.mcBackground

/**
 * Full screen: Connecting → Items n/N → Recipes m/M, or why the Scan failed with "Scan again".
 * Calls [onCommitted] as soon as Items and Recipes are in the `Catalog`.
 */
@Composable
internal fun ScanProgressScreen(
    viewModel: ScanProgressViewModel,
    onCommitted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (state == ScanProgressUiState.Committed) {
        LaunchedEffect(Unit) { onCommitted() }
    }
    Column(
        modifier
            .fillMaxSize()
            .mcBackground(McTheme.skin.screen)
            .safeDrawingPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (val current = state) {
            is ScanProgressUiState.Running -> {
                Steps(current.step)
            }

            is ScanProgressUiState.Failed -> {
                McText(
                    stringResource(messageOf(current.reason)),
                    color = McTheme.colors.textOnDark,
                    style = McTheme.typography.body.copy(textAlign = TextAlign.Center),
                )
                McButton(stringResource(Res.string.scan_again), onClick = viewModel::scanAgain)
            }

            ScanProgressUiState.Committed -> {
                Unit
            }
        }
    }
}

/** The three steps: done and current ones in white, the ones still to come greyed out. */
@Composable
private fun Steps(step: Step) {
    val reached =
        when (step) {
            Step.Connecting -> 0
            is Step.Items -> 1
            is Step.Recipes -> 2
        }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StepLine(stringResource(Res.string.scan_step_connecting), reached >= 0)
        StepLine(
            if (step is Step.Items) {
                stringResource(Res.string.scan_step_items_count, step.received, step.total)
            } else {
                stringResource(Res.string.scan_step_items)
            },
            reached >= 1,
        )
        StepLine(
            if (step is Step.Recipes) {
                stringResource(Res.string.scan_step_recipes_count, step.received, step.total)
            } else {
                stringResource(Res.string.scan_step_recipes)
            },
            reached >= 2,
        )
    }
}

@Composable
private fun StepLine(
    text: String,
    reached: Boolean,
) {
    McText(text, color = if (reached) McTheme.colors.textOnDark else McTheme.colors.textHint)
}

private fun messageOf(reason: FailureReason): StringResource =
    when (reason) {
        FailureReason.Unauthorized -> Res.string.scan_error_unauthorized
        FailureReason.Busy -> Res.string.scan_error_busy
        FailureReason.GameError -> Res.string.scan_error_game
        FailureReason.VersionMismatch -> Res.string.scan_error_version_mismatch
        FailureReason.InvalidCode -> Res.string.scan_error_invalid_code
        FailureReason.Network -> Res.string.scan_error_network
        FailureReason.LocalNetworkDenied -> Res.string.scan_error_local_network
        FailureReason.Interrupted -> Res.string.scan_error_interrupted
    }
