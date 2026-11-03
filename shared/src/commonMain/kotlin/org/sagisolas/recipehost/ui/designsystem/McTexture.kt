package org.sagisolas.recipehost.ui.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp

/**
 * A GUI texture, always drawn as a nine-slice: [border] (in texture pixels) keeps its proportions
 * at [pixelSize] per texture pixel and the rest stretches to the component's bounds.
 */
@Immutable
internal class McTexture(
    image: ImageBitmap,
    border: SliceInsets,
    pixelSize: Dp,
) {
    internal val painter: Painter = NineSlicePainter(image, border, pixelSize)
}

/** A texture per interaction state, drawn by [McStateIndication]; `null` draws nothing. */
@Immutable
internal data class McStateTextures(
    val normal: McTexture?,
    val pressed: McTexture?,
    val disabled: McTexture?,
) {
    companion object {
        fun all(texture: McTexture?) = McStateTextures(texture, texture, texture)
    }
}

/** Draws [texture] as a nine-slice behind the content. */
internal fun Modifier.mcBackground(texture: McTexture): Modifier =
    paint(texture.painter, sizeToIntrinsics = false, contentScale = ContentScale.FillBounds)
