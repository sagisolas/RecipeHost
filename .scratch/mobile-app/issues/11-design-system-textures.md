# Design system: real GUI textures and sizes

Status: needs-info
Blocked by: 06
Spec: ../spec.md

## What

Issue 06 ships every component with flat-colour backgrounds (`McSkin.flat()`) and placeholder sizes (`McDimens`). This issue replaces both with the user-supplied textures.

- **Files**: the user supplies PNGs in `shared/src/commonMain/composeResources/drawable/`, named after Minecraft's sprite paths with `/` replaced by `_` (e.g. `widget_button.png`, `widget_button_highlighted.png`, `widget_button_disabled.png`, `container_slot.png`). They are large textures with the component's proportions.
- **Skin**: add a composable `McSkin` factory built from `imageResource(...)` wrapped in `BitmapPainter(bitmap, filterQuality = FilterQuality.None)`, one painter per `McSkin` field and state, and pass it to `McTheme(skin = ...)` from `App`. Keep `McSkin.flat()` for previews and tests.
- **Sizes**: set each `McDimens` value (aspect ratios, slot, tab, bottom bar, progress bar, square button) from the real texture proportions. A component draws its texture stretched to its bounds, so its aspect ratio must match the texture or the pixels distort.
- **Check**: walk the `McGallery` preview and a device with each texture in place.

## Open questions for the user

- The full list of sprite files and which component/state each maps to (normal / pressed / disabled for buttons, tabs and bottom-bar items; focused for the text field).
- Target dp size of each component (e.g. button height), so `McDimens` can be filled in.

## Acceptance

- Every component in the gallery draws its real texture with no smoothing blur.
- No component's texture is visibly stretched out of proportion. Any that must stretch go into Comments as nine-slice candidates.
