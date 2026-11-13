/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.sagisolas.recipehost.data.scan.ScanProgress
import org.sagisolas.recipehost.domain.catalog.CatalogItem
import org.sagisolas.recipehost.domain.catalog.ItemSearch

/** What the Items tab's grid shows. */
internal data class ItemGridUiState(
    val searchText: String = "",
    val items: List<CatalogItem> = emptyList(),
    /**
     * How far the running Scan's Icons and layouts are, 0..1: Icons fill the first half, layouts the
     * second. `null` when neither is arriving.
     */
    val loadingProgress: Float? = null,
)

/**
 * The Items tab's grid: the `Item`s matching its search text, which it alone owns. The mod list
 * asks for a mod filter through [modFilters], which becomes the text's `@modid` token.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class ItemGridViewModel(
    itemSearch: ItemSearch,
    modFilters: ModFilterRequests,
    scanProgress: StateFlow<ScanProgress?>,
    private val qr: QrPairing,
) : ViewModel() {
    private val searchText = MutableStateFlow("")

    val state: StateFlow<ItemGridUiState> =
        combine(
            searchText,
            searchText.flatMapLatest { itemSearch.items(it) },
            scanProgress.map(::loadingProgressOf),
        ) { text, items, loadingProgress -> ItemGridUiState(text, items, loadingProgress) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, ItemGridUiState())

    init {
        viewModelScope.launch {
            modFilters.requests.collect { modId -> searchText.update { withModFilter(it, modId) } }
        }
    }

    fun onSearchTextChange(text: String) {
        searchText.value = text
    }

    fun scan() {
        viewModelScope.launch { qr.tryScanQr() }
    }
}

private fun loadingProgressOf(progress: ScanProgress?): Float? =
    when (progress) {
        is ScanProgress.Icons -> HALF * fractionOf(progress.received, progress.total)
        is ScanProgress.Layouts -> HALF + HALF * fractionOf(progress.received, progress.total)
        else -> null
    }

private const val HALF = 0.5f

/** [received] of [total], 0..1; an empty phase counts as done. */
private fun fractionOf(
    received: Int,
    total: Int,
): Float = if (total > 0) (received.toFloat() / total).coerceAtMost(1f) else 1f

/** [text] with its `@` tokens replaced by `@[modId]` in front; the name words stay. */
private fun withModFilter(
    text: String,
    modId: String,
): String =
    (listOf("@$modId") + text.split(' ', '\t').filter { it.isNotEmpty() && !it.startsWith('@') })
        .joinToString(" ")
