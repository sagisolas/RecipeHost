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
     * first, then by name. The mod list filters the item grid and the Mod versions screen picks
     * which release it shows, so a mod with no Items has no place in either.
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
        releases = map { ModRelease(it.id, it.version, it.mc_version, hasItems = it.has_items) },
        selectedReleaseId = first().selected_release_id,
    )

/** A mod in the `Catalog`: its [releases] newest first, and the one in the `Version selection`. */
internal data class CatalogMod(
    val modId: String,
    val name: String,
    val releases: List<ModRelease>,
    val selectedReleaseId: Long?,
) {
    /** The release browsed: the saved one, or the newest while none is saved. */
    val selectedRelease: ModRelease get() = releases.firstOrNull { it.id == selectedReleaseId } ?: releases.first()

    /** [release]'s Minecraft version, or null for minecraft itself, whose version already is one. */
    fun mcVersionOf(release: ModRelease): String? = release.mcVersion.takeUnless { modId == MINECRAFT }
}

/** One `Mod release`: [version] of the mod for Minecraft [mcVersion]; [hasItems] if it adds any `Item`. */
internal data class ModRelease(
    val id: Long,
    val version: String,
    val mcVersion: String,
    val hasItems: Boolean,
)
