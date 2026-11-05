# App: Catalog storage and ingest of layouts

Status: ready-for-agent
Blocked by: 01
Spec: ../spec.md

## What

- Schema:
  - `recipe_layout(key PK, png BLOB, width, height, slots BLOB)`;
  - `recipe.layout_key` (nullable).
- The commit after the last `RecipeBatch` writes replaced recipes with `layout_key = NULL`.
- `ScanIngest` handles each `LayoutBatch` as it arrives, after the commit:
  - it upserts the layout when `png` is present;
  - it sets `layout_key` on this Scan's recipes listed in `recipe_ids`, scoped to this Instance and the Scan's releases.
- After the Scan ends, normally or by a drop, it deletes layouts no recipe references. Clear Catalog deletes all of them.
- The Recipes and Uses queries return each recipe's layout key, plus the layout's size and slots when the layout is present.

## Acceptance

- `ScanIngestTest`:
  - a layout arriving after the commit is stored and linked;
  - a recipes-only entry links a key that was sent earlier;
  - a drop during layouts keeps what arrived;
  - unreferenced layouts are deleted;
  - another Instance's layouts are untouched.
- Query test: a recipe with a missing layout still loads, with a null layout.
