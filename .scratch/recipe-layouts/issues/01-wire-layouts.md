# Wire: RecipeLayout, RecipeSlot, LayoutBatch

Status: done
Spec: ../spec.md

## What

- In the proto:
  - Add `RecipeLayout` and `RecipeSlot` (with a `Role` enum: `INPUT`, `OUTPUT`, `CATALYST`) as in the spec's "Wire changes".
  - Add `LayoutBatch` as `Envelope.layout_batch = 8`.
  - Add `Recipe.layout_stacks = 6` (`repeated Slot`).
- `WireProtocol.PROTOCOL_VERSION` stays at 2. Nothing is published yet, so these changes amend v2 (see the spec).
- Add `Iterator<LayoutBatch> layoutBatches()` to `SnapshotSource`. `SnapshotSender` streams it after the icons and before `Done`. `MinecraftSnapshotSource` returns an empty iterator for now.
- Make the mobile app's `ScanIngest` tolerate `LayoutBatch` by ignoring it until issue 06.

## Acceptance

- `:protocol` round-trip test for `LayoutBatch` and `Recipe.layout_stacks`.
- `SnapshotSender` test: layout batches come after icon batches and before `Done`.
- `ScanIngest` test: a stream with a `LayoutBatch` still completes.
