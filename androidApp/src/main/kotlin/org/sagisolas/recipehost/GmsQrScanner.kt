/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost

import android.content.Context
import android.util.Log
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.suspendCancellableCoroutine
import org.sagisolas.recipehost.platform.QrScanner
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * [QrScanner] on the Google code scanner (`play-services-code-scanner`), which runs its own
 * camera UI and needs no camera permission. Needs Google Play services: without them [scan]
 * throws, and the `recipehost://pair` deep link from any camera app still works.
 *
 * Leaving the scanner doesn't always cancel the task: its close button can fail it with
 * [MlKitException.CODE_SCANNER_CANCELLED], and the back button with [MlKitException.INTERNAL]
 * ("Failed to scan code."), so both failures are cancels too.
 */
internal class GmsQrScanner(
    context: Context,
) : QrScanner {
    private val client =
        GmsBarcodeScanning.getClient(
            context,
            GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build(),
        )

    override suspend fun scan(): String? =
        suspendCancellableCoroutine { continuation ->
            client
                .startScan()
                .addOnSuccessListener { continuation.resume(it.rawValue) }
                .addOnCanceledListener { continuation.resume(null) }
                .addOnFailureListener {
                    if (it.isCancel()) {
                        continuation.resume(null)
                    } else {
                        Log.w(TAG, "Code scanner failed", it)
                        continuation.resumeWithException(it)
                    }
                }
        }
}

private const val TAG = "GmsQrScanner"

private fun Exception.isCancel(): Boolean =
    this is MlKitException &&
        (errorCode == MlKitException.CODE_SCANNER_CANCELLED || errorCode == MlKitException.INTERNAL)
