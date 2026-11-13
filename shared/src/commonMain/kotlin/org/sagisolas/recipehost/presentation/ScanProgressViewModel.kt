/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.sagisolas.recipehost.data.pairing.PairingPayloadException
import org.sagisolas.recipehost.data.scan.ScanProgress
import org.sagisolas.recipehost.data.transport.ScanFailure
import org.sagisolas.recipehost.protocol.ErrorCode

/** What the Scan progress screen shows. */
internal sealed interface ScanProgressUiState {
    data class Running(
        val step: Step,
    ) : ScanProgressUiState

    /** Items and Recipes are in the `Catalog`: the screen leaves for the Items tab. */
    data object Committed : ScanProgressUiState

    /** The Scan stopped (or never started); the screen offers "Scan again". */
    data class Failed(
        val reason: FailureReason,
    ) : ScanProgressUiState

    sealed interface Step {
        data object Connecting : Step

        data class Items(
            val received: Int,
            val total: Int,
        ) : Step

        data class Recipes(
            val received: Int,
            val total: Int,
        ) : Step
    }

    enum class FailureReason {
        Unauthorized,
        Busy,
        GameError,
        VersionMismatch,
        InvalidCode,

        /** The connection failed, and failed again on the retry. */
        Network,

        /** No host was reachable because the OS denies the app the local network (iOS). */
        LocalNetworkDenied,

        /** No Scan has run since the app started, e.g. after the OS killed it mid-Scan. */
        Interrupted,
    }
}

/**
 * Maps the running `Scan`'s [progress] and the last rejected pairing code ([pairingFailure],
 * which wins, since a rejected code starts no Scan) to [ScanProgressUiState].
 */
internal class ScanProgressViewModel(
    progress: StateFlow<ScanProgress?>,
    pairingFailure: StateFlow<PairingPayloadException?>,
    private val qr: QrPairing,
) : ViewModel() {
    val state: StateFlow<ScanProgressUiState> =
        combine(progress, pairingFailure, ::stateOf)
            .stateIn(viewModelScope, SharingStarted.Eagerly, stateOf(progress.value, pairingFailure.value))

    fun scanAgain() {
        viewModelScope.launch { qr.tryScanQr() }
    }
}

private fun stateOf(
    progress: ScanProgress?,
    pairingFailure: PairingPayloadException?,
): ScanProgressUiState {
    if (pairingFailure != null) return ScanProgressUiState.Failed(reasonOf(pairingFailure))
    return when (progress) {
        null -> {
            ScanProgressUiState.Failed(ScanProgressUiState.FailureReason.Interrupted)
        }

        ScanProgress.Connecting -> {
            ScanProgressUiState.Running(ScanProgressUiState.Step.Connecting)
        }

        is ScanProgress.Items -> {
            ScanProgressUiState.Running(
                ScanProgressUiState.Step.Items(progress.received, progress.total),
            )
        }

        is ScanProgress.Recipes -> {
            ScanProgressUiState.Running(
                ScanProgressUiState.Step.Recipes(progress.received, progress.total),
            )
        }

        ScanProgress.Committed, is ScanProgress.Icons, is ScanProgress.Layouts, ScanProgress.Done -> {
            ScanProgressUiState.Committed
        }

        is ScanProgress.Failed -> {
            ScanProgressUiState.Failed(reasonOf(progress.cause))
        }
    }
}

private fun reasonOf(failure: PairingPayloadException) =
    when (failure) {
        is PairingPayloadException.UnsupportedVersion -> {
            ScanProgressUiState.FailureReason.VersionMismatch
        }

        is PairingPayloadException.MalformedUri, is PairingPayloadException.BadToken -> {
            ScanProgressUiState.FailureReason.InvalidCode
        }
    }

private fun reasonOf(cause: Throwable) =
    when (cause) {
        is ScanFailure.Network.LocalNetworkDenied -> {
            ScanProgressUiState.FailureReason.LocalNetworkDenied
        }

        is ScanFailure.Network -> {
            ScanProgressUiState.FailureReason.Network
        }

        is ScanFailure.Domain.UnsupportedProtocolVersion -> {
            ScanProgressUiState.FailureReason.VersionMismatch
        }

        is ScanFailure.Domain.ModError -> {
            when (cause.code) {
                ErrorCode.UNAUTHORIZED -> ScanProgressUiState.FailureReason.Unauthorized
                ErrorCode.BUSY -> ScanProgressUiState.FailureReason.Busy
                ErrorCode.SOURCE_FAILURE, ErrorCode.UNSPECIFIED -> ScanProgressUiState.FailureReason.GameError
            }
        }

        // A bug or a malformed Snapshot on either side: nothing the user can fix but retry.
        else -> {
            ScanProgressUiState.FailureReason.GameError
        }
    }
