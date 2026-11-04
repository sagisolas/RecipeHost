package org.sagisolas.recipehost.data.catalog

/**
 * A namespaced id's namespace, which names the mod that owns it (ADR 0011); Minecraft reads a
 * bare id as `minecraft:`.
 */
internal fun namespaceOf(id: String): String = id.substringBefore(':', missingDelimiterValue = "minecraft")
