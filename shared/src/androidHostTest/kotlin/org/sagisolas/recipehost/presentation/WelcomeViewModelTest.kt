/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.presentation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.sagisolas.recipehost.platform.CameraAccessDenied
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class WelcomeViewModelTest {
    @BeforeTest
    fun setMain() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun resetMain() = Dispatchers.resetMain()

    @Test
    fun aDeviceWithoutAScannerPointsToTheCameraApp() {
        val viewModel = WelcomeViewModel(QrPairing { error("no Google Play services") })
        assertNull(viewModel.state.value.lastScan)

        viewModel.scanQr()

        assertEquals(QrScanResult.NoScanner, viewModel.state.value.lastScan)
    }

    @Test
    fun aDeniedCameraPointsToSettings() {
        val viewModel = WelcomeViewModel(QrPairing { throw CameraAccessDenied() })

        viewModel.scanQr()

        assertEquals(QrScanResult.CameraDenied, viewModel.state.value.lastScan)
    }

    @Test
    fun aWorkingScannerShowsNoNotice() {
        val viewModel = WelcomeViewModel(QrPairing {})

        viewModel.scanQr()

        assertEquals(QrScanResult.Scanned, viewModel.state.value.lastScan)
    }
}
