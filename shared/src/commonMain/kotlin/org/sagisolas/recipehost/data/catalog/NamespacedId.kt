/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.data.catalog

/**
 * A namespaced id's namespace, which names the mod that owns it (ADR 0011); Minecraft reads a
 * bare id as `minecraft:`.
 */
internal fun namespaceOf(id: String): String = id.substringBefore(':', missingDelimiterValue = MINECRAFT)

/** Minecraft's own namespace and mod id. */
internal const val MINECRAFT = "minecraft"
