package org.sagisolas.recipehost.ui.designsystem

import androidx.compose.ui.unit.dp

/** Fixed sizes. Components don't scale with the screen; larger screens get different layouts. */
internal object McDimens {
    /** Width to height of a button, dropdown and text field (Minecraft's 200×20 widget). */
    const val WIDGET_ASPECT_RATIO = 10f
    val SquareButtonSize = 40.dp
    val SlotSize = 36.dp
    /** The slot's frame is 1/18 of its size on each side, so the content area is 16/18 of it. */
    val SlotInset = SlotSize / 18
    val SlotContentSize = SlotSize - SlotInset * 2
    val TabHeight = 40.dp
    val ProgressBarHeight = 6.dp
    val BottomBarHeight = 64.dp
    val BottomBarIconSize = 24.dp
}
