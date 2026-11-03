# SQLDelight for the Catalog

The `Catalog` is stored in SQLite through **SQLDelight**, which uses the Android driver now and the native driver on iOS later. Icons are stored as BLOB columns next to their `Item`. Queries the UI needs are backed by indexes: recipes by result item, recipes by input item, items by `Mod release`, and full-text search over display names.

This goes against the "keep dependencies minimal" rule on purpose. Wire already brings in okio, so storing framed protobuf files and indexing them in memory would have added no dependency at all. That option was rejected because the companion mod expands tags into concrete items, which inflates `Slot`s heavily. A single `#planks` slot becomes about 50 `ItemStack`s, so a large modpack's recipes could reach roughly 50–150 MB serialised, and several times that as Kotlin objects. That is not safe on low-end Android. The additive `Catalog` (ADR 0010) also needs transactional merges and upserts across `Scan`s, which SQLite gives us directly.

**Room KMP** was rejected because it needs KSP and is a heavier toolchain than SQLDelight, which generates code from `.sq` files.
