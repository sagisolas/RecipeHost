# Design system foundations: Monocraft, core components with flat-colour backgrounds

Status: ready-for-agent
Spec: ../spec.md

## What

In `ui/designsystem`, using only Compose foundation, ui and runtime. Remove `material3` from `shared/build.gradle.kts` and the version catalog.

- **No GUI scale.** Components have a fixed aspect ratio and a fixed size in dp, like any mobile UI. They don't scale with the screen. Larger screens get different layouts.
- **Backgrounds**: each component draws its background from a `Painter` that Compose stretches to the component's bounds. For now every background is a flat colour (`ColorPainter`). The real textures and their sizes are issue 11.
- **States**: `McButton` has separate looks for normal, pressed and disabled, driven by the Compose `Indication` / `IndicationNodeFactory` API reading the `InteractionSource`, not by `collectIsPressedAsState` recomposition.
- **Font**: Monocraft (OFL), stored as `composeResources/font/monocraft.ttf` with its `OFL.txt`. `McTypography` sizes are ordinary `sp`.
- **Components**:
  - `McPanel`, `McButton`, `McTextField`, `McSlot` (a frame around a smaller content area);
  - `McDropdown`, `McTabs`, `McPager` (◀ ▶ and an "n / N" counter), `McProgressBar`, `McText`;
  - `McBottomBar`: the root tab bar, one item per tab with an icon and a label, and selected and unselected states.
- A `@Preview` gallery screen showing every component.
- **Item Icons** live outside the design system, in `ui/item`, because they are recipe-viewer code: `ItemIcon(key, png: ByteArray?, cache)`, which decodes with `decodeToImageBitmap()` through `ItemIconCache` (cached by item key) and falls back to the skin's missing texture.

## Acceptance

- Each component renders in the preview gallery, with its Monocraft text.
- Pressed and disabled buttons look different from normal ones.
- `ItemIcon` shows the placeholder for `null` and for undecodable bytes.
- The iOS framework still compiles.
- Note in the Comments section any component where fixed proportions don't fit in practice. Those are the candidates for nine-slice.

## Comments

- Implemented with flat colours (`McSkin.flat()`) and placeholder sizes (`McDimens`); textures and real sizes are issue 11.
- Nine-slice candidates (components that stretch with fixed proportions not fitting):
  - `McPanel`, `McTabs`, `McBottomBar` and `McProgressBar` take their width from the layout (panel also its height from content), so their texture will stretch.
  - `McButton`, `McDropdown` and `McTextField` keep Minecraft's 10:1 ratio, so their height follows their width: a 240dp-wide button is only 24dp tall, under the 48dp touch target. Either fix the dp height and let the texture stretch horizontally (nine-slice) or pick sizes in issue 11.
