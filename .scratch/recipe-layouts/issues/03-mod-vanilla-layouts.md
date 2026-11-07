# Mod: vanilla layouts when EMI is absent

Status: done
Blocked by: 02
Spec: ../spec.md

## What

- `VanillaLayouts` produces layout jobs for these types:
  - shaped and shapeless share one layout (`minecraft:crafting`). A shaped pattern sits in the 3×3 grid where the vanilla recipe book puts it. A shaped recipe bigger than 3×3, or a shapeless one with more than nine inputs, gets no layout;
  - smelting, blasting, smoking and campfire, keyed by type plus cook time and XP, which are baked in as game-font text;
  - smithing (transform);
  - stonecutter.
- Crafting, cooking and smithing layouts are the game's own container screens. Each screen's `renderBg` is drawn with an empty menu and cropped to the menu's own slots, so the geometry and pixels are vanilla's, and resource packs and mods that restyle those screens apply. Campfires use the furnace screen. The stonecutter screen shows its outputs as a list of buttons, so its layout is hand-drawn from vanilla textures. This is its own renderer, not an emulation of EMI.
- `RecipeMapper` fills `Recipe.layout_stacks` in the layout's slot order and registers the recipe's id with its layout job.
- Shell recipes get no layout.

## Acceptance

- `RecipeMapperTest`: a shaped pattern is placed in the 3×3 grid like the recipe book places it, including an empty grid cell.
- The cooking parameter key is tested as a pure function.
- Manual (user, deferred until issue 07 draws layouts in the app): with no EMI, check 2×2 and 3×3 crafting, a furnace, smithing and the stonecutter in the app, both with and without a resource pack.
