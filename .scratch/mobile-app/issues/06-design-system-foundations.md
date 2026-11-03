# Design system foundations: GUI scale, textures, Monocraft, core components

Status: ready-for-agent
Spec: ../spec.md

## What

In `ui/designsystem`, using only Compose foundation, ui and runtime. Remove the commented-out `material3` line from `shared/build.gradle.kts`.

- **`LocalGuiScale`**: a whole number picked from the window width so that about 320 GUI pixels fit across. Make that target a single constant that's easy to tune. Add `Int.gpx` / `Float.gpx` helpers that turn GUI pixels into `Dp` so that each GUI pixel covers exactly `scale` physical pixels.
- **Textures**:
  - They come from `composeResources/drawable/`, named after Minecraft's sprite paths with `/` replaced by `_`. The user supplies the files.
  - They are drawn at native proportions × scale, with `FilterQuality.None`.
  - No nine-slice: every component has a fixed size in GUI pixels.
  - Until the real files arrive, use a placeholder texture set made of solid-colour PNGs of the right sizes, so the build works.
- **Font**: Monocraft (OFL), stored as `composeResources/font/monocraft.ttf` with its `OFL.txt`. `McTextStyle` sizes are whole multiples of the GUI scale.
- **Components**:
  - `McPanel`, `McButton` (normal, pressed and disabled states), `McTextField`, `McSlot` (an 18×18 frame around a 16×16 content area);
  - `ItemIcon(png: ByteArray?)`, which decodes with `decodeToImageBitmap()` and caches by item key, falling back to a placeholder;
  - `McDropdown`, `McTabs`, `McPager` (◀ ▶ and an "n / N" counter), `McProgressBar`, `McText`;
  - `McBottomBar`: the root tab bar, one item per tab with an icon and a label, and selected and unselected states.
- A `@Preview` gallery screen showing every component.

## Acceptance

- Each component renders in the preview gallery at GUI scales 2, 3 and 4 with crisp pixels, meaning no blur from smoothing.
- `ItemIcon` shows the placeholder for `null` and for undecodable bytes.
- The iOS framework still compiles.
- Note in the Comments section any component where fixed proportions don't fit in practice. Those are the candidates for nine-slice.
