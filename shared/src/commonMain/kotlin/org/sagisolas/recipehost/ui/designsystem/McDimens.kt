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
    /** A list scrollbar is 6 texture pixels wide, as in Minecraft. */
    val ScrollbarWidth = 12.dp
    /** Space a list keeps between its content, or its scrollbar, and its left and right edges. */
    val ListEdgePadding = 8.dp
    /** Space between a scrollbar and the content beside it, and the list's top and bottom. */
    val ScrollbarGap = 4.dp
    /** Minecraft's minimum of 32 texture pixels, so a long list's thumb stays easy to see. */
    val ScrollbarMinThumbHeight = 64.dp
    /**
     * Room a scrollbar takes at a list's right edge: the gap from the content, the bar, then the
     * edge padding. Content stays this far in, and a touch anywhere in it goes to the scrollbar.
     */
    val ScrollbarGutter = ScrollbarGap + ScrollbarWidth + ListEdgePadding
    val BottomBarHeight = 64.dp
    val BottomBarIconSize = 24.dp
}
