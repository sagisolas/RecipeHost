# Design system: real GUI textures and sizes

Status: needs-info
Blocked by: 06
Spec: ../spec.md

## What

Issue 06 ships every component with generated placeholder textures (bevelled frames in `composeResources/drawable/`, mapped in `rememberMcSkin()` in `McSkin.kt`) and placeholder sizes (`McDimens`). This issue replaces both with the user-supplied textures.

- **Files**: the user supplies PNGs in `shared/src/commonMain/composeResources/drawable/`, named after Minecraft's sprite paths with `/` replaced by `_` (e.g. `widget_button.png`, `widget_button_highlighted.png`, `widget_button_disabled.png`, `container_slot.png`). They are large textures with the component's proportions.
- **Skin**: overwrite the placeholder PNGs with the real ones (or rename the mapping in `rememberMcSkin()` where the real sprite names differ) and set each texture's nine-slice border there.
- **Borders and sizes**: give each texture its nine-slice border in texture pixels (Minecraft's `.png.mcmeta` `gui.scaling.border` values) and one `McDimens.TexturePixelSize` so all borders look equally thick. Set each `McDimens` value (widget height, slot, tab, bottom bar, progress bar, square button) to suit the real textures.
- **Check**: walk the `McGallery` preview and a device with each texture in place.

## Open questions for the user

- The full list of sprite files and which component/state each maps to (normal / pressed / disabled for buttons, tabs and bottom-bar items; focused and disabled for the text field), with each one's nine-slice border.
- Target dp size of each component (e.g. button height), so `McDimens` can be filled in.

## Acceptance

- Every component in the gallery draws its real texture with no smoothing blur.
- Corners and borders keep their proportions at every component size in the gallery.
