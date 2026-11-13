/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import org.jetbrains.compose.resources.Font
import org.sagisolas.recipehost.shared.generated.resources.Res
import org.sagisolas.recipehost.shared.generated.resources.monocraft
import org.sagisolas.recipehost.ui.designsystem.McTheme
import org.sagisolas.recipehost.ui.designsystem.McTypography

/** The app's [McTheme], with Monocraft from the app's resources. */
@Composable
internal fun AppTheme(content: @Composable () -> Unit) {
    val monocraft = FontFamily(Font(Res.font.monocraft))
    val typography = remember(monocraft) { McTypography.of(monocraft) }
    McTheme(typography = typography, content = content)
}
