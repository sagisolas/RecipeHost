# Inventory button: Platform hooks + PairingButton + ButtonRowLayout

Status: ready-for-agent
Blocked by: 02
Spec: ../spec.md

## What

- Add `Platform.onInventoryInit(BiConsumer<InventoryScreen, Consumer<AbstractWidget>>)` and `Platform.onWorldLeave(Runnable)`:
  - Fabric: a mixin on `InventoryScreen#init` TAIL and a mixin on `Minecraft#disconnect` HEAD;
  - NeoForge: `ScreenEvent.Init.Post` and `ClientPlayerNetworkEvent.LoggingOut`.
- Add the common mixin `AbstractContainerScreenAccessor` (`leftPos`, `topPos`) in `recipehost-common.mixins.json`, registered by both loaders.
- `ButtonRowLayout.slotX` (pure) starts at `leftPos + 126`, steps 22 px, and continues past the panel edge when the row is full.
- `PairingButton` is a vanilla `Button` labelled `QR`, 20×18, with a literal tooltip. It repositions every frame in `renderWidget`. Clicking it opens the QR screen; until issue 04 lands, that is a placeholder.
- `RecipeHost.init` wires both hooks.

## Acceptance

- `ButtonRowLayoutTest` covers:
  - an empty row;
  - the first slot taken;
  - a gap between taken slots;
  - a partial overlap;
  - overflow past the panel edge;
  - the same result when the occupied list is shuffled.
- Smoke test on both loaders:
  - the button appears;
  - it follows the recipe-book toggle;
  - it steps aside for another widget.
