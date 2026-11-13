/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.ui.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

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

/**
 * Draws [texture] as a nine-slice behind the content, over the component's own bounds. Draw-only:
 * unlike `Modifier.paint(sizeToIntrinsics = false)`, which raises the minimum size to the maximum,
 * it never changes the component's size.
 */
internal fun Modifier.mcBackground(texture: McTexture): Modifier = drawBehind { with(texture.painter) { draw(size) } }

/**
 * Draws [image] repeated from the top-left corner behind the content, each texture pixel
 * [McDimens.TexturePixelSize] wide, the way Minecraft draws its dirt list backgrounds.
 */
internal fun Modifier.mcTiledBackground(image: ImageBitmap): Modifier =
    drawBehind {
        val pixel = McDimens.TexturePixelSize.toPx()
        val tile = IntSize((image.width * pixel).roundToInt(), (image.height * pixel).roundToInt())
        clipRect {
            for (y in 0 until size.height.toInt() step tile.height) {
                for (x in 0 until size.width.toInt() step tile.width) {
                    drawImage(image, dstOffset = IntOffset(x, y), dstSize = tile, filterQuality = FilterQuality.None)
                }
            }
        }
    }
