package org.sagisolas.recipehost.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.sagisolas.recipehost.ui.designsystem.McButton
import org.sagisolas.recipehost.ui.designsystem.McText
import org.sagisolas.recipehost.ui.designsystem.McTheme

/** One placeholder action button: its label and what it does. */
internal class PlaceholderAction(val label: String, val onClick: () -> Unit)

/** Stands in for a route or screen until its real screen lands (issues 08-10). */
@Composable
internal fun PlaceholderScreen(
    text: String,
    modifier: Modifier = Modifier,
    actions: List<PlaceholderAction> = emptyList(),
) {
    Column(
        modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        McText(text, color = McTheme.colors.textOnDark)
        actions.forEach { McButton(it.label, onClick = it.onClick) }
    }
}
