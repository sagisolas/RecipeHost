# SQLDelight for the Catalog

The `Catalog` is stored in SQLite through **SQLDelight**, which uses the Android driver now and the native driver on iOS later. Icons are stored as BLOBs in their own `icon` table, keyed like `Item`s by `(Mod release, item id)`. Queries the UI needs are backed by indexes: recipes by result item, recipes by input item, items by `Mod release`, and full-text search over display names.

This goes against the "keep dependencies minimal" rule on purpose. Wire already brings in okio, so storing framed protobuf files and indexing them in memory would have added no dependency at all. That option was rejected because the companion mod expands tags into concrete items, which inflates `Slot`s heavily. A single `#planks` slot becomes about 50 `ItemStack`s, so a large modpack's recipes could reach roughly 50–150 MB serialised, and several times that as Kotlin objects. That is not safe on low-end Android. The additive `Catalog` (ADR 0010) also needs transactional merges and upserts across `Scan`s, which SQLite gives us directly.

**Room KMP** was rejected because it needs KSP and is a heavier toolchain than SQLDelight, which generates code from `.sq` files.

## Considered options

- **An Icon BLOB column on `item`.** This was rejected because Icons arrive after the `Item`s are committed, one IconBatch at a time. Writing them into `item` would rewrite item rows and fire the full-text index triggers once per Icon. It would also leave no place for an Icon whose `Item` isn't in the `Catalog`. A separate table keyed the same way joins just as cheaply.

## Consequences

- Android's minimum SDK (24) ships SQLite 3.9. That rules out `INSERT … ON CONFLICT DO UPDATE`, so upserts are written as an `UPDATE` followed by `INSERT OR IGNORE`. It also rules out FTS5, so full-text search uses FTS4. Foreign-key cascades are avoided too, because they need a pragma on every connection, so child rows are deleted explicitly.
- Android reads query rows through a 2 MB `CursorWindow`, and a row bigger than that throws `SQLiteBlobTooBigException`. A `Slot` that accepts any item expands to tens of thousands of `ItemStack`s, which makes a recipe body of about 3 MB, so the mod sends each `Slot` with at most its first 2048 alternatives (`RecipeAssembly.MAX_ALTERNATIVES`). The cap is applied on the mod side so the alternatives it drops are never transferred, and nothing on the wire marks a `Slot` as cut. In a large modpack about 250 recipes have more than 1,024 distinct input items, and one has 3,727 (a decorative-block cutter); the "any item" `Slot`s that caused the crash had 44,000–64,000. A capped `Slot` takes about 70 KB of a body, so a row only reaches 2 MB with about 15 capped `Slot`s, counting `inputs` and `layout_stacks` together. An item past the cap doesn't list that recipe among the recipes that use it. A `Catalog` that already holds an oversized recipe keeps it until the next `Scan` of that `Instance` replaces it.
- SQLDelight generates public classes, so the `Catalog`'s table and query types show up in the iOS framework's exported API. The services that use them stay `internal`.
