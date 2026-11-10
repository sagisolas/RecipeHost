package org.sagisolas.recipehost.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class WelcomeUiState(
    /** The last "Scan QR" found no scanner on this device: point to the system camera instead. */
    val scannerUnavailable: Boolean = false,
)

/** The Welcome screen, shown while the `Catalog` is empty. */
internal class WelcomeViewModel(
    private val qr: QrPairing,
) : ViewModel() {
    private val _state = MutableStateFlow(WelcomeUiState())
    val state: StateFlow<WelcomeUiState> = _state.asStateFlow()

    fun scanQr() {
        viewModelScope.launch {
            _state.value = WelcomeUiState(scannerUnavailable = !qr.tryScanQr())
        }
    }
}
