package org.sagisolas.recipehost.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.sagisolas.recipehost.domain.catalog.CatalogMod
import org.sagisolas.recipehost.domain.catalog.ModList

/**
 * What the mod list shows: minecraft first, then by name. A mod whose selected release adds no
 * Items would filter the grid to nothing, so it is left out.
 */
internal data class ModListUiState(
    val mods: List<ModListEntry> = emptyList(),
)

/** One mod's row: its name, and how many Items its selected `Mod release` adds. */
internal data class ModListEntry(
    val modId: String,
    val name: String,
    val itemCount: Long,
)

/**
 * The mod list, pushed onto the Items tab's stack by the grid's Mods button: picks a mod to
 * filter the grid by. Versions change on the Mod versions screen.
 */
internal class ModListViewModel(
    modList: ModList,
    private val modFilters: ModFilterRequests,
) : ViewModel() {
    val state: StateFlow<ModListUiState> =
        modList.mods
            .map { mods -> ModListUiState(mods.filter { it.selectedRelease.hasItems }.map(::entryOf)) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, ModListUiState())

    /** Asks the grid to filter by [modId]; the screen then goes back to it. */
    fun chooseMod(modId: String) = modFilters.request(modId)
}

private fun entryOf(mod: CatalogMod) = ModListEntry(mod.modId, mod.name, itemCount = mod.selectedRelease.itemCount)
