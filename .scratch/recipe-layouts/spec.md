# Recipe layouts captured from the game

Status: ready-for-agent

## Problem

The mobile-app spec defers how each recipe type looks: every `Recipe` is shown in a generic row (inputs, arrow, results). Drawing each type by hand in the app only covers vanilla, and modded or datapack types can't be known in advance (ADR 0002). Players expect a recipe to look the way it does in game, in EMI or JEI.

## Solution

The companion mod renders each recipe's GUI offscreen and sends it as a `Recipe layout`. A layout is an image of everything the game draws, except the Items inside the `Recipe slot`s, plus the slot rectangles. The mobile app draws the image as it is, places `ItemIcon`s in the slots, and keeps them tappable. A recipe without a layout keeps the generic row.

There are two sources:
- **EMI loaded:** every recipe EMI knows (vanilla, modded, and JEI-only through Jemi) is captured from its widgets. This is also when ADR 0008's optional EMI layer lands, so EMI supplies the recipe semantics too.
- **EMI absent:** the companion mod draws its own layouts for the vanilla-dispatch types.

Decisions are in ADR 0015, which amends ADR 0008. Vocabulary is in `GLOSSARY.md` (`Recipe layout`, `Recipe slot`).

## Wire changes (amend protocol version 2)

- New `RecipeLayout { string key; bytes png; uint32 width; uint32 height; repeated RecipeSlot slots; repeated string recipe_ids; }`. `width` and `height` are in GUI pixels. `recipe_ids` lists the recipes in this `Snapshot` that use the layout. A recipe id appears under at most one layout per `Snapshot`.
- `RecipeSlot { uint32 x; uint32 y; uint32 width; uint32 height; Role role; }`, where `Role` is `INPUT`, `OUTPUT` or `CATALYST`. The order is significant: recipes fill the slots by index.
- `Recipe.layout_stacks = 6` is a `repeated Slot`, one entry per slot of the recipe's layout, in order. It reuses the wire `Slot` (a list of alternatives) and is empty when the recipe will have no layout. `inputs` and `results` stay as they are and remain the semantic truth.
- The recipe doesn't name its layout: an EMI layout's key is only known once it has been rendered, and that happens after the recipes are committed. The layout names its recipes instead.
- New `LayoutBatch { repeated RecipeLayout layouts; }` as `Envelope.layout_batch = 8`. It is sent after all `RecipeBatch`es, interleaved with or after the `IconBatch`es, and each key's `png` is sent once per `Snapshot` (a later entry for that key carries only more `recipe_ids`).
- `WireProtocol.PROTOCOL_VERSION` stays at 2. Neither the mod nor the app has been published, so these changes amend v2 instead of bumping the version.
- `SnapshotSource` gains `Iterator<LayoutBatch> layoutBatches()`, and `SnapshotSender` streams it after icons.

## Implementation decisions

### Mod: rendering pipeline (`:mod:common`)

- **`LayoutRenderer`:** an instance owned by `MinecraftSnapshotSource`, like `IconRenderer`.
  - It renders variable-size layouts onto a shared offscreen sheet with shelf packing, reads the sheet back once, then crops and encodes each layout to its own PNG with `PngEncoder`.
  - It runs on the render thread under the same per-frame time budget as Icons, starting only after the last `IconBatch` has been produced.
  - Mouse position is passed as `-1, -1` so no hover highlight is drawn.
- **Dedup:** each layout source supplies a key.
  - Vanilla layouts use a parameter key, e.g. `minecraft:crafting_shaped/3x3` or `minecraft:smelting/200/0.1`. A key that was already rendered isn't rendered again.
  - EMI layouts are rendered, then keyed by a hash of the cropped pixels and the slot rectangles (SHA-256, hex-encoded, truncated to 128 bits).
  - `RecipeBatch`es go out before any rendering, carrying only `layout_stacks`. Rendering runs afterwards. Each `LayoutBatch` entry carries the ids of the recipes that produced it. If a later recipe renders to an already-sent key, that key is sent again with only `recipe_ids` and an empty `png`.
- **Failures:** if a recipe's layout throws or renders nothing, that recipe ships without a layout. The failure is logged once per `type_id`.

### Mod: vanilla layouts (EMI absent)

- `VanillaLayouts` draws shaped (by width×height), shapeless, the four cooking types, smithing and stonecutter.
  - It uses vanilla GUI sprites through `GuiGraphics`, so resource packs apply.
  - It draws text (cook time, XP) in the game font, in the game locale.
  - It is a renderer of its own, separate from EMI capture.
- Each layout lists its `RecipeSlot`s. `RecipeMapper` fills `layout_stacks` from the same data it already uses for `inputs`/`results`.
- Shell recipes for unknown types get no layout.

### Mod: EMI source (soft dependency)

- `compileOnly` the EMI API (the xplat Mojmap artifact) in `:mod:common`. All EMI-touching classes sit behind a check that EMI is loaded (via `Platform` mod list), so the mod loads without EMI.
- **Readiness:** if EMI is loaded but `EmiReloadManager.isLoaded()` (internal API, guarded) is false, the QR screen doesn't open. A chat message ("Recipes are still loading, try again in a moment") is shown instead.
- **Recipes:** every recipe from `EmiApi.getRecipeManager().getRecipes()` becomes a wire `Recipe`:
  - `type_id` is the category id.
  - `inputs` come from `getInputs()`, `results` from `getOutputs()`, item stacks only.
  - `id` is `getId()`. A null id becomes `<category ns>:<hash of the encoded body>`, and synthetic `ns:/path` ids are kept as they are.

  The vanilla dispatch isn't used when EMI is loaded.
- **Capture:** `CapturingWidgetHolder implements WidgetHolder` (`getWidth`, `getHeight`, `add`) collects the widgets from `addWidgets`. Then, for each widget:
  - `SlotWidget` (Jemi slots and tanks included) becomes a `RecipeSlot` from `getBounds()`. Its role is `OUTPUT` if `getRecipe() != null`. `CATALYST` comes from the protected `catalyst` field, read through a small subclass check or left as `INPUT` if unreadable (the implementer decides). Its stacks are the item stacks of `getStack()`.

    Only `drawBackground` is drawn into the image, except for non-item stacks (fluids, chemicals): there `drawStack` is drawn too, so their contents are baked in.
  - `GeneratedSlotWidget`: `getStack()` is sampled once.
  - `FillingArrowWidget`: only the empty arrow is drawn.
  - Every other widget, text and other animated textures included, renders as it is.

### Mobile app

- **Catalog:**
  - `recipe_layout(key PK, png BLOB, width, height, slots BLOB)`, where `slots` is the encoded list of `RecipeSlot`s.
  - `recipe` gains `layout_key` (nullable), and the encoded body keeps `layout_stacks`.
  - The commit after the last `RecipeBatch` clears `layout_key` on the recipes it replaces.
  - `ScanIngest` upserts each `LayoutBatch` as it arrives, after the commit (like Icons). It stores the layout when `png` is present, and sets `layout_key` on this Scan's recipes whose ids it lists (scoped to this Instance and the Scan's releases).
  - After the Scan completes, or drops, it deletes layouts no recipe references.
  - Clear Catalog deletes them too.
- **Rendering:** the `RecipeLayout` seam from mobile-app issue 09 gains a captured implementation, chosen whenever the recipe has a layout and that layout is present in the Catalog.
  - The image is drawn at the largest whole-number scale that fits the section width, with nearest-neighbour filtering.
  - Each `RecipeSlot` rectangle (scaled the same way) shows an `ItemIcon` with its count, cycling alternatives on the screen's shared clock. No `McSlot` chrome is drawn, because the image already has it.
  - Tapping a slot opens that Item.
  - Layout images are decoded off the main thread and cached by key in an app-scoped `LayoutImageCache`. Keys are immutable, so the cache needs no invalidation.
- Until a recipe's layout arrives, and for recipes without one, the generic layout is used.

## Out of scope

- Widget tooltips and long-press info.
- Tappable or searchable fluids and other non-item stacks.
- Animating arrows, flames and other animated widgets in the app.
- Monocraft for text inside layouts (the text is baked in the game font).
- Re-rendering layouts without a full `Snapshot`.

## Testing

- **Mod, pure:**
  - `LayoutKeysTest`: vanilla parameter keys, and the content hash being stable for equal pixels plus rectangles and different when a rectangle moves.
  - `ShelfPackerTest`: layouts of varied size fit into a sheet without overlap.
  - `RecipeMapperTest`: `layout_stacks` line up with the vanilla layouts' slot order.
  - EMI id fallback: a null id gives `<ns>:<hash>`, and the same body gives the same id.
- **Mod, `:protocol`:** round-trip of `LayoutBatch` and of the new `Recipe` fields.
- **App:**
  - `ScanIngestTest`: layouts arriving after the commit are stored; a drop during layouts keeps the ones that arrived; unreferenced layouts are deleted after a Scan; a recipe whose layout is missing still loads.
  - Layout scale: the whole-number factor is chosen for given widths, and slot rectangles scale with it.
  - `ItemViewModel`: picks the captured layout when one is present and generic otherwise.
- **Manual (user):**
  - vanilla without EMI: crafting 2×2 and 3×3, furnace, smithing, stonecutter, with a resource pack;
  - with EMI: vanilla recipes, a Create pack (heat, fluids), a JEI-only mod;
  - opening the QR screen right after a resource reload with EMI shows the chat message;
  - a large modpack: time from connect to commit, and from commit to the last layout.

## Issues

1. Wire: `RecipeLayout`, `RecipeSlot`, `LayoutBatch`, the new `Recipe` fields (amending protocol 2).
2. Mod: `LayoutRenderer` pipeline (sheet packing, readback, PNG, keys, render budget after Icons) and streaming `LayoutBatch`.
3. Mod: vanilla layouts when EMI is absent.
4. Mod: EMI as recipe source (soft dependency, readiness chat message, id fallback).
5. Mod: EMI layout capture (`CapturingWidgetHolder`, slot roles, baked fluids, failures).
6. App: Catalog storage and ingest of layouts.
7. App: captured `RecipeLayout` rendering in the item screen.
