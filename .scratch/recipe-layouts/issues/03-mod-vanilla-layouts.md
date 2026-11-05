# Mod: vanilla layouts when EMI is absent

Status: ready-for-agent
Blocked by: 02
Spec: ../spec.md

## What

- `VanillaLayouts` produces layout jobs for these types:
  - shaped, keyed by width×height (`minecraft:crafting_shaped/3x3`);
  - shapeless, keyed by its input count rounded up to a 2×2 or 3×3 grid;
  - smelting, blasting, smoking and campfire, keyed by type plus cook time and XP, which are baked in as game-font text;
  - smithing;
  - stonecutter.
- It draws with vanilla GUI sprites through `GuiGraphics`, so resource packs apply. This is its own renderer, not an emulation of EMI.
- `RecipeMapper` fills `Recipe.layout_stacks` in the layout's slot order and registers the recipe's id with its layout job.
- Shell recipes get no layout.

## Acceptance

- `RecipeMapperTest`: `layout_stacks` line up with each vanilla layout's slot order, including an empty grid cell in a shaped recipe.
- The parameter keys are tested as pure functions.
- Manual (user): with no EMI, check 2×2 and 3×3 crafting, a furnace, smithing and the stonecutter in the app, both with and without a resource pack.
