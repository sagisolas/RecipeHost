# Inventory button: Platform hooks + PairingButton

Status: resolved
Blocked by: 02
Spec: ../spec.md

## What

- Add `Platform.onInventoryInit(BiConsumer<InventoryScreen, Consumer<AbstractWidget>>)` and `Platform.onWorldLeave(Runnable)`:
  - Fabric: a mixin on `InventoryScreen#init` TAIL and a mixin on world leave;
  - NeoForge: `ScreenEvent.Init.Post` and `ClientPlayerNetworkEvent.LoggingOut`.
- `PairingButtonInstaller` (an instance holding the `PairingController`) places a 20×20 `ImageButton` (sprite only, no button background) in the top-left corner of the screen at (4, 4), stepping right past existing widgets. The icon is the sprite `recipehost:pairing_button` in `assets/recipehost/textures/gui/sprites/`. Until issue 04 lands, clicking calls `open()` (the URI goes to the log), then `screenClosed()`, which gives a 60 s window for the probe.
- `RecipeHost.init` wires both hooks.

## Acceptance

- No unit tests (the user decided).
- `:mod:fabric` and `:mod:neoforge` build, and the mixin annotation processor resolves every target.
- In-game check on both loaders:
  - the button appears;
  - it sits in the screen's top-left corner and shows the icon;
  - it steps aside for an existing widget;
  - clicking it logs a URI the probe can pull.

## Comments

- Implemented. Both loaders build, and the Fabric refmap resolves `InventoryScreen#init()V` and `Minecraft#clearClientLevel(Screen)V`.
- `ScreenRectangle#intersects` doesn't exist in 1.21.1, so the overlap test uses `AbstractWidget` getters.
- No `leftPos` accessor mixin was needed.
- **Pending: the in-game check on both loaders.** The open question is whether `clearClientLevel` runs on every way of leaving a world (singleplayer quit, server disconnect, kick).
- Moved from the recipe-book row to the screen's top-left corner with a custom icon (user request). The anchor and per-frame repositioning are gone.
- Icon moved to the conventional sprite directory after fabric-api was adopted (ADR 0009). `PairingButton` and `PairingIcon` were replaced by vanilla `ImageButton`, which draws no button background.
