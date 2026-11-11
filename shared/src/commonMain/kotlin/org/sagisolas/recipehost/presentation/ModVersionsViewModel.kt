package org.sagisolas.recipehost.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.sagisolas.recipehost.domain.catalog.CatalogMod
import org.sagisolas.recipehost.domain.catalog.ModList

/** What the Mod versions screen shows: minecraft first, then by name. */
internal data class ModVersionsUiState(
    val mods: List<ModVersionsEntry> = emptyList(),
)

/** One mod's row: its [releases] newest first, and the one in the `Version selection`. */
internal data class ModVersionsEntry(
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

/** The Mod versions screen, pushed onto the Settings tab's stack: picks each mod's `Mod release`. */
internal class ModVersionsViewModel(
    private val modList: ModList,
) : ViewModel() {
    val state: StateFlow<ModVersionsUiState> =
        modList.mods
            .map { mods -> ModVersionsUiState(mods.map(::entryOf)) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, ModVersionsUiState())

    /** Saves [releaseId] as [modId]'s `Version selection` right away; the grid follows it. */
    fun select(
        modId: String,
        releaseId: Long,
    ) {
        viewModelScope.launch { modList.select(modId, releaseId) }
    }
}

private fun entryOf(mod: CatalogMod) =
    ModVersionsEntry(
        modId = mod.modId,
        name = mod.name,
        releases =
            mod.releases.map {
                ReleaseOption(it.id, it.version, mod.mcVersionOf(it))
            },
        selectedReleaseId = mod.selectedRelease.id,
    )
