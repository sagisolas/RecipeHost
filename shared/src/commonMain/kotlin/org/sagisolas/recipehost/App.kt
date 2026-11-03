package org.sagisolas.recipehost

import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.sagisolas.recipehost.ui.AppTheme
import org.sagisolas.recipehost.ui.designsystem.McGallery

/** Shows the design-system gallery until the app shell (issue 07) replaces it. */
@Composable
fun App() {
    AppTheme {
        McGallery(Modifier.safeContentPadding())
    }
}
