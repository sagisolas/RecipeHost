# Catalog database size

Two `Catalog`s pulled from a device were measured read-only with `sqlite3` and its `dbstat` table: a small sample (90 MB, below) and a large modpack (2.4 GB, [Large modpack](#large-modpack)). The large one drove the shrink that ADR 0018 and ADR 0019 describe; [What was done](#what-was-done) lists it and its measured result.

## Small sample

A sample `Catalog` (`catalog_db.db`, 90 MB): one Instance scanned with EMI, with 10,178 Items, 33,252 recipes and 3,250 `Recipe layout`s.

Measured 2026-11-13. The recipe bodies have since lost `extras` (commit `6ce0e48`), but it took about 0 MB in this sample.

### Stats

Page size 4096, 21,951 pages, no free pages, `journal_mode = delete`.

| Table, with its indexes | MB | Notes |
|---|---|---|
| `recipe` (+ 3 indexes) | ~43 | 33,252 rows. `body` blobs are 25.5 MB |
| `recipe_input` (+ 2 indexes) | ~20.5 | 163,630 rows, text `item_id` of about 28 chars |
| `recipe_layout` | 10.5 | 3,250 PNGs of about 2.5 KB, average 177×84 GUI px |
| `icon_atlas` | 5.7 | 671 PNGs of about 8 KB for 10,178 Icons |
| `recipe_output` (+ 2 indexes) | ~4.6 | 35,835 rows |
| `item` and the rest | ~3 | `item_fts` takes under 0.3 MB |

Per index:

| Index | MB |
|---|---|
| `sqlite_autoindex_recipe_input_1` (primary key) | 7.4 |
| `recipe_input_by_item` | 6.7 |
| `sqlite_autoindex_recipe_1` (unique key) | 2.9 |
| `recipe_by_recipe_id` | 2.7 |
| `sqlite_autoindex_recipe_output_1` | 1.7 |
| `recipe_by_layout` | 1.5 |
| `recipe_output_by_item` | 1.5 |

#### Recipe bodies

Total 25.5 MB, 767 bytes on average, 75 KB at most. By field:

| Field | MB |
|---|---|
| `layout_stacks` | 11.1 |
| `inputs` | 9.7 |
| `id` | 2.0 |
| `results` | 1.2 |
| `type_id` | 0.7 |
| `extras` | ~0 |

- **Item ids:** item-id strings in slots take 17.1 MB, yet there are only 8,494 distinct ids.
- **Stacks per slot:** median 1, p90 2, p99 24, max 300 (tag alternatives).
- **Compression:** zlib gets 6.8× on each body alone and 12.5× on the whole column.
- **Other `recipe` columns:** `body_hash` is 32 bytes per row (1 MB). `layout_key` is 32 hex chars (1 MB, and 1.5 MB more in its index). `recipe_id` averages 59 chars and is stored in the column, two indexes and the body.

By type:

| `type_id` | Recipes | Avg body (bytes) | Layout keys |
|---|---|---|---|
| `minecraft:crafting` | 8,812 | 1,005 | 2 |
| `create:automatic_shaped` | 5,262 | 1,239 | 85 |
| `create:automatic_shapeless` | 2,732 | 645 | 7 |
| `minecraft:stonecutting` | 1,930 | 570 | 1 |
| `emi:anvil_repairing` | 1,805 | 301 | 1 |
| `create:mixing` | 701 | 347 | 481 |

#### Images

- **Icon atlases:** all have 16 columns of 32 px cells.
  - 15 Icons per atlas on average. 43 atlases hold one Icon, and most hold 2–28.
  - Sheets can hold 128 cells, but the mod's 3 ms per-frame budget ends each sheet first (ADR 0014 amendment).
  - Atlas ids start at 2,378, so earlier Scans replaced many of them.
- **Layouts:** 3,250 keys. 45 PNGs are byte-identical under different keys. `create:mixing` has 481 keys because heat and time are drawn into the image.

## Large modpack

A `Catalog` of one large modpack (`catalog.db`, 2,365 MiB): one Instance with EMI, 296 `Mod release`s, 64,491 Items, 616,754 recipes and 27,904 `Recipe layout`s. Measured 2026-11-14, before ADR 0018. Its largest bodies are over 2 MB, so it predates the 2048-alternative cap (commit `ce52f26`).

Page size 4096, 605,449 pages, no free pages, `auto_vacuum = FULL`.

| Table, with its indexes | MiB | Notes |
|---|---|---|
| `recipe` | 1,077 | `body` blobs are 892 MB: 1,516 bytes on average, 3.7 MB at most |
| `recipe_input` (+ 2 indexes) | 935 | 6.9M rows for 63,841 distinct item ids |
| `recipe` indexes | 147 | `recipe_id` (70 chars on average) is in the unique key and `recipe_by_recipe_id` |
| `recipe_layout` | 83 | 56.5 MB of PNGs |
| `recipe_output` (+ 2 indexes) | 61 | 439,859 rows |
| `icon_atlas` | 41 | 2,704 PNGs, 24 Icons each on average |
| `item`, `icon`, `item_fts` and the rest | ~21 | |

### Recipe bodies

All 616,754 bodies were decoded:

- **Slots repeat.** The bodies held 4.58M Slots, but only 100,259 distinct ones. Stored, their stacks took 786 MB; distinct, 33 MB.
- **Item ids.** Item-id strings took 721 MB of the bodies, for 63,841 distinct ids.
- **`layout_stacks`** took 430 MB, of which 365 MB were Slots that also appear in the same recipe's `inputs`.
- **`recipe_input` is lopsided.** 17k recipes (2.8%) made 74% of its rows: MineColonies' crafters, Create's mechanical crafting, `ars_nouveau:apparatus_enchanting` and `domum_ornamentum:architects_cutter`.
- **The 2048-alternative cap barely helps.** It takes `recipe_input` from 6.90M to 6.52M rows.
- **Compression.** zlib gets 6.1× per body.

### Recipe layouts

- **Shapes.** The 27,904 keys fall into 864 shapes (type, size and `Recipe slot`s), and 16,919 layouts are used by one recipe each.
- **No exact duplicates.** No layout is pixel-identical to its shape's most-used one.
- **Animation frames.** Create's machines (`create:automatic_shaped` alone has 8,117 keys) differ only in a cog or blaze frame, 1–2% of their pixels.
- **Real per-recipe content.** Other types differ by what the recipe draws, such as `generatorgalore:fluid_fuels`' rate and burn-time text, or fluids in slot backgrounds.
- **How much differs.** Compared with its shape's base in its bounding box, the area that differs is under 5% for 6,416 layouts, 5–20% for 10,457, 20–50% for 9,206 and more than half for 961.

## What was done

Done on 2026-11-14. Ideas 1–3 below are done in a different form: the app interns Slots instead of changing the wire, and layouts are patched rather than re-encoded.

- **ADR 0018, app schema only.**
  - Each distinct Slot is a `slot` row and each item id an `item_name` row; a recipe's body lists its Slots and results by row id.
  - `recipe_input` is replaced by `recipe_slot` and `slot_item`. Both link tables are `WITHOUT ROWID` with integer keys.
  - `recipe_id` is indexed by a 64-bit hash, `type_id` is interned, `body_hash` is gone, and layouts are referenced by integer id.
  - Unreferenced Slots, item ids and type keys are deleted after each Scan.
- **ADR 0019, mod and wire (protocol 4).**
  - The first EMI layout of each shape is its base.
  - Every later one sends the base's key plus a `LayoutPatch` of the 8×8 tiles that differ, or nothing if none differ.
  - The app stores patches in `layout_patch` and draws them over the base.

Measured result for the large modpack. The recipe tables were rebuilt from `catalog.db` in the final schema by a script that mirrors `RecipeStore`; the layout figures come from diffing every stored PNG against its shape's base in 8×8 tiles.

| Part | Before (MiB) | After (MiB) |
|---|---|---|
| Recipes: `recipe`, `recipe_input`/`recipe_slot`, `recipe_output`, Slots, item ids, with indexes | 2,282 | 178 |
| Layout images (PNG bytes) | 56.5 | 25.7 (1.5 of bases, 24.2 of patches) |
| Icon atlases | 41 | 41 (unchanged) |
| Whole file | 2,365 | about 270 |

After the change, the recipe tables break down like this:

| Table, with its indexes | MiB |
|---|---|
| `recipe` | 73 |
| `recipe_slot` (+ index by recipe), 1.66M rows | 42 |
| `recipe_by_recipe_id` (64-bit hash) | 11 |
| `slot_item` | 10.5 |
| `slot` (+ unique stacks) | 9.5 |
| `recipe_by_instance_release` | 9 |
| `recipe_output` (+ index by recipe) | 11 |
| `recipe_by_layout` | 7 |
| `item_name` (+ unique name) | 6 |

`recipe` is now mostly its `recipe_id` text (41 MB). Bodies are 21 bytes on average and 17 KB at most.

## Shrink ideas, largest payoff first

These ideas came from the small sample, before the work above. 1–3 are done in another form (see above), and 5 is already in place: the large Catalog has `auto_vacuum = FULL`.

1. **Recipe bodies (25.5 MB).** Protocol change.
   - `layout_stacks` mostly repeats `inputs` in slot order. A slot-to-input index instead would roughly halve the bodies.
   - Replace item-id strings with integer indices (per batch or Catalog-wide). They are 17.1 MB of the bodies for 8,494 distinct ids.
   - Compress bodies. zlib gets about 6.8× per row. A zstd dictionary trained on bodies would come close to the column's 12.5× while still decoding one body at a time. Android has `java.util.zip`; iOS has libz or libcompression.
   - Drop the `id` and `type_id` copies from the body, since both have their own columns.
2. **`recipe_input` and `recipe_output` (~25 MB).** The text `item_id` is stored three times: in the table, the primary-key index and `*_by_item`.
   - `WITHOUT ROWID` makes the primary key the table itself, which removes one copy (about 6–7 MB).
   - Integer item keys would shrink the rest about 4×.
3. **`recipe` columns and indexes.**
   - Reorder the unique key to `(recipe_id, release_id, instance_id)` and drop `recipe_by_recipe_id` (2.7 MB).
   - Reference layouts by an integer row id instead of the 32-char hex `layout_key`, which is about 2.5 MB counting its index.
   - Shorten `body_hash` to 8–16 bytes.
4. **Images (16 MB).** Mod change.
   - Palette (indexed) PNGs and a higher compression level in `PngEncoder`. GUI art has few colours, so this usually saves 30–50%.
   - Store Icons at 16 px instead of 32 px cells if the app scales them when drawing. That's 4× fewer pixels.
   - Fill Icon sheets across several frames, or merge consecutive sheets before encoding. This saves little space but means about 9× fewer atlases to decode and cache.
5. **Housekeeping.** SQLite never shrinks the file by itself, and rescans churn atlases and layouts. Use `auto_vacuum = INCREMENTAL` with an occasional `incremental_vacuum`, or run `VACUUM` after a Scan.

Ideas 2, 3 and 5 touch only the app's schema. Ideas 1 and 4 change the mod, the wire format or both.

### Still open

- **Freeze animations when drawing layouts.** Patches that only carry a cog or blaze frame are waste (ADR 0019).
- **Icon atlases (41 MB)**, idea 4: palette PNGs and fuller sheets.
- **`recipe_id` text (41 MB).** It could drop its namespace, which the release already implies.
- **Lifting `MAX_ALTERNATIVES`.** Storage no longer needs it (ADR 0018).
