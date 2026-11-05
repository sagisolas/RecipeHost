package org.sagisolas.recipehost.domain.catalog

/**
 * An `Item` of one `Mod release` in the `Catalog`. [icon] is where its Icon sits in an
 * `Icon atlas`, `null` until its `Icon` arrives.
 */
internal data class CatalogItem(
    val releaseId: Long,
    val itemId: String,
    val displayName: String,
    val icon: IconRegion?,
)

/** Builds a [CatalogItem] from an item query row; the icon columns are all `null` or all set. */
internal fun catalogItemOf(
    releaseId: Long,
    itemId: String,
    displayName: String,
    atlasId: Long?,
    cell: Long?,
    columns: Long?,
    cellSize: Long?,
): CatalogItem {
    val icon = if (atlasId != null && cell != null && columns != null && cellSize != null) {
        iconRegionOf(atlasId, cell.toInt(), columns.toInt(), cellSize.toInt())
    } else {
        null
    }
    return CatalogItem(releaseId, itemId, displayName, icon)
}

/** Cell [cell] of a row-major atlas [columns] cells wide. */
private fun iconRegionOf(atlasId: Long, cell: Int, columns: Int, cellSize: Int) =
    IconRegion(atlasId, x = (cell % columns) * cellSize, y = (cell / columns) * cellSize, size = cellSize)
