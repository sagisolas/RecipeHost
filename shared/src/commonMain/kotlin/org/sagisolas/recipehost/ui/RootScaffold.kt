package org.sagisolas.recipehost.ui

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import org.jetbrains.compose.resources.stringResource
import org.sagisolas.recipehost.shared.generated.resources.Res
import org.sagisolas.recipehost.shared.generated.resources.tab_items
import org.sagisolas.recipehost.shared.generated.resources.tab_settings
import org.sagisolas.recipehost.ui.designsystem.McBottomBar
import org.sagisolas.recipehost.ui.designsystem.McBottomBarItem
import org.sagisolas.recipehost.ui.designsystem.McTheme
import org.sagisolas.recipehost.ui.navigation.Navigator
import org.sagisolas.recipehost.ui.navigation.Screen
import org.sagisolas.recipehost.ui.navigation.StackEntry
import org.sagisolas.recipehost.ui.navigation.Tab

/**
 * The bottom-navigation root: a `NavDisplay` of the tabs in use above the [McBottomBar], which
 * stays visible on pushed screens. Back is handled by `NavDisplay`, which calls [Navigator.pop].
 */
@Composable
internal fun RootScaffold(
    navigator: Navigator,
    modifier: Modifier = Modifier,
    screen: @Composable (Screen) -> Unit,
) {
    // Every tab keeps its decorated entries, so a hidden tab keeps its saved state and ViewModels.
    val entries =
        Tab.entries.associateWith { tab ->
            rememberDecoratedNavEntries(
                backStack = navigator.stacks.getValue(tab),
                entryDecorators =
                    listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator(),
                    ),
                // The default content key is the key's toString(), which includes StackEntry's unique id.
                entryProvider = { key -> NavEntry(key) { screen((it as StackEntry).screen) } },
            )
        }
    val items = Tab.entries.map { barItem(it) }
    Column(modifier.fillMaxSize()) {
        NavDisplay(
            entries = navigator.tabsInUse.flatMap { entries.getValue(it) },
            onBack = navigator::pop,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            // Screens swap with no animation. During the predictive back gesture the screen being
            // left stays alone on screen until the gesture commits.
            transitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
            popTransitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
            predictivePopTransitionSpec = { swapAtEnd() },
        )
        McBottomBar(
            items = items,
            selectedIndex = navigator.selectedTab.ordinal,
            onSelect = { navigator.selectTab(Tab.entries[it]) },
        )
    }
}

/**
 * Keeps a tab screen's content below the status bar and clear of display cutouts. Apply it after
 * the screen's background, so the background runs behind the status bar; the bottom bar takes the
 * navigation bar.
 */
@Composable
internal fun Modifier.tabScreenInsetsPadding(): Modifier =
    windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))

/** Shows the new screen and hides the old one only at the very end of the (seeked) transition. */
private fun swapAtEnd(): ContentTransform =
    fadeIn(
        keyframes {
            durationMillis = SWAP_MILLIS
            0f at SWAP_MILLIS - 1
        },
    ) togetherWith
        fadeOut(
            keyframes {
                durationMillis = SWAP_MILLIS
                1f at SWAP_MILLIS - 1
            },
        )

private const val SWAP_MILLIS = 100

/** A tab's entry in the bar. */
@Composable
private fun barItem(tab: Tab): McBottomBarItem {
    // TODO(issue 11): real tab icons; the missing texture stands in until then.
    val missingTexture = McTheme.skin.missingTexture
    val icon = remember(missingTexture) { BitmapPainter(missingTexture, filterQuality = FilterQuality.None) }
    val label =
        when (tab) {
            Tab.Items -> stringResource(Res.string.tab_items)
            Tab.Settings -> stringResource(Res.string.tab_settings)
        }
    return McBottomBarItem(label, icon)
}
