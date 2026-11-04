package org.sagisolas.recipehost.presentation

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import org.sagisolas.recipehost.data.pairing.PairingPayload
import org.sagisolas.recipehost.data.pairing.PairingPayloadException
import org.sagisolas.recipehost.data.scan.ScanController
import org.sagisolas.recipehost.platform.QrScanner

/** Where a `Pairing payload` enters the app: the `recipehost://pair` deep link or the in-app scanner. */
class PairingEntry internal constructor(
    private val scans: ScanController,
    private val qrScanner: QrScanner,
) {
    private val _failure = MutableStateFlow<PairingPayloadException?>(null)

    /**
     * Why the last URI was rejected, or `null` once a valid one arrives. The Scan progress screen
     * shows it as a domain error, next to [ScanController.progress].
     */
    internal val failure: StateFlow<PairingPayloadException?> = _failure.asStateFlow()

    /** Conflated: a link that arrives before the UI collects (a cold-start deep link) still lands. */
    private val _progressRequests = Channel<Unit>(Channel.CONFLATED)

    /**
     * Emits each time a URI arrives, valid or not, asking the UI to show Scan progress. The UI owns
     * navigation state (Navigation 3 saves it), so this asks rather than navigates.
     */
    internal val progressRequests: Flow<Unit> = _progressRequests.receiveAsFlow()

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
        _progressRequests.trySend(Unit)
    }

    /** Opens the in-app scanner and [start]s from the code it reads; a cancelled scan does nothing. */
    internal suspend fun scanQr() {
        qrScanner.scan()?.let { start(it) }
    }
}
