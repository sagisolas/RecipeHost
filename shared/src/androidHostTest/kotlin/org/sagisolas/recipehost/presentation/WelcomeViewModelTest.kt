package org.sagisolas.recipehost.presentation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class WelcomeViewModelTest {
    @BeforeTest
    fun setMain() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun resetMain() = Dispatchers.resetMain()

    @Test
    fun aDeviceWithoutAScannerPointsToTheCameraApp() {
        val viewModel = WelcomeViewModel(QrPairing { error("no Google Play services") })
        assertFalse(viewModel.state.value.scannerUnavailable)

        viewModel.scanQr()

        assertTrue(viewModel.state.value.scannerUnavailable)
    }

    @Test
    fun aWorkingScannerShowsNoNotice() {
        val viewModel = WelcomeViewModel(QrPairing {})

        viewModel.scanQr()

        assertFalse(viewModel.state.value.scannerUnavailable)
    }
}
