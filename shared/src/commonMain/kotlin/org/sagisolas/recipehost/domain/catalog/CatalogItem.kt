package org.sagisolas.recipehost.domain.catalog

import okio.ByteString

/**
 * An `Item` of one `Mod release` in the `Catalog`. [icon] is the 16×16 PNG, `null` until its
 * `Icon` arrives.
 */
internal data class CatalogItem(
    val releaseId: Long,
    val itemId: String,
    val displayName: String,
    val icon: ByteString?,
)
