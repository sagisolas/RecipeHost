/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.data.scan

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
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
    /** Fills an empty `Catalog` from the `Demo catalog`; `AppGraph` passes `DemoCatalog`. */
    private val loadDemo: suspend () -> Unit,
    /** Empties the `Catalog`; `AppGraph` passes `ClearCatalog`. */
    private val clear: suspend () -> Unit,
) {
    private val _progress = MutableStateFlow<ScanProgress?>(null)

    /** The current or last `Scan`'s progress; `null` before the first one. */
    val progress: StateFlow<ScanProgress?> = _progress.asStateFlow()

    /** Whether a `Scan` is in progress: from [start] until it is done or has failed. */
    val running: Flow<Boolean> = progress.map(::isRunning).distinctUntilChanged()

    private var scan: Job? = null

    /** The last [clearCatalog]'s deletes. */
    private var clearing: Job? = null

    /** Starts a `Scan` from [payload]; returns false and does nothing while another one runs. */
    fun start(payload: PairingPayload): Boolean = run { ingest.ingest(sessions.open(payload)) }

    /**
     * Replaces the `Catalog` with the `Demo catalog`, reported like a `Scan` so the same screens
     * follow it; returns false and does nothing while a Scan runs.
     */
    fun startDemo(): Boolean =
        run {
            flow<ScanProgress> {
                clear()
                loadDemo()
                // Done, like a Scan's end: the Scan progress screen moves on to the Items tab.
                emit(ScanProgress.Done)
            }.catch { emit(ScanProgress.Failed(it)) }
        }

    /** Runs the Scan whose progress [scanProgress] reports, unless one is running already. */
    private fun run(scanProgress: () -> Flow<ScanProgress>): Boolean {
        if (scan?.isActive == true) return false
        // Right away, so Scan progress never shows the last Scan's end for the new one.
        _progress.value = ScanProgress.Connecting
        val clear = clearing
        scan =
            scope.launch {
                // A Scan writing into a half-cleared Catalog would leave it neither empty nor whole.
                clear?.join()
                scanProgress().collect { _progress.value = it }
            }
        return true
    }

    /**
     * Empties the `Catalog`; returns false and does nothing while a `Scan` runs. The deletes run in
     * [scope], so they finish even if the caller is cancelled. Call from the main thread.
     */
    suspend fun clearCatalog(): Boolean {
        if (scan?.isActive == true) return false
        val job = scope.launch { clear() }
        clearing = job
        job.join()
        return true
    }
}

private fun isRunning(progress: ScanProgress?): Boolean =
    progress != null && progress != ScanProgress.Done && progress !is ScanProgress.Failed
