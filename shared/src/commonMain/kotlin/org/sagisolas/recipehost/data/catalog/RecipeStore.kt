/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.data.catalog

import okio.Buffer
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import org.sagisolas.recipehost.protocol.ItemStack
import org.sagisolas.recipehost.protocol.Recipe
import org.sagisolas.recipehost.protocol.Slot

/**
 * Recipes in the `Catalog`'s interned form (ADR 0018). Each distinct Slot is one `slot` row and each
 * item id one `item_name` row; a recipe's body lists its input Slots, its `layout_stacks` Slots and its
 * results by those row ids. Equal recipes therefore have equal bodies.
 */
internal class RecipeStore(
    private val catalog: CatalogDatabase,
) {
    /** A writer for one Scan's commit; call it inside the commit's transaction. */
    fun writer(): Writer = Writer()

    /**
     * [bodies] as wire Recipes carrying only `inputs`, `layout_stacks` and `results`, in order.
     * Reads every Slot and item id they name in a few batched queries.
     */
    fun recipesOf(bodies: List<ByteArray>): List<Recipe> {
        val decoded = bodies.map(::decodeBody)
        val slotIds = decoded.flatMapTo(HashSet()) { it.inputs + it.layoutStacks }
        val slots =
            slotIds
                .chunkedQuery { ids -> catalog.slotQueries.stacksOf(ids).executeAsList() }
                .associate { it.id to decodeStacks(it.stacks) }
        val nameIds =
            slots.values.flatMapTo(HashSet()) { stacks -> stacks.map { it.nameId } } +
                decoded.flatMap { body -> body.results.map { it.nameId } }
        val names =
            nameIds
                .chunkedQuery { ids -> catalog.slotQueries.namesOf(ids).executeAsList() }
                .associate { it.id to it.name }

        fun stackOf(stack: StoredStack) = ItemStack(item_id = names.getValue(stack.nameId), count = stack.count)

        fun slotOf(id: Long) = Slot(stacks = slots.getValue(id).map(::stackOf))
        return decoded.map { body ->
            Recipe(
                inputs = body.inputs.map(::slotOf),
                layout_stacks = body.layoutStacks.map(::slotOf),
                results = body.results.map(::stackOf),
            )
        }
    }

    /**
     * Deletes the Slots, item ids and type keys no recipe names any more. Run once a Scan has ended,
     * like the layout and atlas cleanup: a full scan of each table.
     */
    fun deleteUnreferenced() =
        catalog.transaction {
            with(catalog.slotQueries) {
                deleteUnreferenced()
                deleteUnreferencedItems()
                deleteUnreferencedNames()
            }
            catalog.recipeQueries.deleteUnreferencedTypeKeys()
        }

    /**
     * Writes recipes for one commit. It remembers the row id of each Slot, item id and type it has
     * interned, so a Slot repeated across the Scan's recipes is looked up once.
     */
    inner class Writer internal constructor() {
        private val slotIds = HashMap<ByteString, Long>()
        private val nameIds = HashMap<String, Long>()
        private val typeKeys = HashMap<String, Long>()
        private var nextPk: Long? = null

        /**
         * Inserts [recipe] (its Slots already expanded, ADR 0016) for [instanceId] under [releaseId],
         * with its outputs and Slots indexed. The first recipe's key comes from AUTOINCREMENT and
         * the rest follow it, so no key is ever reused and SQLite isn't asked each time.
         */
        fun insert(
            releaseId: Long,
            instanceId: Long,
            recipe: Recipe,
        ) = with(catalog.recipeQueries) {
            val inputs = recipe.inputs.map(::internSlot)
            val layoutStacks = recipe.layout_stacks.map(::internSlot)
            val results = recipe.results.map { StoredStack(internName(it.item_id), it.count) }
            val body = encodeBody(StoredBody(inputs, layoutStacks, results))
            val typeKey = internType(recipe.type_id)
            val idHash = recipeIdHash(recipe.id)
            val pk =
                nextPk?.also { insertWithPk(it, releaseId, recipe.id, idHash, instanceId, typeKey, body) }
                    ?: run {
                        insert(releaseId, recipe.id, idHash, instanceId, typeKey, body)
                        lastInsertedId().executeAsOne()
                    }
            results.mapTo(HashSet()) { it.nameId }.forEach { insertOutput(it, pk) }
            // A Slot that is an input wins over the same Slot listed only in layout_stacks.
            val inputSet = inputs.toSet()
            (inputSet + layoutStacks).forEach { insertSlot(it, pk, if (it in inputSet) 1L else 0L) }
            nextPk = pk + 1
        }

        private fun internSlot(slot: Slot): Long {
            val stacks = slot.stacks.map { StoredStack(internName(it.item_id), it.count) }
            val encoded = encodeStacks(stacks)
            return slotIds.getOrPut(encoded) {
                with(catalog.slotQueries) {
                    val blob = encoded.toByteArray()
                    insertIfAbsent(blob)
                    idOf(blob).executeAsOne().also { id ->
                        stacks.mapTo(HashSet()) { it.nameId }.forEach { insertItem(it, id) }
                    }
                }
            }
        }

        private fun internName(name: String): Long =
            nameIds.getOrPut(name) {
                with(catalog.slotQueries) {
                    insertName(name)
                    nameIdOf(name).executeAsOne()
                }
            }

        private fun internType(typeId: String): Long =
            typeKeys.getOrPut(typeId) {
                with(catalog.recipeQueries) {
                    insertTypeKey(typeId)
                    typeKeyOf(typeId).executeAsOne()
                }
            }
    }
}

/**
 * The first 64 bits of [recipeId]'s SHA-256: what `recipe.recipe_id_hash` and `bookmark.recipe_id_hash`
 * hold, so a recipe id is indexed as an integer. Lookups compare the id too.
 */
internal fun recipeIdHash(recipeId: String): Long = Buffer().write(recipeId.encodeUtf8().sha256()).readLong()

/** An `ItemStack` as stored: its item id's `item_name` row and its wire count (0 means 1). */
private data class StoredStack(
    val nameId: Long,
    val count: Int,
)

/** A recipe body as stored: its Slots by `slot` row id, and its results. */
private class StoredBody(
    val inputs: List<Long>,
    val layoutStacks: List<Long>,
    val results: List<StoredStack>,
)

/** SQLite before 3.32 (Android before API 31) allows 999 bound variables per statement. */
private const val MAX_IDS_PER_QUERY = 500

private fun <T> Set<Long>.chunkedQuery(query: (Collection<Long>) -> List<T>): List<T> =
    chunked(MAX_IDS_PER_QUERY).flatMap(query)

private fun encodeBody(body: StoredBody): ByteArray =
    Buffer()
        .apply {
            writeIds(body.inputs)
            writeIds(body.layoutStacks)
            writeVarint(body.results.size.toLong())
            body.results.forEach { writeStack(it) }
        }.readByteArray()

private fun decodeBody(body: ByteArray): StoredBody =
    Buffer().write(body).run {
        StoredBody(
            inputs = readIds(),
            layoutStacks = readIds(),
            results = List(readVarint().toInt()) { readStack() },
        )
    }

private fun encodeStacks(stacks: List<StoredStack>): ByteString =
    Buffer().apply { stacks.forEach { writeStack(it) } }.readByteString()

private fun decodeStacks(stacks: ByteArray): List<StoredStack> =
    Buffer().write(stacks).run {
        buildList { while (!exhausted()) add(readStack()) }
    }

private fun Buffer.writeIds(ids: List<Long>) {
    writeVarint(ids.size.toLong())
    ids.forEach { writeVarint(it) }
}

private fun Buffer.readIds(): List<Long> = List(readVarint().toInt()) { readVarint() }

private fun Buffer.writeStack(stack: StoredStack) {
    writeVarint(stack.nameId)
    writeVarint(stack.count.toLong() and 0xFFFF_FFFFL)
}

private fun Buffer.readStack(): StoredStack = StoredStack(readVarint(), readVarint().toInt())

/** An unsigned LEB128 varint, as protobuf writes one. */
private fun Buffer.writeVarint(value: Long) {
    var rest = value
    while (rest and 0x7FL.inv() != 0L) {
        writeByte(((rest and 0x7F) or 0x80).toInt())
        rest = rest ushr 7
    }
    writeByte(rest.toInt())
}

private fun Buffer.readVarint(): Long {
    var result = 0L
    var shift = 0
    while (true) {
        val byte = readByte().toLong()
        result = result or ((byte and 0x7F) shl shift)
        if (byte and 0x80 == 0L) return result
        shift += 7
    }
}
