package org.sagisolas.recipehost.domain.catalog

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.flow.Flow
import org.sagisolas.recipehost.data.catalog.CatalogDatabase
import org.sagisolas.recipehost.domain.CATALOG_REFRESH_PERIOD
import org.sagisolas.recipehost.domain.throttleLatest
import kotlin.coroutines.CoroutineContext

/**
 * The `Item`s of the `Version selection`, searched by text and optionally limited to one mod.
 * Order: minecraft first, then the mods in their latest `Scan`'s order, then display name.
 * The flows re-emit when the `Catalog` changes, at most once per [CATALOG_REFRESH_PERIOD], so
 * arriving `Icon`s show up live without re-running the query for every IconBatch.
 *
 * [context] runs the queries; `AppGraph` passes an I/O dispatcher.
 */
internal class ItemSearch(
    private val catalog: CatalogDatabase,
    private val context: CoroutineContext,
) {
    /**
     * The Items matching [text], limited to [modId] when it is given.
     *
     * An `@token` in [text] keeps the mods whose id starts with it (the last one wins). Every
     * other word must start a word of the display name, in any order and case.
     */
    fun items(text: String, modId: String? = null): Flow<List<CatalogItem>> {
        val search = parseSearchText(text)
        return catalog.itemQueries.selected(modId, search.modPrefix, search.nameMatch, ::catalogItemOf)
            .asFlow()
            .throttleLatest(CATALOG_REFRESH_PERIOD)
            .mapToList(context)
    }
}

/** Search text split into its `@modid` filter and an FTS4 MATCH expression over names. */
private data class SearchText(val modPrefix: String?, val nameMatch: String?)

private fun parseSearchText(text: String): SearchText {
    val (modTokens, nameTokens) = text.split(' ', '\t').filter { it.isNotEmpty() }.partition { it.startsWith('@') }
    val modPrefix = modTokens.lastOrNull()?.removePrefix("@")?.lowercase()?.takeIf { it.isNotEmpty() }
    // FTS4's simple tokenizer splits on anything but letters and digits; doing the same here
    // keeps quotes, '*', '-' and parentheses out of the MATCH syntax. Each word is quoted
    // and lowercased so AND, OR and NOT are never read as operators.
    val words = nameTokens.flatMap(::ftsWords)
    val nameMatch = words.takeIf { it.isNotEmpty() }?.joinToString(" ") { "\"${it.lowercase()}*\"" }
    return SearchText(modPrefix, nameMatch)
}

/** [token]'s runs of letters and digits. */
private fun ftsWords(token: String): List<String> =
    token.map { if (it.isLetterOrDigit()) it else ' ' }.joinToString("").split(' ').filter { it.isNotEmpty() }
