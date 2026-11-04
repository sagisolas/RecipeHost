package org.sagisolas.recipehost.platform

/** The platform's QR code scanner, supplied by the Android or iOS wrapper. */
fun interface QrScanner {
    /**
     * Lets the user scan one QR code and returns its raw text, or `null` if they cancel. Throws if
     * no scanner is available on this device.
     */
    suspend fun scan(): String?
}
