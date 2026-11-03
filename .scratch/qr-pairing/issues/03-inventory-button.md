# Inventory button: Platform hooks + PairingButton

Status: claimed
Blocked by: 02
Spec: ../spec.md

## What

- Add `Platform.onInventoryInit(BiConsumer<InventoryScreen, Consumer<AbstractWidget>>)` and `Platform.onWorldLeave(Runnable)`:
  - Fabric: a mixin on `InventoryScreen#init` TAIL and a mixin on world leave;
  - NeoForge: `ScreenEvent.Init.Post` and `ClientPlayerNetworkEvent.LoggingOut`.
- `PairingButton` is a vanilla `Button` labelled `QR`, 20×18, with a literal tooltip.
  - It anchors to vanilla's recipe-book button.
  - It picks a free slot once, at init, from `screen.children()`.
  - It follows the anchor's position each frame.
- `PairingButtonInstaller` (an instance holding the `PairingController`) picks the slot and creates the `PairingButton`. Until issue 04 lands, clicking calls `open()` (the URI goes to the log), then `screenClosed()`, which gives a 60 s window for the probe.
- `RecipeHost.init` wires both hooks.

## Acceptance

- No unit tests (the user decided).
- `:mod:fabric` and `:mod:neoforge` build, and the mixin annotation processor resolves every target.
- In-game check on both loaders:
  - the button appears;
  - it follows the recipe-book toggle;
  - it steps aside for an existing widget;
  - clicking it logs a URI the probe can pull.

## Comments

- Implemented. Both loaders build, and the Fabric refmap resolves `InventoryScreen#init()V` and `Minecraft#clearClientLevel(Screen)V`.
- `ScreenRectangle#intersects` doesn't exist in 1.21.1, so the overlap test uses `AbstractWidget` getters.
- No `leftPos` accessor mixin: the button follows the recipe-book button instead.
- **Pending: the in-game check on both loaders.** The open question is whether `clearClientLevel` runs on every way of leaving a world (singleplayer quit, server disconnect, kick).
