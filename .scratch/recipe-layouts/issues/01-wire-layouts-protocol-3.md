# Wire: RecipeLayout, RecipeSlot, LayoutBatch, protocol 3

Status: ready-for-agent
Spec: ../spec.md

## What

- In the proto:
  - Add `RecipeLayout` and `RecipeSlot` (with a `Role` enum: `INPUT`, `OUTPUT`, `CATALYST`) as in the spec's "Wire changes".
  - Add `LayoutBatch` as `Envelope.layout_batch = 8`.
  - Add `Recipe.layout_stacks = 6` (`repeated Slot`).
- Bump `WireProtocol.PROTOCOL_VERSION` to 3. The mobile app accepts only 3 and rejects anything else with its existing domain error.
- Add `Iterator<LayoutBatch> layoutBatches()` to `SnapshotSource`. `SnapshotSender` streams it after the icons and before `Done`. `MinecraftSnapshotSource` returns an empty iterator for now.
- Make the mobile app's `ScanIngest` tolerate `LayoutBatch` by ignoring it until issue 06.

## Acceptance

- `:protocol` round-trip test for `LayoutBatch` and `Recipe.layout_stacks`.
- `SnapshotSender` test: layout batches come after icon batches and before `Done`.
- The app's `SessionClient` test for a version mismatch is updated to expect 3.
