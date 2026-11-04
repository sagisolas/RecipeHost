package org.sagisolas.recipehost.presentation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.sagisolas.recipehost.data.pairing.PairingPayload
import org.sagisolas.recipehost.data.pairing.PairingPayloadException
import org.sagisolas.recipehost.data.scan.ScanController
import org.sagisolas.recipehost.platform.QrScanner
import org.sagisolas.recipehost.presentation.navigation.Navigator

/** Where a `Pairing payload` enters the app: the `recipehost://pair` deep link or the in-app scanner. */
class PairingEntry internal constructor(
    private val scans: ScanController,
    private val navigator: Navigator,
    private val qrScanner: QrScanner,
) {
    private val _failure = MutableStateFlow<PairingPayloadException?>(null)

    /**
     * Why the last URI was rejected, or `null` once a valid one arrives. The Scan progress screen
     * shows it as a domain error, next to [ScanController.progress].
     */
    internal val failure: StateFlow<PairingPayloadException?> = _failure.asStateFlow()

    /**
     * Starts a `Scan` from a `recipehost://pair` URI and shows its progress; while a Scan runs,
     * this only shows the running one. Call from the main thread.
     */
    fun start(uri: String) {
        PairingPayload.parse(uri)
            .onSuccess {
                _failure.value = null
                scans.start(it)
            }
            .onFailure { _failure.value = it as PairingPayloadException }
        navigator.showScanProgress()
    }

    /** Opens the in-app scanner and [start]s from the code it reads; a cancelled scan does nothing. */
    internal suspend fun scanQr() {
        qrScanner.scan()?.let { start(it) }
    }
}
