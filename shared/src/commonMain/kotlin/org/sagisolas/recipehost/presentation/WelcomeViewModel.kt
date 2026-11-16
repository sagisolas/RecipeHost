/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class WelcomeUiState(
    /** How the last "Scan QR" went, or `null` before the first; a failure shows a notice. */
    val lastScan: QrScanResult? = null,
)

/**
 * The Welcome screen, shown while the `Catalog` is empty. [startDemo] loads the `Demo catalog`, for
 * store reviewers who have no game to pair with.
 */
internal class WelcomeViewModel(
    private val qr: QrPairing,
    private val startDemo: () -> Unit,
) : ViewModel() {
    private val _state = MutableStateFlow(WelcomeUiState())
    val state: StateFlow<WelcomeUiState> = _state.asStateFlow()

    fun scanQr() {
        viewModelScope.launch {
            _state.value = WelcomeUiState(lastScan = qr.tryScanQr())
        }
    }

    /** Fills the Catalog from the Demo catalog; the UI shows Scan progress, as for a pairing link. */
    fun loadDemo() = startDemo()
}
