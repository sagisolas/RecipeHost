package org.sagisolas.recipehost.domain

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import org.sagisolas.recipehost.data.catalog.CatalogDatabase
import org.sagisolas.recipehost.data.scan.ScanIngest
import org.sagisolas.recipehost.data.scan.ScanProgress
import org.sagisolas.recipehost.protocol.Envelope
import org.sagisolas.recipehost.protocol.Handshake
import org.sagisolas.recipehost.protocol.IconBatch
import org.sagisolas.recipehost.protocol.Item
import org.sagisolas.recipehost.protocol.ItemBatch
import org.sagisolas.recipehost.protocol.ItemStack
import org.sagisolas.recipehost.protocol.ModInfo
import org.sagisolas.recipehost.protocol.Recipe
import org.sagisolas.recipehost.protocol.RecipeBatch
import org.sagisolas.recipehost.protocol.Slot
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant

/** An IconBatch carrying one Icon atlas of 16 px cells, 8 per row, holding [itemIds] in cell order. */
internal fun iconAtlas(png: ByteString, vararg itemIds: String) =
    Envelope(icon_batch = IconBatch(png = png, cell_size = 16, columns = 8, item_ids = itemIds.toList()))

/** An in-memory `Catalog` seeded through [ScanIngest], for the domain query tests. */
internal class CatalogFixture {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { CatalogDatabase.Schema.create(it) }
    val database = CatalogDatabase(driver)

    private var nowMillis = 1_000L
    private val clock = object : Clock {
        override fun now() = Instant.fromEpochMilliseconds(nowMillis)
    }
    private val ingest = ScanIngest(database, clock)

    fun close() = driver.close()

    /**
     * Runs a complete Scan of [instance] on Minecraft [mcVersion] carrying [mods]; each Scan is
     * one second later than the previous one. [icons] go out as one Icon atlas, in cell order.
     * Asserts it finished.
     */
    suspend fun scan(
        instance: String,
        vararg mods: ModInfo,
        mcVersion: String = "1.21.1",
        items: List<Pair<String, String>> = emptyList(),
        recipes: List<Recipe> = emptyList(),
        icons: List<String> = emptyList(),
    ) {
        nowMillis += 1_000
        val envelopes = buildList {
            add(
                Envelope(
                    handshake = Handshake(
                        protocol_version = 1,
                        minecraft_version = mcVersion,
                        instance_name = instance,
                        mods = mods.toList(),
                        total_items = items.size,
                        total_recipes = recipes.size,
                    ),
                ),
            )
            if (items.isNotEmpty()) {
                add(Envelope(item_batch = ItemBatch(items = items.map { (id, name) -> Item(id = id, display_name = name) })))
            }
            if (recipes.isNotEmpty()) add(Envelope(recipe_batch = RecipeBatch(recipes = recipes)))
            if (icons.isNotEmpty()) {
                add(iconAtlas(ATLAS_PNG, *icons.toTypedArray()))
            }
        }
        val progress = ingest.ingest(envelopes.asFlow()).toList()
        assertEquals(ScanProgress.Done, progress.last(), "Scan failed: $progress")
    }

    fun releaseId(mod: ModInfo, mcVersion: String = "1.21.1"): Long =
        database.modReleaseQueries.idOf(mod.id, mod.version, mcVersion).executeAsOne()

    companion object {
        /** The PNG of the one Icon atlas a [scan] with icons sends; never decoded. */
        val ATLAS_PNG = "atlas-png".encodeUtf8()

        val minecraft = ModInfo(id = "minecraft", name = "Minecraft", version = "1.21.1")
        val create6 = ModInfo(id = "create", name = "Create", version = "6.0.1")
        val create5 = ModInfo(id = "create", name = "Create", version = "5.1.0")
        val createAddition = ModInfo(id = "createaddition", name = "Create Crafts & Additions", version = "1.3.0")
        val jei = ModInfo(id = "jei", name = "Just Enough Items", version = "19.0.0")

        fun shaped(id: String, result: String, vararg inputs: String, type: String = "minecraft:crafting_shaped") = Recipe(
            id = id,
            type_id = type,
            results = listOf(ItemStack(item_id = result, count = 1)),
            inputs = inputs.map { Slot(stacks = listOf(ItemStack(item_id = it, count = 1))) },
        )
    }
}
