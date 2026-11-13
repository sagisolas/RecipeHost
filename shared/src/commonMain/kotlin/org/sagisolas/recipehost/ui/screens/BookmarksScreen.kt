/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.sagisolas.recipehost.presentation.BookmarkUi
import org.sagisolas.recipehost.presentation.BookmarksViewModel
import org.sagisolas.recipehost.shared.generated.resources.Res
import org.sagisolas.recipehost.shared.generated.resources.bookmarks_empty
import org.sagisolas.recipehost.shared.generated.resources.bookmarks_remove
import org.sagisolas.recipehost.shared.generated.resources.ic_delete
import org.sagisolas.recipehost.ui.designsystem.McIconButton
import org.sagisolas.recipehost.ui.designsystem.McSlot
import org.sagisolas.recipehost.ui.designsystem.McText
import org.sagisolas.recipehost.ui.designsystem.McTheme
import org.sagisolas.recipehost.ui.designsystem.mcBackground
import org.sagisolas.recipehost.ui.item.IconAtlasCache
import org.sagisolas.recipehost.ui.item.LayoutImageCache
import org.sagisolas.recipehost.ui.item.StackIcon
import org.sagisolas.recipehost.ui.navigation.Navigator
import org.sagisolas.recipehost.ui.navigation.Screen
import org.sagisolas.recipehost.ui.recipe.RecipeLayout
import org.sagisolas.recipehost.ui.recipe.RecipeVariantDropdown
import org.sagisolas.recipehost.ui.tabScreenInsetsPadding

/**
 * The Bookmarks tab's root: every bookmarked recipe, newest first, each headed by its result with
 * a button that removes the bookmark. Tapping a stack opens its Item in this tab.
 */
@Composable
internal fun BookmarksScreen(
    viewModel: BookmarksViewModel,
    navigator: Navigator,
    atlases: IconAtlasCache,
    layoutImages: LayoutImageCache,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val open = { itemId: String -> navigator.push(Screen.Item(itemId)) }
    val bookmarks = state.bookmarks
    Box(modifier.fillMaxSize().mcBackground(McTheme.skin.screen).tabScreenInsetsPadding()) {
        when {
            // Until the first state arrives, the empty hint would flash.
            bookmarks == null -> {
                Unit
            }

            bookmarks.isEmpty() -> {
                McText(
                    stringResource(Res.string.bookmarks_empty),
                    Modifier.align(Alignment.Center).padding(16.dp),
                    color = McTheme.colors.textOnDark,
                    style = McTheme.typography.body.copy(textAlign = TextAlign.Center),
                )
            }

            else -> {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(bookmarks, key = { it.recipeId }) { bookmark ->
                        Bookmark(
                            bookmark,
                            atlases,
                            layoutImages,
                            onRemove = { viewModel.remove(bookmark.recipeId) },
                            onSelectVariant = { viewModel.selectVariant(bookmark.recipeId, it) },
                            onOpen = open,
                        )
                    }
                }
            }
        }
    }
}

/**
 * One bookmarked recipe: its result's Icon, name and recipe type with the remove button at the
 * end, the "From <instance>" choice when the recipe has several bodies, then the recipe.
 */
@Composable
private fun Bookmark(
    bookmark: BookmarkUi,
    atlases: IconAtlasCache,
    layoutImages: LayoutImageCache,
    onRemove: () -> Unit,
    onSelectVariant: (Int) -> Unit,
    onOpen: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            bookmark.result?.let { McSlot { StackIcon(it, atlases) } }
            Column(Modifier.weight(1f)) {
                McText(bookmark.title, color = McTheme.colors.textOnDark, maxLines = 2)
                bookmark.subtitle?.let {
                    McText(it, color = McTheme.colors.textOnDark, style = McTheme.typography.small, maxLines = 1)
                }
            }
            McIconButton(painterResource(Res.drawable.ic_delete), stringResource(Res.string.bookmarks_remove), onRemove)
        }
        RecipeVariantDropdown(bookmark.variants, bookmark.selectedVariant, onSelectVariant)
        RecipeLayout(bookmark.layout, atlases, layoutImages, onOpen)
    }
}
