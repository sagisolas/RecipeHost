package org.sagisolas.recipehost.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.sagisolas.recipehost.data.scan.ScanProgress
import org.sagisolas.recipehost.domain.catalog.CatalogCounts

/** What the Settings tab's root screen shows. */
internal data class SettingsUiState(
    /** What the `Catalog` holds; null until first counted. */
    val counts: CatalogCounts? = null,
    /** False while a `Scan` runs, since one committing after the clear would fill the `Catalog` again. */
    val clearEnabled: Boolean = true,
    /** The "Clear Catalog" confirmation is open. */
    val confirming: Boolean = false,
    /** The `Catalog` is empty now: the screen leaves for Welcome. */
    val cleared: Boolean = false,
)

/** The Settings tab's root screen: the `Catalog`'s counts, and "Clear Catalog" behind a confirmation. */
internal class SettingsViewModel(
    /** Empties the `Catalog`; false if a `Scan` runs. `AppGraph` passes `ScanController.clearCatalog`. */
    private val clearCatalog: suspend () -> Boolean,
    scanProgress: StateFlow<ScanProgress?>,
    /** `AppGraph` passes `CatalogStats.counts`. */
    counts: Flow<CatalogCounts>,
) : ViewModel() {
    private val step = MutableStateFlow(Step.Idle)

    val state: StateFlow<SettingsUiState> =
        combine(step, scanProgress.map(::isRunning), counts.onStart<CatalogCounts?> { emit(null) }, ::stateOf)
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                stateOf(step.value, isRunning(scanProgress.value), counts = null),
            )

    init {
        // A Scan that starts while the confirmation is open closes it, so it doesn't reopen when the Scan ends.
        viewModelScope.launch {
            scanProgress.map(::isRunning).filter { it }.collect {
                step.update { if (it == Step.Confirming) Step.Idle else it }
            }
        }
    }

    /** Opens the confirmation. */
    fun requestClear() {
        step.value = Step.Confirming
    }

    fun cancelClear() {
        step.value = Step.Idle
    }

    fun confirmClear() {
        step.value = Step.Idle
        viewModelScope.launch {
            if (clearCatalog()) step.value = Step.Cleared
        }
    }
}

private enum class Step { Idle, Confirming, Cleared }

private fun stateOf(
    step: Step,
    scanRunning: Boolean,
    counts: CatalogCounts?,
) = SettingsUiState(
    counts = counts,
    clearEnabled = !scanRunning,
    confirming = step == Step.Confirming && !scanRunning,
    cleared = step == Step.Cleared,
)

/** A Scan runs from its start until it's [ScanProgress.Done] or [ScanProgress.Failed]. */
private fun isRunning(progress: ScanProgress?) =
    progress != null && progress != ScanProgress.Done && progress !is ScanProgress.Failed
