package org.sagisolas.recipehost.domain.catalog

/** A square of [size] px at ([x], [y]) in the `Icon atlas` [atlasId]: where one `Icon` is drawn from. */
internal data class IconRegion(val atlasId: Long, val x: Int, val y: Int, val size: Int)
