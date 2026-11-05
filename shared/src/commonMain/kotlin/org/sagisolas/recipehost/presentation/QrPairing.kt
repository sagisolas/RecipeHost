package org.sagisolas.recipehost.presentation

import kotlinx.coroutines.CancellationException

/**
 * Opens the in-app QR scanner and pairs from the code it reads. `AppGraph` backs it with
 * [PairingEntry.scanQr]; the screens' ViewModels take it so tests can pass a fake.
 */
internal fun interface QrPairing {
    /** Throws if no scanner is available on this device; a cancelled scan just returns. */
    suspend fun scanQr()
}

/**
 * [QrPairing.scanQr], returning false instead of throwing when this device has no scanner (no
 * Google Play services). The system camera's `recipehost://pair` deep link still works then.
 */
internal suspend fun QrPairing.tryScanQr(): Boolean =
    try {
        scanQr()
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    }
