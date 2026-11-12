package org.sagisolas.recipehost.data.scan

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import okio.IOException
import org.sagisolas.recipehost.data.catalog.CatalogDatabase
import org.sagisolas.recipehost.domain.iconAtlas
import org.sagisolas.recipehost.protocol.Envelope
import org.sagisolas.recipehost.protocol.Handshake
import org.sagisolas.recipehost.protocol.IconBatch
import org.sagisolas.recipehost.protocol.Item
import org.sagisolas.recipehost.protocol.ItemBatch
import org.sagisolas.recipehost.protocol.ItemStack
import org.sagisolas.recipehost.protocol.LayoutBatch
import org.sagisolas.recipehost.protocol.ModInfo
import org.sagisolas.recipehost.protocol.Recipe
import org.sagisolas.recipehost.protocol.RecipeBatch
import org.sagisolas.recipehost.protocol.RecipeLayout
import org.sagisolas.recipehost.protocol.RecipeSlot
import org.sagisolas.recipehost.protocol.Slot
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class ScanIngestTest {
    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { CatalogDatabase.Schema.create(it) }
    private val database = CatalogDatabase(driver)
    private var nowMillis = 1_000L
    private val clock =
        object : Clock {
            override fun now() = Instant.fromEpochMilliseconds(nowMillis)
        }
    private val ingest = ScanIngest(database, clock)

    @AfterTest
    fun closeDriver() = driver.close()

    private val minecraft = ModInfo(id = "minecraft", name = "Minecraft", version = "1.21.1")
    private val create6 = ModInfo(id = "create", name = "Create", version = "6.0.1")

    @Test
    fun aFullScanCommitsItemsRecipesAndSelectionAndReportsProgress() =
        runTest {
            val progress =
                ingest
                    .ingest(
                        flowOf(
                            handshake("ATM9", minecraft, create6, items = 3, recipes = 2),
                            items("minecraft:stone" to "Stone", "minecraft:stick" to "Stick"),
                            items("create:cogwheel" to "Cogwheel"),
                            recipes(shaped("minecraft:stick", result = "minecraft:stick", "minecraft:oak_planks")),
                            recipes(
                                shaped(
                                    "create:cogwheel",
                                    result = "create:cogwheel",
                                    "minecraft:stick",
                                    "minecraft:oak_planks",
                                ),
                            ),
                        ),
                    ).toList()

            assertEquals(
                listOf(
                    ScanProgress.Connecting,
                    ScanProgress.Items(received = 2, total = 3),
                    ScanProgress.Items(received = 3, total = 3),
                    ScanProgress.Recipes(received = 1, total = 2),
                    ScanProgress.Recipes(received = 2, total = 2),
                    ScanProgress.Committed,
                    ScanProgress.Done,
                ),
                progress,
            )
            assertEquals(listOf("Stone" to 0L, "Stick" to 1L), itemsOf(minecraft))
            assertEquals(listOf("Cogwheel" to 2L), itemsOf(create6))
            assertEquals(listOf("create:cogwheel", "minecraft:stick"), recipeIdsOf("ATM9"))
            assertEquals(mapOf("create" to releaseId(create6), "minecraft" to releaseId(minecraft)), selections())
        }

    @Test
    fun aDropBeforeTheLastRecipeBatchWritesNothing() =
        runTest {
            val progress =
                ingest
                    .ingest(
                        flowOf(
                            handshake("ATM9", minecraft, items = 1, recipes = 2),
                            items("minecraft:stick" to "Stick"),
                            recipes(shaped("minecraft:stick", result = "minecraft:stick", "minecraft:oak_planks")),
                        ).thenDrop(),
                    ).toList()

            assertIs<IOException>(assertIs<ScanProgress.Failed>(progress.last()).cause)
            assertFalse(ScanProgress.Committed in progress)
            assertEquals(emptyList(), database.modReleaseQueries.all().executeAsList())
            assertEquals(emptyList(), recipeIdsOf("ATM9"))
            assertEquals(emptyMap(), selections())
        }

    @Test
    fun aLaterScanOfAnInstanceReplacesItsRecipesForEachReleaseIncludingRemovedOnes() =
        runTest {
            scan("ATM9", minecraft, stickRecipe, torchRecipe)

            scan("ATM9", minecraft, stickRecipe)

            assertEquals(listOf("minecraft:stick"), recipeIdsOf("ATM9"))
        }

    @Test
    fun anotherInstancesRecipesAreKept() =
        runTest {
            scan("ATM9", minecraft, stickRecipe, torchRecipe)

            scan("Vanilla", minecraft, stickRecipe)

            assertEquals(listOf("minecraft:stick", "minecraft:torch"), recipeIdsOf("ATM9"))
            assertEquals(listOf("minecraft:stick"), recipeIdsOf("Vanilla"))
        }

    @Test
    fun identicalBodiesAcrossInstancesShareABodyHashAndDifferentOnesDont() =
        runTest {
            val overriddenTorch =
                shaped("minecraft:torch", result = "minecraft:torch", "minecraft:charcoal", "minecraft:stick")
            scan("ATM9", minecraft, stickRecipe, torchRecipe)
            scan("Vanilla", minecraft, stickRecipe, overriddenTorch)

            val atm9 = hashesOf("ATM9")
            val vanilla = hashesOf("Vanilla")

            assertEquals(atm9.getValue("minecraft:stick"), vanilla.getValue("minecraft:stick"))
            assertNotEquals(atm9.getValue("minecraft:torch"), vanilla.getValue("minecraft:torch"))
            assertEquals(
                "minecraft:charcoal",
                Recipe.ADAPTER
                    .decode(bodiesOf("Vanilla").getValue("minecraft:torch"))
                    .inputs
                    .first()
                    .stacks
                    .single()
                    .item_id,
            )
        }

    @Test
    fun recipeOutputsAndEveryInputAlternativeAreIndexed() =
        runTest {
            val planks =
                Recipe(
                    id = "minecraft:sticks_any",
                    type_id = "minecraft:crafting_shaped",
                    results = listOf(ItemStack(item_id = "minecraft:stick", count = 4)),
                    inputs =
                        listOf(
                            Slot(
                                stacks =
                                    listOf(
                                        ItemStack(item_id = "minecraft:oak_planks"),
                                        ItemStack(item_id = "minecraft:birch_planks"),
                                    ),
                            ),
                            Slot(stacks = listOf(ItemStack(item_id = "minecraft:oak_planks"))),
                        ),
                )
            scan("ATM9", minecraft, planks)

            val pk =
                database.recipeQueries
                    .ofInstance("ATM9")
                    .executeAsOne()
                    .id

            assertEquals(listOf("minecraft:stick"), database.recipeQueries.outputsOf(pk).executeAsList())
            assertEquals(
                listOf("minecraft:birch_planks", "minecraft:oak_planks"),
                database.recipeQueries.inputsOf(pk).executeAsList(),
            )
        }

    @Test
    fun aRescanKeysItsRecipesAfterEveryKeyUsedBeforeNeverReusingOne() =
        runTest {
            scan("ATM9", minecraft, stickRecipe, torchRecipe)
            val before =
                database.recipeQueries
                    .ofInstance("ATM9")
                    .executeAsList()
                    .map { it.id }

            scan("ATM9", minecraft, stickRecipe, torchRecipe)
            val after =
                database.recipeQueries
                    .ofInstance("ATM9")
                    .executeAsList()
                    .map { it.id }

            assertEquals(2, after.distinct().size)
            assertTrue(after.min() > before.max(), "keys $after reuse one of $before")
        }

    @Test
    fun aSharedSlotIsStoredAsTheSlotItStandsFor() =
        runTest {
            val planks =
                Slot(
                    stacks =
                        listOf(
                            ItemStack(item_id = "minecraft:oak_planks"),
                            ItemStack(item_id = "minecraft:birch_planks"),
                        ),
                )
            val shared = Slot(shared = 1)
            val chest =
                Recipe(
                    id = "minecraft:chest",
                    type_id = "minecraft:crafting_shaped",
                    results = listOf(ItemStack(item_id = "minecraft:chest", count = 1)),
                    inputs = listOf(shared, shared),
                    layout_stacks = listOf(shared),
                )
            ingest
                .ingest(
                    flowOf(
                        handshake("ATM9", minecraft, items = 0, recipes = 1),
                        Envelope(recipe_batch = RecipeBatch(recipes = listOf(chest), shared_slots = listOf(planks))),
                    ),
                ).toList()
                .also { assertEquals(ScanProgress.Done, it.last()) }

            val stored = Recipe.ADAPTER.decode(bodiesOf("ATM9").getValue("minecraft:chest"))
            assertEquals(listOf(planks, planks), stored.inputs)
            assertEquals(listOf(planks), stored.layout_stacks)
            val pk =
                database.recipeQueries
                    .ofInstance("ATM9")
                    .executeAsOne()
                    .id
            assertEquals(
                listOf("minecraft:birch_planks", "minecraft:oak_planks"),
                database.recipeQueries.inputsOf(pk).executeAsList(),
            )
        }

    @Test
    fun aSharedSlotPastTheBatchsTableFailsTheScanAndWritesNothing() =
        runTest {
            val progress =
                ingest
                    .ingest(
                        flowOf(
                            handshake("ATM9", minecraft, items = 0, recipes = 1),
                            Envelope(
                                recipe_batch =
                                    RecipeBatch(
                                        recipes = listOf(stickRecipe.copy(inputs = listOf(Slot(shared = 2)))),
                                    ),
                            ),
                        ),
                    ).toList()

            assertIs<InvalidSharedSlotException>(assertIs<ScanProgress.Failed>(progress.last()).cause)
            assertEquals(emptyList(), recipeIdsOf("ATM9"))
        }

    @Test
    fun aLaterScanOverwritesDisplayNamesAndOrdinals() =
        runTest {
            scan("ATM9", minecraft, items = listOf("minecraft:stone" to "Stone", "minecraft:stick" to "Stick"))

            scan("Pack FR", minecraft, items = listOf("minecraft:stick" to "Bâton", "minecraft:stone" to "Roche"))

            assertEquals(listOf("Bâton" to 0L, "Roche" to 1L), itemsOf(minecraft))
        }

    @Test
    fun versionSelectionFollowsTheScanAndModsMissingFromItKeepTheirChoice() =
        runTest {
            val create5 = ModInfo(id = "create", name = "Create", version = "5.1.0")
            val jei = ModInfo(id = "jei", name = "JEI", version = "19.0.0")
            scan("Old pack", minecraft, create5, jei)

            scan("New pack", minecraft, create6)

            assertEquals(
                mapOf(
                    "create" to releaseId(create6),
                    "jei" to releaseId(jei),
                    "minecraft" to releaseId(minecraft),
                ),
                selections(),
            )
        }

    @Test
    fun iconsAreFilledInAfterTheCommit() =
        runTest {
            val progress =
                ingest
                    .ingest(
                        flowOf(
                            handshake("ATM9", minecraft, create6, items = 2, recipes = 0),
                            items("minecraft:stick" to "Stick", "create:cogwheel" to "Cogwheel"),
                            iconAtlas(stickAtlas, "minecraft:stick"),
                            iconAtlas(cogAtlas, "create:cogwheel"),
                        ),
                    ).toList()

            assertEquals(
                listOf(
                    ScanProgress.Connecting,
                    ScanProgress.Items(received = 2, total = 2),
                    ScanProgress.Committed,
                    ScanProgress.Icons(received = 1, total = 2),
                    ScanProgress.Icons(received = 2, total = 2),
                    ScanProgress.Done,
                ),
                progress,
            )
            assertEquals(stickAtlas to 0L, iconOf(minecraft, "minecraft:stick"))
            assertEquals(cogAtlas to 0L, iconOf(create6, "create:cogwheel"))
        }

    @Test
    fun layoutProgressCountsTheRecipesLinkedSoFar() =
        runTest {
            val progress =
                ingest
                    .ingest(
                        flowOf(
                            handshake("ATM9", minecraft, items = 0, recipes = 2),
                            recipes(stickRecipe, torchRecipe),
                            layouts(craftingLayout(recipeIds = listOf("minecraft:stick"))),
                            layouts(recipesOnly("minecraft:crafting", "minecraft:torch")),
                        ),
                    ).toList()

            assertEquals(
                listOf(
                    ScanProgress.Connecting,
                    ScanProgress.Recipes(received = 2, total = 2),
                    ScanProgress.Committed,
                    ScanProgress.Layouts(received = 1, total = 2),
                    ScanProgress.Layouts(received = 2, total = 2),
                    ScanProgress.Done,
                ),
                progress,
            )
        }

    @Test
    fun aLayoutArrivingAfterTheCommitIsStoredAndLinkedToItsRecipes() =
        runTest {
            val progress =
                ingest
                    .ingest(
                        flowOf(
                            handshake("ATM9", minecraft, items = 1, recipes = 2),
                            items("minecraft:stick" to "Stick"),
                            recipes(stickRecipe, torchRecipe),
                            iconAtlas(stickAtlas, "minecraft:stick"),
                            layouts(craftingLayout(recipeIds = listOf("minecraft:stick"))),
                        ),
                    ).toList()

            assertEquals(ScanProgress.Done, progress.last())
            assertEquals(
                mapOf("minecraft:stick" to "minecraft:crafting", "minecraft:torch" to null),
                layoutKeysOf("ATM9"),
            )
            val stored = database.recipeLayoutQueries.byKey("minecraft:crafting").executeAsOne()
            assertEquals(craftingPng, stored.png.toByteString())
            assertEquals(116L to 54L, stored.width to stored.height)
            assertEquals(craftingSlots, RecipeLayout.ADAPTER.decode(stored.slots).slots)
        }

    @Test
    fun aRecipesOnlyEntryLinksAKeySentEarlierWithoutReplacingItsImage() =
        runTest {
            ingest
                .ingest(
                    flowOf(
                        handshake("ATM9", minecraft, items = 0, recipes = 2),
                        recipes(stickRecipe, torchRecipe),
                        layouts(craftingLayout(recipeIds = listOf("minecraft:stick"))),
                        layouts(recipesOnly("minecraft:crafting", "minecraft:torch")),
                    ),
                ).toList()

            assertEquals(
                mapOf("minecraft:stick" to "minecraft:crafting", "minecraft:torch" to "minecraft:crafting"),
                layoutKeysOf("ATM9"),
            )
            assertEquals(
                craftingPng,
                database.recipeLayoutQueries
                    .byKey("minecraft:crafting")
                    .executeAsOne()
                    .png
                    .toByteString(),
            )
        }

    @Test
    fun aDropDuringLayoutsKeepsTheLayoutsThatArrived() =
        runTest {
            val progress =
                ingest
                    .ingest(
                        flowOf(
                            handshake("ATM9", minecraft, items = 0, recipes = 2),
                            recipes(stickRecipe, torchRecipe),
                            layouts(craftingLayout(recipeIds = listOf("minecraft:stick"))),
                        ).thenDrop(),
                    ).toList()

            assertIs<ScanProgress.Failed>(progress.last())
            assertEquals(
                mapOf("minecraft:stick" to "minecraft:crafting", "minecraft:torch" to null),
                layoutKeysOf("ATM9"),
            )
            assertEquals(1L, database.recipeLayoutQueries.count().executeAsOne())
        }

    @Test
    fun layoutsNoRecipePointsAtAfterAScanAreDeleted() =
        runTest {
            scanLayouts(
                "ATM9",
                craftingLayout(recipeIds = listOf("minecraft:stick")),
                smeltingLayout(recipeIds = listOf("minecraft:torch")),
            )

            // The rescan's recipes start without layouts; only crafting is linked again.
            scanLayouts("ATM9", craftingLayout(recipeIds = listOf("minecraft:stick")))

            assertEquals(
                mapOf("minecraft:stick" to "minecraft:crafting", "minecraft:torch" to null),
                layoutKeysOf("ATM9"),
            )
            assertNull(database.recipeLayoutQueries.byKey(smeltingKey).executeAsOneOrNull())
            assertEquals(1L, database.recipeLayoutQueries.count().executeAsOne())
        }

    @Test
    fun aDroppedScanAlsoDeletesTheLayoutsItsCommitLeftUnreferenced() =
        runTest {
            scanLayouts("ATM9", smeltingLayout(recipeIds = listOf("minecraft:torch")))

            ingest
                .ingest(
                    flowOf(
                        handshake("ATM9", minecraft, items = 0, recipes = 2),
                        recipes(stickRecipe, torchRecipe),
                    ).thenDrop(),
                ).toList()

            assertEquals(0L, database.recipeLayoutQueries.count().executeAsOne())
        }

    @Test
    fun anotherInstancesLayoutsAndLinksAreUntouched() =
        runTest {
            scanLayouts("Vanilla", smeltingLayout(recipeIds = listOf("minecraft:torch")))

            scanLayouts("ATM9", craftingLayout(recipeIds = listOf("minecraft:stick", "minecraft:torch")))

            assertEquals(mapOf("minecraft:stick" to null, "minecraft:torch" to smeltingKey), layoutKeysOf("Vanilla"))
            assertEquals(
                mapOf("minecraft:stick" to "minecraft:crafting", "minecraft:torch" to "minecraft:crafting"),
                layoutKeysOf("ATM9"),
            )
            assertEquals(2L, database.recipeLayoutQueries.count().executeAsOne())
        }

    @Test
    fun aLaterScansImageReplacesTheOneStoredUnderTheSameKey() =
        runTest {
            // Another resource pack draws the same vanilla layout differently: the latest Scan wins.
            val otherPack = "crafting-layout-other-pack".encodeUtf8()
            scanLayouts("ATM9", craftingLayout(recipeIds = listOf("minecraft:stick")))

            scanLayouts("Vanilla", craftingLayout(recipeIds = listOf("minecraft:stick"), png = otherPack))

            assertEquals(
                otherPack,
                database.recipeLayoutQueries
                    .byKey("minecraft:crafting")
                    .executeAsOne()
                    .png
                    .toByteString(),
            )
            assertEquals("minecraft:crafting", layoutKeysOf("ATM9")["minecraft:stick"])
        }

    @Test
    fun anAtlasFilesEachItemUnderItsCellAndItsNamespacesRelease() =
        runTest {
            val progress =
                ingest
                    .ingest(
                        flowOf(
                            handshake("ATM9", minecraft, create6, items = 2, recipes = 0),
                            items("minecraft:stick" to "Stick", "create:cogwheel" to "Cogwheel"),
                            iconAtlas(sharedAtlas, "minecraft:stick", "create:cogwheel"),
                        ),
                    ).toList()

            assertEquals(ScanProgress.Icons(received = 2, total = 2), progress[progress.size - 2])
            assertEquals(sharedAtlas to 0L, iconOf(minecraft, "minecraft:stick"))
            assertEquals(sharedAtlas to 1L, iconOf(create6, "create:cogwheel"))
            assertEquals(1L, database.iconQueries.atlasCount().executeAsOne())
        }

    @Test
    fun anAtlasIsDeletedOnceALaterScanReplacesAllItsCells() =
        runTest {
            suspend fun scanIcons(vararg batches: Envelope) =
                ingest
                    .ingest(
                        flowOf(
                            handshake("ATM9", minecraft, items = 2, recipes = 0),
                            items("minecraft:stick" to "Stick", "minecraft:torch" to "Torch"),
                            *batches,
                        ),
                    ).toList()
                    .also { assertEquals(ScanProgress.Done, it.last()) }

            val firstStick = "first-stick-atlas".encodeUtf8()
            val torch = "torch-atlas".encodeUtf8()
            val secondStick = "second-stick-atlas".encodeUtf8()

            scanIcons(iconAtlas(firstStick, "minecraft:stick"), iconAtlas(torch, "minecraft:torch"))
            scanIcons(iconAtlas(secondStick, "minecraft:stick"))

            // The first stick atlas lost its only cell; the torch atlas is still in use.
            assertEquals(2L, database.iconQueries.atlasCount().executeAsOne())
            assertEquals(secondStick to 0L, iconOf(minecraft, "minecraft:stick"))
            assertEquals(torch to 0L, iconOf(minecraft, "minecraft:torch"))
        }

    @Test
    fun anAtlasWithoutColumnsFailsTheScanAndWritesNoIcons() =
        runTest {
            val progress =
                ingest
                    .ingest(
                        flowOf(
                            handshake("ATM9", minecraft, items = 1, recipes = 0),
                            items("minecraft:stick" to "Stick"),
                            Envelope(
                                icon_batch =
                                    IconBatch(
                                        png = stickAtlas,
                                        cell_size = 16,
                                        columns = 0,
                                        item_ids = listOf("minecraft:stick"),
                                    ),
                            ),
                        ),
                    ).toList()

            assertIs<InvalidIconAtlasException>((progress.last() as ScanProgress.Failed).cause)
            assertNull(iconOf(minecraft, "minecraft:stick"))
            assertEquals(0L, database.iconQueries.atlasCount().executeAsOne())
        }

    @Test
    fun aDropDuringIconsKeepsTheCommittedItemsRecipesAndIcons() =
        runTest {
            val progress =
                ingest
                    .ingest(
                        flowOf(
                            handshake("ATM9", minecraft, items = 2, recipes = 1),
                            items("minecraft:stick" to "Stick", "minecraft:torch" to "Torch"),
                            recipes(stickRecipe),
                            iconAtlas(stickAtlas, "minecraft:stick"),
                        ).thenDrop(),
                    ).toList()

            assertIs<ScanProgress.Failed>(progress.last())
            assertEquals(listOf("Stick" to 0L, "Torch" to 1L), itemsOf(minecraft))
            assertEquals(listOf("minecraft:stick"), recipeIdsOf("ATM9"))
            assertEquals(stickAtlas to 0L, iconOf(minecraft, "minecraft:stick"))
            assertNull(iconOf(minecraft, "minecraft:torch"))
        }

    @Test
    fun moreRecipesThanTheHandshakeAnnouncedFailsTheScanAndWritesNothing() =
        runTest {
            val progress =
                ingest
                    .ingest(
                        flowOf(
                            handshake("ATM9", minecraft, items = 0, recipes = 1),
                            recipes(stickRecipe, torchRecipe),
                        ),
                    ).toList()

            assertIs<RecipeCountExceededException>(assertIs<ScanProgress.Failed>(progress.last()).cause)
            assertEquals(emptyList(), recipeIdsOf("ATM9"))
        }

    @Test
    fun displayNamesAreSearchableAndFollowALaterScan() =
        runTest {
            scan("ATM9", minecraft, items = listOf("minecraft:stone" to "Smooth Stone"))
            scan("ATM9", minecraft, items = listOf("minecraft:stone" to "Polished Rock"))

            assertEquals(emptyList(), searchNames("smooth"))
            assertEquals(listOf("Polished Rock"), searchNames("rock"))
        }

    @Test
    fun aRecipeWhoseNamespaceHasNoModInfoFailsTheScanAndWritesNothing() =
        runTest {
            val progress =
                ingest
                    .ingest(
                        flowOf(
                            handshake("ATM9", minecraft, items = 1, recipes = 2),
                            items("minecraft:stick" to "Stick"),
                            recipes(
                                stickRecipe,
                                shaped("mypack:cheap_torch", result = "minecraft:torch", "minecraft:stick"),
                            ),
                        ),
                    ).toList()

            val failure = assertIs<UnknownNamespaceException>(assertIs<ScanProgress.Failed>(progress.last()).cause)
            assertEquals("mypack", failure.namespace)
            assertEquals("mypack:cheap_torch", failure.id)
            assertEquals(emptyList(), database.modReleaseQueries.all().executeAsList())
            assertEquals(emptyList(), recipeIdsOf("ATM9"))
        }

    @Test
    fun aRetriedSessionsNewHandshakeDiscardsWhatTheFailedAttemptStaged() =
        runTest {
            ingest
                .ingest(
                    flowOf(
                        handshake("ATM9", minecraft, items = 2, recipes = 1),
                        items("minecraft:stone" to "Stone"),
                        handshake("ATM9", minecraft, items = 2, recipes = 1),
                        items("minecraft:stone" to "Stone", "minecraft:stick" to "Stick"),
                        recipes(stickRecipe),
                    ),
                ).toList()

            assertEquals(listOf("Stone" to 0L, "Stick" to 1L), itemsOf(minecraft))
        }

    // --- scripted envelopes ---

    private fun handshake(
        instance: String,
        vararg mods: ModInfo,
        items: Int,
        recipes: Int,
    ) = Envelope(
        handshake =
            Handshake(
                protocol_version = 1,
                minecraft_version = "1.21.1",
                instance_name = instance,
                mods = mods.toList(),
                total_items = items,
                total_recipes = recipes,
            ),
    )

    private fun items(vararg items: Pair<String, String>) =
        Envelope(item_batch = ItemBatch(items = items.map { (id, name) -> Item(id = id, display_name = name) }))

    private fun recipes(vararg recipes: Recipe) = Envelope(recipe_batch = RecipeBatch(recipes = recipes.toList()))

    private fun layouts(vararg layouts: RecipeLayout) = Envelope(layout_batch = LayoutBatch(layouts = layouts.toList()))

    private val craftingPng = "crafting-layout".encodeUtf8()
    private val craftingSlots =
        listOf(
            RecipeSlot(x = 0, y = 0, width = 18, height = 18, role = RecipeSlot.Role.INPUT),
            RecipeSlot(x = 98, y = 18, width = 18, height = 18, role = RecipeSlot.Role.OUTPUT),
        )

    private fun craftingLayout(
        recipeIds: List<String>,
        png: ByteString = craftingPng,
    ) = RecipeLayout(
        key = "minecraft:crafting",
        png = png,
        width = 116,
        height = 54,
        slots = craftingSlots,
        recipe_ids = recipeIds,
    )

    private val smeltingKey = "minecraft:smelting/200/0.1"

    private fun smeltingLayout(recipeIds: List<String>) =
        RecipeLayout(
            key = smeltingKey,
            png = "smelting-layout".encodeUtf8(),
            width = 98,
            height = 70,
            slots = craftingSlots,
            recipe_ids = recipeIds,
        )

    /** A complete Scan of [instance] with the stick and torch recipes, then [layouts] in one batch. */
    private suspend fun scanLayouts(
        instance: String,
        vararg layouts: RecipeLayout,
    ) = ingest
        .ingest(
            flowOf(
                handshake(instance, minecraft, items = 0, recipes = 2),
                recipes(stickRecipe, torchRecipe),
                layouts(*layouts),
            ),
        ).toList()
        .also { assertEquals(ScanProgress.Done, it.last()) }

    /** A key-only entry: more recipes for a key whose image was sent before. */
    private fun recipesOnly(
        key: String,
        vararg recipeIds: String,
    ) = RecipeLayout(key = key, recipe_ids = recipeIds.toList())

    private val stickAtlas = "stick-atlas".encodeUtf8()
    private val cogAtlas = "cog-atlas".encodeUtf8()
    private val sharedAtlas = "stick-and-cog-atlas".encodeUtf8()

    private fun shaped(
        id: String,
        result: String,
        vararg inputs: String,
    ) = Recipe(
        id = id,
        type_id = "minecraft:crafting_shaped",
        results = listOf(ItemStack(item_id = result, count = 1)),
        inputs = inputs.map { Slot(stacks = listOf(ItemStack(item_id = it, count = 1))) },
    )

    private val stickRecipe = shaped("minecraft:stick", result = "minecraft:stick", "minecraft:oak_planks")
    private val torchRecipe = shaped("minecraft:torch", result = "minecraft:torch", "minecraft:coal", "minecraft:stick")

    /** Runs a complete Scan of [instance] carrying [mods], and asserts it finished. */
    private suspend fun scan(
        instance: String,
        vararg mods: ModInfo,
        recipes: List<Recipe> = emptyList(),
        items: List<Pair<String, String>> = emptyList(),
    ): List<ScanProgress> {
        val envelopes =
            buildList {
                add(handshake(instance, *mods, items = items.size, recipes = recipes.size))
                if (items.isNotEmpty()) add(items(*items.toTypedArray()))
                if (recipes.isNotEmpty()) add(recipes(*recipes.toTypedArray()))
            }
        return ingest.ingest(envelopes.asFlow()).toList().also { assertEquals(ScanProgress.Done, it.last()) }
    }

    private suspend fun scan(
        instance: String,
        mod: ModInfo,
        vararg recipes: Recipe,
    ) = scan(instance, mod, recipes = recipes.toList())

    private fun Flow<Envelope>.thenDrop(): Flow<Envelope> =
        flow {
            collect { emit(it) }
            throw IOException("connection reset")
        }

    // --- reading the Catalog back ---

    private fun releaseId(
        mod: ModInfo,
        mcVersion: String = "1.21.1",
    ) = database.modReleaseQueries.idOf(mod.id, mod.version, mcVersion).executeAsOne()

    private fun itemsOf(mod: ModInfo) =
        database.itemQueries
            .ofRelease(releaseId(mod))
            .executeAsList()
            .map { it.display_name to it.ordinal }

    private fun layoutKeysOf(instance: String) =
        database.recipeQueries
            .ofInstance(instance)
            .executeAsList()
            .associate { it.recipe_id to it.layout_key }

    private fun recipeIdsOf(instance: String) =
        database.recipeQueries
            .ofInstance(instance)
            .executeAsList()
            .map { it.recipe_id }

    private fun hashesOf(instance: String) =
        database.recipeQueries
            .ofInstance(instance)
            .executeAsList()
            .associate { it.recipe_id to it.body_hash.toByteString() }

    private fun bodiesOf(instance: String) =
        database.recipeQueries
            .ofInstance(instance)
            .executeAsList()
            .associate { it.recipe_id to it.body }

    /** The atlas PNG and cell of [itemId]'s Icon in [mod]'s release. */
    private fun iconOf(
        mod: ModInfo,
        itemId: String,
    ) = database.iconQueries.iconOf(releaseId(mod), itemId).executeAsOneOrNull()?.let { icon ->
        database.iconQueries
            .atlasPng(icon.atlas_id)
            .executeAsOne()
            .toByteString() to icon.cell
    }

    private fun searchNames(query: String) =
        database.itemQueries
            .searchNames(query)
            .executeAsList()
            .map { it.display_name }

    private fun selections() =
        database.versionSelectionQueries
            .all()
            .executeAsList()
            .associate { it.mod_id to it.release_id }
}
