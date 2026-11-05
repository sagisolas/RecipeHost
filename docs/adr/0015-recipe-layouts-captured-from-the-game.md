# Recipe layouts are captured from the game's GUI, not drawn per type in the app

How a `Recipe` looks is decided by the companion mod, not the mobile app. The mod renders each recipe's GUI offscreen (on the render thread, sharing the Icon renderer's per-frame budget, after all Icons) into a **Recipe layout**: a PNG of everything the game draws — backgrounds, arrows, text, fluids — except the Items inside the slots, plus an ordered list of **Recipe slots** (rectangle in GUI pixels and a role: input, output or catalyst). The app draws the image at the largest whole-number scale that fits, nearest-neighbour, and places `ItemIcon`s and counts in the slot rectangles; it draws no slot chrome of its own. A recipe without a layout falls back to the generic row layout.

Drawing each recipe type by hand in the app was rejected: modded and datapack types are open-ended (ADR 0002), so only vanilla types would ever look right, and every mod's GUI would have to be re-implemented. Capturing reuses the work mods already did for EMI/JEI and follows the player's resource pack and game locale (ADR 0004).

## Two sources, two renderers

- **EMI loaded**: every recipe EMI knows (vanilla included, and JEI-only ones through EMI's Jemi layer) is captured by passing our own `WidgetHolder` to `EmiRecipe.addWidgets`. Slot widgets are drawn with `drawBackground` only; their bounds and role become Recipe slots. Every other widget, text included, is drawn into the image — one code path, no special-casing of text. Filling arrows show their empty frame; other animated textures show whatever frame is current. Generated slots are sampled once. A recipe whose widgets throw offscreen ships without a layout, logged once per type.
- **EMI absent**: the companion mod draws its own layouts for the vanilla-dispatch types (ADR 0008) with vanilla GUI sprites. This is a separate renderer, not an emulation of EMI's widgets.

If EMI is loaded but has not finished loading its recipes, opening the QR screen is refused with a chat message, rather than silently snapshotting with the vanilla path and replacing the Instance's EMI recipes.

## Sharing and transport

Many recipes share one layout: the layout holds the image and slot geometry, each `Recipe` carries one stack list per layout slot index. Layouts are deduplicated in the mod: vanilla layouts by a parameter key (type, grid size, baked text), EMI layouts by a hash of the rendered pixels and slot rectangles — a parameter key is unsafe there because mod widgets draw things type and input count don't describe (heat, energy, category text), and a collision would silently show the wrong picture. EMI recipes are rendered in batched sheets with one readback, as Icons are.

Layouts travel after the recipes in their own `LayoutBatch`, one PNG per layout (no atlas: they vary in size and the item screen draws few at a time, so ADR 0014's reasoning does not apply). Each layout lists the ids of the recipes that use it, rather than each recipe naming its layout: an EMI layout's key only exists once it has been rendered. That way the commit after the last `RecipeBatch` doesn't wait for rendering; a drop during layouts keeps what arrived. The Catalog stores layouts by key and deletes those no recipe references after a Scan. Protocol version 3.

## Consequences

- `Recipe.inputs`/`results` stay the semantic truth for Recipes and Uses; Recipe slots duplicate the stacks for presentation.
- Text in captured layouts uses the game's font, not Monocraft, and differing text (cook times) makes otherwise identical layouts distinct.
- Fluids and other non-item stacks are visible only as pixels: not tappable, not searchable.
- EMI recipes with a null id are attributed to their category id's namespace, with a fabricated id `<category ns>:<body hash>` so it is stable across Scans (ADR 0011).
- Tooltips on widgets are not captured in the first slice.
