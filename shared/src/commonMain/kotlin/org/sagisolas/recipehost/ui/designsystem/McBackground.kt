package org.sagisolas.recipehost.ui.designsystem

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale

/** Draws [painter] stretched to the bounds, behind the content. */
internal fun Modifier.mcBackground(painter: Painter): Modifier =
    paint(painter, sizeToIntrinsics = false, contentScale = ContentScale.FillBounds)
