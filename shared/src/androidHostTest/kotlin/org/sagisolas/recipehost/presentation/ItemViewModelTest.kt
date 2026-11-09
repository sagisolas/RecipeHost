package org.sagisolas.recipehost.presentation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.sagisolas.recipehost.domain.catalog.CatalogItem
import org.sagisolas.recipehost.domain.catalog.StackResolution
import org.sagisolas.recipehost.domain.recipes.CatalogRecipe
import org.sagisolas.recipehost.domain.recipes.DisplaySlot
import org.sagisolas.recipehost.domain.recipes.DisplayStack
import org.sagisolas.recipehost.domain.recipes.RecipeDirection
import org.sagisolas.recipehost.domain.recipes.RecipeDisplay
import org.sagisolas.recipehost.domain.recipes.RecipeGroup
import org.sagisolas.recipehost.domain.recipes.RecipeVariant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class ItemViewModelTest {

    private val recipes = MutableStateFlow<List<RecipeGroup>>(emptyList())
    private val uses = MutableStateFlow<List<RecipeGroup>>(emptyList())

    /** Every id resolves to an Item named after its path, except those in [unknown]. */
    private val unknown = mutableSetOf<String>()

    @BeforeTest
    fun setMain() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(itemId: String = "minecraft:stick"): ItemViewModel {
        val viewModel = ItemViewModel(
            itemId = itemId,
            itemRecipes = { _, direction -> if (direction == RecipeDirection.Recipes) recipes else uses },
            resolveStack = { id -> MutableStateFlow(resolutionOf(id)) },
        )
        // The state is shared while the screen collects it, as the UI does.
        backgroundScope.launch { viewModel.state.collect {} }
        runCurrent()
        return viewModel
    }

    private fun resolutionOf(id: String): StackResolution =
        if (id in unknown) StackResolution.Unresolved(id) else StackResolution.Resolved(itemOf(id))

    @Test
    fun recipesShowOneSectionPerRecipeType() = runTest {
        recipes.value = listOf(
            group("minecraft:crafting_shaped", recipe("minecraft:stick"), recipe("minecraft:stick_from_bamboo")),
            group("minecraft:stonecutting", recipe("minecraft:stick_from_stone")),
        )
        val viewModel = viewModel()

        val sections = viewModel.state.value.sections
        assertEquals(listOf("minecraft:crafting_shaped", "minecraft:stonecutting"), sections.map { it.typeId })
        assertEquals(listOf(2, 1), sections.map { it.count })
        assertEquals(listOf(0, 0), sections.map { it.index })
    }

    @Test
    fun thePagerMovesWithinItsSectionOnly() = runTest {
        recipes.value = listOf(
            group("minecraft:crafting_shaped", recipe("minecraft:stick"), recipe("minecraft:stick_from_bamboo")),
            group("minecraft:stonecutting", recipe("minecraft:stick_from_stone"), recipe("minecraft:stick_from_andesite")),
        )
        val viewModel = viewModel()

        viewModel.next("minecraft:crafting_shaped")
        runCurrent()
        assertEquals(listOf("minecraft:stick_from_bamboo", "minecraft:stick_from_stone"), viewModel.state.value.sections.map { it.recipeId })

        viewModel.next("minecraft:crafting_shaped")
        runCurrent()
        assertEquals(1, viewModel.state.value.sections.first().index, "the pager stops at the last recipe")

        viewModel.previous("minecraft:crafting_shaped")
        runCurrent()
        assertEquals("minecraft:stick", viewModel.state.value.sections.first().recipeId)
    }

    @Test
    fun onlyARecipeWithSeveralBodiesOffersItsInstances() = runTest {
        recipes.value = listOf(
            group(
                "minecraft:crafting_shaped",
                recipe(
                    "minecraft:stick",
                    listOf("Pack") to display(inputs = listOf(listOf("minecraft:bamboo"))),
                    listOf("Vanilla", "Other") to display(inputs = listOf(listOf("minecraft:oak_planks"))),
                ),
            ),
            group("minecraft:stonecutting", recipe("minecraft:stick_from_stone")),
        )
        val viewModel = viewModel()

        val (crafting, stonecutting) = viewModel.state.value.sections
        assertEquals(listOf(listOf("Pack"), listOf("Vanilla", "Other")), crafting.variants)
        assertEquals(0, crafting.selectedVariant, "the newest variant shows first")
        assertEquals(listOf("minecraft:bamboo"), crafting.inputIds)
        assertEquals(emptyList(), stonecutting.variants)
    }

    @Test
    fun switchingTheVariantShowsItsBody() = runTest {
        recipes.value = listOf(
            group(
                "minecraft:crafting_shaped",
                recipe(
                    "minecraft:stick",
                    listOf("Pack") to display(inputs = listOf(listOf("minecraft:bamboo"))),
                    listOf("Vanilla") to display(inputs = listOf(listOf("minecraft:oak_planks"))),
                ),
            ),
        )
        val viewModel = viewModel()

        viewModel.selectVariant("minecraft:crafting_shaped", 1)
        runCurrent()

        val section = viewModel.state.value.sections.single()
        assertEquals(1, section.selectedVariant)
        assertEquals(listOf("minecraft:oak_planks"), section.inputIds)
    }

    @Test
    fun alternativesCycleEverySecondOnOneClock() = runTest {
        val planks = listOf("minecraft:oak_planks", "minecraft:spruce_planks", "minecraft:birch_planks")
        val logs = listOf("minecraft:oak_log", "minecraft:spruce_log")
        recipes.value = listOf(group("minecraft:crafting_shaped", recipe("minecraft:stick", listOf("ATM") to display(inputs = listOf(planks, logs)))))
        val viewModel = viewModel()
        assertEquals(listOf("minecraft:oak_planks", "minecraft:oak_log"), viewModel.state.value.sections.single().inputIds)

        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(listOf("minecraft:spruce_planks", "minecraft:spruce_log"), viewModel.state.value.sections.single().inputIds)

        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(listOf("minecraft:oak_planks", "minecraft:spruce_log"), viewModel.state.value.sections.single().inputIds)
    }

    @Test
    fun aCapturedRecipeShowsOneAlternativePerSlot() = runTest {
        val crafting = RecipeDisplay.Captured(
            key = "minecraft:crafting",
            width = 116,
            height = 54,
            slots = listOf(
                DisplaySlot(x = 1, y = 1, width = 18, height = 18, stacks = listOf(DisplayStack("minecraft:oak_planks", 1), DisplayStack("minecraft:spruce_planks", 1))),
                DisplaySlot(x = 19, y = 1, width = 18, height = 18, stacks = emptyList()),
                DisplaySlot(x = 94, y = 18, width = 26, height = 26, stacks = listOf(DisplayStack("minecraft:stick", 4))),
            ),
        )
        recipes.value = listOf(
            group("minecraft:crafting_shaped", CatalogRecipe(1, "minecraft:stick", listOf(RecipeVariant(crafting, listOf("ATM"))))),
            group("minecraft:stonecutting", recipe("minecraft:stick_from_stone")),
        )
        val viewModel = viewModel()

        val (shaped, stonecutting) = viewModel.state.value.sections
        assertEquals(
            RecipeLayoutUi.Captured(
                key = "minecraft:crafting",
                width = 116,
                height = 54,
                slots = listOf(
                    LayoutSlotUi(x = 1, y = 1, width = 18, height = 18, stack = StackUi("minecraft:oak_planks", 1, itemOf("minecraft:oak_planks"))),
                    LayoutSlotUi(x = 19, y = 1, width = 18, height = 18, stack = null),
                    LayoutSlotUi(x = 94, y = 18, width = 26, height = 26, stack = StackUi("minecraft:stick", 4, itemOf("minecraft:stick"))),
                ),
            ),
            shaped.layout,
        )
        assertIs<RecipeLayoutUi.Generic>(stonecutting.layout, "a recipe without a stored layout stays generic")

        advanceTimeBy(1_000)
        runCurrent()
        val firstSlot = (viewModel.state.value.sections.first().layout as RecipeLayoutUi.Captured).slots.first()
        assertEquals("minecraft:spruce_planks", firstSlot.stack?.itemId, "alternatives cycle on the same clock")
    }

    @Test
    fun stacksShowTheirItemOrJustTheirIdWhenUnresolved() = runTest {
        unknown += "gadgets:widget"
        recipes.value = listOf(
            group(
                "minecraft:crafting_shaped",
                recipe("minecraft:stick", listOf("ATM") to display(inputs = listOf(listOf("gadgets:widget"), emptyList()), results = listOf("minecraft:stick" to 1))),
            ),
        )
        val viewModel = viewModel()

        val layout = viewModel.state.value.sections.single().layout as RecipeLayoutUi.Generic
        assertEquals(listOf(StackUi("gadgets:widget", count = 1, item = null), null), layout.inputs)
        assertEquals(listOf(StackUi("minecraft:stick", count = 1, item = itemOf("minecraft:stick"))), layout.results)
    }

    @Test
    fun theHeaderShowsTheItem() = runTest {
        unknown += "gadgets:widget"

        assertEquals(StackUi("minecraft:stick", count = 1, item = itemOf("minecraft:stick")), viewModel("minecraft:stick").state.value.header)
        assertEquals(StackUi("gadgets:widget", count = 1, item = null), viewModel("gadgets:widget").state.value.header)
    }

    @Test
    fun theUsesTabShowsUsesWithItsOwnPagers() = runTest {
        recipes.value = listOf(group("minecraft:crafting_shaped", recipe("minecraft:stick"), recipe("minecraft:stick_from_bamboo")))
        uses.value = listOf(group("minecraft:crafting_shaped", recipe("minecraft:torch"), recipe("minecraft:ladder")))
        val viewModel = viewModel()
        viewModel.next("minecraft:crafting_shaped")
        runCurrent()

        viewModel.selectTab(ItemTab.Uses)
        runCurrent()

        assertEquals(ItemTab.Uses, viewModel.state.value.tab)
        assertEquals(listOf("minecraft:torch"), viewModel.state.value.sections.map { it.recipeId })
    }
}

/** The stacks the section's generic layout shows in its input slots now. */
private val RecipeSectionUi.inputIds get() = (layout as RecipeLayoutUi.Generic).inputs.map { it?.itemId }

private fun itemOf(id: String) = CatalogItem(releaseId = 1, itemId = id, displayName = id.substringAfter(':'), icon = null)

private fun group(typeId: String, vararg recipes: CatalogRecipe) = RecipeGroup(typeId, recipes.toList())

/** A recipe with one variant per entry of [variants]: its Instance names and how it is drawn. */
private fun recipe(id: String, vararg variants: Pair<List<String>, RecipeDisplay> = arrayOf(listOf("ATM") to display())) =
    CatalogRecipe(releaseId = 1, recipeId = id, variants = variants.map { (names, display) -> RecipeVariant(display, names) })

/** A generic display taking one stack per input slot (alternatives within a slot) and making [results]. */
private fun display(inputs: List<List<String>> = listOf(listOf("minecraft:oak_planks")), results: List<Pair<String, Int>> = listOf("minecraft:stick" to 4)) =
    RecipeDisplay.Generic(
        inputs = inputs.map { alternatives -> alternatives.map { DisplayStack(it, 1) } },
        results = results.map { (item, count) -> DisplayStack(item, count) },
    )

