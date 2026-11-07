# Mod: LayoutRenderer pipeline and LayoutBatch streaming

Status: done
Blocked by: 01
Spec: ../spec.md

## What

- `LayoutRenderer` is an instance owned by `MinecraftSnapshotSource`, next to `IconRenderer`. It takes layout jobs, each with:
  - a size, in GUI pixels;
  - a draw callback against `GuiGraphics`;
  - its `RecipeSlot`s;
  - either a parameter key or "hash after render";
  - the recipe ids that use it.
- Jobs are shelf-packed onto a shared offscreen sheet. Each sheet is read back once, and each layout is cropped and encoded to its own PNG with `PngEncoder`.
- Hash keys are SHA-256 over the cropped RGBA pixels plus the slot rectangles, hex-encoded and truncated to 128 bits. Jobs that share a parameter key are merged before rendering, so the key is drawn once and its entry lists all their recipe ids.
- Each key is sent once per Snapshot with its `png`. Later recipes that resolve to the same key are sent as an entry with `recipe_ids` only.
- It runs on the render thread under the same per-frame time budget as icons, and starts only after the last icon batch.
- If a job's draw callback throws, that job is dropped, its recipes ship without a layout, and the failure is logged once per `type_id`.
- Draws use mouse `-1, -1`.

## Acceptance

- `ShelfPackerTest`: varied sizes fit without overlap, and a layout taller than the sheet's free space starts a new sheet.
- `LayoutKeysTest`: the hash is stable for equal pixels plus rectangles and changes when one rectangle moves.
- A test with a fake renderer and job source: a repeated key is sent once with its `png`, then as `recipe_ids`-only entries.
