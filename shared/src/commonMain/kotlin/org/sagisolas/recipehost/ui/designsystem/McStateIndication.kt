package org.sagisolas.recipehost.ui.designsystem

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import kotlinx.coroutines.launch

/**
 * An [androidx.compose.foundation.Indication] that draws the background for the current
 * interaction state: [McStateTextures.disabled] when not [enabled], [McStateTextures.pressed]
 * while any press is active, otherwise [McStateTextures.normal].
 *
 * Pass a remembered `InteractionSource` to `clickable` with it: with `null`, `clickable`
 * creates the indication only on first press, so nothing would be drawn before that.
 */
internal data class McStateIndication(
    private val textures: McStateTextures,
    private val enabled: Boolean,
) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        McStateIndicationNode(interactionSource, textures, enabled)
}

private class McStateIndicationNode(
    private val interactionSource: InteractionSource,
    private val textures: McStateTextures,
    private val enabled: Boolean,
) : Modifier.Node(), DrawModifierNode {
    private var pressed = false

    override fun onAttach() {
        coroutineScope.launch {
            val presses = mutableListOf<PressInteraction.Press>()
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> presses += interaction
                    is PressInteraction.Release -> presses -= interaction.press
                    is PressInteraction.Cancel -> presses -= interaction.press
                }
                val nowPressed = presses.isNotEmpty()
                if (nowPressed != pressed) {
                    pressed = nowPressed
                    invalidateDraw()
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        val texture = when {
            !enabled -> textures.disabled
            pressed -> textures.pressed
            else -> textures.normal
        }
        if (texture != null) with(texture.painter) { draw(size) }
        drawContent()
    }
}
