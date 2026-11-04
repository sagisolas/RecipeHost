package org.sagisolas.recipehost

import android.content.Context
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
 */
internal class GmsQrScanner(context: Context) : QrScanner {
    private val client = GmsBarcodeScanning.getClient(
        context,
        GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build(),
    )

    override suspend fun scan(): String? = suspendCancellableCoroutine { continuation ->
        client.startScan()
            .addOnSuccessListener { continuation.resume(it.rawValue) }
            .addOnCanceledListener { continuation.resume(null) }
            .addOnFailureListener { continuation.resumeWithException(it) }
    }
}
