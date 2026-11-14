/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.presentation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.sagisolas.recipehost.data.pairing.PairingPayloadException
import org.sagisolas.recipehost.data.scan.ScanProgress
import org.sagisolas.recipehost.data.transport.ScanFailure
import org.sagisolas.recipehost.platform.LocalNetworkAccess
import org.sagisolas.recipehost.presentation.ScanProgressUiState.Failed
import org.sagisolas.recipehost.presentation.ScanProgressUiState.FailureReason
import org.sagisolas.recipehost.presentation.ScanProgressUiState.Running
import org.sagisolas.recipehost.presentation.ScanProgressUiState.Step
import org.sagisolas.recipehost.protocol.ErrorCode
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ScanProgressViewModelTest {
    private val progress = MutableStateFlow<ScanProgress?>(null)
    private val pairingFailure = MutableStateFlow<PairingPayloadException?>(null)
    private var scansRequested = 0
    private var settingsOpened = 0

    private val localNetwork =
        object : LocalNetworkAccess {
            override suspend fun isDenied(host: String) = false

            override fun openSettings() {
                settingsOpened++
            }
        }

    @BeforeTest
    fun setMain() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun resetMain() = Dispatchers.resetMain()

    private fun viewModel() =
        ScanProgressViewModel(progress, pairingFailure, QrPairing { scansRequested++ }, localNetwork)

    @Test
    fun showsTheStepsOfARunningScan() {
        val viewModel = viewModel()

        progress.value = ScanProgress.Connecting
        assertEquals(Running(Step.Connecting), viewModel.state.value)
        progress.value = ScanProgress.Items(received = 200, total = 1000)
        assertEquals(Running(Step.Items(200, 1000)), viewModel.state.value)
        progress.value = ScanProgress.Recipes(received = 30, total = 400)
        assertEquals(Running(Step.Recipes(30, 400)), viewModel.state.value)
    }

    @Test
    fun movesOnOnceItemsAndRecipesAreCommitted() {
        val viewModel = viewModel()

        progress.value = ScanProgress.Committed
        assertEquals(ScanProgressUiState.Committed, viewModel.state.value)
        // Shown again while Icons arrive (a second pairing link during the same Scan): still done.
        progress.value = ScanProgress.Icons(received = 5, total = 1000)
        assertEquals(ScanProgressUiState.Committed, viewModel.state.value)
        progress.value = ScanProgress.Done
        assertEquals(ScanProgressUiState.Committed, viewModel.state.value)
    }

    @Test
    fun showsWhyTheModRefused() {
        val viewModel = viewModel()

        progress.value = ScanProgress.Failed(ScanFailure.Domain.ModError(ErrorCode.UNAUTHORIZED))
        assertEquals(Failed(FailureReason.Unauthorized), viewModel.state.value)
        progress.value = ScanProgress.Failed(ScanFailure.Domain.ModError(ErrorCode.BUSY))
        assertEquals(Failed(FailureReason.Busy), viewModel.state.value)
        progress.value = ScanProgress.Failed(ScanFailure.Domain.ModError(ErrorCode.SOURCE_FAILURE))
        assertEquals(Failed(FailureReason.GameError), viewModel.state.value)
        progress.value = ScanProgress.Failed(ScanFailure.Domain.UnsupportedProtocolVersion(7))
        assertEquals(Failed(FailureReason.VersionMismatch), viewModel.state.value)
    }

    @Test
    fun aDeniedLocalNetworkPointsToItsSetting() {
        val viewModel = viewModel()

        progress.value = ScanProgress.Failed(ScanFailure.Network.LocalNetworkDenied(listOf("192.168.1.5")))

        assertEquals(Failed(FailureReason.LocalNetworkDenied), viewModel.state.value)
    }

    @Test
    fun aNetworkFailureThatOutlastedTheRetryAsksAboutWifi() {
        val viewModel = viewModel()

        progress.value = ScanProgress.Failed(ScanFailure.Network.Unreachable(listOf("192.168.1.5")))
        assertEquals(Failed(FailureReason.Network), viewModel.state.value)
        progress.value = ScanProgress.Failed(ScanFailure.Network.Dropped())
        assertEquals(Failed(FailureReason.Network), viewModel.state.value)
    }

    @Test
    fun aRejectedPairingCodeWinsOverTheLastScansProgress() {
        progress.value = ScanProgress.Done
        val viewModel = viewModel()

        pairingFailure.value = PairingPayloadException.BadToken()
        assertEquals(Failed(FailureReason.InvalidCode), viewModel.state.value)
        pairingFailure.value = PairingPayloadException.UnsupportedVersion("2")
        assertEquals(Failed(FailureReason.VersionMismatch), viewModel.state.value)
    }

    @Test
    fun withNoScanAtAllItOffersToScanAgain() {
        // e.g. the app was killed during a Scan and restored onto this screen.
        assertEquals(Failed(FailureReason.Interrupted), viewModel().state.value)
    }

    @Test
    fun scanAgainOpensTheScanner() {
        viewModel().scanAgain()
        assertEquals(1, scansRequested)
    }

    @Test
    fun openSettingsOpensThePlatformSettings() {
        viewModel().openSettings()

        assertEquals(1, settingsOpened)
    }
}
