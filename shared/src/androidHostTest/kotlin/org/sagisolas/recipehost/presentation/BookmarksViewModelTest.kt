package org.sagisolas.recipehost.presentation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.sagisolas.recipehost.domain.catalog.CatalogItem
import org.sagisolas.recipehost.domain.catalog.StackResolution
import org.sagisolas.recipehost.domain.recipes.BookmarkedRecipe
import org.sagisolas.recipehost.domain.recipes.DisplayStack
import org.sagisolas.recipehost.domain.recipes.RecipeDisplay
import org.sagisolas.recipehost.domain.recipes.RecipeVariant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class BookmarksViewModelTest {
    private val bookmarked = MutableStateFlow<List<BookmarkedRecipe>>(emptyList())
    private val removed = mutableListOf<String>()

    /** Every id resolves to an Item named after its path, except those in [unknown]. */
    private val unknown = mutableSetOf<String>()

    @BeforeTest
    fun setMain() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(): BookmarksViewModel {
        val viewModel =
            BookmarksViewModel(
                bookmarkedRecipes = bookmarked,
                resolveStacks = { ids -> MutableStateFlow(ids.associateWith(::resolutionOf)) },
                removeBookmark = { removed += it },
            )
        backgroundScope.launch { viewModel.state.collect {} }
        runCurrent()
        return viewModel
    }

    private fun resolutionOf(id: String): StackResolution =
        if (id in unknown) StackResolution.Unresolved(id) else StackResolution.Resolved(itemOf(id))

    private fun TestScope.bookmarks() = viewModel().state.value.bookmarks!!

    @Test
    fun aBookmarkIsHeadedByItsFirstResultWithoutCountAndItsTypeName() =
        runTest {
            bookmarked.value =
                listOf(
                    bookmark(
                        "minecraft:stick",
                        typeName = "Crafting",
                        listOf("ATM") to display(results = listOf("minecraft:stick" to 4, "minecraft:bowl" to 1)),
                    ),
                )

            val bookmark = bookmarks().single()

            assertEquals(StackUi("minecraft:stick", count = 1, item = itemOf("minecraft:stick")), bookmark.result)
            assertEquals("stick", bookmark.title)
            assertEquals("Crafting", bookmark.subtitle)
        }

    @Test
    fun anUnresolvedResultIsTitledWithItsIdAndANamelessTypeWithItsTypeId() =
        runTest {
            unknown += "create:cogwheel"
            bookmarked.value =
                listOf(
                    bookmark(
                        "create:cogwheel",
                        typeName = null,
                        listOf("ATM") to display(results = listOf("create:cogwheel" to 1)),
                    ),
                )

            val bookmark = bookmarks().single()

            assertEquals("create:cogwheel", bookmark.title)
            assertEquals(TYPE_ID, bookmark.subtitle)
        }

    @Test
    fun aRecipeWithNoResultsIsTitledWithItsType() =
        runTest {
            bookmarked.value =
                listOf(bookmark("emi:info/stick", typeName = "Info", listOf("ATM") to display(results = emptyList())))

            val bookmark = bookmarks().single()

            assertNull(bookmark.result)
            assertEquals("Info", bookmark.title)
            assertNull(bookmark.subtitle)
        }

    @Test
    fun bookmarksKeepTheirOrder() =
        runTest {
            bookmarked.value = listOf(bookmark("minecraft:torch"), bookmark("minecraft:stick"))

            assertEquals(listOf("minecraft:torch", "minecraft:stick"), bookmarks().map { it.recipeId })
        }

    @Test
    fun selectingAVariantShowsItsResultAndLayout() =
        runTest {
            bookmarked.value =
                listOf(
                    bookmark(
                        "minecraft:stick",
                        typeName = "Crafting",
                        listOf("Vanilla+") to
                            display(inputs = listOf("minecraft:bamboo"), results = listOf("minecraft:stick" to 1)),
                        listOf("ATM", "Old") to
                            display(inputs = listOf("minecraft:oak_planks"), results = listOf("minecraft:torch" to 1)),
                    ),
                )
            val viewModel = viewModel()
            val first =
                viewModel.state.value.bookmarks!!
                    .single()
            assertEquals(listOf(listOf("Vanilla+"), listOf("ATM", "Old")), first.variants)
            assertEquals(0, first.selectedVariant)
            assertEquals(listOf("minecraft:bamboo"), first.inputIds)

            viewModel.selectVariant("minecraft:stick", 1)
            runCurrent()

            val second =
                viewModel.state.value.bookmarks!!
                    .single()
            assertEquals(1, second.selectedVariant)
            assertEquals("minecraft:torch", second.result?.itemId)
            assertEquals(listOf("minecraft:oak_planks"), second.inputIds)
        }

    @Test
    fun aRecipeWithOneBodyOffersNoVariants() =
        runTest {
            bookmarked.value = listOf(bookmark("minecraft:stick"))

            assertEquals(emptyList(), bookmarks().single().variants)
        }

    @Test
    fun removeDeletesTheBookmark() =
        runTest {
            bookmarked.value = listOf(bookmark("minecraft:stick"))
            val viewModel = viewModel()

            viewModel.remove("minecraft:stick")
            runCurrent()

            assertEquals(listOf("minecraft:stick"), removed)
        }

    @Test
    fun noBookmarksIsAnEmptyListOnceLoaded() =
        runTest {
            assertEquals(emptyList(), bookmarks())
        }
}

private const val TYPE_ID = "minecraft:crafting_shaped"

/** The stacks the bookmark's generic layout shows in its input slots now. */
private val BookmarkUi.inputIds get() = (layout as RecipeLayoutUi.Generic).inputs.map { it?.itemId }

private fun itemOf(id: String) =
    CatalogItem(releaseId = 1, itemId = id, displayName = id.substringAfter(':'), icon = null)

/** A bookmarked recipe with one variant per entry of [variants]: its Instance names and how it is drawn. */
private fun bookmark(
    id: String,
    typeName: String? = "Crafting",
    vararg variants: Pair<List<String>, RecipeDisplay> = arrayOf(listOf("ATM") to display()),
) = BookmarkedRecipe(
    recipeId = id,
    typeId = TYPE_ID,
    typeName = typeName,
    variants = variants.map { (names, display) -> RecipeVariant(display, names) },
)

/** A generic display taking one stack per input slot and making [results]. */
private fun display(
    inputs: List<String> = listOf("minecraft:oak_planks"),
    results: List<Pair<String, Int>> = listOf("minecraft:stick" to 4),
) = RecipeDisplay.Generic(
    inputs = inputs.map { listOf(DisplayStack(it, 1)) },
    results = results.map { (item, count) -> DisplayStack(item, count) },
)
