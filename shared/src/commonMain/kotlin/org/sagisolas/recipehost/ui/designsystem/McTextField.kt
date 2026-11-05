package org.sagisolas.recipehost.ui.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor

/**
 * A single-line text field [McDimens.WidgetHeight] tall, showing [value] and reporting edits
 * through [onValueChange]. Give it a width.
 */
@Composable
internal fun McTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    hint: String = "",
    enabled: Boolean = true,
) {
    var focused by remember { mutableStateOf(false) }
    val colors = McTheme.colors
    val skin = McTheme.skin
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .height(McDimens.WidgetHeight)
            .onFocusChanged { focused = it.isFocused },
        enabled = enabled,
        textStyle = McTheme.typography.body.copy(color = colors.onDark(enabled)),
        singleLine = true,
        cursorBrush = SolidColor(colors.textOnDark),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .mcBackground(
                        when {
                            !enabled -> skin.textFieldDisabled
                            focused -> skin.textFieldFocused
                            else -> skin.textField
                        },
                    )
                    .padding(horizontal = McDimens.WidgetPadding),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty() && hint.isNotEmpty()) {
                    McText(hint, color = colors.textHint, maxLines = 1)
                }
                innerTextField()
            }
        },
    )
}
