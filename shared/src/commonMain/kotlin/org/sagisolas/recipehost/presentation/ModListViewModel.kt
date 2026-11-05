package org.sagisolas.recipehost.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.sagisolas.recipehost.data.catalog.MINECRAFT
import org.sagisolas.recipehost.domain.catalog.CatalogMod
import org.sagisolas.recipehost.domain.catalog.ModList

/** What the mod list shows: minecraft first, then by name. */
internal data class ModListUiState(val mods: List<ModListEntry> = emptyList())

/** One mod's row: its [releases] newest first, and the one in the `Version selection`. */
internal data class ModListEntry(
    val modId: String,
    val name: String,
    val releases: List<ReleaseOption>,
    val selectedReleaseId: Long,
)

/** One `Mod release` in the dropdown: [version] for Minecraft [mcVersion]. */
internal data class ReleaseOption(
    val id: Long,
    val version: String,
    /** `null` for Minecraft itself, whose version already is the Minecraft version. */
    val mcVersion: String?,
)

/** The mod list, pushed onto the Items tab's stack by the grid's Mods button. */
internal class ModListViewModel(
    private val modList: ModList,
    private val modFilters: ModFilterRequests,
) : ViewModel() {

    val state: StateFlow<ModListUiState> = modList.mods
        .map { mods -> ModListUiState(mods.map(::entryOf)) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ModListUiState())

    /** Saves [releaseId] as [modId]'s `Version selection`; the grid follows it. */
    fun select(modId: String, releaseId: Long) {
        viewModelScope.launch { modList.select(modId, releaseId) }
    }

    /** Asks the grid to filter by [modId]; the screen then goes back to it. */
    fun chooseMod(modId: String) = modFilters.request(modId)
}

private fun entryOf(mod: CatalogMod) = ModListEntry(
    modId = mod.modId,
    name = mod.name,
    releases = mod.releases.map {
        ReleaseOption(it.id, it.version, it.mcVersion.takeUnless { mod.modId == MINECRAFT })
    },
    // ModList only lists mods with a release; with no selection saved yet, the newest one shows.
    selectedReleaseId = mod.selectedReleaseId ?: mod.releases.first().id,
)
