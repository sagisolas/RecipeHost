# Mod: EMI layout capture

Status: done
Blocked by: 02, 04
Spec: ../spec.md

## What

- `CapturingWidgetHolder implements WidgetHolder` (`getWidth`, `getHeight`, `add`) records the widgets from `EmiRecipe.addWidgets`.
- Each recipe becomes a "hash after render" layout job of size `getDisplayWidth()` × `getDisplayHeight()`. The job draws each widget as follows:
  - **`SlotWidget`** (Jemi slots and tanks included): it becomes a `RecipeSlot` from `getBounds()`.
    - Role: `OUTPUT` if `getRecipe() != null`. `CATALYST` if it's readable from the protected `catalyst` field, otherwise `INPUT`.
    - Drawing: `drawBackground` only. For non-item stacks (fluids, chemicals), `drawStack` is drawn too, so their contents are baked in.
    - Stacks: the item stacks of `getStack()` go into `layout_stacks`.
  - **`GeneratedSlotWidget`**: `getStack()` is sampled once.
  - **`FillingArrowWidget`**: only the empty arrow is drawn.
  - **Other animated textures** (a furnace's flame) aren't drawn, so the static texture under them shows: they follow the wall clock, and equal layouts would differ.
  - **Everything else** renders as it is, text included, on the GUI's light panel grey (`0xFFC6C6C6`), with a 4 GUI px border of it around the recipe's display area; slots are shifted to match.
- Slots are read once `addWidgets` returns, not in `add`: EMI configures a slot after adding it (`addSlot(...).large(true).recipeContext(this)`).
- `LayoutRenderer` clips each job with a raw GL scissor in sheet pixels. A widget that calls `GuiGraphics.enableScissor` gets a box computed from the window's GUI scale, and its `disableScissor` turns the job's clip off. Handle that here, for example by wrapping the `GuiGraphics` passed to widgets.
- If `addWidgets` throws, or a widget throws while drawing, the recipe has no layout. The failure is logged once per category.

## Acceptance

- Unit test for `CapturingWidgetHolder` with fake widgets: slot order, roles and bounds are recorded, and non-slot widgets are kept in order.
- Manual (user):
  - EMI vanilla recipes look like EMI's;
  - a Create pack's mixing recipe shows its heat and fluid tanks;
  - a JEI-only mod's recipe shows its background.
