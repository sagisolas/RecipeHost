package org.sagisolas.recipehost.data.scan

/** Where a `Scan` is, as reported by [ScanIngest.ingest]. Totals come from the `Handshake`. */
internal sealed interface ScanProgress {
    /** Waiting for the `Handshake`. */
    data object Connecting : ScanProgress

    data class Items(
        val received: Int,
        val total: Int,
    ) : ScanProgress

    data class Recipes(
        val received: Int,
        val total: Int,
    ) : ScanProgress

    /** Items and Recipes are in the `Catalog`; Icons may still be arriving. */
    data object Committed : ScanProgress

    data class Icons(
        val received: Int,
        val total: Int,
    ) : ScanProgress

    /**
     * `Recipe layout`s arriving after the commit: [received] counts the recipes linked to a layout
     * so far, of the Handshake's [total] recipes. Recipes without a layout leave it short of [total].
     */
    data class Layouts(
        val received: Int,
        val total: Int,
    ) : ScanProgress

    data object Done : ScanProgress

    /** The `Scan` stopped. Nothing is written unless [Committed] came first, and then that stays. */
    data class Failed(
        val cause: Throwable,
    ) : ScanProgress
}
