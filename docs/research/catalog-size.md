# Catalog database size

A sample `Catalog` pulled from a device (`catalog_db.db`, 90 MB) was measured read-only with `sqlite3` and its `dbstat` table. It is one Instance scanned with EMI: 10,178 Items, 33,252 recipes and 3,250 `Recipe layout`s.

Measured 2026-11-13. The recipe bodies have since lost `extras` (commit `6ce0e48`), but it took about 0 MB in this sample.

## Stats

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

### Recipe bodies

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

### Images

- **Icon atlases:** all have 16 columns of 32 px cells.
  - 15 Icons per atlas on average. 43 atlases hold one Icon, and most hold 2–28.
  - Sheets can hold 128 cells, but the mod's 3 ms per-frame budget ends each sheet first (ADR 0014 amendment).
  - Atlas ids start at 2,378, so earlier Scans replaced many of them.
- **Layouts:** 3,250 keys. 45 PNGs are byte-identical under different keys. `create:mixing` has 481 keys because heat and time are drawn into the image.

## Shrink ideas, largest payoff first

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
