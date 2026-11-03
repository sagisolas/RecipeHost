package org.sagisolas.recipehost.ui.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.painter.Painter

/** A background per interaction state, drawn by [McStateIndication]. */
@Immutable
internal data class McStatePainters(
    val normal: Painter,
    val pressed: Painter,
    val disabled: Painter,
) {
    companion object {
        fun all(painter: Painter) = McStatePainters(painter, painter, painter)
    }
}
