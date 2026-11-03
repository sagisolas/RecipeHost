package org.sagisolas.recipehost.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import org.jetbrains.compose.resources.Font
import org.sagisolas.recipehost.shared.generated.resources.Res
import org.sagisolas.recipehost.shared.generated.resources.monocraft
import org.sagisolas.recipehost.ui.designsystem.McSkin
import org.sagisolas.recipehost.ui.designsystem.McTheme
import org.sagisolas.recipehost.ui.designsystem.McTypography

/** The app's [McTheme]: Monocraft from the app's resources and the app's skin. */
@Composable
internal fun AppTheme(content: @Composable () -> Unit) {
    val monocraft = FontFamily(Font(Res.font.monocraft))
    val typography = remember(monocraft) { McTypography.of(monocraft) }
    val skin = remember { McSkin.flat() }
    McTheme(typography = typography, skin = skin, content = content)
}
