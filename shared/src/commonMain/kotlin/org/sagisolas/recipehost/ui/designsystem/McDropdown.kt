package org.sagisolas.recipehost.ui.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

/**
 * A button showing the selected option; tapping it opens a list of [options] below it.
 * Size it by width, like [McButton].
 */
@Composable
internal fun <T> McDropdown(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    var anchorSize by remember { mutableStateOf(IntSize.Zero) }
    Box(modifier.onSizeChanged { anchorSize = it }) {
        McPressable(
            onClick = { expanded = true },
            painters = McTheme.skin.button,
            modifier = Modifier.aspectRatio(McDimens.WIDGET_ASPECT_RATIO),
            enabled = enabled,
        ) {
            val color = McTheme.colors.onDark(enabled)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                McText(label(selected), Modifier.weight(1f), color = color, maxLines = 1)
                McText("▼", color = color)
            }
        }
        if (expanded) {
            Popup(
                offset = IntOffset(0, anchorSize.height),
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true),
            ) {
                val width = with(LocalDensity.current) { anchorSize.width.toDp() }
                McPanel(Modifier.width(width), contentPadding = PaddingValues(4.dp)) {
                    Column {
                        options.forEach { option ->
                            McPressable(
                                onClick = {
                                    expanded = false
                                    onSelect(option)
                                },
                                painters = McTheme.skin.dropdownOption,
                                modifier = Modifier.fillMaxWidth(),
                                role = Role.DropdownList,
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                McText(
                                    text = label(option),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
