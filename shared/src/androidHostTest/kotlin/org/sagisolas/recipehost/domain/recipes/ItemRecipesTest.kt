package org.sagisolas.recipehost.domain.recipes

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.encodeUtf8
import org.sagisolas.recipehost.domain.CatalogFixture
import org.sagisolas.recipehost.domain.CatalogFixture.Companion.create5
import org.sagisolas.recipehost.domain.CatalogFixture.Companion.create6
import org.sagisolas.recipehost.domain.CatalogFixture.Companion.minecraft
import org.sagisolas.recipehost.domain.CatalogFixture.Companion.shaped
import org.sagisolas.recipehost.domain.catalog.ModList
import org.sagisolas.recipehost.protocol.ItemStack
import org.sagisolas.recipehost.protocol.RecipeLayout
import org.sagisolas.recipehost.protocol.RecipeSlot
import org.sagisolas.recipehost.protocol.RecipeType
import org.sagisolas.recipehost.protocol.Slot
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ItemRecipesTest {
    private val catalog = CatalogFixture()
    private val itemRecipes = ItemRecipes(catalog.database, EmptyCoroutineContext)

    private fun recipesFor(itemId: String) = itemRecipes(itemId, RecipeDirection.Recipes)

    private fun usesOf(itemId: String) = itemRecipes(itemId, RecipeDirection.Uses)

    private val modList = ModList(catalog.database, EmptyCoroutineContext)

    @AfterTest
    fun close() = catalog.close()

    private val pressing = "create:pressing"
    private val crafting = "minecraft:crafting_shaped"

    @Test
    fun recipesWhoseResultIsTheItemAreGroupedByType() =
        runTest {
            catalog.scan(
                "ATM9",
                minecraft,
                create6,
                recipes =
                    listOf(
                        shaped(
                            "minecraft:iron_ingot_from_nuggets",
                            result = "minecraft:iron_ingot",
                            "minecraft:iron_nugget",
                        ),
                        shaped(
                            "minecraft:iron_ingot_from_block",
                            result = "minecraft:iron_ingot",
                            "minecraft:iron_block",
                        ),
                        shaped(
                            "create:pressing/iron_ingot",
                            result = "create:iron_sheet",
                            "minecraft:iron_ingot",
                            type = pressing,
                        ),
                        shaped(
                            "create:crushing/raw_iron",
                            result = "minecraft:iron_ingot",
                            "minecraft:raw_iron",
                            type = "create:crushing",
                        ),
                    ),
            )

            val groups = recipesFor("minecraft:iron_ingot").first()

            assertEquals(
                listOf(
                    crafting to listOf("minecraft:iron_ingot_from_block", "minecraft:iron_ingot_from_nuggets"),
                    "create:crushing" to listOf("create:crushing/raw_iron"),
                ),
                groups.map { group -> group.typeId to group.recipes.map { it.recipeId } },
            )
        }

    @Test
    fun minecraftTypesComeFirstThenTheOthersByTypeId() =
        runTest {
            catalog.scan(
                "ATM9",
                minecraft,
                create6,
                recipes =
                    listOf(
                        shaped(
                            "create:mixing/iron",
                            result = "minecraft:iron_ingot",
                            "minecraft:raw_iron",
                            type = "create:mixing",
                        ),
                        shaped(
                            "minecraft:iron_ingot_from_nuggets",
                            result = "minecraft:iron_ingot",
                            "minecraft:iron_nugget",
                        ),
                        shaped(
                            "create:crushing/raw_iron",
                            result = "minecraft:iron_ingot",
                            "minecraft:raw_iron",
                            type = "create:crushing",
                        ),
                        shaped(
                            "minecraft:iron_ingot_from_blasting",
                            result = "minecraft:iron_ingot",
                            "minecraft:raw_iron",
                            type = "minecraft:blasting",
                        ),
                    ),
            )

            assertEquals(
                listOf("minecraft:blasting", crafting, "create:crushing", "create:mixing"),
                recipesFor("minecraft:iron_ingot").first().map { it.typeId },
            )
        }

    @Test
    fun onlyRecipesOwnedByTheSelectedReleasesCount() =
        runTest {
            catalog.scan(
                "Old",
                minecraft,
                create5,
                recipes =
                    listOf(
                        shaped(
                            "create:pressing/old",
                            result = "create:iron_sheet",
                            "minecraft:iron_ingot",
                            type = pressing,
                        ),
                    ),
            )
            catalog.scan(
                "New",
                minecraft,
                create6,
                recipes =
                    listOf(
                        shaped(
                            "create:pressing/new",
                            result = "create:iron_sheet",
                            "minecraft:iron_ingot",
                            type = pressing,
                        ),
                    ),
            )

            assertEquals(listOf("create:pressing/new"), recipeIdsFor("create:iron_sheet"))

            modList.select("create", catalog.releaseId(create5))

            assertEquals(listOf("create:pressing/old"), recipeIdsFor("create:iron_sheet"))
        }

    @Test
    fun identicalBodiesFromTwoInstancesAreOneVariantListingBothNamesNewestFirst() =
        runTest {
            val stick = shaped("minecraft:stick", result = "minecraft:stick", "minecraft:oak_planks")
            catalog.scan("Vanilla", minecraft, recipes = listOf(stick))
            catalog.scan("ATM9", minecraft, recipes = listOf(stick))

            val recipe =
                recipesFor("minecraft:stick")
                    .first()
                    .single()
                    .recipes
                    .single()

            assertEquals(listOf(listOf("ATM9", "Vanilla")), recipe.variants.map { it.instanceNames })
            assertEquals(generic("minecraft:oak_planks"), recipe.defaultVariant.display)
        }

    @Test
    fun differentBodiesAreTwoVariantsAndTheNewestInstanceIsTheDefault() =
        runTest {
            val vanillaStick = shaped("minecraft:stick", result = "minecraft:stick", "minecraft:oak_planks")
            val packStick = shaped("minecraft:stick", result = "minecraft:stick", "minecraft:bamboo")
            catalog.scan("ATM9", minecraft, recipes = listOf(packStick))
            catalog.scan("Vanilla", minecraft, recipes = listOf(vanillaStick))
            catalog.scan("Skyblock", minecraft, recipes = listOf(packStick))

            val recipe =
                recipesFor("minecraft:stick")
                    .first()
                    .single()
                    .recipes
                    .single()

            assertEquals(
                listOf(
                    generic("minecraft:bamboo") to listOf("Skyblock", "ATM9"),
                    generic("minecraft:oak_planks") to listOf("Vanilla"),
                ),
                recipe.variants.map { it.display to it.instanceNames },
            )
        }

    @Test
    fun groupsCarryTheirTypesNameAndAnUnnamedTypeHasNone() =
        runTest {
            catalog.scan(
                "ATM9",
                minecraft,
                create6,
                recipes =
                    listOf(
                        shaped("minecraft:iron_sheet", result = "create:iron_sheet", "minecraft:iron_ingot"),
                        shaped(
                            "create:pressing/iron_sheet",
                            result = "create:iron_sheet",
                            "minecraft:iron_ingot",
                            type = pressing,
                        ),
                    ),
                types = listOf(RecipeType(id = crafting, name = "Crafting")),
            )

            assertEquals(
                listOf(crafting to "Crafting", pressing to null),
                recipesFor("create:iron_sheet").first().map { it.typeId to it.name },
            )
        }

    @Test
    fun aLaterScanRenamesATypeOfTheSameRelease() =
        runTest {
            val recipes = listOf(shaped("minecraft:stick", result = "minecraft:stick", "minecraft:oak_planks"))
            catalog.scan(
                "ATM9",
                minecraft,
                recipes = recipes,
                types = listOf(RecipeType(id = crafting, name = "Crafting")),
            )
            catalog.scan(
                "Vanilla",
                minecraft,
                recipes = recipes,
                types = listOf(RecipeType(id = crafting, name = "Werkbank")),
            )

            assertEquals(listOf("Werkbank"), recipesFor("minecraft:stick").first().map { it.name })
        }

    @Test
    fun aTypeIsNamedOnlyByTheSelectedReleaseOfItsNamespace() =
        runTest {
            val press =
                shaped(
                    "create:pressing/iron_sheet",
                    result = "create:iron_sheet",
                    "minecraft:iron_ingot",
                    type = pressing,
                )
            catalog.scan(
                "Old",
                minecraft,
                create5,
                recipes = listOf(press),
                types = listOf(RecipeType(id = pressing, name = "Pressing")),
            )
            catalog.scan("New", minecraft, create6, recipes = listOf(press))

            assertEquals(listOf(null), recipesFor("create:iron_sheet").first().map { it.name })
        }

    @Test
    fun anItemWithNoRecipesHasNoGroups() =
        runTest {
            catalog.scan(
                "ATM9",
                minecraft,
                recipes = listOf(shaped("minecraft:stick", result = "minecraft:stick", "minecraft:oak_planks")),
            )

            assertEquals(emptyList(), recipesFor("minecraft:oak_planks").first())
        }

    private suspend fun recipeIdsFor(itemId: String) =
        recipesFor(itemId).first().flatMap { group -> group.recipes.map { it.recipeId } }

    private val stick = shaped("minecraft:stick", result = "minecraft:stick", "minecraft:oak_planks")
    private val craftingSlots =
        listOf(
            RecipeSlot(x = 29, y = 16, width = 18, height = 18, role = RecipeSlot.Role.INPUT),
            RecipeSlot(x = 123, y = 34, width = 18, height = 18, role = RecipeSlot.Role.OUTPUT),
        )

    @Test
    fun aRecipeWithAStoredLayoutShowsItsLayoutStacksInItsSlots() =
        runTest {
            catalog.scan(
                "ATM9",
                minecraft,
                recipes =
                    listOf(
                        stick.copy(
                            layout_stacks =
                                listOf(
                                    Slot(stacks = listOf(ItemStack("minecraft:oak_planks", 1))),
                                    Slot(stacks = listOf(ItemStack("minecraft:stick", 0))),
                                ),
                        ),
                    ),
                layouts =
                    listOf(
                        RecipeLayout(
                            key = "minecraft:crafting",
                            png = "crafting-layout".encodeUtf8(),
                            width = 128,
                            height = 70,
                            slots = craftingSlots,
                            recipe_ids = listOf("minecraft:stick"),
                        ),
                    ),
            )

            val variant =
                recipesFor("minecraft:stick")
                    .first()
                    .single()
                    .recipes
                    .single()
                    .defaultVariant

            assertEquals(
                RecipeDisplay.Captured(
                    key = "minecraft:crafting",
                    width = 128,
                    height = 70,
                    slots =
                        listOf(
                            DisplaySlot(
                                x = 29,
                                y = 16,
                                width = 18,
                                height = 18,
                                stacks = listOf(DisplayStack("minecraft:oak_planks", 1)),
                            ),
                            // A wire count of 0 means 1.
                            DisplaySlot(
                                x = 123,
                                y = 34,
                                width = 18,
                                height = 18,
                                stacks = listOf(DisplayStack("minecraft:stick", 1)),
                            ),
                        ),
                ),
                variant.display,
            )
        }

    @Test
    fun aLayoutSlotPastTheRecipesLayoutStacksStaysEmpty() =
        runTest {
            catalog.scan(
                "ATM9",
                minecraft,
                recipes =
                    listOf(
                        stick.copy(layout_stacks = listOf(Slot(stacks = listOf(ItemStack("minecraft:oak_planks", 1))))),
                    ),
                layouts =
                    listOf(
                        RecipeLayout(
                            key = "minecraft:crafting",
                            png = "crafting-layout".encodeUtf8(),
                            width = 128,
                            height = 70,
                            slots = craftingSlots,
                            recipe_ids = listOf("minecraft:stick"),
                        ),
                    ),
            )

            val display =
                recipesFor("minecraft:stick")
                    .first()
                    .single()
                    .recipes
                    .single()
                    .defaultVariant.display

            assertEquals(
                listOf(listOf("minecraft:oak_planks"), emptyList()),
                (display as RecipeDisplay.Captured).slots.map { slot -> slot.stacks.map { it.itemId } },
            )
        }

    @Test
    fun aRecipeWhoseLayoutImageIsMissingIsShownGenerically() =
        runTest {
            // Linked by a recipes-only entry to a key whose image never arrived.
            catalog.scan(
                "ATM9",
                minecraft,
                recipes = listOf(stick),
                layouts = listOf(RecipeLayout(key = "minecraft:crafting", recipe_ids = listOf("minecraft:stick"))),
            )

            val variant =
                recipesFor("minecraft:stick")
                    .first()
                    .single()
                    .recipes
                    .single()
                    .defaultVariant

            assertEquals(generic("minecraft:oak_planks"), variant.display)
        }

    /** How the fixture's one-input shaped stick recipe is drawn without a layout. */
    private fun generic(input: String) =
        RecipeDisplay.Generic(
            inputs = listOf(listOf(DisplayStack(input, 1))),
            results = listOf(DisplayStack("minecraft:stick", 1)),
        )

    @Test
    fun recipesTakingTheItemInAnySlotAlternativeAreGroupedByType() =
        runTest {
            val chest =
                shaped("minecraft:chest", result = "minecraft:chest").copy(
                    inputs =
                        listOf(
                            Slot(
                                stacks =
                                    listOf(
                                        ItemStack(item_id = "minecraft:oak_planks"),
                                        ItemStack(item_id = "minecraft:spruce_planks"),
                                    ),
                            ),
                        ),
                )
            catalog.scan(
                "ATM9",
                minecraft,
                create6,
                recipes =
                    listOf(
                        chest,
                        shaped("minecraft:stick", result = "minecraft:stick", "minecraft:spruce_planks"),
                        shaped(
                            "create:cutting/spruce_planks",
                            result = "minecraft:stick",
                            "minecraft:spruce_planks",
                            type = "create:cutting",
                        ),
                        shaped("minecraft:spruce_planks", result = "minecraft:spruce_planks", "minecraft:spruce_log"),
                    ),
            )

            val groups = usesOf("minecraft:spruce_planks").first()

            assertEquals(
                listOf(
                    "minecraft:crafting_shaped" to listOf("minecraft:chest", "minecraft:stick"),
                    "create:cutting" to listOf("create:cutting/spruce_planks"),
                ),
                groups.map { group -> group.typeId to group.recipes.map { it.recipeId } },
            )
        }

    @Test
    fun usesFromAReleaseThatIsNotSelectedAreLeftOut() =
        runTest {
            catalog.scan(
                "Old",
                minecraft,
                create5,
                recipes = listOf(shaped("create:pressing/old", result = "create:iron_sheet", "minecraft:iron_ingot")),
            )
            catalog.scan("New", minecraft, create6)

            assertEquals(emptyList(), usesOf("minecraft:iron_ingot").first())
        }
}
