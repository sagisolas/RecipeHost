/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.presentation

import kotlinx.coroutines.CancellationException
import org.sagisolas.recipehost.platform.CameraAccessDenied

/**
 * Opens the in-app QR scanner and pairs from the code it reads. `AppGraph` backs it with
 * [PairingEntry.scanQr]; the screens' ViewModels take it so tests can pass a fake.
 */
internal fun interface QrPairing {
    /**
     * Throws [CameraAccessDenied] if camera access is denied, or anything else if no scanner is
     * available on this device; a cancelled scan just returns.
     */
    suspend fun scanQr()
}

/** How a [tryScanQr] went. */
internal enum class QrScanResult {
    /** The scanner ran: it read a code (and pairing started) or the user cancelled. */
    Scanned,

    /** No scanner on this device (no Google Play services, no camera): use the system camera. */
    NoScanner,

    /** The user denied camera access: allow it in Settings, or use the system camera. */
    CameraDenied,
}

/**
 * [QrPairing.scanQr], returning why it couldn't scan instead of throwing. The system camera's
 * `recipehost://pair` deep link still works in either case.
 */
internal suspend fun QrPairing.tryScanQr(): QrScanResult =
    try {
        scanQr()
        QrScanResult.Scanned
    } catch (e: CancellationException) {
        throw e
    } catch (e: CameraAccessDenied) {
        QrScanResult.CameraDenied
    } catch (e: Exception) {
        QrScanResult.NoScanner
    }
