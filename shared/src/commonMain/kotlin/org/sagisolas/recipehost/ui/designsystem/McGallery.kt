package org.sagisolas.recipehost.ui.designsystem

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Every design-system component on one screen, for eyeballing looks and states.
 */
@Composable
internal fun McGallery(modifier: Modifier = Modifier) {
    Column(Modifier.fillMaxSize().mcBackground(McTheme.skin.screen).then(modifier)) {
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            McPanel(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    McText("McText title", style = McTheme.typography.title)
                    McText("McText body: The quick brown fox jumps over the lazy dog")
                    McText("McText small", style = McTheme.typography.small)
                }
            }

            Section("McButton") {
                var clicks by remember { mutableIntStateOf(0) }
                McButton("Clicked $clicks times", onClick = { clicks++ }, Modifier.width(240.dp))
                McButton("Disabled", onClick = {}, Modifier.width(240.dp), enabled = false)
                McButton("Wraps its text", onClick = {})
                McButton("Full width", onClick = {}, Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    McSquareButton("✓", onClick = {})
                    McSquareButton("✓", onClick = {}, enabled = false)
                }
            }

            Section("McTextField") {
                var text by remember { mutableStateOf("") }
                McTextField(text, { text = it }, Modifier.width(240.dp), hint = "Search items")
                McTextField("Disabled", {}, Modifier.width(240.dp), enabled = false)
            }

            Section("McSlot") {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    McSlot()
                    McSlot {
                        Image(
                            McTheme.skin.missingTexture,
                            contentDescription = null,
                            modifier = Modifier.size(McDimens.SlotContentSize),
                            filterQuality = FilterQuality.None,
                        )
                    }
                }
            }

            Section("McDropdown") {
                val releases = listOf("6.0.1 · MC 1.21.1", "5.4.0 · MC 1.20.1", "4.9.2 · MC 1.19.2")
                var selected by remember { mutableStateOf(releases.first()) }
                McDropdown(releases, selected, { selected = it }, { it }, Modifier.width(240.dp))
            }

            Section("McTabs") {
                var tab by remember { mutableIntStateOf(0) }
                McTabs(listOf("Recipes", "Uses"), tab, { tab = it })
            }

            Section("McPager") {
                var page by remember { mutableIntStateOf(2) }
                McPager(page, 12, onPrevious = { page-- }, onNext = { page++ })
            }

            Section("McSlotGrid") {
                McSlotGrid(Modifier.fillMaxWidth().height(160.dp), verticalPadding = 4.dp) {
                    items(200) { McSlot() }
                }
            }

            Section("McProgressBar") {
                var progress by remember { mutableFloatStateOf(0.4f) }
                McProgressBar(progress)
                McButton("+10%", onClick = { progress = (progress + 0.1f) % 1.1f }, Modifier.width(120.dp))
            }
        }

        var tab by remember { mutableIntStateOf(0) }
        val missingTexture = McTheme.skin.missingTexture
        val icon = remember(missingTexture) { BitmapPainter(missingTexture, filterQuality = FilterQuality.None) }
        McBottomBar(
            items = listOf(McBottomBarItem("Items", icon), McBottomBarItem("Settings", icon)),
            selectedIndex = tab,
            onSelect = { tab = it },
        )
    }
}

@Composable
private fun Section(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        McText(title, color = McTheme.colors.textOnDark, style = McTheme.typography.small)
        content()
    }
}

@Preview
@Composable
private fun McGalleryPreview() {
    McTheme { McGallery() }
}
