/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.domain.catalog

/** A square of [size] px at ([x], [y]) in the `Icon atlas` [atlasId]: where one `Icon` is drawn from. */
internal data class IconRegion(
    val atlasId: Long,
    val x: Int,
    val y: Int,
    val size: Int,
)
