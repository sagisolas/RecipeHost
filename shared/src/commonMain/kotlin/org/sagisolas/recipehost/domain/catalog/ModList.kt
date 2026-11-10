package org.sagisolas.recipehost.domain.catalog

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.sagisolas.recipehost.data.catalog.CatalogDatabase
import org.sagisolas.recipehost.data.catalog.MINECRAFT
import org.sagisolas.recipehost.data.catalog.WithSelection
import kotlin.coroutines.CoroutineContext

/**
 * The mods in the `Catalog` with their `Mod release`s, and the `Version selection` between them.
 *
 * [context] runs the queries and writes; `AppGraph` passes an I/O dispatcher.
 */
internal class ModList(
    private val catalog: CatalogDatabase,
    private val context: CoroutineContext,
) {
    /**
     * Every mod that adds Items in at least one of its releases, with all its releases: minecraft
     * first, then by name. The list filters the item grid, so a mod with no Items has no place in it.
     */
    val mods: Flow<List<CatalogMod>> =
        catalog.modReleaseQueries.withSelection().asFlow().mapToList(context).map { rows ->
            rows
                .groupBy { it.mod_id }
                .map { (modId, releases) -> releases.toMod(modId) }
                .sortedWith(
                    compareBy<CatalogMod> { it.modId != MINECRAFT }.thenBy { it.name.lowercase() }.thenBy { it.modId },
                )
        }

    /** Makes [releaseId] the selected release of [modId]; it must be one of that mod's releases. */
    suspend fun select(
        modId: String,
        releaseId: Long,
    ) = withContext(context) {
        val owner = catalog.modReleaseQueries.modOf(releaseId).executeAsOneOrNull()
        require(owner == modId) { "release $releaseId is not a release of $modId" }
        catalog.versionSelectionQueries.choose(modId, releaseId)
    }
}

private fun List<WithSelection>.toMod(modId: String) =
    CatalogMod(
        modId = modId,
        // The rows come newest first, so a renamed mod shows its newest name.
        name = first().name,
        releases = map { ModRelease(it.id, it.version, it.mc_version) },
        selectedReleaseId = first().selected_release_id,
    )

/** A mod in the `Catalog`: its [releases] newest first, and the one in the `Version selection`. */
internal data class CatalogMod(
    val modId: String,
    val name: String,
    val releases: List<ModRelease>,
    val selectedReleaseId: Long?,
)

/** One `Mod release`: [version] of the mod for Minecraft [mcVersion]. */
internal data class ModRelease(
    val id: Long,
    val version: String,
    val mcVersion: String,
)
