# The Catalog stores each Slot and item id once

The `Catalog` interns the parts of a recipe that repeat. Each distinct `Slot` is one row of `slot`, which holds its alternatives as varint pairs of an `item_name` id and a count. Each item id is one row of `item_name`. A recipe's body is no longer the wire `Recipe`. It lists its input Slots and its `layout_stacks` Slots by row id, plus its results as `(item_name id, count)`. Its `id` and `type_id` live only in their columns. `RecipeStore` writes this form during a Scan's commit and rebuilds wire `Recipe`s when reading, using a few batched lookups per query.

ADR 0016 rejected this "for a storage gain nobody has measured". It has now been measured (`docs/research/catalog-size.md`). In a 2.4 GB `Catalog` of one large modpack, 616k recipes held 4.58M Slots, but only 100k of them were distinct: 786 MB stored, 33 MB distinct. Item-id strings took 721 MB of the 892 MB of bodies, for 64k distinct ids. `recipe_input`, with one row per input item per recipe, was another 935 MB with its indexes. Of its rows, 74% came from the 2.8% of recipes with a tag Slot. With interning, the same recipes take 178 MiB in all.

## How the queries change

- `recipe_input` is replaced by `recipe_slot (slot_id, recipe_pk, input)` and `slot_item (name_id, slot_id)`. The uses of an item go item → Slots → recipes. `recipe_slot` lists every Slot a body names, so that orphaned Slots can be found. `input` marks the ones the recipe consumes, so that an item only in `layout_stacks` (a result slot) is not a use.
- `recipe_output` keys by `item_name` id. Both link tables are `WITHOUT ROWID`, and each has an index by `recipe_pk` for deleting a replaced Instance's rows.
- Equal recipes have equal bodies, because a Slot's row id is canonical within one database. Variants across Instances therefore merge by comparing bodies, and `body_hash` is gone.
- `recipe.recipe_id_hash`, the first 64 bits of the id's SHA-256, replaces the unique key and the text index on `recipe_id`. Those held the 70-character ids twice more. Every lookup by id compares `recipe_id` too, and `bookmark` carries the same hash to join on. Uniqueness of `(release, recipe id, Instance)` is now the ingest's job: a Scan replaces all of an Instance's recipes for each of its releases.
- `type_id` is interned in `recipe_type_key`, and recipes point at `recipe_layout` by an integer id.
- Slots, item ids and type keys that no recipe names are deleted once a Scan ends, along with orphaned layouts and atlases.

## Consequences

- A body now holds only row ids, so no row comes near Android's 2 MB `CursorWindow`. The largest is 17 KB, and a capped Slot row is about 6 KB. The reason ADR 0012 gives for `RecipeAssembly.MAX_ALTERNATIVES` therefore no longer applies to storage. The cap still bounds what goes over the wire, and lifting it is a separate decision.
- A body is meaningless outside its own database. Nothing exports bodies, and `Recipe.ADAPTER` is no longer used on stored recipes.
- There is no migration: the schema changed in place, so an existing install has to clear its app data.
