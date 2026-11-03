package org.sagisolas.recipehost.ui.designsystem

import androidx.compose.ui.unit.dp

/** Fixed sizes. Components don't scale with the screen; larger screens get different layouts. */
internal object McDimens {
    /** Size on screen of one texture pixel, shared by every texture so all borders look alike. */
    val TexturePixelSize = 2.dp
    /** Height of a button, dropdown and text field; their width is up to the layout. */
    val WidgetHeight = 40.dp
    /** Space between a widget's edge and its text. */
    val WidgetPadding = 8.dp
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
