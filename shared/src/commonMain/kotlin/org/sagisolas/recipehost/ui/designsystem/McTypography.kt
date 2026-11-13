/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.ui.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

@Immutable
internal data class McTypography(
    val small: TextStyle,
    val body: TextStyle,
    val title: TextStyle,
) {
    companion object {
        fun of(family: FontFamily) =
            McTypography(
                small = TextStyle(fontFamily = family, fontSize = 12.sp, lineHeight = 16.sp),
                body = TextStyle(fontFamily = family, fontSize = 16.sp, lineHeight = 20.sp),
                title = TextStyle(fontFamily = family, fontSize = 20.sp, lineHeight = 24.sp),
            )
    }
}

/** Defaults to a system monospace font; the app passes its own typography to [McTheme]. */
internal val LocalMcTypography = staticCompositionLocalOf { McTypography.of(FontFamily.Monospace) }
