package org.sagisolas.recipehost

import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.sagisolas.recipehost.ui.designsystem.McGallery
import org.sagisolas.recipehost.ui.designsystem.McTheme

/** Shows the design-system gallery until the app shell (issue 07) replaces it. */
@Composable
fun App() {
    McTheme {
        McGallery(Modifier.safeContentPadding())
    }
}
