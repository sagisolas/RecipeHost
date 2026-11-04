package org.sagisolas.recipehost.data.scan

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.sagisolas.recipehost.data.pairing.PairingPayload
import org.sagisolas.recipehost.data.transport.SessionSource

/**
 * Runs one `Scan` at a time in an app-scoped [scope], so it keeps going while the user browses
 * (the screens only observe [progress]). `AppGraph` owns it and builds [scope] on an I/O
 * dispatcher, since the `Catalog` writes block.
 *
 * Call [start] from the main thread.
 */
internal class ScanController(
    private val scope: CoroutineScope,
    private val sessions: SessionSource,
    private val ingest: ScanIngest,
) {
    private val _progress = MutableStateFlow<ScanProgress?>(null)

    /** The current or last `Scan`'s progress; `null` before the first one. */
    val progress: StateFlow<ScanProgress?> = _progress.asStateFlow()

    private var running: Job? = null

    /** Starts a `Scan` from [payload]; returns false and does nothing while another one runs. */
    fun start(payload: PairingPayload): Boolean {
        if (running?.isActive == true) return false
        running = scope.launch {
            ingest.ingest(sessions.open(payload)).collect { _progress.value = it }
        }
        return true
    }
}
